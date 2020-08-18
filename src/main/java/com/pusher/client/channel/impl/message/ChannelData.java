package com.pusher.client.channel.impl.message;

import com.google.gson.annotations.SerializedName;

public class ChannelData {
    @SerializedName("user_id")
    private String userId;

    public String getUserId() {
        return userId;
    }
}
