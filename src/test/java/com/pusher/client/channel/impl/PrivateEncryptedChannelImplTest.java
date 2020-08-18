package com.pusher.client.channel.impl;

import com.pusher.client.AuthorizationFailureException;
import com.pusher.client.Authorizer;
import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.channel.PrivateEncryptedChannelEventListener;
import com.pusher.client.connection.impl.InternalConnection;
import com.pusher.client.crypto.nacl.SecretBoxOpener;
import com.pusher.client.crypto.nacl.SecretBoxOpenerFactory;
import com.pusher.client.util.internal.Base64;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PrivateEncryptedChannelImplTest extends ChannelImplTest {

    final String AUTH_RESPONSE = "{\"auth\":\"636a81ba7e7b15725c00:3ee04892514e8a669dc5d30267221f16727596688894712cad305986e6fc0f3c\",\"shared_secret\":\"iBvNoPVYwByqSfg6anjPpEQ2j051b3rt1Vmnb+z5doo=\"}";
    final String AUTH_RESPONSE_MISSING_AUTH = "{\"shared_secret\":\"iBvNoPVYwByqSfg6anjPpEQ2j051b3rt1Vmnb+z5doo=\"}";
    final String AUTH_RESPONSE_MISSING_SHARED_SECRET = "{\"auth\":\"636a81ba7e7b15725c00:3ee04892514e8a669dc5d30267221f16727596688894712cad305986e6fc0f3c\"}";
    final String AUTH_RESPONSE_INVALID_JSON = "potatoes";
    final String SHARED_SECRET = "iBvNoPVYwByqSfg6anjPpEQ2j051b3rt1Vmnb+z5doo=";
    final String AUTH_RESPONSE_INCORRECT_SHARED_SECRET = "{\"auth\":\"636a81ba7e7b15725c00:3ee04892514e8a669dc5d30267221f16727596688894712cad305986e6fc0f3c\",\"shared_secret\":\"iBvNoPVYwByqSfg6anjPpEQ2j051b3rt1Vmnb+z5do0=\"}";
    final String SHARED_SECRET_INCORRECT = "iBvNoPVYwByqSfg6anjPpEQ2j051b3rt1Vmnb+z5do0=";

    @Mock
    InternalConnection mockInternalConnection;
    @Mock
    Authorizer mockAuthorizer;
    @Mock
    SecretBoxOpenerFactory mockSecretBoxOpenerFactory;

    @Override
    @Before
    public void setUp() {
        super.setUp();
        when(mockAuthorizer.authorize(eq(getChannelName()), anyString())).thenReturn(AUTH_RESPONSE);
    }

    protected PrivateEncryptedChannelImpl newInstance() {
        return new PrivateEncryptedChannelImpl(mockInternalConnection, getChannelName(),
                mockAuthorizer, factory, mockSecretBoxOpenerFactory);
    }

    @Override
    protected ChannelImpl newInstance(final String channelName) {
        return new PrivateEncryptedChannelImpl(mockInternalConnection, channelName, mockAuthorizer,
                factory, mockSecretBoxOpenerFactory);
    }

    protected String getChannelName() {
        return "private-encrypted-channel";
    }

    @Test
    public void toStringIsAccurate() {
        assertEquals("[Private Encrypted Channel: name="+getChannelName()+"]", channel.toString());
    }

    
    /*
    TESTING VALID PRIVATE ENCRYPTED CHANNEL NAMES
     */

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
    @Test
    public void testPrivateEncryptedChannelName() {
        newInstance("private-encrypted-stuffchannel");
    }

    @Override
    @Test(expected = IllegalArgumentException.class)
    public void testPrivateChannelName() {  newInstance("private-stuffchannel");  }

    /*
    TESTING SUBSCRIBE MESSAGE
     */

    @Override
    @Test
    public void testReturnsCorrectSubscribeMessage() {
        assertEquals("{\"event\":\"pusher:subscribe\",\"data\":{" +
                "\"auth\":\"636a81ba7e7b15725c00:3ee04892514e8a669dc5d30267221f16727596688894712cad305986e6fc0f3c\","+
                "\"channel\":\"" + getChannelName() + "\"" +
                "}}", channel.getSubscribeMessage());
    }

    /*
    TESTING AUTHENTICATION METHOD
     */

    @Test
    public void authenticationSucceedsGivenValidAuthorizer() {
        when(mockAuthorizer.authorize(Matchers.anyString(), Matchers.anyString()))
                .thenReturn(AUTH_RESPONSE);

        PrivateEncryptedChannelImpl channel = newInstance();

        channel.getSubscribeMessage();
    }

    protected ChannelEventListener getEventListener() {
        return mock(PrivateEncryptedChannelEventListener.class);
    }

    @Test(expected = AuthorizationFailureException.class)
    public void authenticationThrowsExceptionIfNoAuthKey() {
        when(mockAuthorizer.authorize(Matchers.anyString(), Matchers.anyString()))
                .thenReturn(AUTH_RESPONSE_MISSING_AUTH);

        PrivateEncryptedChannelImpl channel = newInstance();

        channel.getSubscribeMessage();
    }

    @Test(expected = AuthorizationFailureException.class)
    public void authenticationThrowsExceptionIfNoSharedSecret() {
        when(mockAuthorizer.authorize(Matchers.anyString(), Matchers.anyString()))
                .thenReturn(AUTH_RESPONSE_MISSING_SHARED_SECRET);

        PrivateEncryptedChannelImpl channel = newInstance();

        channel.getSubscribeMessage();
    }

    @Test(expected = AuthorizationFailureException.class)
    public void authenticationThrowsExceptionIfMalformedJson() {
        when(mockAuthorizer.authorize(Matchers.anyString(), Matchers.anyString()))
                .thenReturn(AUTH_RESPONSE_INVALID_JSON);

        PrivateEncryptedChannelImpl channel = newInstance();

        channel.getSubscribeMessage();
    }

    /*
    ON MESSAGE
     */
    @Test
    public void testDataIsExtractedFromMessageAndPassedToSingleListener() {
        PrivateEncryptedChannelImpl channel = new PrivateEncryptedChannelImpl(
                mockInternalConnection,
                getChannelName(),
                mockAuthorizer,
                factory,
                mockSecretBoxOpenerFactory);

        when(mockAuthorizer.authorize(Matchers.anyString(), Matchers.anyString()))
                .thenReturn(AUTH_RESPONSE);
        when(mockSecretBoxOpenerFactory.create(any()))
                .thenReturn(new SecretBoxOpener(Base64.decode(SHARED_SECRET)));

        channel.getSubscribeMessage();

        PrivateEncryptedChannelEventListener mockListener = mock(PrivateEncryptedChannelEventListener.class);

        channel.bind("my-event", mockListener);
        channel.onMessage("my-event", "{\"event\":\"event1\",\"data\":\"{" +
                "\\\"nonce\\\": \\\"4sVYwy4j/8dCcjyxtPCWyk19GaaViaW9\\\"," +
                "\\\"ciphertext\\\": \\\"/GMESnFGlbNn01BuBjp31XYa3i9vZsGKR8fgR9EDhXKx3lzGiUD501A=\\\"" +
                "}\"}");

        verify(mockListener, times(1)).onEvent(argCaptor.capture());
        assertEquals("event1", argCaptor.getValue().getEventName());
        assertEquals("{\"message\":\"hello world\"}", argCaptor.getValue().getData());
    }

    @Test
    public void testDataIsExtractedFromMessageAndPassedToMultipleListeners() {
        PrivateEncryptedChannelImpl channel = new PrivateEncryptedChannelImpl(
                mockInternalConnection,
                getChannelName(),
                mockAuthorizer,
                factory,
                mockSecretBoxOpenerFactory);

        when(mockAuthorizer.authorize(Matchers.anyString(), Matchers.anyString()))
                .thenReturn(AUTH_RESPONSE);
        when(mockSecretBoxOpenerFactory.create(any()))
                .thenReturn(new SecretBoxOpener(Base64.decode(SHARED_SECRET)));

        channel.getSubscribeMessage();

        PrivateEncryptedChannelEventListener mockListener1 = mock(PrivateEncryptedChannelEventListener.class);
        PrivateEncryptedChannelEventListener mockListener2 = mock(PrivateEncryptedChannelEventListener.class);

        channel.bind("my-event", mockListener1);
        channel.bind("my-event", mockListener2);
        channel.onMessage("my-event", "{\"event\":\"event1\",\"data\":\"{" +
                "\\\"nonce\\\": \\\"4sVYwy4j/8dCcjyxtPCWyk19GaaViaW9\\\"," +
                "\\\"ciphertext\\\": \\\"/GMESnFGlbNn01BuBjp31XYa3i9vZsGKR8fgR9EDhXKx3lzGiUD501A=\\\"" +
                "}\"}");

        verify(mockListener1).onEvent(argCaptor.capture());
        assertEquals("event1", argCaptor.getValue().getEventName());
        assertEquals("{\"message\":\"hello world\"}", argCaptor.getValue().getData());

        verify(mockListener2).onEvent(argCaptor.capture());
        assertEquals("event1", argCaptor.getValue().getEventName());
        assertEquals("{\"message\":\"hello world\"}", argCaptor.getValue().getData());
    }

    @Test
    public void onMessageRaisesExceptionWhenFailingToDecryptTwice() {
        PrivateEncryptedChannelImpl channel = new PrivateEncryptedChannelImpl(
                mockInternalConnection,
                getChannelName(),
                mockAuthorizer,
                factory,
                mockSecretBoxOpenerFactory);

        when(mockAuthorizer.authorize(Matchers.anyString(), Matchers.anyString()))
                .thenReturn(AUTH_RESPONSE_INCORRECT_SHARED_SECRET)
                .thenReturn(AUTH_RESPONSE_INCORRECT_SHARED_SECRET);
        when(mockSecretBoxOpenerFactory.create(any()))
                .thenReturn(new SecretBoxOpener(Base64.decode(SHARED_SECRET_INCORRECT)))
                .thenReturn(new SecretBoxOpener(Base64.decode(SHARED_SECRET_INCORRECT)));

        channel.getSubscribeMessage();

        PrivateEncryptedChannelEventListener mockListener1 = mock(PrivateEncryptedChannelEventListener.class);
        channel.bind("my-event", mockListener1);
        channel.onMessage("my-event", "{\"event\":\"event1\",\"data\":\"{" +
                "\\\"nonce\\\": \\\"4sVYwy4j/8dCcjyxtPCWyk19GaaViaW9\\\"," +
                "\\\"ciphertext\\\": \\\"/GMESnFGlbNn01BuBjp31XYa3i9vZsGKR8fgR9EDhXKx3lzGiUD501A=\\\"" +
                "}\"}");

        verify(mockListener1).onDecryptionFailure(anyString(), anyString());
    }

    @Test
    public void onMessageRetriesDecryptionOnce() {
        PrivateEncryptedChannelImpl channel = new PrivateEncryptedChannelImpl(
                mockInternalConnection,
                getChannelName(),
                mockAuthorizer,
                factory,
                mockSecretBoxOpenerFactory);

        when(mockAuthorizer.authorize(Matchers.anyString(), Matchers.anyString()))
                .thenReturn(AUTH_RESPONSE_INCORRECT_SHARED_SECRET)
                .thenReturn(AUTH_RESPONSE);
        when(mockSecretBoxOpenerFactory.create(any()))
                .thenReturn(new SecretBoxOpener(Base64.decode(SHARED_SECRET_INCORRECT)))
                .thenReturn(new SecretBoxOpener(Base64.decode(SHARED_SECRET)));

        channel.getSubscribeMessage();

        PrivateEncryptedChannelEventListener mockListener1 = mock(PrivateEncryptedChannelEventListener.class);
        channel.bind("my-event", mockListener1);
        channel.onMessage("my-event", "{\"event\":\"event1\",\"data\":\"{" +
                "\\\"nonce\\\": \\\"4sVYwy4j/8dCcjyxtPCWyk19GaaViaW9\\\"," +
                "\\\"ciphertext\\\": \\\"/GMESnFGlbNn01BuBjp31XYa3i9vZsGKR8fgR9EDhXKx3lzGiUD501A=\\\"" +
                "}\"}");

        verify(mockListener1).onEvent(argCaptor.capture());
        assertEquals("event1", argCaptor.getValue().getEventName());
        assertEquals("{\"message\":\"hello world\"}", argCaptor.getValue().getData());
    }

    @Test
    public void twoEventsReceivedWithSecondRetryCorrect() {

        PrivateEncryptedChannelImpl channel = new PrivateEncryptedChannelImpl(
                mockInternalConnection,
                getChannelName(),
                mockAuthorizer,
                factory,
                mockSecretBoxOpenerFactory);

        when(mockAuthorizer.authorize(Matchers.anyString(), Matchers.anyString()))
                .thenReturn(AUTH_RESPONSE_INCORRECT_SHARED_SECRET)
                .thenReturn(AUTH_RESPONSE_INCORRECT_SHARED_SECRET)
                .thenReturn(AUTH_RESPONSE);
        when(mockSecretBoxOpenerFactory.create(any()))
                .thenReturn(new SecretBoxOpener(Base64.decode(SHARED_SECRET_INCORRECT)))
                .thenReturn(new SecretBoxOpener(Base64.decode(SHARED_SECRET_INCORRECT)))
                .thenReturn(new SecretBoxOpener(Base64.decode(SHARED_SECRET)));

        channel.getSubscribeMessage();

        PrivateEncryptedChannelEventListener mockListener1 = mock(PrivateEncryptedChannelEventListener.class);
        channel.bind("my-event", mockListener1);
        channel.onMessage("my-event", "{\"event\":\"event1\",\"data\":\"{" +
                "\\\"nonce\\\": \\\"4sVYwy4j/8dCcjyxtPCWyk19GaaViaW9\\\"," +
                "\\\"ciphertext\\\": \\\"/GMESnFGlbNn01BuBjp31XYa3i9vZsGKR8fgR9EDhXKx3lzGiUD501A=\\\"" +
                "}\"}");

        verify(mockListener1).onDecryptionFailure("my-event", "Failed to decrypt message.");

        // send a second message
        channel.onMessage("my-event", "{\"event\":\"event1\",\"data\":\"{" +
                "\\\"nonce\\\": \\\"4sVYwy4j/8dCcjyxtPCWyk19GaaViaW9\\\"," +
                "\\\"ciphertext\\\": \\\"/GMESnFGlbNn01BuBjp31XYa3i9vZsGKR8fgR9EDhXKx3lzGiUD501A=\\\"" +
                "}\"}");

        verify(mockListener1).onEvent(argCaptor.capture());
        assertEquals("event1", argCaptor.getValue().getEventName());
        assertEquals("{\"message\":\"hello world\"}", argCaptor.getValue().getData());
    }

    @Test
    public void twoEventsReceivedWithIncorrectSharedSecret() {

        PrivateEncryptedChannelImpl channel = new PrivateEncryptedChannelImpl(
                mockInternalConnection,
                getChannelName(),
                mockAuthorizer,
                factory,
                mockSecretBoxOpenerFactory);

        when(mockAuthorizer.authorize(Matchers.anyString(), Matchers.anyString()))
                .thenReturn(AUTH_RESPONSE_INCORRECT_SHARED_SECRET)
                .thenReturn(AUTH_RESPONSE_INCORRECT_SHARED_SECRET)
                .thenReturn(AUTH_RESPONSE_INCORRECT_SHARED_SECRET);
        when(mockSecretBoxOpenerFactory.create(any()))
                .thenReturn(new SecretBoxOpener(Base64.decode(SHARED_SECRET_INCORRECT)))
                .thenReturn(new SecretBoxOpener(Base64.decode(SHARED_SECRET_INCORRECT)))
                .thenReturn(new SecretBoxOpener(Base64.decode(SHARED_SECRET_INCORRECT)));

        channel.getSubscribeMessage();

        PrivateEncryptedChannelEventListener mockListener1 = mock(PrivateEncryptedChannelEventListener.class);
        channel.bind("my-event", mockListener1);
        channel.onMessage("my-event", "{\"event\":\"event1\",\"data\":\"{" +
                "\\\"nonce\\\": \\\"4sVYwy4j/8dCcjyxtPCWyk19GaaViaW9\\\"," +
                "\\\"ciphertext\\\": \\\"/GMESnFGlbNn01BuBjp31XYa3i9vZsGKR8fgR9EDhXKx3lzGiUD501A=\\\"" +
                "}\"}");
        // send a second message
        channel.onMessage("my-event", "{\"event\":\"event1\",\"data\":\"{" +
                "\\\"nonce\\\": \\\"4sVYwy4j/8dCcjyxtPCWyk19GaaViaW9\\\"," +
                "\\\"ciphertext\\\": \\\"/GMESnFGlbNn01BuBjp31XYa3i9vZsGKR8fgR9EDhXKx3lzGiUD501A=\\\"" +
                "}\"}");

        verify(mockListener1, times(2))
                .onDecryptionFailure("my-event", "Failed to decrypt message.");
    }

}
