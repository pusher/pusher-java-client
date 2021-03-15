package com.pusher.client.connection.websocket;

import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.net.ssl.SSLException;

import org.java_websocket.handshake.ServerHandshake;

import com.google.gson.Gson;

import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.connection.impl.InternalConnection;
import com.pusher.client.util.Factory;

public class WebSocketConnection implements InternalConnection, WebSocketListener {
    private static final Logger log = Logger.getLogger(WebSocketConnection.class.getName());
    private static final Gson GSON = new Gson();

    private static final String INTERNAL_EVENT_PREFIX = "pusher:";
    private static final String PING_EVENT_SERIALIZED = "{\"event\": \"pusher:ping\"}";

    private final Factory factory;
    private final ActivityTimer activityTimer;
    private final Map<ConnectionState, Set<ConnectionEventListener>> eventListeners = new ConcurrentHashMap<ConnectionState, Set<ConnectionEventListener>>();
    private final URI webSocketUri;
    private final Proxy proxy;
    private final int maxReconnectionAttempts;
    private final int maxReconnectionGap;

    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private WebSocketClientWrapper underlyingConnection;
    private String socketId;
    private int reconnectAttempts = 0;


    public WebSocketConnection(
            final String url,
            final long activityTimeout,
            final long pongTimeout,
            int maxReconnectionAttempts,
            int maxReconnectionGap,
            final Proxy proxy,
            final Factory factory) throws URISyntaxException {
        webSocketUri = new URI(url);
        activityTimer = new ActivityTimer(activityTimeout, pongTimeout);
        this.maxReconnectionAttempts = maxReconnectionAttempts;
        this.maxReconnectionGap = maxReconnectionGap;
        this.proxy = proxy;
        this.factory = factory;

        for (final ConnectionState state : ConnectionState.values()) {
            eventListeners.put(state, Collections.newSetFromMap(new ConcurrentHashMap<ConnectionEventListener, Boolean>()));
        }
    }

    /* Connection implementation */

    @Override
    public void connect() {
        factory.queueOnEventThread(new Runnable() {

            @Override
            public void run() {
                if (state == ConnectionState.DISCONNECTED) {
                    tryConnecting();
                }
            }
        });
    }

    private void tryConnecting(){
        try {
            underlyingConnection = factory
                    .newWebSocketClientWrapper(webSocketUri, proxy, WebSocketConnection.this);
            updateState(ConnectionState.CONNECTING);
            underlyingConnection.connect();
        }
        catch (final SSLException e) {
            sendErrorToAllListeners("Error connecting over SSL", null, e);
        }
    }

    @Override
    public void disconnect() {
        factory.queueOnEventThread(new Runnable() {
            @Override
            public void run() {
                if (state == ConnectionState.CONNECTED) {
                    updateState(ConnectionState.DISCONNECTING);
                    underlyingConnection.close();
                }
            }
        });
    }

    @Override
    public void bind(final ConnectionState state, final ConnectionEventListener eventListener) {
        eventListeners.get(state).add(eventListener);
    }

    @Override
    public boolean unbind(final ConnectionState state, final ConnectionEventListener eventListener) {
        return eventListeners.get(state).remove(eventListener);
    }

    @Override
    public ConnectionState getState() {
        return state;
    }

    /* InternalConnection implementation detail */

