package com.pusher.client.channel.impl;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.pusher.client.AuthorizationFailureException;
import com.pusher.client.Authorizer;
import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.channel.PresenceChannel;
import com.pusher.client.channel.PresenceChannelEventListener;
import com.pusher.client.channel.SubscriptionEventListener;
import com.pusher.client.channel.User;
import com.pusher.client.connection.impl.InternalConnection;
import com.pusher.client.util.Factory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PresenceChannelImpl extends PrivateChannelImpl implements PresenceChannel {

    private static final String MEMBER_ADDED_EVENT = "pusher_internal:member_added";
    private static final String MEMBER_REMOVED_EVENT = "pusher_internal:member_removed";
    private static final Gson GSON = new Gson();

    private final Map<String, User> idToUserMap = Collections.synchronizedMap(new LinkedHashMap<String, User>());

    private String myUserID;

    public PresenceChannelImpl(final InternalConnection connection, final String channelName,
            final Authorizer authorizer, final Factory factory) {
        super(connection, channelName, authorizer, factory);
    }

    /* PresenceChannel implementation */

    @Override
    public Set<User> getUsers() {
        return new LinkedHashSet<User>(idToUserMap.values());
    }

    @Override
    public User getMe() {
        return idToUserMap.get(myUserID);
    }

    /* Base class overrides */

    @Override
    public void onMessage(final String event, final String message) {

        super.onMessage(event, message);

        if (event.equals(SUBSCRIPTION_SUCCESS_EVENT)) {
            handleSubscriptionSuccessfulMessage(message);
        }
        else if (event.equals(MEMBER_ADDED_EVENT)) {
            handleMemberAddedEvent(message);
        }
        else if (event.equals(MEMBER_REMOVED_EVENT)) {
            handleMemberRemovedEvent(message);
        }
    }

    @Override
    public String toSubscribeMessage() {
        String msg = super.toSubscribeMessage();
        myUserID = extractUserIdFromChannelData(channelData);
        return msg;
    }

    @Override
    public void bind(final String eventName, final SubscriptionEventListener listener) {

        if (!(listener instanceof PresenceChannelEventListener)) {
            throw new IllegalArgumentException(
                    "Only instances of PresenceChannelEventListener can be bound to a presence channel");
        }

        super.bind(eventName, listener);
    }

    @Override
    protected String[] getDisallowedNameExpressions() {
        return new String[] { "^(?!presence-).*" };
    }

    @Override
    public String toString() {
        return String.format("[Presence Channel: name=%s]", name);
    }

    private void handleSubscriptionSuccessfulMessage(final String message) {
        final ChannelEventListener listener = getEventListener();

        // extract data from the JSON message
        final PresenceData presenceData = extractPresenceDataFrom(message);
        if (presenceData == null) {
            if (listener != null) {
                listener.onError(
                    "Subscription failed: Presence data not found",
                    null
                );
            }
            return;
        }

        final List<String> ids = presenceData.ids;
        final Map<String, Object> hash = presenceData.hash;

        if (ids != null && !ids.isEmpty()) {
            // build the collection of Users
            for (final String id : ids) {
                final String userData = hash.get(id) != null ? GSON.toJson(hash.get(id)) : null;
                final User user = new User(id, userData);
                idToUserMap.put(id, user);
            }
        }

        if (listener != null) {
            final PresenceChannelEventListener presenceListener = (PresenceChannelEventListener)listener;
            presenceListener.onUsersInformationReceived(getName(), getUsers());
        }
    }

    private void handleMemberAddedEvent(final String message) {
        final String dataString = extractDataStringFrom(message);
        MemberData memberData = GSON.fromJson(dataString, MemberData.class);


        final String id = memberData.userId;
        final String userData = memberData.userInfo!= null ? GSON.toJson(memberData.userInfo) : null;

        final User user = new User(id, userData);
        idToUserMap.put(id, user);

        final ChannelEventListener listener = getEventListener();
        if (listener != null) {
            final PresenceChannelEventListener presenceListener = (PresenceChannelEventListener)listener;
            presenceListener.userSubscribed(getName(), user);
        }
    }

    private void handleMemberRemovedEvent(final String message) {

        final String dataString = extractDataStringFrom(message);
        final MemberData memberData = GSON.fromJson(dataString, MemberData.class);

        final User user = idToUserMap.remove(memberData.userId);

        final ChannelEventListener listener = getEventListener();
        if (listener != null) {
            final PresenceChannelEventListener presenceListener = (PresenceChannelEventListener)listener;
            presenceListener.userUnsubscribed(getName(), user);
        }
    }

    @SuppressWarnings("rawtypes")
    private static String extractDataStringFrom(final String message) {
        final Map jsonObject = GSON.fromJson(message, Map.class);
        return  (String) jsonObject.get("data");
    }

    private static PresenceData extractPresenceDataFrom(final String message) {
        final String dataString = extractDataStringFrom(message);
        return GSON.fromJson(dataString, Presence.class).presence;
    }

    @SuppressWarnings("rawtypes")
    private String extractUserIdFromChannelData(final String channelData) {
        final Map channelDataMap;
        try {
            channelDataMap = GSON.fromJson((String)channelData, Map.class);
        } catch (final JsonSyntaxException e) {
            throw new AuthorizationFailureException("Invalid response from Authorizer: unable to parse channel_data object: " + channelData, e);
        }
        Object maybeUserId;
        try {
            maybeUserId = channelDataMap.get("user_id");
        } catch (final NullPointerException e) {
            throw new AuthorizationFailureException("Invalid response from Authorizer: no user_id key in channel_data object: " + channelData);
        }
        if (maybeUserId == null) {
            throw new AuthorizationFailureException("Invalid response from Authorizer: no user_id key in channel_data object: " + channelData);
        }
        // user_id can be a string or an integer in the Channels websocket protocol
        return String.valueOf(maybeUserId);
    }

    private class MemberData {
        @SerializedName("user_id")
        public String userId;
        @SerializedName("user_info")
        public Object userInfo;
    }

    private class PresenceData {
        @SerializedName("count")
        public Integer count;
        @SerializedName("ids")
        public List<String> ids;
        @SerializedName("hash")
        public Map<String, Object> hash;
    }

    private class Presence {
        @SerializedName("presence")
        public PresenceData presence;
    }

}
