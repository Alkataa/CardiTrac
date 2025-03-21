package com.example.s;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.annotation.RequiresPermission;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends ComponentActivity {
    private BluetoothAdapter bluetoothAdapter;
    private ListView listView;
    private ArrayList<BluetoothDevice> pairedDevicesList = new ArrayList<>();

    private TextView receivedDataTextView;

    private StringBuilder dataBuffer = new StringBuilder(); // Store received messages

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  // Android 12+
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.FOREGROUND_SERVICE
            }, 1);
        }

        receivedDataTextView = findViewById(R.id.received_data);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        listView = findViewById(R.id.device_list);
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        pairedDevicesList.addAll(pairedDevices);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice selectedDevice = pairedDevicesList.get(position);
            saveLastDevice(selectedDevice.getAddress());
            startBluetoothService(selectedDevice.getAddress());
        });

        IntentFilter filter = new IntentFilter("com.example.s.BLUETOOTH_DATA");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) // Android 13+
            registerReceiver(bluetoothDataReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(bluetoothDataReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(bluetoothDataReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace(); // Receiver not registered
        }
    }

    private final BroadcastReceiver bluetoothDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String newData = intent.getStringExtra("data");
            if (newData != null) {
                dataBuffer.append(newData); // Append new data
                receivedDataTextView.setText(dataBuffer.toString()); // Update UI
            }
        }
    };

    private void startBluetoothService(String deviceAddress) {
        Intent serviceIntent = new Intent(this, BluetoothForegroundService.class);
        serviceIntent.putExtra("DEVICE_ADDRESS", deviceAddress);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        }
    }

    private void saveLastDevice(String deviceAddress) {
        SharedPreferences prefs = getSharedPreferences("BluetoothPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("LastDevice", deviceAddress);
        editor.apply();
    }

}