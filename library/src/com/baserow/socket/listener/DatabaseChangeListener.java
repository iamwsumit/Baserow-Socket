package com.baserow.socket.listener;

public interface DatabaseChangeListener {
    void onDataChanged(String type, String response);

    void onRowAdded(int rowId, String response);

    void onRowDeleted(int rowId, String response);

    void onRowUpdated(int rowId, String response);
}