    @Override
    public void sendMessage(final String message) {
        factory.queueOnEventThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (state == ConnectionState.CONNECTED) {
                        underlyingConnection.send(message);
                    }
                    else {
                        sendErrorToAllListeners("Cannot send a message while in " + state + " state", null, null);
                    }
                }
                catch (final Exception e) {
                    sendErrorToAllListeners("An exception occurred while sending message [" + message + "]", null, e);
                }
            }
        });
    }

    @Override
    public String getSocketId() {
        return socketId;
    }

    /* implementation detail */

    private void updateState(final ConnectionState newState) {
        log.fine("State transition requested, current [" + state + "], new [" + newState + "]");

        final ConnectionStateChange change = new ConnectionStateChange(state, newState);
        state = newState;

        final Set<ConnectionEventListener> interestedListeners = new HashSet<ConnectionEventListener>();
        interestedListeners.addAll(eventListeners.get(ConnectionState.ALL));
        interestedListeners.addAll(eventListeners.get(newState));

        for (final ConnectionEventListener listener : interestedListeners) {
            factory.queueOnEventThread(new Runnable() {
                @Override
                public void run() {
                    listener.onConnectionStateChange(change);
                }
            });
        }
    }

    private void handleEvent(final String event, final String wholeMessage) {
        if (event.startsWith(INTERNAL_EVENT_PREFIX)) {
            handleInternalEvent(event, wholeMessage);
        }
        else {
            factory.getChannelManager().onMessage(event, wholeMessage);
        }
    }

    private void handleInternalEvent(final String event, final String wholeMessage) {
        if (event.equals("pusher:connection_established")) {
            handleConnectionMessage(wholeMessage);
        }
        else if (event.equals("pusher:error")) {
            handleError(wholeMessage);
        }
    }

    @SuppressWarnings("rawtypes")
    private void handleConnectionMessage(final String message) {
        final Map jsonObject = GSON.fromJson(message, Map.class);
        final String dataString = (String)jsonObject.get("data");
        final Map dataMap = GSON.fromJson(dataString, Map.class);
        socketId = (String)dataMap.get("socket_id");

        if(state != ConnectionState.CONNECTED){
            updateState(ConnectionState.CONNECTED);

        }
        reconnectAttempts = 0;
    }

    @SuppressWarnings("rawtypes")
    private void handleError(final String wholeMessage) {
        final Map json = GSON.fromJson(wholeMessage, Map.class);
        final Object data = json.get("data");

        Map dataMap;
        if (data instanceof String) {
            dataMap = GSON.fromJson((String)data, Map.class);
        }
        else {
            dataMap = (Map)data;
        }

        final String message = (String)dataMap.get("message");

        final Object codeObject = dataMap.get("code");
        String code = null;
        if (codeObject != null) {
            code = String.valueOf(Math.round((Double)codeObject));
        }

        sendErrorToAllListeners(message, code, null);
    }

    private void sendErrorToAllListeners(final String message, final String code, final Exception e) {
        final Set<ConnectionEventListener> allListeners = new HashSet<ConnectionEventListener>();
        for (final Set<ConnectionEventListener> listenersForState : eventListeners.values()) {
            allListeners.addAll(listenersForState);
        }

        for (final ConnectionEventListener listener : allListeners) {
            factory.queueOnEventThread(new Runnable() {
                @Override
                public void run() {
                    listener.onError(message, code, e);
                }
            });
        }
    }

    /* WebSocketListener implementation */

    @Override
    public void onOpen(final ServerHandshake handshakeData) {
        // TODO: log the handshake data
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onMessage(final String message) {
        activityTimer.activity();

        factory.queueOnEventThread(new Runnable() {
            @Override
            public void run() {
                final Map<String, String> map = GSON.fromJson(message, Map.class);
                final String event = map.get("event");
                handleEvent(event, message);
            }
        });
    }

    @Override
    public void onClose(final int code, final String reason, final boolean remote) {
        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.RECONNECTING) {
            log.warning("Received close from underlying socket when already disconnected." + "Close code ["
                    + code + "], Reason [" + reason + "], Remote [" + remote + "]");
            return;
        }

        if(!shouldReconnect(code)) {
            updateState(ConnectionState.DISCONNECTING);
        }

        //Reconnection logic
        if(state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING){

            if(reconnectAttempts < maxReconnectionAttempts){
                tryReconnecting();
            }
            else{
                updateState(ConnectionState.DISCONNECTING);
                cancelTimeoutsAndTransitionToDisconnected();
            }
            return;
        }

        if (state == ConnectionState.DISCONNECTING){
            cancelTimeoutsAndTransitionToDisconnected();
        }
    }

    private void tryReconnecting() {
        reconnectAttempts++;
        updateState(ConnectionState.RECONNECTING);
        long reconnectInterval = Math.min(maxReconnectionGap, reconnectAttempts * reconnectAttempts);

        factory.getTimers().schedule(new Runnable() {
            @Override
            public void run() {
                underlyingConnection.removeWebSocketListener();
                tryConnecting();
            }
        }, reconnectInterval, TimeUnit.SECONDS);
    }

    // Received error codes 4000-4099 indicate we shouldn't attempt reconnection
    // https://pusher.com/docs/pusher_protocol#error-codes
    private boolean shouldReconnect(int code) {
        return code < 4000 || code >= 4100;
    }

    private void cancelTimeoutsAndTransitionToDisconnected() {
        activityTimer.cancelTimeouts();

        factory.queueOnEventThread(new Runnable() {
            @Override
            public void run() {
                updateState(ConnectionState.DISCONNECTED);
                factory.shutdownThreads();
            }
        });
        reconnectAttempts = 0;
    }

    @Override
    public void onError(final Exception ex) {
        factory.queueOnEventThread(new Runnable() {
            @Override
            public void run() {
                // Do not change connection state as Java_WebSocket will also
                // call onClose.
                // See:
                // https://github.com/leggetter/pusher-java-client/issues/8#issuecomment-16128590
                // updateState(ConnectionState.DISCONNECTED);
                sendErrorToAllListeners("An exception was thrown by the websocket", null, ex);
            }
        });
    }

    private class ActivityTimer {
        private final long activityTimeout;
        private final long pongTimeout;

        private Future<?> pingTimer;
        private Future<?> pongTimer;

        ActivityTimer(final long activityTimeout, final long pongTimeout) {
            this.activityTimeout = activityTimeout;
            this.pongTimeout = pongTimeout;
        }

        /**
         * On any activity from the server - Cancel pong timeout - Cancel
         * currently ping timeout and re-schedule
         */
        synchronized void activity() {
            if (pongTimer != null) {
                pongTimer.cancel(true);
            }

            if (pingTimer != null) {
                pingTimer.cancel(false);
            }
            pingTimer = factory.getTimers().schedule(new Runnable() {
                @Override
                public void run() {
                    log.fine("Sending ping");
                    sendMessage(PING_EVENT_SERIALIZED);
                    schedulePongCheck();
                }
            }, activityTimeout, TimeUnit.MILLISECONDS);
        }

        /**
         * Cancel any pending timeouts, for example because we are disconnected.
         */
        synchronized void cancelTimeouts() {
            if (pingTimer != null) {
                pingTimer.cancel(false);
            }
            if (pongTimer != null) {
                pongTimer.cancel(false);
            }
        }

        /**
         * Called when a ping is sent to await the response - Cancel any
         * existing timeout - Schedule new one
         */
        private synchronized void schedulePongCheck() {
            if (pongTimer != null) {
                pongTimer.cancel(false);
            }

            pongTimer = factory.getTimers().schedule(new Runnable() {
                @Override
                public void run() {
                    log.fine("Timed out awaiting pong from server - disconnecting");

                    underlyingConnection.removeWebSocketListener();

                    underlyingConnection.close();

                    // Proceed immediately to handle the close
                    // The WebSocketClient will attempt a graceful WebSocket shutdown by exchanging the close frames
                    // but may not succeed if this disconnect was called due to pong timeout...
                    onClose(-1, "Pong timeout", false);
                }
            }, pongTimeout, TimeUnit.MILLISECONDS);
        }
    }
}
