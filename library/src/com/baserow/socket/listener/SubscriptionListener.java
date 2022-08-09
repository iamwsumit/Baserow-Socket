package com.baserow.socket.listener;

public interface SubscriptionListener {
    void onSubscribed(String tableId,String response);

    void onFailedToSubscribe(String response);
}
