// Copyright 2020 Espressif Systems (Shanghai) PTE LTD
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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.WiFiAccessPoint;
import com.espressif.rainmaker.R;

import java.util.ArrayList;

public class WiFiListAdapter extends ArrayAdapter<WiFiAccessPoint> {

    private Context context;
    private ArrayList<WiFiAccessPoint> wifiApList;

    public WiFiListAdapter(Context context, ArrayList<WiFiAccessPoint> wifiList) {
        super(context, 0, wifiList);
        this.context = context;
        this.wifiApList = wifiList;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        return initView(position, convertView, parent);
    }

    private View initView(int position, View convertView, ViewGroup parent) {

        WiFiAccessPoint wiFiAccessPoint = wifiApList.get(position);

        // It is used to set our custom view.
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_wifi_access_point, parent, false);
        }

        TextView wifiNameText = convertView.findViewById(R.id.tv_wifi_name);
        ImageView rssiImage = convertView.findViewById(R.id.iv_wifi_rssi);
        ImageView lockImage = convertView.findViewById(R.id.iv_wifi_security);

        // It is used the name to the TextView when the
        // current item is not null.
        if (wiFiAccessPoint != null) {

            String wifiName = wiFiAccessPoint.getWifiName();
            wifiNameText.setText(wifiName);
            rssiImage.setImageLevel(getRssiLevel(wiFiAccessPoint.getRssi()));

            if (!TextUtils.isEmpty(wifiName)
                    && wifiName.equals(context.getString(R.string.select_network))) {
                rssiImage.setVisibility(View.GONE);
                lockImage.setVisibility(View.VISIBLE);
                lockImage.setImageResource(R.drawable.ic_down_arrow);
            } else {
                rssiImage.setVisibility(View.VISIBLE);
                lockImage.setImageResource(R.drawable.ic_lock);

                if (wiFiAccessPoint.getSecurity() == ESPConstants.WIFI_OPEN) {
                    lockImage.setVisibility(View.INVISIBLE);
                } else {
                    lockImage.setVisibility(View.VISIBLE);
                }
            }
        }
        return convertView;
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return initView(position, convertView, parent);
    }

    private int getRssiLevel(int rssiValue) {

        if (rssiValue > -50) {
            return 3;
        } else if (rssiValue >= -60) {
            return 2;
        } else if (rssiValue >= -67) {
            return 1;
        } else {
            return 0;
        }
    }
}
