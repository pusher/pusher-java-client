package com.pusher.client.channel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PusherEventTest {
    private Gson GSON;

    @Before
    public void setUp() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(PusherEvent.class, new PusherEventDeserializer());
        GSON = gsonBuilder.create();
    }

    @Test
    public void testChannelNameIsExtracted() {
        final PusherEvent e = GSON.fromJson("{\"channel\": \"my-channel\", \"event\":\"my-event\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\"}", PusherEvent.class);
        assertEquals("my-channel", e.getChannelName());
    }

    @Test
    public void testEventNameIsExtracted() {
        final PusherEvent e = GSON.fromJson("{\"channel\": \"my-channel\", \"event\":\"my-event\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\"}", PusherEvent.class);
        assertEquals("my-event", e.getEventName());
    }

    @Test
    public void testDataIsExtracted() {
        final PusherEvent e = GSON.fromJson("{\"channel\": \"my-channel\", \"event\":\"my-event\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\"}", PusherEvent.class);
        assertEquals("{\"fish\":\"chips\"}", e.getData());
    }

    @Test
    public void testUserIdIsExtracted() {
        final PusherEvent e = GSON.fromJson("{\"channel\": \"my-channel\", \"event\":\"my-event\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\", \"user_id\": \"my-user-id\"}", PusherEvent.class);
        assertEquals("my-user-id", e.getUserId());
    }

    @Test
    public void testStringPropertyIsExtracted() {
        final PusherEvent e = GSON.fromJson("{\"channel\": \"my-channel\", \"event\":\"my-event\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\", \"my_property\": \"test\"}", PusherEvent.class);
        assertEquals("test", (String)e.getProperty("my_property"));
    }

    @Test
    public void testIntegerPropertyIsExtracted() {
        final PusherEvent e = GSON.fromJson("{\"channel\": \"my-channel\", \"event\":\"my-event\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\", \"my_property\": 42}", PusherEvent.class);
        assertEquals(Double.valueOf(42), (Double)e.getProperty("my_property"));
    }

    @Test
    public void testBooleanPropertyIsExtracted() {
        final PusherEvent e = GSON.fromJson("{\"channel\": \"my-channel\", \"event\":\"my-event\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\", \"my_property\": true}", PusherEvent.class);
        assertEquals(true, (Boolean)e.getProperty("my_property"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testArrayPropertyIsExtracted() {
        final PusherEvent e = GSON.fromJson("{\"channel\": \"my-channel\", \"event\":\"my-event\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\", \"my_property\": [42]}", PusherEvent.class);
        assertEquals(Double.valueOf(42), ((ArrayList<Double>)e.getProperty("my_property")).get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testObjectPropertyIsExtracted() {
        final PusherEvent e = GSON.fromJson("{\"channel\": \"my-channel\", \"event\":\"my-event\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\", \"my_property\": {\"test\": 42}}", PusherEvent.class);
        final Map<String, Object> m = (Map<String, Object>)e.getProperty("my_property");
        assertEquals(Double.valueOf(42), (Double)m.get("test"));
    }

    @Test
    public void testNullPropertyIsExtracted() {
        final PusherEvent e = GSON.fromJson("{\"channel\": \"my-channel\", \"event\":\"my-event\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\", \"my_property\": null}", PusherEvent.class);
        assertNull(e.getProperty("my_property"));
    }
}
