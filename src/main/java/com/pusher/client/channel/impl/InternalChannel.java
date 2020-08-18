package com.pusher.client.channel.impl;

import com.pusher.client.channel.Channel;
import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.channel.ChannelState;
import com.pusher.client.channel.PusherEvent;

public interface InternalChannel extends Channel, Comparable<InternalChannel> {

    String getSubscribeMessage();

    String getUnsubscribeMessage();

    PusherEvent prepareEvent(String event, String message);

    void onMessage(String event, String message);

    void updateState(ChannelState state);

    void setEventListener(ChannelEventListener listener);

    ChannelEventListener getEventListener();
}
