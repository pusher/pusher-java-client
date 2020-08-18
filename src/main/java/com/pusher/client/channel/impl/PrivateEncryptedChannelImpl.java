package com.pusher.client.channel.impl;

import com.google.gson.JsonSyntaxException;
import com.pusher.client.AuthorizationFailureException;
import com.pusher.client.Authorizer;
import com.pusher.client.channel.ChannelState;
import com.pusher.client.channel.PrivateEncryptedChannel;
import com.pusher.client.channel.PrivateEncryptedChannelEventListener;
import com.pusher.client.channel.PusherEvent;
import com.pusher.client.channel.SubscriptionEventListener;
import com.pusher.client.channel.impl.message.AuthResponse;
import com.pusher.client.channel.impl.message.EncryptedReceivedData;
import com.pusher.client.channel.impl.message.SubscribeMessage;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.connection.impl.InternalConnection;
import com.pusher.client.crypto.nacl.AuthenticityException;
import com.pusher.client.crypto.nacl.SecretBoxOpener;
import com.pusher.client.crypto.nacl.SecretBoxOpenerFactory;
import com.pusher.client.util.Factory;
import com.pusher.client.util.internal.Base64;

import java.util.Map;
import java.util.Set;

public class PrivateEncryptedChannelImpl extends ChannelImpl implements PrivateEncryptedChannel {

    private final InternalConnection connection;
    private final Authorizer authorizer;
    private SecretBoxOpenerFactory secretBoxOpenerFactory;
    private SecretBoxOpener secretBoxOpener;

    // For not hanging on to shared secret past the Pusher.disconnect() call,
    // i.e. when not necessary. Pusher.connect(...) call will trigger re-subscribe
    // and hence re-authenticate which creates a new secretBoxOpener.
    private ConnectionEventListener disposeSecretBoxOpenerOnDisconnectedListener =
            new ConnectionEventListener() {

        @Override
        public void onConnectionStateChange(ConnectionStateChange change) {
            disposeSecretBoxOpener();
        }

        @Override
        public void onError(String message, String code, Exception e) {
            // nop
        }
    };

    public PrivateEncryptedChannelImpl(final InternalConnection connection,
                                       final String channelName,
                                       final Authorizer authorizer,
                                       final Factory factory,
                                       final SecretBoxOpenerFactory secretBoxOpenerFactory) {
        super(channelName, factory);
        this.connection = connection;
        this.authorizer = authorizer;
        this.secretBoxOpenerFactory = secretBoxOpenerFactory;
    }

    @Override
    public void bind(final String eventName, final SubscriptionEventListener listener) {

        if (!(listener instanceof PrivateEncryptedChannelEventListener)) {
            throw new IllegalArgumentException(
                    "Only instances of PrivateEncryptedChannelEventListener can be bound " +
                            "to a private encrypted channel");
        }

        super.bind(eventName, listener);
    }

    @Override
    public String getSubscribeMessage() {

        return GSON.toJson(new SubscribeMessage(name, authenticate(), null));
    }

    private String authenticate() {
        try {
            final AuthResponse authResponse = GSON.fromJson(getAuthResponse(), AuthResponse.class);
            if (authResponse.getAuth() == null
                    || authResponse.getSharedSecret() == null) {
                throw new AuthorizationFailureException("Didn't receive all the fields expected " +
                        "from the Authorizer, expected an auth and shared_secret.");
            } else {
                createSecretBoxOpener(Base64.decode(authResponse.getSharedSecret()));
                return authResponse.getAuth();
            }

        } catch (JsonSyntaxException e) {
            throw new AuthorizationFailureException("Unable to parse response from Authorizer");
        }
    }

    private void createSecretBoxOpener(byte[] key) {
        secretBoxOpener = secretBoxOpenerFactory.create(key);
        setListenerToDisposeSecretBoxOpenerOnDisconnected();
    }

    private void setListenerToDisposeSecretBoxOpenerOnDisconnected() {
        connection.bind(ConnectionState.DISCONNECTED,
                disposeSecretBoxOpenerOnDisconnectedListener);
    }

    @Override
    public void updateState(ChannelState state) {
        super.updateState(state);

        if (state == ChannelState.UNSUBSCRIBED) {
            disposeSecretBoxOpener();
        }
    }

    @Override
    public PusherEvent prepareEvent(String event, String message) {

        try {
            return decryptMessage(message);
        } catch (AuthenticityException e1) {

            // retry once only.
            disposeSecretBoxOpener();
            authenticate();

            try {
                return decryptMessage(message);
            } catch (AuthenticityException e2) {
                // deliberately not destroying the secretBoxOpener so the next message
                // has an opportunity to fetch a new key and decrypt
                notifyListenersOfDecryptFailure(event, "Failed to decrypt message.");
            }
        }

        return null;
    }

    private void notifyListenersOfDecryptFailure(final String event, final String reason) {
        Set<SubscriptionEventListener> listeners = getInterestedListeners(event);
        if (listeners != null) {
            for (SubscriptionEventListener listener : listeners) {
                ((PrivateEncryptedChannelEventListener)listener).onDecryptionFailure(
                        event, reason);
            }
        }
    }

    private PusherEvent decryptMessage(String message) {

        Map<String, Object> receivedMessage =
                GSON.<Map<String, Object>>fromJson(message, Map.class);

        final EncryptedReceivedData encryptedReceivedData =
                GSON.fromJson((String)receivedMessage.get("data"), EncryptedReceivedData.class);

        String decryptedData = secretBoxOpener.open(
                encryptedReceivedData.getCiphertext(),
                encryptedReceivedData.getNonce());

        receivedMessage.put("data", decryptedData);

        return new PusherEvent(receivedMessage);
    }

    private void disposeSecretBoxOpener() {
        if (secretBoxOpener != null) {
            secretBoxOpener.clearKey();
            secretBoxOpener = null;
            removeListenerToDisposeSecretBoxOpenerOnDisconnected();
        }
    }

    private void removeListenerToDisposeSecretBoxOpenerOnDisconnected() {
        connection.unbind(ConnectionState.DISCONNECTED,
                disposeSecretBoxOpenerOnDisconnectedListener);
    }

    private String getAuthResponse() {
        final String socketId = connection.getSocketId();
        return authorizer.authorize(getName(), socketId);
    }

    @Override
    protected String[] getDisallowedNameExpressions() {
        return new String[] { "^(?!private-encrypted-).*" };
    }

    @Override
    public String toString() {
        return String.format("[Private Encrypted Channel: name=%s]", name);
    }
}
