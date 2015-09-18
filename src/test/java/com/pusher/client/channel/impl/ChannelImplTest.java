package com.pusher.client.channel.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.gson.Gson;

import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.channel.ChannelState;
import com.pusher.client.util.Factory;
import com.pusher.client.util.InstantExecutor;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class ChannelImplTest {

    private static final String EVENT_NAME = "my-event";
    protected ChannelImpl channel;
    protected @Mock Factory factory;
    protected @Mock ChannelEventListener mockListener;

    @Before
    public void setUp() {
        when(factory.getEventQueue()).thenReturn(new InstantExecutor());

        mockListener = getEventListener();
        channel = newInstance(getChannelName(), null);
        channel.setEventListener(mockListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullChannelNameThrowsException() {
        newInstance(null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPrivateChannelName() {
        newInstance("private-my-channel", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPresenceChannelName() {
        newInstance("presence-my-channel", null);
    }

    @Test
    public void testPublicChannelName() {
        newInstance("my-channel", null);
    }

    @Test
    public void testGetNameReturnsName() {
        assertEquals(getChannelName(), channel.getName());
    }

    @Test
    public void testReturnsCorrectSubscribeMessage() {
        assertEquals("{\"event\":\"pusher:subscribe\",\"data\":{\"channel\":\"" + getChannelName() + "\"}}",
                channel.toSubscribeMessage());
    }

    @Test
    public void testReturnsCorrectUnsubscribeMessage() {
        assertEquals("{\"event\":\"pusher:unsubscribe\",\"data\":{\"channel\":\"" + getChannelName() + "\"}}",
                channel.toUnsubscribeMessage());
    }

    @Test
    public void testInternalSubscriptionSucceededMessageIsTranslatedToASubscriptionSuccessfulCallback() {
        channel.bind(EVENT_NAME, mockListener);
        channel.onMessage("pusher_internal:subscription_succeeded",
                "{\"event\":\"pusher_internal:subscription_succeeded\",\"data\":\"{}\",\"channel\":\""
                        + getChannelName() + "\"}");

        verify(mockListener).onSubscriptionSucceeded(getChannelName(), null);
    }

    @Test
    public void testInternalSubscriptionSucceededMessageWithResumeDataIsTranslatedToASubscriptionSuccessfulCallback() {
        channel.bind(EVENT_NAME, mockListener);
        channel.onMessage("pusher_internal:subscription_succeeded",
                "{\"event\":\"pusher_internal:subscription_succeeded\",\"data\":\"{\\\"resume\\\":{\\\"after_id\\\":\\\"blah\\\",\\\"ok\\\":true}}\",\"channel\":\""
                        + getChannelName() + "\"}");

        verify(mockListener).onSubscriptionSucceeded(getChannelName(), Boolean.TRUE);
    }

    @Test
    public void testInternalSubscriptionFailedMessageIsTranslatedToASubscriptionFailedCallback() {
        channel.bind(EVENT_NAME, mockListener);
        channel.onMessage("pusher_internal:subscription_failed",
                "{\"event\":\"pusher_internal:subscription_failed\","
                + "\"data\":\"{\\\"code\\\":1000,\\\"message\\\":\\\"Generic test error\\\"}\","
                + "\"channel\":\"" + getChannelName() + "\"}");

        verify(mockListener).onSubscriptionFailed(getChannelName(), 1000, "Generic test error");
    }

    @Test
    public void testDataIsExtractedFromMessageAndPassedToSingleListener() {
        // {"event":"my-event","data":"{\"some\":\"data\"}","channel":"my-channel"}
        channel.bind(EVENT_NAME, mockListener);
        channel.onMessage(EVENT_NAME, "{\"event\":\"event1\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\"}");

        verify(mockListener).onEvent(getChannelName(), EVENT_NAME, "{\"fish\":\"chips\"}");
    }

    @Test
    public void testDataIsExtractedFromMessageAndPassedToMultipleListeners() {
        final ChannelEventListener mockListener2 = getEventListener();

        channel.bind(EVENT_NAME, mockListener);
        channel.bind(EVENT_NAME, mockListener2);
        channel.onMessage(EVENT_NAME, "{\"event\":\"event1\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\"}");

        verify(mockListener).onEvent(getChannelName(), EVENT_NAME, "{\"fish\":\"chips\"}");
        verify(mockListener2).onEvent(getChannelName(), EVENT_NAME, "{\"fish\":\"chips\"}");
    }

    @Test
    public void testEventIsNotPassedOnIfThereAreNoMatchingListeners() {

        channel.bind(EVENT_NAME, mockListener);
        channel.onMessage("DifferentEventName", "{\"event\":\"event1\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\"}");

        verify(mockListener, never()).onEvent(anyString(), anyString(), anyString());
    }

    @Test
    public void testEventIsNotPassedOnIfListenerHasUnboundFromEvent() {

        channel.bind(EVENT_NAME, mockListener);
        channel.unbind(EVENT_NAME, mockListener);
        channel.onMessage(EVENT_NAME, "{\"event\":\"event1\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\"}");

        verify(mockListener, never()).onEvent(anyString(), anyString(), anyString());
    }

    @Test
    public void testMessageIdIsPassedInSubscribe() {
        channel.onMessage("blah-event", "{\"event\":\"event1\",\"id\":\"ab6342e9f23c34\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\"}");

        String subscribeMessage = channel.toSubscribeMessage();
        Map<String, String> subscribeData = (Map<String, String>)new Gson().fromJson(subscribeMessage, Map.class).get("data");

        assertEquals("ab6342e9f23c34", subscribeData.get("resume_after_id"));
    }

    @Test
    public void testMessageIdPassedInSubscribeIsLastSeen() {
        channel.onMessage("blah-event", "{\"event\":\"event1\",\"id\":\"az335rety45456\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\"}");
        channel.onMessage("blah-event", "{\"event\":\"event1\",\"id\":\"sdawer346rytye\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\"}");
        channel.onMessage("blah-event", "{\"event\":\"event1\",\"id\":\"ab6342e9f23c34\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\"}");

        String subscribeMessage = channel.toSubscribeMessage();
        Map<String, String> subscribeData = (Map<String, String>)new Gson().fromJson(subscribeMessage, Map.class).get("data");

        assertEquals("ab6342e9f23c34", subscribeData.get("resume_after_id"));
    }

    @Test
    public void testMessageIdPassedInSubscribeWhenSetViaConstructor() {
        channel = newInstance(getChannelName(), "ade5427ecba43");

        String subscribeMessage = channel.toSubscribeMessage();
        Map<String, String> subscribeData = (Map<String, String>)new Gson().fromJson(subscribeMessage, Map.class).get("data");

        assertEquals("ade5427ecba43", subscribeData.get("resume_after_id"));
    }

    @Test
    public void testMessageIdSetViaConstructorIsSuperscededByIncomingMessages() {
        channel = newInstance(getChannelName(), "ade5427ecba43");
        channel.onMessage("blah-event", "{\"event\":\"event1\",\"id\":\"ab6342e9f23c34\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\"}");

        String subscribeMessage = channel.toSubscribeMessage();
        Map<String, String> subscribeData = (Map<String, String>)new Gson().fromJson(subscribeMessage, Map.class).get("data");

        assertEquals("ab6342e9f23c34", subscribeData.get("resume_after_id"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBindWithNullEventNameThrowsException() {
        channel.bind(null, mockListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBindWithNullListenerThrowsException() {
        channel.bind(EVENT_NAME, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBindToInternalEventThrowsException() {
        channel.bind("pusher_internal:subscription_succeeded", mockListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnbindWithNullEventNameThrowsException() {
        channel.bind(EVENT_NAME, mockListener);
        channel.unbind(null, mockListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnbindWithNullListenerThrowsException() {
        channel.bind(EVENT_NAME, null);
        channel.unbind(EVENT_NAME, null);
    }

    @Test
    public void testUnbindWhenListenerIsNotBoundToEventIsIgnoredAndDoesNotThrowException() {
        channel.bind(EVENT_NAME, mockListener);
        channel.unbind("different event name", mockListener);
    }

    @Test(expected = IllegalStateException.class)
    public void testBindWhenInUnsubscribedStateThrowsException() {
        channel.updateState(ChannelState.UNSUBSCRIBED);
        channel.bind(EVENT_NAME, mockListener);
    }

    @Test(expected = IllegalStateException.class)
    public void testUnbindWhenInUnsubscribedStateThrowsException() {
        channel.bind(EVENT_NAME, mockListener);
        channel.updateState(ChannelState.UNSUBSCRIBED);
        channel.unbind(EVENT_NAME, mockListener);
    }

    /* end of tests */

    /**
     * This method is overridden in the test subclasses so that these tests can
     * be run against PrivateChannelImpl and PresenceChannelImpl.
     */
    protected ChannelImpl newInstance(final String channelName, final String resumeAfterId) {
        return new ChannelImpl(channelName, resumeAfterId, factory);
    }

    /**
     * This method is overridden in the test subclasses so that the private
     * channel tests can run with a valid private channel name and the presence
     * channel tests can run with a valid presence channel name.
     */
    protected String getChannelName() {
        return "my-channel";
    }

    /**
     * This method is overridden to allow the private and presence channel tests
     * to use the appropriate listener subclass.
     */
    protected ChannelEventListener getEventListener() {
        return mock(ChannelEventListener.class);
    }
}
