package com.example.s;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.app.Service;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothForegroundService extends Service {
    private static final String CHANNEL_ID = "BluetoothServiceChannel";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final String HEARTBEATS_FILE_NAME = "healthData.txt";
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private String deviceAddress;
    private boolean isReconnecting = false; // Flag to track reconnection attempts

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if ("DISCONNECT".equals(intent.getAction())) {
            disconnectFromDevice();
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.d("BluetoothService", "Service started!");
        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS");

        if (deviceAddress == null) {
            Log.e("BluetoothService", "No device address provided. Stopping service.");
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(1, createNotification(false)); // Start with a placeholder notification
        new Thread(this::connectToDevice).start();
        return START_STICKY;
    }

    private Notification createNotification(boolean isConnected) {
        NotificationChannel channel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = new NotificationChannel(
                    CHANNEL_ID, "Bluetooth Service", NotificationManager.IMPORTANCE_LOW);
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(channel);
        }

        // Intent to disconnect when notification is pressed
        Intent disconnectIntent = new Intent(this, BluetoothForegroundService.class);
        disconnectIntent.setAction("DISCONNECT");
        PendingIntent pendingIntent = PendingIntent.getService(
                this, 0, disconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bluetooth Service")
                .setContentText(isConnected ? "Connesso" : "Tentativo di connessione...")
                .setSmallIcon(R.drawable.baseline_notifications_24)
                .setContentIntent(pendingIntent) // Add the disconnect action
                .setOngoing(isConnected) // Make it persistent only when connected
                .build();
    }

    private void sendAlertNotification(String alertText) {
        Notification alertNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Attenzione!")
                .setContentText(alertText)
                .setSmallIcon(R.drawable.baseline_announcement_24)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(new long[]{0, 1000, 200, 500})
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(2, alertNotification);
        }

        Log.d("BluetoothService", "Alert notification sent: " + alertText);
    }

    private void saveHeartbeat(String data) {
        long timestamp = System.currentTimeMillis();
        String message = "Frequenza cardiaca: " + data + " a " + timestamp;

        Log.d("BluetoothService", message);

        Intent intent = new Intent("com.example.s.BLUETOOTH_DATA");
        intent.putExtra("DATA", message);
        sendBroadcast(intent);
    }

    private void saveHeartbeatsToFile(String data) {
        File file = getFileStreamPath(HEARTBEATS_FILE_NAME);

        try {
            // Check if the file exceeds the maximum size
            if (file.exists() && file.length() > MAX_FILE_SIZE) {
                truncateFile(file);
            }

            // Append new data to the file
            try (FileOutputStream fos = new FileOutputStream(file, true)) {
                fos.write((data + "\n").getBytes());
            }

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Dati salute non salvati correttamente", Toast.LENGTH_SHORT).show();
        }
    }

    private void truncateFile(File file) {
        try {
            // Read the file into memory
            StringBuilder data = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    data.append(line).append("\n");
                }
            }

            // Keep only the last half of the file's content
            String[] lines = data.toString().split("\n");
            int start = lines.length / 2; // Start from the middle
            StringBuilder truncatedData = new StringBuilder();
            for (int i = start; i < lines.length; i++) {
                truncatedData.append(lines[i]).append("\n");
            }

            // Overwrite the file with the truncated content
            try (FileOutputStream fos = new FileOutputStream(file, false)) {
                fos.write(truncatedData.toString().getBytes());
            }

            Log.d("BluetoothService", "File truncated successfully. Old entries removed.");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("BluetoothService", "Failed to truncate file.");
        }
    }

    private String loadHeartbeatsFromFile() {
        StringBuilder data = new StringBuilder();
        try (FileInputStream fis = openFileInput(HEARTBEATS_FILE_NAME);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
            String line;
            while ((line = reader.readLine()) != null) {
                data.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Dati salute non caricati correttamente", Toast.LENGTH_SHORT).show();
        }
        return data.toString();
    }

    private void verifyFileSaved(String fileName) {
        File file = getFileStreamPath(fileName);
        if (file.exists()) {
            long fileSize = file.length();
            Log.d("FileVerification", "File exists: " + file.getAbsolutePath() + ", Size: " + fileSize + " bytes");
            Toast.makeText(this, "File salvato correttamente: " + fileName, Toast.LENGTH_SHORT).show();
        } else {
            Log.d("FileVerification", "File does not exist: " + fileName);
            Toast.makeText(this, "File non salvato correttamente: " + fileName, Toast.LENGTH_SHORT).show();
        }
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
            outputStream = bluetoothSocket.getOutputStream();

            // Update notification to show connected status
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(1, createNotification(true));
            }

            listenForData();
        } catch (IOException e) {
            Log.e("BluetoothService", "Connection failed!", e);
            attemptReconnect();
        }
    }

    private void disconnectFromDevice() {
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.cancel(1); // Remove the notification
            }
            Log.d("BluetoothService", "Disconnected from device.");
        } catch (IOException e) {
            Log.e("BluetoothService", "Error while disconnecting!", e);
        }
    }

    private void attemptReconnect() {
        if (isReconnecting) return; // Avoid multiple reconnection attempts
        isReconnecting = true;

        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 60000) { // Retry for 1 minute
                try {
                    Log.d("BluetoothService", "Attempting to reconnect...");
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.e("BluetoothService", "Missing BLUETOOTH_CONNECT permission.");
                        return;
                    }
                    connectToDevice(); // Attempt to reconnect
                    isReconnecting = false; // Stop reconnection attempts if successful
                    return;
                } catch (Exception e) {
                    Log.e("BluetoothService", "Reconnection attempt failed. Retrying...", e);
                }

                try {
                    Thread.sleep(5000); // Wait 5 seconds before retrying
                } catch (InterruptedException e) {
                    Log.e("BluetoothService", "Reconnection thread interrupted", e);
                    break;
                }
            }

            Log.e("BluetoothService", "Failed to reconnect after 1 minute. Stopping service.");
            stopSelf(); // Stop the service if reconnection fails
            isReconnecting = false;
        }).start();
    }

    private void broadcastReceivedData(String data) {
        Intent intent = new Intent("com.example.s.BLUETOOTH_DATA");
        intent.setPackage(getPackageName());
        intent.putExtra("DATA", data); // Use lowercase key: "data"
        sendBroadcast(intent);
        Log.d("BluetoothService", "Broadcasted data: " + data);
    }

    private void listenForData() {
        Log.d("BluetoothService", "Listening for data...");

        StringBuilder messageBuffer = new StringBuilder();
        byte[] buffer = new byte[4096];
        int bytes;

        while (true) {
            try {
                bytes = inputStream.read(buffer);
                if (bytes > 0) {
                    String receivedData = new String(buffer, 0, bytes);
                    Log.d("BluetoothService", "Raw Data: " + receivedData);
                    outputStream.write("*".getBytes());
                    Log.d("BluetoothService", "Sent *");
                    for (char c : receivedData.toCharArray()) {
                        if (c == '%') {
                            String fullMessage = messageBuffer.toString().trim();
                            Log.d("BluetoothService", "Complete Message: " + fullMessage);

                            if (!fullMessage.isEmpty()) {
                                char flag = fullMessage.charAt(fullMessage.length() - 2);

                                if (flag == '1') {
                                    sendAlertNotification("Attenzione! Postura errata!");
                                    Log.d("BluetoothService", "Alert received!");
                                }
                            }

                            broadcastReceivedData(fullMessage);
                            messageBuffer.setLength(0);
                        } else {
                            messageBuffer.append(c);
                        }
                    }

                }
            } catch (IOException e) {
                Log.e("BluetoothService", "Error reading data!", e);
                disconnectFromDevice(); // Disconnect on error
                attemptReconnect(); // Start reconnection attempts if connection is lost
                break;
            }
        }
    }
}
