package com.example.s;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.app.Service;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;


import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
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

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
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
        exampleUsage();
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

    private void sendAlertNotification(String alertText) {
        Notification alertNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Attenzione!")
                .setContentText(alertText)
                .setSmallIcon(R.drawable.baseline_announcement_24)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
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
        try (FileOutputStream fos = openFileOutput(HEARTBEATS_FILE_NAME, Context.MODE_PRIVATE)) {
            fos.write(data.getBytes());
            Toast.makeText(this, "Health data saved successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save heartbeats", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Health data loaded successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load heartbeats", Toast.LENGTH_SHORT).show();
        }
        return data.toString();
    }

    private void verifyFileSaved(String fileName) {
        File file = getFileStreamPath(fileName);
        if (file.exists()) {
            long fileSize = file.length();
            Log.d("FileVerification", "File exists: " + file.getAbsolutePath() + ", Size: " + fileSize + " bytes");
            Toast.makeText(this, "File saved successfully: " + fileName, Toast.LENGTH_SHORT).show();
        } else {
            Log.d("FileVerification", "File does not exist: " + fileName);
            Toast.makeText(this, "File not found: " + fileName, Toast.LENGTH_SHORT).show();
        }
    }

    public void exampleUsage() {
        // Save heartbeats
        String heartbeatsData = "72\n75\n78\n80"; // Example heartbeats data
        saveHeartbeatsToFile(heartbeatsData);
        verifyFileSaved(HEARTBEATS_FILE_NAME);

        // Load heartbeats
        String loadedData = loadHeartbeatsFromFile();
        Log.d("Heartbeats", "Loaded Data: " + loadedData);
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
                                char flag = fullMessage.charAt(0);

                                switch (flag) {
                                    case '@': // Heartbeat
                                        try {
                                            String healthData = fullMessage.substring(1).trim();
                                            saveHeartbeatsToFile(healthData);
                                            broadcastReceivedData("Frequenza cardiaca: " + healthData);
                                        } catch (NumberFormatException e) {
                                            Log.e("BluetoothService", "Formato messaggio invalido: " + fullMessage);
                                        }
                                        break;

                                    case '#': // Alert
                                        sendAlertNotification("Attenzione! Sei un coglione");
                                        broadcastReceivedData("ALERT ricevuto!");
                                        break;

                                    default: // Unknown
                                        broadcastReceivedData(fullMessage); // Optional fallback
                                        break;
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
                break;
            }
        }
    }

}
