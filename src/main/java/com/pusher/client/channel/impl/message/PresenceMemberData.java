package com.pusher.client.channel.impl.message;

import com.google.gson.annotations.SerializedName;

public class PresenceMemberData {
    @SerializedName("user_id")
    private String id;
    @SerializedName("user_info")
    private Object info;

    public String getId() {
        return id;
    }

    public Object getInfo() {
        return info;
    }
}
