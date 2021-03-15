package com.pusher.client.channel.impl;

import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.pusher.client.AuthorizationFailureException;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.channel.ChannelState;
import com.pusher.client.channel.PresenceChannel;
import com.pusher.client.channel.PresenceChannelEventListener;
import com.pusher.client.channel.PrivateChannel;
import com.pusher.client.channel.PrivateChannelEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.connection.impl.InternalConnection;
import com.pusher.client.util.Factory;
import java.util.concurrent.Executors;

@RunWith(MockitoJUnitRunner.class)
public class ChannelManagerTest {

    private static final String CHANNEL_NAME = "my-channel";
    private static final String PRIVATE_CHANNEL_NAME = "private-my-channel";
    private static final String PRESENCE_CHANNEL_NAME = "presence-my-channel";
    private static final String OUTGOING_SUBSCRIBE_MESSAGE = "{\"event\":\"pusher:subscribe\"}";
    private static final String OUTGOING_UNSUBSCRIBE_MESSAGE = "{\"event\":\"pusher:unsubscribe\"}";
    private static final String SOCKET_ID = "21234.41243";
    private static final String PRIVATE_OUTGOING_SUBSCRIBE_MESSAGE = "{\"event\":\"pusher:subscribe\", \"data\":{}}";

    private ChannelManager channelManager;
    private @Mock InternalConnection mockConnection;
    private @Mock InternalChannel mockInternalChannel;
    private @Mock ChannelEventListener mockEventListener;
    private @Mock PrivateChannelImpl mockPrivateChannel;
    private @Mock PrivateChannelEventListener mockPrivateChannelEventListener;
    private @Mock PresenceChannelImpl mockPresenceChannel;
    private @Mock PresenceChannelEventListener mockPresenceChannelEventListener;
    private @Mock Factory factory;

    private ChannelManager subscriptionTestChannelManager;
    private @Mock Factory subscriptionTestFactory;
    private @Mock InternalConnection subscriptionTestConnection;

