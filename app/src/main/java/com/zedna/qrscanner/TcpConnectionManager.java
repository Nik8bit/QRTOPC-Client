package com.zedna.qrscanner;

import android.os.AsyncTask;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TcpConnectionManager {
    private Socket tcpSocket;
    private PrintWriter tcpWriter;
    private boolean isConnected = false;
    private ScheduledExecutorService heartbeatExecutor;
    private ConnectionCallback connectionCallback;

    public interface ConnectionCallback {
        void onConnectionSuccess(Socket socket, PrintWriter writer);
        void onConnectionError(String error);
        void onConnectionLost();
    }

    public interface SendCallback {
        void onSendSuccess();
        void onSendError(String error);
    }

    public void connect(String ip, int port, ConnectionCallback callback) {
        this.connectionCallback = callback;

        new Thread(() -> {
            try {
                tcpSocket = new Socket(ip, port);
                tcpWriter = new PrintWriter(tcpSocket.getOutputStream(), true);
                isConnected = true;

                // Start heartbeat
                startHeartbeat();

                if (callback != null) {
                    callback.onConnectionSuccess(tcpSocket, tcpWriter);
                }
            } catch (Exception e) {
                isConnected = false;
                if (callback != null) {
                    callback.onConnectionError(e.getMessage());
                }
            }
        }).start();
    }

    public void disconnect() {
        isConnected = false;

        try {
            if (heartbeatExecutor != null) {
                heartbeatExecutor.shutdown();
            }
            if (tcpWriter != null) {
                tcpWriter.close();
            }
            if (tcpSocket != null) {
                tcpSocket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendData(String data, SendCallback callback) {
        if (!isConnected || tcpWriter == null) {
            if (callback != null) {
                callback.onSendError("Not connected");
            }
            return;
        }

        new Thread(() -> {
            try {
                tcpWriter.println(data);
                if (callback != null) {
                    callback.onSendSuccess();
                }
            } catch (Exception e) {
                isConnected = false;
                if (callback != null) {
                    callback.onSendError(e.getMessage());
                }
            }
        }).start();
    }

    private void startHeartbeat() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleWithFixedDelay(() -> {
            if (isConnected && tcpWriter != null) {
                try {
                    tcpWriter.println(".HEARTBEAT.");
                } catch (Exception e) {
                    isConnected = false;
                    if (connectionCallback != null) {
                        connectionCallback.onConnectionLost();
                    }
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    public boolean isConnected() {
        return isConnected;
    }
}