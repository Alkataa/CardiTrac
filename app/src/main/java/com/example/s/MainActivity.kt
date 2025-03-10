package com.example.s;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.ListView;

import androidx.activity.ComponentActivity;
import androidx.annotation.RequiresPermission;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends ComponentActivity {
    private BluetoothAdapter bluetoothAdapter;
    private ListView listView;
    private ArrayList<BluetoothDevice> pairedDevicesList = new ArrayList<>();


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        listView = findViewById(R.id.device_list);
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        pairedDevicesList.addAll(pairedDevices);


        listView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice selectedDevice = pairedDevicesList.get(position);
            saveLastDevice(selectedDevice.getAddress());
            startBluetoothService(selectedDevice.getAddress());
        });
    }

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