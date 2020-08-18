package com.pusher.client.channel.impl;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import com.pusher.client.AuthorizationFailureException;
import com.pusher.client.Authorizer;
import com.pusher.client.channel.ChannelState;
import com.pusher.client.channel.PrivateChannel;
import com.pusher.client.channel.PrivateChannelEventListener;
import com.pusher.client.channel.SubscriptionEventListener;
import com.pusher.client.channel.impl.message.AuthResponse;
import com.pusher.client.channel.impl.message.SubscribeMessage;
import com.pusher.client.channel.impl.message.TriggerMessage;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.impl.InternalConnection;
import com.pusher.client.util.Factory;

public class PrivateChannelImpl extends ChannelImpl implements PrivateChannel {

    private static final Gson GSON = new Gson();
    private static final String CLIENT_EVENT_PREFIX = "client-";
    private final InternalConnection connection;
    private final Authorizer authorizer;

    protected String channelData;

    public PrivateChannelImpl(final InternalConnection connection, final String channelName,
            final Authorizer authorizer, final Factory factory) {
        super(channelName, factory);
        this.connection = connection;
        this.authorizer = authorizer;
    }

    /* PrivateChannel implementation */

    @Override
    public void trigger(final String eventName, final String data) {

        if (eventName == null || !eventName.startsWith(CLIENT_EVENT_PREFIX)) {
            throw new IllegalArgumentException("Cannot trigger event " + eventName
                    + ": client events must start with \"client-\"");
        }

        if (state != ChannelState.SUBSCRIBED) {
            throw new IllegalStateException("Cannot trigger event " + eventName + " because channel " + name
                    + " is in " + state.toString() + " state");
        }

        if (connection.getState() != ConnectionState.CONNECTED) {
            throw new IllegalStateException("Cannot trigger event " + eventName + " because connection is in "
                    + connection.getState().toString() + " state");
        }

        connection.sendMessage(
                GSON.toJson(
                        new TriggerMessage(eventName, name, data)));
    }

    /* Base class overrides */

    @Override
    public void bind(final String eventName, final SubscriptionEventListener listener) {

        if (!(listener instanceof PrivateChannelEventListener)) {
            throw new IllegalArgumentException(
                    "Only instances of PrivateChannelEventListener can be bound to a private channel");
        }

        super.bind(eventName, listener);
    }

    private String authenticate() {
        try {
            final AuthResponse authResponse = GSON.fromJson(getAuthResponse(), AuthResponse.class);
            channelData = (String) authResponse.getChannelData();

            if (authResponse.getAuth() == null) {
                throw new AuthorizationFailureException("Didn't receive all the fields expected " +
                        "from the Authorizer, expected an auth and shared_secret.");
            } else {
                return authResponse.getAuth();
            }
        }  catch (JsonSyntaxException e) {
            throw new AuthorizationFailureException("Unable to parse response from Authorizer");
        }
    }

    @Override
    public String getSubscribeMessage() {
        return GSON.toJson(
                new SubscribeMessage(name, authenticate(), channelData));
    }

    @Override
    protected String[] getDisallowedNameExpressions() {
        return new String[] {
                "^(?!private-).*",  // double negative, don't not start with private-
                "^private-encrypted-.*"  // doesn't start with private-encrypted-
        };
    }

    /**
     * Protected access because this is also used by PresenceChannelImpl.
     */
    protected String getAuthResponse() {
        final String socketId = connection.getSocketId();
        return authorizer.authorize(getName(), socketId);
    }

    @Override
    public String toString() {
        return String.format("[Private Channel: name=%s]", name);
    }
}
