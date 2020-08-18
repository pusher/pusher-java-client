package com.pusher.client.channel.impl.message;

import java.util.HashMap;
import java.util.Map;

public class UnsubscribeMessage {

    private String event = "pusher:unsubscribe";
    private Map<String, String> data = new HashMap<>();

    public UnsubscribeMessage(String channelName) {
        data.put("channel", channelName);
    }
}
