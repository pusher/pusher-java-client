package com.pusher.client.connection.websocket;

import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * A thin wrapper around the WebSocketClient class from the Java-WebSocket
 * library. The purpose of this class is to enable the WebSocketConnection class
 * to be unit tested by swapping out an instance of this wrapper for a mock
 * version.
 */
public class WebSocketClientWrapper extends WebSocketClient {

    private static final String WSS_SCHEME = "wss";
    private WebSocketListener webSocketListener;

    public WebSocketClientWrapper(final URI uri, final Proxy proxy, final WebSocketListener webSocketListener) throws SSLException {
        super(uri);

        if (uri.getScheme().equals(WSS_SCHEME)) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, null, null); // will use java's default
                                                   // key and trust store which
                                                   // is sufficient unless you
                                                   // deal with self-signed
                                                   // certificates

                final SSLSocketFactory factory = sslContext.getSocketFactory();// (SSLSocketFactory)
                                                                               // SSLSocketFactory.getDefault();

                setSocket(factory.createSocket());
            }
            catch (final IOException e) {
                throw new SSLException(e);
            }
            catch (final NoSuchAlgorithmException e) {
                throw new SSLException(e);
            }
            catch (final KeyManagementException e) {
                throw new SSLException(e);
            }
        }
        this.webSocketListener = webSocketListener;
        setProxy(proxy);
    }

    @Override
    public void onOpen(final ServerHandshake handshakeData) {
        if (webSocketListener != null) {
            webSocketListener.onOpen(handshakeData);
        }
    }

    @Override
    public void onMessage(final String message) {
        if (webSocketListener != null) {
            webSocketListener.onMessage(message);
        }
    }

    @Override
    public void onClose(final int code, final String reason, final boolean remote) {
        if (webSocketListener != null) {
            webSocketListener.onClose(code, reason, remote);
        }
    }

    @Override
    public void onError(final Exception ex) {
        if (webSocketListener != null) {
            webSocketListener.onError(ex);
        }
    }

    /**
     * Removes the WebSocketListener so that the underlying WebSocketClient doesn't expose any listener events.
     */
    public void removeWebSocketListener() {
        webSocketListener = null;
    }
}
