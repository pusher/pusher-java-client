package com.pusher.client.channel.impl;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.pusher.client.AuthorizationFailureException;
import com.pusher.client.Authorizer;
import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.channel.ChannelState;
import com.pusher.client.channel.PrivateChannelEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.impl.InternalConnection;

@RunWith(MockitoJUnitRunner.class)
public class PrivateChannelImplTest extends ChannelImplTest {

    private static final String AUTH_RESPONSE = "\"auth\":\"a87fe72c6f36272aa4b1:41dce43734b18bb\"";
    private static final String AUTH_RESPONSE_CHANNEL_DATA = "\"channel_data\":\"{\\\"user_id\\\":\\\"51169fc47abac\\\"}\"";

    @Mock
    protected InternalConnection mockConnection;
    @Mock
    protected Authorizer mockAuthorizer;

    @Override
    @Before
    public void setUp() {
        super.setUp();
        when(mockAuthorizer.authorize(eq(getChannelName()), anyString())).thenReturn("{" + AUTH_RESPONSE + "}");
    }

    @Test
    public void testConstructWithNonPrivateChannelNameThrowsException() {

        final String[] invalidNames = new String[] { "my-channel", "private:my-channel", "Private-my-channel",
        "privatemy-channel" };
        for (final String invalidName : invalidNames) {
            try {
                newInstance(invalidName);
                fail("No exception thrown for invalid name: " + invalidName);
            }
            catch (final IllegalArgumentException e) {
                // exception correctly thrown
            }
        }
    }

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void testPublicChannelName() {
        newInstance("stuffchannel");
    }

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void testPresenceChannelName() {
        newInstance("presence-stuffchannel");
    }

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void testPrivateEncryptedChannelName() {
        newInstance("private-encrypted-stuffchannel");
    }

    @Override
    @Test
    public void testPrivateChannelName() {
        newInstance("private-stuffchannel");
    }

    @Test
    @Override
    public void testReturnsCorrectSubscribeMessage() {
        assertEquals("{\"event\":\"pusher:subscribe\",\"data\":{"
                + AUTH_RESPONSE
                + ",\"channel\":\"" + getChannelName() + "\"}}",
                channel.getSubscribeMessage());
    }

    @Test
    public void testReturnsCorrectSubscribeMessageWithChannelData() {
        when(mockAuthorizer.authorize(eq(getChannelName()), anyString())).thenReturn(
                "{" + AUTH_RESPONSE + "," + AUTH_RESPONSE_CHANNEL_DATA + "}");

        assertEquals("{\"event\":\"pusher:subscribe\",\"data\":{"
                + AUTH_RESPONSE_CHANNEL_DATA + ","
                + AUTH_RESPONSE
                + ",\"channel\":\"" + getChannelName() + "\"" + "}}",
                channel.getSubscribeMessage());
    }

    @Test(expected = AuthorizationFailureException.class)
    public void testThrowsAuthorizationFailureExceptionIfAuthorizerThrowsException() {
        when(mockAuthorizer.authorize(eq(getChannelName()), anyString())).thenThrow(
                new AuthorizationFailureException("Unable to contact auth server"));
        channel.getSubscribeMessage();
    }

    @Test(expected = AuthorizationFailureException.class)
    public void testThrowsAuthorizationFailureExceptionIfAuthorizerReturnsBasicString() {
        when(mockAuthorizer.authorize(eq(getChannelName()), anyString())).thenReturn("I'm a string");
        channel.getSubscribeMessage();
    }

    @Test(expected = AuthorizationFailureException.class)
    public void testThrowsAuthorizationFailureExceptionIfAuthorizerReturnsInvalidJSON() {
        when(mockAuthorizer.authorize(eq(getChannelName()), anyString())).thenReturn("{\"auth\":\"");
        channel.getSubscribeMessage();
    }

