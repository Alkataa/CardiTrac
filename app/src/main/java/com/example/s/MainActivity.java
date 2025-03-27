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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends ComponentActivity {

    private static final int BLUETOOTH_PERMISSION_REQUEST = 1;

    private BluetoothAdapter bluetoothAdapter;
    private ListView listView;
    private ArrayList<BluetoothDevice> pairedDevicesList = new ArrayList<>();

    private TextView receivedDataTextView;

    ArrayList<String> deviceNamesList = new ArrayList<>();

    private StringBuilder dataBuffer = new StringBuilder(); // Store received messages

    private static final long updateDelayMs = 5000; // Delay in milliseconds (e.g., 1 second)
    private long lastUpdateTime = 0;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  // Android 12+
            if (!hasBluetoothPermissions()) {
                requestBluetoothPermissions();
            } else {
                setupBluetooth();
            }
        }

        receivedDataTextView = findViewById(R.id.received_data);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        listView = findViewById(R.id.device_list);
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        Log.d("Bluetooth", "Paired Devices Found: " + pairedDevices.size());

        for (BluetoothDevice device : pairedDevices) {
            Log.d("Bluetooth", "Device: " + device.getName() + " - " + device.getAddress());
            pairedDevicesList.add(device);
            deviceNamesList.add(device.getName() + "\n" + device.getAddress());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listView.setAdapter(adapter);
        pairedDevicesList.clear();

        for (BluetoothDevice device : pairedDevices) {
            String deviceInfo = device.getName() + " (" + device.getAddress() + ")";
            adapter.add(deviceInfo);
            pairedDevicesList.add(device);  // Ensure this matches ListView items
        }
        adapter.notifyDataSetChanged();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice selectedDevice = pairedDevicesList.get(position);
            if (selectedDevice != null) {
                String deviceInfo = "Connected to: " + selectedDevice.getName();
                receivedDataTextView.setText(deviceInfo);  // Update UI to show connection

                dataBuffer.setLength(0); // Clear previous data
                receivedDataTextView.append("\nWaiting for data...");

                saveLastDevice(selectedDevice.getAddress());
                startBluetoothService(selectedDevice.getAddress());
            }
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

    @Override
    protected void onResume() {
        super.onResume();
        updateDeviceList();
    }

    private void updateDeviceList() {
        pairedDevicesList.clear();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,cd
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        pairedDevicesList.addAll(pairedDevices);

        ArrayList<String> deviceNamesList = new ArrayList<>();
        for (BluetoothDevice device : pairedDevicesList) {
            deviceNamesList.add(device.getName() + "\n" + device.getAddress());
        }

        // Update adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNamesList);
        listView.setAdapter(adapter);
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Below Android 12, permissions are not required at runtime
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    BLUETOOTH_PERMISSION_REQUEST);
        }
        setupBluetooth();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupBluetooth(); // Continue if permission is granted
            } else {
                Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
        }
    }

    private final BroadcastReceiver bluetoothDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String newData = intent.getStringExtra("DATA");

            if (newData != null) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime >= updateDelayMs) {
                    dataBuffer.append(newData).append("\n");

                    // Log data for debugging
                    Log.d("BluetoothDataReceiver", "Received Data: " + newData);

                    // Ensure UI update runs on main thread
                    runOnUiThread(() -> receivedDataTextView.setText(dataBuffer.toString()));

                    lastUpdateTime = currentTime;
                }
            } else {
                Log.e("BluetoothDataReceiver", "Received null data!");
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