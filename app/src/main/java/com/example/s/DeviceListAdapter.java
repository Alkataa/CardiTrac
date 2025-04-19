package com.example.s;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {

    private List<BluetoothDevice> devices;
    private OnItemClickListener listener;

    public DeviceListAdapter(List<BluetoothDevice> devices, OnItemClickListener listener) {
        this.devices = devices;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_list_item, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);
        holder.bind(device, listener);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView textView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1); // The TextView in simple_list_item_1
        }

        @SuppressLint("MissingPermission")
        public void bind(final BluetoothDevice device, final OnItemClickListener listener) {
            textView.setText((device.getName() != null ? device.getName() : "Unknown Device") + "\n" + device.getAddress());
            itemView.setOnClickListener(v -> listener.onItemClick(device));
        }
    }

    public interface OnItemClickListener {
        void onItemClick(BluetoothDevice device);
    }
}