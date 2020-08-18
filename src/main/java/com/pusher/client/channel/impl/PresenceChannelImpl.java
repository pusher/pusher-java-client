package com.pusher.client.channel.impl;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.pusher.client.AuthorizationFailureException;
import com.pusher.client.Authorizer;
import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.channel.PresenceChannel;
import com.pusher.client.channel.PresenceChannelEventListener;
import com.pusher.client.channel.SubscriptionEventListener;
import com.pusher.client.channel.User;
import com.pusher.client.channel.impl.message.ChannelData;
import com.pusher.client.channel.impl.message.PresenceMemberData;
import com.pusher.client.channel.impl.message.PresenceSubscriptionData;
import com.pusher.client.channel.impl.message.PresenceDataMessage;
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

        switch (event) {
            case SUBSCRIPTION_SUCCESS_EVENT:
                handleSubscriptionSuccessfulMessage(message);
                break;
            case MEMBER_ADDED_EVENT:
                handleMemberAddedEvent(message);
                break;
            case MEMBER_REMOVED_EVENT:
                handleMemberRemovedEvent(message);
                break;
        }
    }

    @Override
    public String getSubscribeMessage() {
        String msg = super.getSubscribeMessage();
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

        // extract data from the JSON message
        final PresenceDataMessage presenceDataMessage =
                GSON.fromJson(message, PresenceDataMessage.class);
        //the presence data is double encoded
        final PresenceSubscriptionData presenceSubscriptionData =
                GSON.fromJson(presenceDataMessage.getData(), PresenceSubscriptionData.class);
        final List<String> ids = presenceSubscriptionData.getIds();
        final Map<String, Object> hash = presenceSubscriptionData.getHash();

        if (ids != null && !ids.isEmpty()) {
            // build the collection of Users
            for (final String id : ids) {
                final String userData = hash.get(id) != null ? GSON.toJson(hash.get(id)) : null;
                final User user = new User(id, userData);
                idToUserMap.put(id, user);
            }
        }
        final ChannelEventListener listener = getEventListener();
        if (listener != null) {
            final PresenceChannelEventListener presenceListener = (PresenceChannelEventListener)listener;
            presenceListener.onUsersInformationReceived(getName(), getUsers());
        }
    }

    private void handleMemberAddedEvent(final String message) {
        final PresenceDataMessage presenceDataMessage = GSON.fromJson(message, PresenceDataMessage.class);
        PresenceMemberData memberData = GSON.fromJson(presenceDataMessage.getData(), PresenceMemberData.class);

        final String id = memberData.getId();
        final String userData = memberData.getInfo()!= null ? GSON.toJson(memberData.getInfo()) : null;

        final User user = new User(id, userData);
        idToUserMap.put(id, user);

        final ChannelEventListener listener = getEventListener();
        if (listener != null) {
            final PresenceChannelEventListener presenceListener = (PresenceChannelEventListener)listener;
            presenceListener.userSubscribed(getName(), user);
        }
    }

    private void handleMemberRemovedEvent(final String message) {

        final PresenceDataMessage presenceDataMessage = GSON.fromJson(message, PresenceDataMessage.class);
        final PresenceMemberData memberData = GSON.fromJson(presenceDataMessage.getData(), PresenceMemberData.class);

        final User user = idToUserMap.remove(memberData.getId());

        final ChannelEventListener listener = getEventListener();
        if (listener != null) {
            final PresenceChannelEventListener presenceListener = (PresenceChannelEventListener)listener;
            presenceListener.userUnsubscribed(getName(), user);
        }
    }

    public String extractUserIdFromChannelData(final String channelDataString) {
        try {
            ChannelData data = GSON.fromJson(channelDataString, ChannelData.class);

            if (data.getUserId() == null) {
                throw new AuthorizationFailureException("Invalid response from Authorizer: no user_id key in channel_data object: " + channelDataString);
            }

            return data.getUserId();

        } catch (final JsonSyntaxException e) {
            throw new AuthorizationFailureException("Invalid response from Authorizer: unable to parse channel_data object: " + channelDataString, e);
        } catch (final NullPointerException e) {
            throw new AuthorizationFailureException("Invalid response from Authorizer: no user_id key in channel_data object: " + channelDataString);
        }

    }




}
