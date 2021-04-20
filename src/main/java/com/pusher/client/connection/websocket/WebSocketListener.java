package com.pusher.client.connection.websocket;

import org.java_websocket.handshake.ServerHandshake;

public interface WebSocketListener {

    void onOpen(ServerHandshake handshakeData);

    void onMessage(String message);

    void onClose(int code, String reason, boolean remote);

    void onError(Exception ex);
}
