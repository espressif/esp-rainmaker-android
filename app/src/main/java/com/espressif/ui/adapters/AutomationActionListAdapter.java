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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.rainmaker.R;
import com.espressif.ui.Utils;
import com.espressif.ui.models.Action;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;

import java.util.ArrayList;

public class AutomationActionListAdapter extends RecyclerView.Adapter<AutomationActionListAdapter.ActionViewHolder> {

    private Activity context;
    private ArrayList<Action> actionList;

    public AutomationActionListAdapter(Activity context, ArrayList<Action> list) {
        this.context = context;
        this.actionList = list;
    }

    @Override
    public ActionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_automation_action, parent, false);
        return new ActionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ActionViewHolder holder, int position) {

        Action action = actionList.get(position);
        Device device = action.getDevice();
        ArrayList<Param> params = device.getParams();
        StringBuilder paramText = new StringBuilder();
        paramText.append(Utils.getActionParamsString(params));
        Utils.setDeviceIcon(holder.ivDevice, device.getDeviceType());
        holder.tvDeviceName.setText(device.getUserVisibleName());
        holder.tvParamNames.setText(paramText.toString());
    }

    @Override
    public int getItemCount() {
        return actionList == null ? 0 : actionList.size();
    }

    public void updateActionList(ArrayList<Action> actionList) {
        ArrayList<Action> newActionList = new ArrayList<>(actionList);
        final ActionDiffCallback diffCallback = new ActionDiffCallback(this.actionList, newActionList);
        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        this.actionList.clear();
        this.actionList.addAll(newActionList);
        diffResult.dispatchUpdatesTo(this);
    }

    static class ActionViewHolder extends RecyclerView.ViewHolder {

        private TextView tvDeviceName, tvParamNames;
        private ImageView ivDevice;

        public ActionViewHolder(View itemView) {
            super(itemView);

            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            tvParamNames = itemView.findViewById(R.id.tv_param_names);
            ivDevice = itemView.findViewById(R.id.iv_device);
        }
    }
}
