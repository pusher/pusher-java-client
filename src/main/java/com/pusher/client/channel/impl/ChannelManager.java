package com.pusher.client.channel.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.pusher.client.AuthorizationFailureException;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.channel.ChannelState;
import com.pusher.client.channel.PrivateEncryptedChannel;
import com.pusher.client.channel.PresenceChannel;
import com.pusher.client.channel.PrivateChannel;
import com.pusher.client.channel.PrivateChannelEventListener;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.connection.impl.InternalConnection;
import com.pusher.client.util.Factory;

public class ChannelManager implements ConnectionEventListener {

    private static final Gson GSON = new Gson();
    private final Map<String, InternalChannel> channelNameToChannelMap = new ConcurrentHashMap<String, InternalChannel>();

    private final Factory factory;
    private InternalConnection connection;

    public ChannelManager(final Factory factory) {
        this.factory = factory;
    }

    public Channel getChannel(String channelName){
        if (channelName.startsWith("private-")){
            throw new IllegalArgumentException("Please use the getPrivateChannel method");
        } else if (channelName.startsWith("presence-")){
            throw new IllegalArgumentException("Please use the getPresenceChannel method");
        }
        return (Channel) findChannelInChannelMap(channelName);
    }

    public PrivateChannel getPrivateChannel(String channelName) throws IllegalArgumentException{
        if (!channelName.startsWith("private-")) {
            throw new IllegalArgumentException("Private channels must begin with 'private-'");
        } else {
            return (PrivateChannel) findChannelInChannelMap(channelName);
        }
    }

    public PrivateEncryptedChannel getPrivateEncryptedChannel(String channelName) throws IllegalArgumentException{
        if (!channelName.startsWith("private-encrypted-")) {
            throw new IllegalArgumentException("Encrypted private channels must begin with 'private-encrypted-'");
        } else {
            return (PrivateEncryptedChannel) findChannelInChannelMap(channelName);
        }
    }

    public PresenceChannel getPresenceChannel(String channelName) throws IllegalArgumentException{
        if (!channelName.startsWith("presence-")) {
            throw new IllegalArgumentException("Presence channels must begin with 'presence-'");
        } else {
            return (PresenceChannel) findChannelInChannelMap(channelName);
        }
    }

    private InternalChannel findChannelInChannelMap(String channelName){
        return channelNameToChannelMap.get(channelName);
    }

    public void setConnection(final InternalConnection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Cannot construct ChannelManager with a null connection");
        }

        if (this.connection != null) {
            this.connection.unbind(ConnectionState.CONNECTED, this);
        }

        this.connection = connection;
        connection.bind(ConnectionState.CONNECTED, this);
    }

    public void subscribeTo(final InternalChannel channel, final ChannelEventListener listener, final String... eventNames) {

        validateArgumentsAndBindEvents(channel, listener, eventNames);
        channelNameToChannelMap.put(channel.getName(), channel);
        sendOrQueueSubscribeMessage(channel);
    }

    public void unsubscribeFrom(final String channelName) {

        if (channelName == null) {
            throw new IllegalArgumentException("Cannot unsubscribe from null channel");
        }

        final InternalChannel channel = channelNameToChannelMap.remove(channelName);
        if (channel == null) {
            return;
        }
        if (connection.getState() == ConnectionState.CONNECTED) {
            sendUnsubscribeMessage(channel);
        }
    }

    @SuppressWarnings("unchecked")
    public void onMessage(final String event, final String wholeMessage) {

        final Map<Object, Object> json = GSON.fromJson(wholeMessage, Map.class);
        final Object channelNameObject = json.get("channel");

        if (channelNameObject != null) {
            final String channelName = (String)channelNameObject;
            final InternalChannel channel = channelNameToChannelMap.get(channelName);

            if (channel != null) {
                channel.onMessage(event, wholeMessage);
            }
        }
    }

    /* ConnectionEventListener implementation */

    @Override
    public void onConnectionStateChange(final ConnectionStateChange change) {

        if (change.getCurrentState() == ConnectionState.CONNECTED) {
            for(final InternalChannel channel : channelNameToChannelMap.values()){
                sendOrQueueSubscribeMessage(channel);
            }
        }
    }

    @Override
    public void onError(final String message, final String code, final Exception e) {
        // ignore or log
    }

    /* implementation detail */

    private void sendOrQueueSubscribeMessage(final InternalChannel channel) {

        factory.queueOnEventThread(new Runnable() {

            @Override
            public void run() {

                if (connection.getState() == ConnectionState.CONNECTED) {
                    try {
                        final String message = channel.getSubscribeMessage();
                        connection.sendMessage(message);
                        channel.updateState(ChannelState.SUBSCRIBE_SENT);
                    } catch (final AuthorizationFailureException e) {
                        handleAuthenticationFailure(channel, e);
                    }
                }
            }
        });
    }

    private void sendUnsubscribeMessage(final InternalChannel channel) {
        factory.queueOnEventThread(new Runnable() {
            @Override
            public void run() {
                connection.sendMessage(channel.getUnsubscribeMessage());
                channel.updateState(ChannelState.UNSUBSCRIBED);
            }
        });
    }

    private void handleAuthenticationFailure(final InternalChannel channel, final Exception e) {

        channelNameToChannelMap.remove(channel.getName());
        channel.updateState(ChannelState.FAILED);

        if (channel.getEventListener() != null) {
            factory.queueOnEventThread(new Runnable() {

                @Override
                public void run() {
                    // Note: this cast is safe because an
                    // AuthorizationFailureException will never be thrown
                    // when subscribing to a non-private channel
                    final ChannelEventListener eventListener = channel.getEventListener();
                    final PrivateChannelEventListener privateChannelListener = (PrivateChannelEventListener)eventListener;
                    privateChannelListener.onAuthenticationFailure(e.getMessage(), e);
                }
            });
        }
    }

    private void validateArgumentsAndBindEvents(final InternalChannel channel, final ChannelEventListener listener, final String... eventNames) {

        if (channel == null) {
            throw new IllegalArgumentException("Cannot subscribe to a null channel");
        }

        if (channelNameToChannelMap.containsKey(channel.getName())) {
            throw new IllegalArgumentException("Already subscribed to a channel with name " + channel.getName());
        }

        for (final String eventName : eventNames) {
            channel.bind(eventName, listener);
        }

        channel.setEventListener(listener);
    }
}
