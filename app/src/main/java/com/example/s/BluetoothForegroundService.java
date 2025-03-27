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
        Log.d("BluetoothService", "Service started!");
        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS");

        if (deviceAddress == null) {
            Log.e("BluetoothService", "No device address provided. Stopping service.");
            stopSelf();
            return START_NOT_STICKY;
        }

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
            // Attempt standard connection
            bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
            bluetoothSocket.connect();
            inputStream = bluetoothSocket.getInputStream();
            listenForData();
        } catch (IOException e) {
            Log.e("BluetoothService", "Standard method failed, trying fallback...", e);
            try {
                // Use reflection to access hidden method
                bluetoothSocket = (BluetoothSocket) device.getClass()
                        .getMethod("createRfcommSocket", int.class)
                        .invoke(device, 1); // Port 1 is usually used for RFCOMM

                bluetoothSocket.connect();
                inputStream = bluetoothSocket.getInputStream();
                listenForData();
            } catch (Exception fallbackException) {
                Log.e("BluetoothService", "Fallback method also failed!", fallbackException);
                stopSelf(); // Stop service if connection fails
            }
        }
    }


    private void broadcastReceivedData(String data) {
        Intent intent = new Intent("com.example.s.BLUETOOTH_DATA");
        intent.putExtra("DATA", data); // Use lowercase key: "data"
        sendBroadcast(intent);
    }


    private void listenForData() {
        Log.d("BluetoothService", "Listening for data...");

        StringBuilder messageBuffer = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytes;

        while (true) {
            try {
                bytes = inputStream.read(buffer);
                if (bytes > 0) {
                    String receivedData = new String(buffer, 0, bytes);
                    Log.d("BluetoothService", "Raw Data: " + receivedData);

                    for (char c : receivedData.toCharArray()) {
                        if (c == '\n') {
                            String fullMessage = messageBuffer.toString().trim();
                            Log.d("BluetoothService", "Complete Message: " + fullMessage);

                            broadcastReceivedData(fullMessage);
                            messageBuffer.setLength(0);
                        } else {
                            messageBuffer.append(c);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e("BluetoothService", "Error reading data!", e);
                break;
            }
        }
    }

}
