package com.pusher.client;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.Proxy;
import java.net.URI;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.pusher.client.channel.impl.ChannelManager;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.connection.impl.InternalConnection;
import com.pusher.client.connection.websocket.WebSocketClientWrapper;
import com.pusher.client.connection.websocket.WebSocketConnection;
import com.pusher.client.connection.websocket.WebSocketListener;
import com.pusher.client.util.DoNothingExecutor;
import com.pusher.client.util.Factory;
import org.java_websocket.handshake.ServerHandshake;

@RunWith(MockitoJUnitRunner.class)
public class EndToEndTest {

    private static final String API_KEY = "123456";
    private static final String AUTH_KEY = "123456";
    private static final String PUBLIC_CHANNEL_NAME = "my-channel";
    private static final String PRIVATE_CHANNEL_NAME = "private-my-channel";
    private static final String OUTGOING_SUBSCRIBE_PRIVATE_MESSAGE = "{\"event\":\"pusher:subscribe\",\"data\":{\"auth\":\"" + AUTH_KEY + "\",\"channel\":\"" + PRIVATE_CHANNEL_NAME + "\"}}";
    private static final long ACTIVITY_TIMEOUT = 120000;
    private static final long PONG_TIMEOUT = 120000;

    private static final Proxy proxy = Proxy.NO_PROXY;

    private @Mock Authorizer mockAuthorizer;
    private @Mock ConnectionEventListener mockConnectionEventListener;
    private @Mock ServerHandshake mockServerHandshake;
    private @Mock Factory factory;
    private Pusher pusher;
    private PusherOptions pusherOptions;
    private InternalConnection connection;
    private TestWebSocketClientWrapper testWebsocket;

    @Before
    public void setUp() throws Exception {
        pusherOptions = new PusherOptions().setAuthorizer(mockAuthorizer).setEncrypted(false);

        connection = new WebSocketConnection(pusherOptions.buildUrl(API_KEY), ACTIVITY_TIMEOUT, PONG_TIMEOUT, pusherOptions.getMaxReconnectionAttempts(),
                pusherOptions.getMaxReconnectGapInSeconds(), proxy, factory);

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                final Runnable r = (Runnable) invocation.getArguments()[0];
                r.run();
                return null;
            }
        }).when(factory).queueOnEventThread(any(Runnable.class));

        when(factory.getTimers()).thenReturn(new DoNothingExecutor());
        when(factory.newWebSocketClientWrapper(any(URI.class), any(Proxy.class), any(WebSocketListener.class))).thenAnswer(
                new Answer<WebSocketClientWrapper>() {
                    @Override
                    public WebSocketClientWrapper answer(final InvocationOnMock invocation) throws Throwable {
                        final URI uri = (URI)invocation.getArguments()[0];
                        final Proxy proxy = (Proxy)invocation.getArguments()[1];
                        final WebSocketListener webSocketListener = (WebSocketListener)invocation.getArguments()[2];
                        testWebsocket = new TestWebSocketClientWrapper(uri, proxy, webSocketListener);
                        return testWebsocket;
                    }
                });

        when(factory.getConnection(API_KEY, pusherOptions)).thenReturn(connection);

        when(factory.getChannelManager()).thenAnswer(new Answer<ChannelManager>() {
            @Override
            public ChannelManager answer(final InvocationOnMock invocation) throws Throwable {
                return new ChannelManager(factory);
            }
        });

        when(factory.newPresenceChannel(any(InternalConnection.class), anyString(), any(Authorizer.class)))
                .thenCallRealMethod();
        when(factory.newPrivateChannel(any(InternalConnection.class), anyString(), any(Authorizer.class)))
                .thenCallRealMethod();
        when(factory.newPublicChannel(anyString())).thenCallRealMethod();

        when(mockAuthorizer.authorize(anyString(), anyString())).thenReturn("{\"auth\":\"" + AUTH_KEY + "\"}");

        pusher = new Pusher(API_KEY, pusherOptions, factory);
    }

    @After
    public void tearDown() {

        pusher.disconnect();
        testWebsocket.onClose(1, "Close", true);
    }

    @Test
    public void testSubscribeToPublicChannelSendsSubscribeMessage() {

        establishConnection();
        pusher.subscribe(PUBLIC_CHANNEL_NAME);

        testWebsocket.assertLatestMessageWas("{\"event\":\"pusher:subscribe\",\"data\":{\"channel\":\""
                + PUBLIC_CHANNEL_NAME + "\"}}");
    }

    @Test
    public void testSubscribeToPrivateChannelSendsSubscribeMessage() {

        establishConnection();
        pusher.subscribePrivate(PRIVATE_CHANNEL_NAME);

        testWebsocket.assertLatestMessageWas(OUTGOING_SUBSCRIBE_PRIVATE_MESSAGE);
    }

    @Test
    public void testForQueuedSubscriptionsAuthorizerIsNotCalledUntilTimeToSubscribe() {

        pusher.subscribePrivate(PRIVATE_CHANNEL_NAME);
        verify(mockAuthorizer, never()).authorize(anyString(), anyString());

        establishConnection();
        verify(mockAuthorizer).authorize(eq(PRIVATE_CHANNEL_NAME), anyString());
    }

    @Test
    public void testSubscriptionsAreResubscribedWithFreshAuthTokensEveryTimeTheConnectionComesUp() {

        pusher.subscribePrivate(PRIVATE_CHANNEL_NAME);
        verify(mockAuthorizer, never()).authorize(anyString(), anyString());

        establishConnection();
        verify(mockAuthorizer).authorize(eq(PRIVATE_CHANNEL_NAME), anyString());
        testWebsocket.assertLatestMessageWas(OUTGOING_SUBSCRIBE_PRIVATE_MESSAGE);
        testWebsocket.assertNumberOfMessagesSentIs(1);

        testWebsocket.onClose(0, "No reason", true);
        testWebsocket.onOpen(mockServerHandshake);
        testWebsocket
                .onMessage("{\"event\":\"pusher:connection_established\",\"data\":\"{\\\"socket_id\\\":\\\"23048.689386\\\"}\"}");

        verify(mockAuthorizer, times(2)).authorize(eq(PRIVATE_CHANNEL_NAME), anyString());
        testWebsocket.assertLatestMessageWas(OUTGOING_SUBSCRIBE_PRIVATE_MESSAGE);
        testWebsocket.assertNumberOfMessagesSentIs(2);
    }

    /** end of tests **/

    private void establishConnection() {

        pusher.connect(mockConnectionEventListener);

        testWebsocket.assertConnectCalled();
        verify(mockConnectionEventListener).onConnectionStateChange(
                new ConnectionStateChange(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING));

        testWebsocket.onOpen(mockServerHandshake);
        testWebsocket
                .onMessage("{\"event\":\"pusher:connection_established\",\"data\":\"{\\\"socket_id\\\":\\\"23048.689386\\\"}\"}");

        verify(mockConnectionEventListener).onConnectionStateChange(
                new ConnectionStateChange(ConnectionState.CONNECTING, ConnectionState.CONNECTED));
    }
}
