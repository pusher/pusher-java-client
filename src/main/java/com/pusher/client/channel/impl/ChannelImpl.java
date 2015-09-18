package com.pusher.client.channel.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;

import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.channel.ChannelState;
import com.pusher.client.channel.SubscriptionEventListener;
import com.pusher.client.util.Factory;

public class ChannelImpl implements InternalChannel {

    private static final String INTERNAL_EVENT_PREFIX = "pusher_internal:";
    protected static final String SUBSCRIPTION_SUCCESS_EVENT = "pusher_internal:subscription_succeeded";
    protected static final String SUBSCRIPTION_FAILED_EVENT = "pusher_internal:subscription_failed";
    protected final String name;
    protected final Map<String, Set<SubscriptionEventListener>> eventNameToListenerMap = new HashMap<String, Set<SubscriptionEventListener>>();
    protected volatile ChannelState state = ChannelState.INITIAL;
    private ChannelEventListener eventListener;
    protected String resumeAfter;
    private final Factory factory;

    public ChannelImpl(final String channelName, final String resumeId, final Factory factory) {
        if (channelName == null) {
            throw new IllegalArgumentException("Cannot subscribe to a channel with a null name");
        }

        for (final String disallowedPattern : getDisallowedNameExpressions()) {
            if (channelName.matches(disallowedPattern)) {
                throw new IllegalArgumentException(
                        "Channel name "
                                + channelName
                                + " is invalid. Private channel names must start with \"private-\" and"
                                + " presence channel names must start with \"presence-\"");
            }
        }

        this.name = channelName;
        this.resumeAfter = resumeId;
        this.factory = factory;
    }

    /* Channel implementation */

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void bind(final String eventName, final SubscriptionEventListener listener) {

        validateArguments(eventName, listener);

        Set<SubscriptionEventListener> listeners = eventNameToListenerMap.get(eventName);
        if (listeners == null) {
            listeners = new HashSet<SubscriptionEventListener>();
            eventNameToListenerMap.put(eventName, listeners);
        }

        listeners.add(listener);
    }

    @Override
    public void unbind(final String eventName, final SubscriptionEventListener listener) {

        validateArguments(eventName, listener);

        final Set<SubscriptionEventListener> listeners = eventNameToListenerMap.get(eventName);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                eventNameToListenerMap.remove(eventName);
            }
        }
    }

    /* InternalChannel implementation */

    @Override
    @SuppressWarnings("unchecked")
    public void onMessage(final String event, final String message) {
        final Map<Object, Object> jsonObject = new Gson().fromJson(message, Map.class);
        final String data = (String)jsonObject.get("data");
        final String eventId = (String)jsonObject.get("id");

        final Map<String, Object> dataMap = new Gson().fromJson(data, Map.class);

        if (event.equals(SUBSCRIPTION_SUCCESS_EVENT)) {
            updateState(ChannelState.SUBSCRIBED);

            final Map<String, Object> resumeData = (Map<String, Object>)dataMap.get("resume");
            final Boolean resumeSuccessful = resumeData == null ? null : (Boolean)resumeData.get("ok");

            if (eventListener != null) {
                factory.getEventQueue().execute(new Runnable() {
                    @Override
                    public void run() {
                        eventListener.onSubscriptionSucceeded(getName(), resumeSuccessful);
                    }
                });
            }
        }
        else if (event.equals(SUBSCRIPTION_FAILED_EVENT)) {
            updateState(ChannelState.FAILED);

            // JSON has only one number type: Javascript-float
            final Double javascriptNumberCode = (Double)dataMap.get("code");

            if (eventListener != null) {
                factory.getEventQueue().execute(new Runnable() {
                    @Override
                    public void run() {
                        eventListener.onSubscriptionFailed(
                                getName(),
                                javascriptNumberCode == null ? null : javascriptNumberCode.intValue(),
                                (String)dataMap.get("message"));
                    }
                });
            }
        }
        else {
            // Update last ID
            if (eventId != null) {
                resumeAfter = eventId;
            }

            final Set<SubscriptionEventListener> listeners = eventNameToListenerMap.get(event);
            if (listeners != null) {
                for (final SubscriptionEventListener listener : listeners) {
                    factory.getEventQueue().execute(new Runnable() {
                        @Override
                        public void run() {
                            listener.onEvent(name, event, data);
                        }
                    });
                }
            }
        }
    }

    @Override
    public String toSubscribeMessage() {
        final Map<Object, Object> jsonObject = new LinkedHashMap<Object, Object>();
        jsonObject.put("event", "pusher:subscribe");

        final Map<Object, Object> dataMap = new LinkedHashMap<Object, Object>();
        dataMap.put("channel", name);

        if (resumeAfter != null) {
          dataMap.put("resume_after_id", resumeAfter);
        }

        jsonObject.put("data", dataMap);

        return new Gson().toJson(jsonObject);
    }

    @Override
    public String toUnsubscribeMessage() {
        final Map<Object, Object> jsonObject = new LinkedHashMap<Object, Object>();
        jsonObject.put("event", "pusher:unsubscribe");

        final Map<Object, Object> dataMap = new LinkedHashMap<Object, Object>();
        dataMap.put("channel", name);

        jsonObject.put("data", dataMap);

        return new Gson().toJson(jsonObject);
    }

    @Override
    public void updateState(final ChannelState state) {
        this.state = state;
    }

    /* Comparable implementation */

    @Override
    public void setEventListener(final ChannelEventListener listener) {
        eventListener = listener;
    }

    @Override
    public ChannelEventListener getEventListener() {
        return eventListener;
    }

    @Override
    public int compareTo(final InternalChannel other) {
        return getName().compareTo(other.getName());
    }

    /* implementation detail */

    @Override
    public String toString() {
        return String.format("[Public Channel: name=%s]", name);
    }

    protected String[] getDisallowedNameExpressions() {
        return new String[] { "^private-.*", "^presence-.*" };
    }

    private void validateArguments(final String eventName, final SubscriptionEventListener listener) {

        if (eventName == null) {
            throw new IllegalArgumentException("Cannot bind or unbind to channel " + name + " with a null event name");
        }

        if (listener == null) {
            throw new IllegalArgumentException("Cannot bind or unbind to channel " + name + " with a null listener");
        }

        if (eventName.startsWith(INTERNAL_EVENT_PREFIX)) {
            throw new IllegalArgumentException("Cannot bind or unbind channel " + name
                    + " with an internal event name such as " + eventName);
        }

        if (state == ChannelState.UNSUBSCRIBED) {
            throw new IllegalStateException(
                    "Cannot bind or unbind to events on a channel that has been unsubscribed. Call Pusher.subscribe() to resubscribe to this channel");
        }
    }
}
