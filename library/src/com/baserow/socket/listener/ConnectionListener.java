package com.baserow.socket.listener;

public interface ConnectionListener {
    void onConnected(String response);

    void onDisconnected(String response);

    void onError(Exception e);
}
