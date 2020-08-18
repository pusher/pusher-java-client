package com.pusher.client.channel.impl.message;

import java.util.HashMap;
import java.util.Map;

public class TriggerMessage {

    private String event;
    private String channel;
    private String data;

    public TriggerMessage(String event, String channelName, String data) {
        this.event = event;
        this.channel = channelName;
        this.data = data;
    }
}
