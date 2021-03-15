package com.pusher.client.channel.impl;

import static org.mockito.Mockito.*;

import com.pusher.client.Authorizer;
import com.pusher.client.channel.ChannelState;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.connection.impl.InternalConnection;
import com.pusher.client.crypto.nacl.SecretBoxOpener;
import com.pusher.client.crypto.nacl.SecretBoxOpenerFactory;
import com.pusher.client.util.Factory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class PrivateEncryptedChannelClearsKeyTest {

    final String CHANNEL_NAME = "private-encrypted-unit-test-channel";
    final String AUTH_RESPONSE = "{\"auth\":\"636a81ba7e7b15725c00:3ee04892514e8a669dc5d30267221f16727596688894712cad305986e6fc0f3c\",\"shared_secret\":\"iBvNoPVYwByqSfg6anjPpEQ2j051b3rt1Vmnb+z5doo=\"}";

    @Mock
    InternalConnection mockInternalConnection;
    @Mock
    Authorizer mockAuthorizer;
    @Mock
    Factory mockFactory;

    @Mock
    SecretBoxOpenerFactory mockSecretBoxOpenerFactory;
    @Mock
    SecretBoxOpener mockSecretBoxOpener;

    PrivateEncryptedChannelImpl subject;

    @Before
    public void setUp() {
        when(mockAuthorizer.authorize(eq(CHANNEL_NAME), anyString())).thenReturn(AUTH_RESPONSE);
        when(mockSecretBoxOpenerFactory.create(any())).thenReturn(mockSecretBoxOpener);

        subject = new PrivateEncryptedChannelImpl(mockInternalConnection, CHANNEL_NAME,
                mockAuthorizer, mockFactory, mockSecretBoxOpenerFactory);
    }

    @Test
    public void secretBoxOpenerIsClearedOnUnsubscribed() {
        subject.toSubscribeMessage();

        subject.updateState(ChannelState.UNSUBSCRIBED);

        verify(mockSecretBoxOpener).clearKey();
    }

    @Test
    public void secretBoxOpenerIsClearedOnDisconnected() {
        doAnswer((Answer<Void>) invocation -> {
            ConnectionEventListener l = (ConnectionEventListener) invocation.getArguments()[1];
            l.onConnectionStateChange(new ConnectionStateChange(
                    ConnectionState.DISCONNECTING,
                    ConnectionState.DISCONNECTED
            ));
            return null;
        }).when(mockInternalConnection).bind(eq(ConnectionState.DISCONNECTED), any());
        subject.toSubscribeMessage();

        verify(mockSecretBoxOpener).clearKey();
    }

    @Test
    public void secretBoxOpenerIsClearedOnceOnUnsubscribedAndThenDisconnected() {
        doAnswer((Answer<Void>) invocation -> {
            subject.updateState(ChannelState.UNSUBSCRIBED);

            ConnectionEventListener l = (ConnectionEventListener) invocation.getArguments()[1];
            l.onConnectionStateChange(new ConnectionStateChange(
                    ConnectionState.DISCONNECTING,
                    ConnectionState.DISCONNECTED
            ));

            return null;
        }).when(mockInternalConnection).bind(eq(ConnectionState.DISCONNECTED), any());
        subject.toSubscribeMessage();

        verify(mockSecretBoxOpener).clearKey();
    }
}
