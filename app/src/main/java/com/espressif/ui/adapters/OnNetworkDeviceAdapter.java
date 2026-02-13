// Copyright 2026 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.espressif.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.rainmaker.R;
import com.espressif.ui.models.OnNetworkDevice;

import java.util.ArrayList;

public class OnNetworkDeviceAdapter extends RecyclerView.Adapter<OnNetworkDeviceAdapter.DeviceViewHolder> {

    private Context context;
    private ArrayList<OnNetworkDevice> deviceList;
    private OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(OnNetworkDevice device);
    }

    public OnNetworkDeviceAdapter(Context context, ArrayList<OnNetworkDevice> deviceList, OnDeviceClickListener listener) {
        this.context = context;
        this.deviceList = deviceList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_on_network_device, parent, false);
        return new DeviceViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final DeviceViewHolder holder, int position) {
        OnNetworkDevice device = deviceList.get(position);
        // Display service name as device name
        holder.tvDeviceName.setText(device.getServiceName());
        
        // Display node ID in device info
        String info = device.getNodeId();
        if (device.isPopRequired()) {
            info += " • POP Required";
        }
        holder.tvDeviceInfo.setText(info);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onDeviceClick(deviceList.get(holder.getAdapterPosition()));
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return deviceList == null ? 0 : deviceList.size();
    }

    public void updateList(ArrayList<OnNetworkDevice> updatedDeviceList) {
        deviceList = updatedDeviceList;
        notifyDataSetChanged();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {

        TextView tvDeviceName;
        TextView tvDeviceInfo;
        ImageView ivArrow;

        public DeviceViewHolder(View itemView) {
            super(itemView);
            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            tvDeviceInfo = itemView.findViewById(R.id.tv_device_info);
            ivArrow = itemView.findViewById(R.id.iv_arrow);
        }
    }
}
