package com.pusher.client.channel.impl.message;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public class PresenceSubscriptionData {
    @SerializedName("presence")
    public PresenceData presence;

    public List<String> getIds() {
        return presence.ids;
    }

    public Map<String, Object> getHash() {
        return presence.hash;
    }

     static class PresenceData {
        @SerializedName("count")
        public Integer count;
        @SerializedName("ids")
        public List<String> ids;
        @SerializedName("hash")
        public Map<String, Object> hash;
    }
}
