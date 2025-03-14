package com.example.s;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.app.Service;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;


import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class BluetoothForegroundService extends Service {
    private static final String CHANNEL_ID = "BluetoothServiceChannel";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private String deviceAddress;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS");
        startForeground(1, createNotification());
        new Thread(this::connectToDevice).start();
        return START_STICKY;
    }

    private Notification createNotification() {
        NotificationChannel channel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = new NotificationChannel(
                    CHANNEL_ID, "Bluetooth Service", NotificationManager.IMPORTANCE_LOW);
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(channel);
            }
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bluetooth Service")
                .setContentText("Listening for Bluetooth data...")
                .setSmallIcon(R.drawable.baseline_notifications_24)
                .build();
    }

    private void sendAlertNotification() {
        Notification alertNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Alert!")
                .setContentText("Boolean value received as false!")
                .setSmallIcon(R.drawable.baseline_announcement_24)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(2, alertNotification);
        }
    }

    private void saveHeartbeat(String data) {
        long timestamp = System.currentTimeMillis();
        String message = "Ricevuto: " + data + " a " + timestamp + "\n";

        Intent intent = new Intent("BluetoothDataUpdate");
        intent.putExtra("data", message);
        sendBroadcast(intent);
    }



    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void connectToDevice() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            bluetoothSocket.connect();
            inputStream = bluetoothSocket.getInputStream();
            listenForData();
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
    }

    private void broadcastReceivedData(String data) {
        Intent intent = new Intent("com.example.s.BLUETOOTH_DATA");
        intent.putExtra("DATA", data);
        sendBroadcast(intent);
    }

    private void listenForData() {
        StringBuilder messageBuffer = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytes;

        while (true) {
            try {
                bytes = inputStream.read(buffer);
                String receivedData = new String(buffer, 0, bytes);

                for (char c : receivedData.toCharArray()) {
                    if (c == '\n') { // Detect newline character
                        String fullMessage = messageBuffer.toString().trim();
                        Log.d("BluetoothService", "Received: " + fullMessage);

                        broadcastReceivedData(fullMessage); // Send data to MainActivity

//                        if ("0".equals(fullMessage)) {
//                            sendAlertNotification();
//                        } else {
//                            saveHeartbeat(fullMessage);
//                        }

                        messageBuffer.setLength(0); // Clear buffer for next message
                    } else {
                        messageBuffer.append(c);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }
}
