package com.sumit.baserowsocket;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.baserow.socket.BaserowWebSocket;
import com.baserow.socket.listener.ConnectionListener;
import com.baserow.socket.listener.DatabaseChangeListener;
import com.baserow.socket.listener.SubscriptionListener;
import com.baserow.socket.listener.TokenGeneratorListener;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "BaserowWebSocket";
    private TextView response;
    private final DatabaseChangeListener listener = new DatabaseChangeListener() {
        @Override
        public void onDataChanged(String s, String s1) {
            response.setText(s1);
        }

        @Override
        public void onRowAdded(int i, String s) {
            response.setText(s);
        }

        @Override
        public void onRowDeleted(int i, String s) {
            response.setText(s);
        }

        @Override
        public void onRowUpdated(int i, String s) {
            response.setText(s);
        }
    };
    private BaserowWebSocket socket;
    private String JWT = "";

    private void handleError(Exception e) {
        Log.e(TAG, "Error : ", e);
        response.setText("Error Occurred");
        Toast.makeText(this, "Some error occurred", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.response = findViewById(R.id.response);

        socket = BaserowWebSocket.initialize(this, BaserowWebSocket.DEFAULT); // BaserowWebSocket.DEFAULT : api.baserow.io

        // Declaring the listener variables

        final TokenGeneratorListener tokenGeneratorListener = new TokenGeneratorListener() {
            @Override
            public void onTokenGenerated(String response, String token) {
                JWT = token;
                connectWebSocket();
            }

            @Override
            public void onError(Exception e) {
                handleError(e);
            }
        };

        BaserowWebSocket.generateJWTToken(this,
                BaserowWebSocket.DEFAULT,
                "<username>",
                "<password>",
                tokenGeneratorListener
        );
    }

    private void subscribe(int tableId) {
        socket.subscribe(tableId, new SubscriptionListener() {
            @Override
            public void onSubscribed(String s, String s1) {
                socket.setDatabaseChangeListener(listener);
                response.setText("Table is subscribed");
            }

            @Override
            public void onFailedToSubscribe(String s) {
                response.setText(s);
            }
        });
    }

    private void connectWebSocket() {
        socket.connect(JWT, new ConnectionListener() {
            @Override
            public void onConnected(String s) {
                subscribe(0000);
            }

            @Override
            public void onDisconnected(String s) {
                response.setText("Web socket disconnected");
            }

            @Override
            public void onError(Exception e) {
                handleError(e);
            }
        });
    }
}