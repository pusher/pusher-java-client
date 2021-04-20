package com.pusher.client;

import com.pusher.client.channel.Channel;
import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.channel.PresenceChannel;
import com.pusher.client.channel.PresenceChannelEventListener;
import com.pusher.client.channel.PrivateChannel;
import com.pusher.client.channel.PrivateChannelEventListener;
import com.pusher.client.connection.Connection;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;

public interface Client {
    Connection getConnection();
    void connect();
    void connect(final ConnectionEventListener eventListener, ConnectionState... connectionStates);
    void disconnect();
    Channel subscribe(final String channelName);
    Channel subscribe(final String channelName, final ChannelEventListener listener, final String... eventNames);
    PrivateChannel subscribePrivate(final String channelName);
    PrivateChannel subscribePrivate(final String channelName, final PrivateChannelEventListener listener,
                                    final String... eventNames);
    PresenceChannel subscribePresence(final String channelName);
    PresenceChannel subscribePresence(final String channelName, final PresenceChannelEventListener listener,
                                      final String... eventNames);
    void unsubscribe(final String channelName);
    Channel getChannel(String channelName);
    PrivateChannel getPrivateChannel(String channelName);
    PresenceChannel getPresenceChannel(String channelName);
}
