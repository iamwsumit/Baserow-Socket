package com.baserow.socket;

import android.app.Activity;
import android.util.Log;

import com.baserow.socket.listener.ConnectionListener;
import com.baserow.socket.listener.DatabaseChangeListener;
import com.baserow.socket.listener.SubscriptionListener;
import com.baserow.socket.listener.TokenGeneratorListener;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public class BaserowWebSocket {
    public static final String DEFAULT = "api.baserow.io";
    private static final String URL = "wss://api.baserow.io/ws/core/?jwt_token=";
    private static final String TAG = "BaserowSocket";

    private final Activity activity;
    private final String INSTANCE_URL;
    private final OkHttpClient client;
    private boolean isConnected = false;
    private WebSocket webSocket;
    private ConnectionListener listener;
    private SubscriptionListener subscriptionListener;
    private DatabaseChangeListener mainListener;

    private BaserowWebSocket(final Activity activity, String url) {
        this.activity = activity;
        this.INSTANCE_URL = URL.replace(DEFAULT, url);
        client = new OkHttpClient();
    }

    public static void generateJWTToken(final Activity activity, final String url, final String email, final String password, TokenGeneratorListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String finalUrl = "https://" + url + "/api/user/token-auth/";
                    String data = makeJson(new String[]{"username", "password"}, new String[]{email, password});
                    HttpURLConnection connection = (HttpURLConnection) new java.net.URL(finalUrl).openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoInput(true);
                    connection.setDoOutput(true);
                    connection.setRequestMethod("POST");
                    OutputStream os = connection.getOutputStream();
                    os.write(data.getBytes(StandardCharsets.UTF_8));
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder stringBuilder = new StringBuilder();
                    int cp;
                    while ((cp = bufferedReader.read()) != -1) {
                        stringBuilder.append((char) cp);
                    }
                    final String result = stringBuilder.toString();
                    JSONObject object = new JSONObject(result);
                    final String tokenGenerated = object.getString("token");
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onTokenGenerated(result, tokenGenerated);
                        }
                    });
                } catch (Exception e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onError(e);
                        }
                    });
                }
            }
        }).start();
    }

    public static BaserowWebSocket initialize(final Activity activity, String url) {
        if (activity == null)
            throw new IllegalArgumentException("Called initialize with null activity");
        else if (url.isEmpty())
            throw new IllegalArgumentException("Called initialized with empty url");
        else {
            return new BaserowWebSocket(activity, url);
        }
    }

    static String makeJson(String[] names, String[] values) {
        final JSONObject object = new JSONObject();
        try {
            for (int i = 0; i < names.length; i++) {
                object.put(names[i], values[i]);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to make the json", e);
        }
        return object.toString();
    }

    public void onDestroy() {
        disconnect();
    }

    private void disconnect() {
        webSocket.close(1000, "Disconnected by User");
    }

    private String getDataFromArray(final String response, String tag, String key) throws JSONException {
        JSONObject object = new JSONObject(response);
        JSONObject jsonObject = object.getJSONObject(tag);
        return jsonObject.getString(key);
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void connect(final String token, ConnectionListener listener) {
        try {
            this.listener = listener;
            okhttp3.Request request = new okhttp3.Request.Builder().url(INSTANCE_URL + token).build();
            this.webSocket = client.newWebSocket(request, new BaserowSocketlistenener());
        } catch (Exception e) {
            listener.onError(e);
            Log.e(TAG, "connection to socket failed due to " + e.getMessage());
        }
    }

    public void subscribe(int tableId, SubscriptionListener listener) {
        if (!isConnected())
            listener.onFailedToSubscribe("You have not connected the web-socket yet");
        else {
            this.subscriptionListener = listener;
            if (webSocket != null)
                webSocket.send(makeJson(new String[]{"page", "table_id"}, new String[]{"table", String.valueOf(tableId)}));
        }
    }

    public void setDatabaseChangeListener(DatabaseChangeListener listener) {
        this.mainListener = listener;
    }

    class BaserowSocketlistenener extends WebSocketListener {
        private static final int NORMAL_CLOSURE_STATUS = 1000;

        protected BaserowSocketlistenener() {
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    isConnected = true;
                    listener.onConnected(response.toString());
                }
            });
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            output(text);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(NORMAL_CLOSURE_STATUS, null);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    isConnected = false;
                    listener.onDisconnected(reason);
                }
            });
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    isConnected = false;
                    listener.onError(new Exception(t));
                }
            });
        }

        public void output(final String text) {
            try {
                final JSONObject object = new JSONObject(text);
                final String eventName = object.getString("type");
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        switch (eventName) {
                            case "page_add":
                                try {
                                    final String tableId = getDataFromArray(text, "parameters", "table_id");
                                    subscriptionListener.onSubscribed(tableId, text);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case "authentication":
                                listener.onConnected(text);
                                break;
                            case "page_discard":
                                subscriptionListener.onFailedToSubscribe(text);
                                break;
                            case "row_created":
                                if (mainListener == null)
                                    return;
                                try {
                                    String row = getDataFromArray(text, "row", "id");
                                    mainListener.onRowAdded(Integer.parseInt(row), text);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case "row_updated":
                                if (mainListener == null)
                                    return;
                                try {
                                    int row = Integer.parseInt(getDataFromArray(text, "row_before_update", "id"));
                                    mainListener.onRowUpdated(row, text);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case "row_deleted":
                                if (mainListener == null)
                                    return;
                                try {
                                    int row = object.getInt("row_id");
                                    mainListener.onRowDeleted(row, text);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                break;
                            default:
                                mainListener.onDataChanged(eventName, text);
                                break;
                        }
                    }
                });
            } catch (JSONException e) {
                listener.onError(e);
            }
        }
    }
}