    @Test(expected = AuthorizationFailureException.class)
    public void testThrowsAuthorizationFailureExceptionIfAuthorizerReturnsJSONWithoutAnAuthToken() {
        when(mockAuthorizer.authorize(eq(getChannelName()), anyString())).thenReturn("{\"fish\":\"chips\"");
        channel.getSubscribeMessage();
    }

    @Test
    public void testTriggerWithValidEventSendsMessage() {
        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTED);
        channel.updateState(ChannelState.SUBSCRIBED);
        ((PrivateChannelImpl)channel).trigger("client-myEvent", "{\"fish\":\"chips\"}");

        verify(mockConnection)
        .sendMessage(
                "{\"event\":\"client-myEvent\",\"channel\":\"" + getChannelName()
                + "\",\"data\":\"{\\\"fish\\\":\\\"chips\\\"}\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTriggerWithNullEventNameThrowsException() {
        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTED);
        channel.updateState(ChannelState.SUBSCRIBED);

        ((PrivateChannelImpl)channel).trigger(null, "{\"fish\":\"chips\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTriggerWithInvalidEventNameThrowsException() {
        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTED);
        channel.updateState(ChannelState.SUBSCRIBED);

        ((PrivateChannelImpl)channel).trigger("myEvent", "{\"fish\":\"chips\"}");
    }

    @Test
    public void testTriggerWithString() {
        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTED);
        channel.updateState(ChannelState.SUBSCRIBED);

        ((PrivateChannelImpl)channel).trigger("client-myEvent", "string");

        verify(mockConnection).sendMessage(
                "{\"event\":\"client-myEvent\",\"channel\":\"" + getChannelName()
                        + "\",\"data\":\"string\"}");
    }

    @Test(expected = IllegalStateException.class)
    public void testTriggerWhenChannelIsInInitialStateThrowsException() {
        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTED);

        ((PrivateChannelImpl)channel).trigger("client-myEvent", "{\"fish\":\"chips\"}");
    }

    @Test(expected = IllegalStateException.class)
    public void testTriggerWhenChannelIsInSubscribeSentStateThrowsException() {
        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTED);
        channel.updateState(ChannelState.SUBSCRIBE_SENT);

        ((PrivateChannelImpl)channel).trigger("client-myEvent", "{\"fish\":\"chips\"}");
    }

    @Test(expected = IllegalStateException.class)
    public void testTriggerWhenChannelIsInUnsubscribedStateThrowsException() {
        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTED);
        channel.updateState(ChannelState.UNSUBSCRIBED);

        ((PrivateChannelImpl)channel).trigger("client-myEvent", "{\"fish\":\"chips\"}");
    }

    @Test(expected = IllegalStateException.class)
    public void testTriggerWhenConnectionIsInDisconnectedStateThrowsException() {
        when(mockConnection.getState()).thenReturn(ConnectionState.DISCONNECTED);
        channel.updateState(ChannelState.SUBSCRIBED);

        ((PrivateChannelImpl)channel).trigger("client-myEvent", "{\"fish\":\"chips\"}");
    }

    @Test(expected = IllegalStateException.class)
    public void testTriggerWhenConnectionIsInConnectingStateThrowsException() {
        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTING);
        channel.updateState(ChannelState.SUBSCRIBED);

        ((PrivateChannelImpl)channel).trigger("client-myEvent", "{\"fish\":\"chips\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCannotBindIfListenerIsNotAPrivateChannelEventListener() {
        final ChannelEventListener listener = mock(ChannelEventListener.class);
        channel.bind("private-myEvent", listener);
    }

    /* end of tests */

    @Override
    protected ChannelImpl newInstance(final String channelName) {
        return new PrivateChannelImpl(mockConnection, channelName, mockAuthorizer, factory);
    }

    @Override
    protected String getChannelName() {
        return "private-my-channel";
    }

    @Override
    protected ChannelEventListener getEventListener() {
        final PrivateChannelEventListener listener = mock(PrivateChannelEventListener.class);
        return listener;
    }
}
