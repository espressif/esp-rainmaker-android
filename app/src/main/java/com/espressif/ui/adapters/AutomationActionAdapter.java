// Copyright 2022 Espressif Systems (Shanghai) PTE LTD
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

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.rainmaker.R;
import com.espressif.ui.Utils;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;

import java.util.ArrayList;
import java.util.HashMap;

public class AutomationActionAdapter extends RecyclerView.Adapter<AutomationActionAdapter.ActionViewHolder> {

    private Activity context;
    private ArrayList<Device> deviceList;
    private AutomationParamAdapter paramAdapter;
    private HashMap<Integer, Integer> deviceSelectMap;

    public AutomationActionAdapter(Activity context, ArrayList<Device> list) {
        this.context = context;
        this.deviceList = list;
        deviceSelectMap = new HashMap<>();

        for (int devicePosition = 0; devicePosition < list.size(); devicePosition++) {
            int state = list.get(devicePosition).getSelectedState();
            if (state != AppConstants.ACTION_SELECTED_NONE) {
                deviceSelectMap.put(devicePosition, state);
            }
        }
    }

    @Override
    public ActionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_action, parent, false);
        return new ActionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ActionViewHolder holder, int position) {

        Device device = deviceList.get(position);
        holder.bind(device);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
        linearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        holder.paramRecyclerView.setLayoutManager(linearLayoutManager);

        ArrayList<Param> params = Utils.getWritableParams(device.getParams());
        paramAdapter = new AutomationParamAdapter(context, this, device, params);
        holder.paramRecyclerView.setAdapter(paramAdapter);

        switch (device.getSelectedState()) {
            case AppConstants.ACTION_SELECTED_NONE:
                holder.ivDeviceSelect.setChecked(false);
                holder.ivDeviceSelect.setButtonDrawable(R.drawable.ic_checkbox_unchecked);
                break;
            case AppConstants.ACTION_SELECTED_ALL:
                holder.ivDeviceSelect.setChecked(true);
                holder.ivDeviceSelect.setButtonDrawable(R.drawable.ic_checkbox_checked);
                break;
            case AppConstants.ACTION_SELECTED_PARTIAL:
                holder.ivDeviceSelect.setButtonDrawable(R.drawable.ic_checkbox_indeterminate);
                break;
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Device d = deviceList.get(holder.getAdapterPosition());
                boolean expanded = d.isExpanded();
                d.setExpanded(!expanded);
                if (expanded) {
                    holder.ivExpandArrow.animate().rotation(0).setInterpolator(new LinearInterpolator()).setDuration(200);
                } else {
                    holder.ivExpandArrow.animate().rotation(90).setInterpolator(new LinearInterpolator()).setDuration(200);
                }
                notifyItemChanged(holder.getAdapterPosition());
            }
        });

        holder.ivDeviceSelect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                // Ignore callback if checkbox value is changed programmatically not by user click.
                if (!buttonView.isPressed()) {
                    return;
                }

                Device d = deviceList.get(holder.getAdapterPosition());
                int selectedState = AppConstants.ACTION_SELECTED_NONE;
                if (isChecked) {
                    selectedState = AppConstants.ACTION_SELECTED_ALL;
                    d.setExpanded(true);
                    holder.ivExpandArrow.animate().rotation(90).setInterpolator(new LinearInterpolator()).setDuration(200);
                }
                d.setSelectedState(selectedState);

                switch (selectedState) {
                    case AppConstants.ACTION_SELECTED_NONE:
                        holder.ivDeviceSelect.setChecked(false);
                        holder.ivDeviceSelect.setButtonDrawable(R.drawable.ic_checkbox_unchecked);
                        break;
                    case AppConstants.ACTION_SELECTED_ALL:
                        holder.ivDeviceSelect.setChecked(true);
                        holder.ivDeviceSelect.setButtonDrawable(R.drawable.ic_checkbox_checked);
                        break;
                }
                ArrayList<Param> deviceParams = d.getParams();
                for (int i = 0; i < deviceParams.size(); i++) {
                    deviceParams.get(i).setSelected(isChecked);
                }
                notifyItemChanged(holder.getAdapterPosition());
            }
        });

        holder.itemView.setAlpha(1f);
        holder.ivDeviceSelect.setEnabled(true);
        holder.tvOffline.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return deviceList == null ? 0 : deviceList.size();
    }

    static class ActionViewHolder extends RecyclerView.ViewHolder {

        private TextView tvDeviceName, tvOffline;
        private ImageView ivExpandArrow;
        private AppCompatCheckBox ivDeviceSelect;
        private RecyclerView paramRecyclerView;

        public ActionViewHolder(View itemView) {
            super(itemView);

            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            tvOffline = itemView.findViewById(R.id.tv_offline);
            ivExpandArrow = itemView.findViewById(R.id.iv_expand_arrow);
            ivDeviceSelect = itemView.findViewById(R.id.iv_device_select);
            paramRecyclerView = itemView.findViewById(R.id.rv_param_list);
        }

        private void bind(Device device) {

            boolean expanded = device.isExpanded();
            paramRecyclerView.setVisibility(expanded ? View.VISIBLE : View.GONE);
            tvDeviceName.setText(device.getUserVisibleName());
        }
    }
}
