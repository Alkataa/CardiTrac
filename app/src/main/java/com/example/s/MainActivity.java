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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends ComponentActivity {

    private static final int BLUETOOTH_PERMISSION_REQUEST = 1;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 2;

    private BluetoothAdapter bluetoothAdapter;
    private ListView listView;
    private ArrayAdapter<String> adapter;

    private ArrayList<BluetoothDevice> pairedDevicesList = new ArrayList<>();

    private TextView receivedDataTextView;

    private ArrayList<String> deviceNamesList = new ArrayList<>();
    private List<String[]> healthDataList = new ArrayList<>();

    private static final String HEALTH_DATA_FILE = "health_data.json";

    private ActivityResultLauncher<String[]> bluetoothPermissionRequestLauncher;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (!hasNotificationPermission()) {
                requestNotificationPermission();
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  // Android 12+
            bluetoothPermissionRequestLauncher = registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        if (Boolean.TRUE.equals(permissions.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false)) &&
                                Boolean.TRUE.equals(permissions.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false))) {
                            setupBluetooth();  // Permissions granted, set up Bluetooth
                        } else {
                            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show();
                            // Consider disabling Bluetooth functionality or providing further guidance to the user.
                        }
                    }
            );

            if (!hasBluetoothPermissions()) {
                requestBluetoothPermissions();
            } else {
                setupBluetooth();
            }
        }

        listView = findViewById(R.id.device_list_view); // Ensure the ID matches the layout
        if (listView == null) {
            Log.e("MainActivity", "ListView is null. Check the layout file.");
            return;
        }
        receivedDataTextView = findViewById(R.id.received_data);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNamesList); // Initialize adapter with the list and listener
        listView.setAdapter(adapter); // Set the adapter to the ListView

        listView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = pairedDevicesList.get(position);

            if (device != null)
                onItemClick(device); // Call the click listener
        });

        findViewById(R.id.graph_button).setOnClickListener(v -> {
            if (!healthDataList.isEmpty()) {
                Intent intent = new Intent(MainActivity.this, GraphActivity.class);
                intent.putExtra("HEALTH_DATA", new ArrayList<>(healthDataList)); // Pass the health data
                startActivity(intent);
            } else {
                Toast.makeText(this, "No health data available to display", Toast.LENGTH_SHORT).show();
            }
        });

        IntentFilter filter = new IntentFilter("com.example.s.BLUETOOTH_DATA");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) // Android 13+
            registerReceiver(bluetoothDataReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(bluetoothDataReceiver, filter);

        // Load health data list from file
        healthDataList = loadHealthDataList();
    }

    @SuppressLint("MissingPermission") // Suppress the lint warning as we check the permission in setupBluetooth
    public void onItemClick(BluetoothDevice device) {  // Listener implementation for clicks
        if (device != null) {
            String deviceInfo = "Connecting to: " + device.getName();
            receivedDataTextView.setText(deviceInfo);

            saveLastDevice(device.getAddress());
            startBluetoothService(device.getAddress());
        }
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

        String lastHealthData = loadHealthData();
        if (receivedDataTextView != null) {
            receivedDataTextView.setText(lastHealthData);
        }

        // Reload health data list from file
        healthDataList = loadHealthDataList();

        // Attempt to reconnect to the last known device
        String lastDeviceAddress = loadLastDevice();
        if (lastDeviceAddress != null && !lastDeviceAddress.isEmpty()) {
            Log.d("MainActivity", "Attempting to reconnect to last device: " + lastDeviceAddress);
            startBluetoothService(lastDeviceAddress);
        }
    }

    protected void updateDeviceList() {
        pairedDevicesList.clear();
        deviceNamesList.clear();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MainActivity", "BLUETOOTH_CONNECT permission missing in updateDeviceList()");
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            pairedDevicesList.add(device);
            deviceNamesList.add(device.getName() + "\n" + device.getAddress());
        }

        adapter.notifyDataSetChanged();
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
            bluetoothPermissionRequestLauncher.launch(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN});
        } else {
            setupBluetooth(); // For devices below Android S, no runtime request needed.
        }
    }


    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Below Android 13, no runtime permission required
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }
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
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permissions required for full functionality", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("MissingPermission")  // Suppress the lint warning as we check the permission in setupBluetooth
    private void setupBluetooth() {
        listView = findViewById(R.id.device_list_view);
        if (listView == null) {
            Log.e("MainActivity", "RecyclerView is null. Check the layout file.");
            return;
        }

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNamesList);
        listView.setAdapter(adapter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return; // Exit if Bluetooth isn't supported.
        }

        if (!hasBluetoothPermissions() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Should not reach here if the flow is correct, as requestBluetoothPermissions should have been called.
            Log.e("MainActivity", "setupBluetooth called without permissions granted on Android 12+. This is unexpected.");
            Toast.makeText(this, "Bluetooth permissions not granted.", Toast.LENGTH_SHORT).show();
            return; // Exit or handle appropriately, e.g., disable Bluetooth features.
        }
        // From this point on, we know we have the necessary permissions (either granted or not needed).
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        Log.d("Bluetooth", "Paired Devices Found: " + pairedDevices.size());

        pairedDevicesList.clear();  // Clear the list before adding
        deviceNamesList.clear();   // Clear this one too

        for (BluetoothDevice device : pairedDevices) {
            Log.d("Bluetooth", "Device: " + device.getName() + " - " + device.getAddress());
            pairedDevicesList.add(device);
            deviceNamesList.add(device.getName() + "\n" + device.getAddress());
        }
        if (pairedDevicesList.isEmpty()) {
            Toast.makeText(this, "No paired devices found.", Toast.LENGTH_SHORT).show();
        }

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNamesList); // Initialize adapter with the list and listener
        listView.setAdapter(adapter);
    }

    private final BroadcastReceiver bluetoothDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String newData = intent.getStringExtra("DATA");
            Log.d("MainActivity", "Received data in main: " + newData);

            if (newData != null) {
                // Parse the health data
                String[] healthData = newData.split(";");
                if (healthData.length == 4) {
                    String frequenzaCardiaca = healthData[0];
                    String saturazione = healthData[1];
                    String temperatura = healthData[2];
                    boolean notifica = healthData[3].equals("1");

                    if (Integer.parseInt(temperatura) <= 30 || Integer.parseInt(frequenzaCardiaca) <= 30 || Integer.parseInt(saturazione) <= 30) {
                        Log.d("MainActivity", "Filtered out data");
                        return;
                    }

                    // Add the parsed data to the list for graphing
                    healthDataList.add(new String[]{frequenzaCardiaca, saturazione, temperatura, String.valueOf(notifica)});

                    // Save the updated health data list to file
                    saveHealthDataList(healthDataList);

                    // Update the UI with the latest entry
                    String latestEntry = "Frequenza Cardiaca: " + frequenzaCardiaca + "\n" +
                            "Saturazione: " + saturazione + "\n" +
                            "Temperatura: " + temperatura + "\n" +
                            "Postura: " + (notifica ? "Incorretta" : "Corretta");
                    receivedDataTextView.setText(latestEntry);

                    // Save the latest entry for persistence
                    saveHealthData(latestEntry);
                } else
                    Log.e("BluetoothDataReceiver", "Invalid data format: " + newData);
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
        boolean success = editor.commit();
        if (success)
            Log.d("MainActivity", "Last device saved successfully.");
        else
            Log.e("MainActivity", "Failed to save last device.");
    }

    private void saveHealthData(String healthData) {
        SharedPreferences prefs = getSharedPreferences("HealthDataPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("LastHealthData", healthData);
        boolean success = editor.commit();
        if (success)
            Log.d("MainActivity", "Health data saved successfully.");
        else
            Log.e("MainActivity", "Failed to save health data.");
    }

    private String loadHealthData() {
        SharedPreferences prefs = getSharedPreferences("HealthDataPrefs", MODE_PRIVATE);
        return prefs.getString("LastHealthData", "No health data available");
    }

    private String loadLastDevice() {
        SharedPreferences prefs = getSharedPreferences("BluetoothPrefs", MODE_PRIVATE);
        return prefs.getString("LastDevice", null);
    }

    // Save the health data list to a file as JSON
    private void saveHealthDataList(List<String[]> dataList) {
        JSONArray jsonArray = new JSONArray();
        for (String[] arr : dataList) {
            JSONArray inner = new JSONArray();
            for (String s : arr) inner.put(s);
            jsonArray.put(inner);
        }
        try (FileOutputStream fos = openFileOutput(HEALTH_DATA_FILE, MODE_PRIVATE)) {
            fos.write(jsonArray.toString().getBytes());
        } catch (IOException e) {
            Log.e("MainActivity", "Failed to save health data list", e);
        }
    }

    // Load the health data list from a file
    private List<String[]> loadHealthDataList() {
        List<String[]> list = new ArrayList<>();
        try (FileInputStream fis = openFileInput(HEALTH_DATA_FILE)) {
            byte[] bytes = new byte[fis.available()];
            fis.read(bytes);
            String json = new String(bytes);
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONArray inner = jsonArray.getJSONArray(i);
                String[] arr = new String[inner.length()];
                for (int j = 0; j < inner.length(); j++) {
                    arr[j] = inner.getString(j);
                }
                list.add(arr);
            }
        } catch (FileNotFoundException e) {
            Log.d("MainActivity", "No previous health data list found.");
        } catch (IOException | org.json.JSONException e) {
            Log.e("MainActivity", "Failed to load health data list", e);
        }
        return list;
    }
}
