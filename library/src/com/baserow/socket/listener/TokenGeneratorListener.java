package com.baserow.socket.listener;

public interface TokenGeneratorListener {
    void onTokenGenerated(String response, String token);

    void onError(Exception e);
}