    @Before
    public void setUp() throws AuthorizationFailureException {

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                final Runnable r = (Runnable) invocation.getArguments()[0];
                r.run();
                return null;
            }
        }).when(factory).queueOnEventThread(any(Runnable.class));
        when(mockInternalChannel.getName()).thenReturn(CHANNEL_NAME);
        when(mockInternalChannel.toSubscribeMessage()).thenReturn(OUTGOING_SUBSCRIBE_MESSAGE);
        when(mockInternalChannel.toUnsubscribeMessage()).thenReturn(OUTGOING_UNSUBSCRIBE_MESSAGE);
        when(mockInternalChannel.getEventListener()).thenReturn(mockEventListener);
        when(mockConnection.getSocketId()).thenReturn(SOCKET_ID);
        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTED);
        when(mockPrivateChannel.getName()).thenReturn(PRIVATE_CHANNEL_NAME);
        when(mockPrivateChannel.toSubscribeMessage()).thenReturn(PRIVATE_OUTGOING_SUBSCRIBE_MESSAGE);
        when(mockPrivateChannel.getEventListener()).thenReturn(mockPrivateChannelEventListener);
        when(mockPresenceChannel.getName()).thenReturn(PRESENCE_CHANNEL_NAME);
        when(mockPresenceChannel.toSubscribeMessage()).thenReturn(PRIVATE_OUTGOING_SUBSCRIBE_MESSAGE);
        when(mockPresenceChannel.getEventListener()).thenReturn(mockPresenceChannelEventListener);

        channelManager = new ChannelManager(factory);
        channelManager.setConnection(mockConnection);


        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                final Runnable r = (Runnable) invocation.getArguments()[0];
                r.run();
                return null;
            }
        }).when(subscriptionTestFactory).queueOnEventThread(any(Runnable.class));
        subscriptionTestChannelManager = new ChannelManager(subscriptionTestFactory);
        subscriptionTestChannelManager.setConnection(subscriptionTestConnection);

    }

    @Test
    public void testSetConnectionBindsAsListener() {
        final ChannelManager manager = new ChannelManager(factory);
        final InternalConnection connection = mock(InternalConnection.class);

        manager.setConnection(connection);
        verify(connection).bind(ConnectionState.CONNECTED, manager);
    }

    @Test
    public void testSetConnectionUnbindsFromPreviousConnection() {
        final ChannelManager manager = new ChannelManager(factory);
        final InternalConnection connection = mock(InternalConnection.class);

        manager.setConnection(connection);

        final InternalConnection secondConnection = mock(InternalConnection.class);
        manager.setConnection(secondConnection);
        verify(connection).unbind(ConnectionState.CONNECTED, manager);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetConnectionWithNullConnectionThrowsException() {
        final ChannelManager manager = new ChannelManager(factory);
        manager.setConnection(null);
    }

    @Test
    public void testSubscribeWithAListenerAndNoEventsSubscribes() {
        channelManager.subscribeTo(mockInternalChannel, mockEventListener);

        verify(mockConnection).sendMessage(OUTGOING_SUBSCRIBE_MESSAGE);
        verify(mockInternalChannel, never()).bind(anyString(), any(ChannelEventListener.class));
    }

    @Test
    public void testSubscribeWithAListenerAndEventsBindsTheListenerToTheEventsBeforeSubscribing() {
        channelManager.subscribeTo(mockInternalChannel, mockEventListener, "event1", "event2");

        verify(mockInternalChannel).bind("event1", mockEventListener);
        verify(mockInternalChannel).bind("event2", mockEventListener);
        verify(mockConnection).sendMessage(OUTGOING_SUBSCRIBE_MESSAGE);
    }

    @Test
    public void testSubscribeSetsStatusOfChannelToSubscribeSent() {
        channelManager.subscribeTo(mockInternalChannel, mockEventListener);
        verify(mockInternalChannel).updateState(ChannelState.SUBSCRIBE_SENT);
    }

    @Test
    public void testSubscribeWithANullListenerAndNoEventsSubscribes() {
        channelManager.subscribeTo(mockInternalChannel, null);

        verify(mockConnection).sendMessage(OUTGOING_SUBSCRIBE_MESSAGE);
        verify(mockInternalChannel, never()).bind(anyString(), any(ChannelEventListener.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubscribeWithNullChannelThrowsException() {
        channelManager.subscribeTo(null, mockEventListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubscribeWithADuplicateNameThrowsException() {
        channelManager.subscribeTo(mockInternalChannel, mockEventListener);
        channelManager.subscribeTo(mockInternalChannel, mockEventListener);
    }

    @Test
    public void testSubscribeToPrivateChannelSubscribes() throws AuthorizationFailureException {
        channelManager.subscribeTo(mockPrivateChannel, mockPrivateChannelEventListener);

        verify(mockPrivateChannel).toSubscribeMessage();
        verify(mockConnection).sendMessage(PRIVATE_OUTGOING_SUBSCRIBE_MESSAGE);
    }

    @Test
    public void testSubscribeWhileDisconnectedQueuesSubscriptionUntilConnectedCallbackIsReceived() {
        when(mockConnection.getState()).thenReturn(ConnectionState.DISCONNECTED);

        channelManager.subscribeTo(mockInternalChannel, mockEventListener);
        verify(mockConnection, never()).sendMessage(anyString());

        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTED);
        channelManager.onConnectionStateChange(new ConnectionStateChange(ConnectionState.CONNECTING,
                ConnectionState.CONNECTED));
        verify(mockConnection).sendMessage(OUTGOING_SUBSCRIBE_MESSAGE);
    }

    @Test
    public void testDelayedSubscriptionThatFailsToAuthorizeNotifiesListenerAndDoesNotAttemptToSubscribe() {
        final AuthorizationFailureException exception = new AuthorizationFailureException(
                "Unable to contact auth server");
        when(mockConnection.getState()).thenReturn(ConnectionState.DISCONNECTED);
        when(mockPrivateChannel.toSubscribeMessage()).thenThrow(exception);

        channelManager.subscribeTo(mockPrivateChannel, mockPrivateChannelEventListener);
        verify(mockConnection, never()).sendMessage(anyString());

        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTED);
        channelManager.onConnectionStateChange(new ConnectionStateChange(ConnectionState.CONNECTING,
                ConnectionState.CONNECTED));
        verify(mockPrivateChannelEventListener).onAuthenticationFailure("Unable to contact auth server", exception);
        verify(mockConnection, never()).sendMessage(anyString());
    }

    @Test
    public void testSubscriptionsAreResubscribedEveryTimeTheConnectionIsReestablished() {
        when(mockConnection.getState()).thenReturn(ConnectionState.DISCONNECTED);

        // initially the connection is down so it should not attempt to
        // subscribe
        channelManager.subscribeTo(mockInternalChannel, mockEventListener);
        verify(mockConnection, never()).sendMessage(anyString());

        // when the connection is made the first subscribe attempt should be
        // made
        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTED);
        channelManager.onConnectionStateChange(new ConnectionStateChange(ConnectionState.CONNECTING,
                ConnectionState.CONNECTED));
        verify(mockConnection, times(1)).sendMessage(anyString());

        // when the connection fails and comes back up the channel should be
        // subscribed again
        when(mockConnection.getState()).thenReturn(ConnectionState.DISCONNECTED);
        channelManager.onConnectionStateChange(new ConnectionStateChange(ConnectionState.CONNECTED,
                ConnectionState.DISCONNECTED));

        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTED);
        channelManager.onConnectionStateChange(new ConnectionStateChange(ConnectionState.DISCONNECTED,
                ConnectionState.CONNECTED));

        verify(mockConnection, times(2)).sendMessage(OUTGOING_SUBSCRIBE_MESSAGE);
    }

    @Test
    public void testDelayedSubscriptionDoesNotUpdateChannelStateToSubscribeSentUntilConnectedCallbackIsReceived() {
        when(mockConnection.getState()).thenReturn(ConnectionState.DISCONNECTED);

        channelManager.subscribeTo(mockInternalChannel, mockEventListener);
        verify(mockInternalChannel, never()).updateState(any(ChannelState.class));

        when(mockConnection.getState()).thenReturn(ConnectionState.CONNECTED);
        channelManager.onConnectionStateChange(new ConnectionStateChange(ConnectionState.CONNECTING,
                ConnectionState.CONNECTED));
        verify(mockInternalChannel).updateState(ChannelState.SUBSCRIBE_SENT);
    }

    @Test
    public void testReceiveMessageForSubscribedChannelPassesItToChannel() {
        channelManager.subscribeTo(mockInternalChannel, mockEventListener, "my-event");
        channelManager.onMessage("my-event", "{\"event\":\"my-event\",\"data\":{\"fish\":\"chips\"},\"channel\":\""
                + CHANNEL_NAME + "\"}");

        verify(mockInternalChannel).onMessage("my-event",
                "{\"event\":\"my-event\",\"data\":{\"fish\":\"chips\"},\"channel\":\"" + CHANNEL_NAME + "\"}");
    }

    @Test
    public void testReceiveMessageWithNoMatchingChannelIsIgnoredAndDoesNotThrowException() {
        channelManager.subscribeTo(mockInternalChannel, mockEventListener, "my-event");
        channelManager.onMessage("my-event", "{\"event\":\"my-event\",\"data\":{\"fish\":\"chips\"},\"channel\":\""
                + "DIFFERENT_CHANNEL_NAME" + "\"}");

        verify(mockInternalChannel, never()).onMessage(anyString(), anyString());
    }

    @Test
    public void testReceiveMessageWithNoChannelIsIgnoredAndDoesNotThrowException() {
        channelManager.onMessage("connection_established",
                "{\"event\":\"connection_established\",\"data\":{\"socket_id\":\"21098.967780\"}}");
    }

    @Test
    public void testUnsubscribeFromSubscribedChannelSendsUnsubscribeMessage() {
        channelManager.subscribeTo(mockInternalChannel, mockEventListener);
        channelManager.unsubscribeFrom(CHANNEL_NAME);

        verify(mockConnection).sendMessage(OUTGOING_UNSUBSCRIBE_MESSAGE);
        assertFalse(mockInternalChannel.isSubscribed());
    }

    @Test
    public void testUnsubscribeFromSubscribedChannelUnsubscribesInEventQueue() {
        subscriptionTestChannelManager.subscribeTo(mockInternalChannel, mockEventListener);
        subscriptionTestChannelManager.unsubscribeFrom(CHANNEL_NAME);

        verify(subscriptionTestFactory).queueOnEventThread(any(Runnable.class));
    }

    @Test
    public void testUnsubscribeFromSubscribedChannelSetsStatusOfChannelToUnsubscribed() {
        channelManager.subscribeTo(mockInternalChannel, mockEventListener);
        channelManager.unsubscribeFrom(CHANNEL_NAME);

        verify(mockInternalChannel).updateState(ChannelState.UNSUBSCRIBED);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnsubscribeWithNullChannelNameThrowsException() {
        channelManager.unsubscribeFrom(null);
    }

    @Test
    public void testReceiveMessageAfterUnsubscribeDoesNotPassItToChannel() {
        channelManager.subscribeTo(mockInternalChannel, mockEventListener, "my-event");
        channelManager.unsubscribeFrom(CHANNEL_NAME);
        channelManager.onMessage("my-event", "{\"event\":\"my-event\",\"data\":{\"fish\":\"chips\"},\"channel\":\""
                + CHANNEL_NAME + "\"}");

        verify(mockInternalChannel, never()).onMessage(anyString(), anyString());
    }

    @Test
    public void testSubscriptionIsReSubscribedFollowingDisconnectThenConnect() {
        when(mockConnection.getState()).thenReturn(ConnectionState.DISCONNECTED);

        channelManager.subscribeTo(mockInternalChannel, mockEventListener);
        channelManager.onConnectionStateChange(new ConnectionStateChange(ConnectionState.CONNECTING,
                ConnectionState.CONNECTED));

        verify(mockConnection, never()).sendMessage(anyString());
    }

    @Test
    public void testGetChannelFromString(){
        channelManager.subscribeTo(mockInternalChannel, mockEventListener);
        Channel channel = channelManager.getChannel(CHANNEL_NAME);
        assertEquals(channel, mockInternalChannel);
    }

    @Test
    public void testGetNonExistentChannelFromString(){
        Channel channel = channelManager.getChannel("woot");
        assertNull(channel);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPrivateChannelWithGetChannelRaisesError(){
        channelManager.getChannel("private-yolo");
    }

    @Test
    public void testGetPrivateChannelFromString(){
        channelManager.subscribeTo(mockPrivateChannel, mockPrivateChannelEventListener);
        PrivateChannel channel = channelManager.getPrivateChannel(PRIVATE_CHANNEL_NAME);
        assertEquals(channel, mockPrivateChannel);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPrivateChannelPassingInPresencePrefixedString(){
        channelManager.getPrivateChannel("presence-yolo");
    }

    @Test
    public void testGetNonExistentPrivateChannel(){
        PrivateChannel channel = channelManager.getPrivateChannel("private-yolo");
        assertNull(channel);
    }

    @Test
    public void testGetPresenceChannelFromString(){
        channelManager.subscribeTo(mockPresenceChannel, mockPresenceChannelEventListener);
        PresenceChannel channel = channelManager.getPresenceChannel(PRESENCE_CHANNEL_NAME);
        assertEquals(channel, mockPresenceChannel);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPresenceChannelPassingInPrivatePrefixedString(){
        channelManager.getPresenceChannel("private-yolo");
    }

    @Test
    public void testGetNonExistentPresenceChannel(){
        PresenceChannel channel = channelManager.getPresenceChannel("presence-yolo");
        assertNull(channel);
    }

    @Test
    public void testConcurrentModificationExceptionDoesNotHappenWhenConnectionIsEstablished() {
        for(int i = 0; i<1000; i++) {
            channelManager.subscribeTo(new ChannelImpl("channel" + i, factory), null);
        }

        Runnable removeChannels = new Runnable() {
            @Override
            public void run() {
                System.out.println("Start unsubscribe");
                for(int i=900; i<1000; i++){
                    channelManager.unsubscribeFrom("channel"+i);
                }
                System.out.println("end unsubscribe");
            }
        };
        Executors.newSingleThreadExecutor().submit(removeChannels);

        channelManager.onConnectionStateChange(new ConnectionStateChange(ConnectionState.CONNECTING, ConnectionState.CONNECTED));
    }
}
