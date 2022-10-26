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
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.ui.Utils;
import com.espressif.ui.activities.AutomationDetailActivity;
import com.espressif.ui.fragments.AutomationFragment;
import com.espressif.ui.models.Action;
import com.espressif.ui.models.Automation;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;
import com.google.gson.JsonObject;

import java.util.ArrayList;

public class AutomationAdapter extends RecyclerView.Adapter<AutomationAdapter.AutomationViewHolder> {

    private final String TAG = AutomationAdapter.class.getSimpleName();

    private Activity context;
    private AutomationFragment fragment;
    private ArrayList<Automation> automationList;
    private ApiManager apiManager;
    private EspApplication espApp;

    public AutomationAdapter(Activity context, AutomationFragment fragment, ArrayList<Automation> automationList) {
        this.context = context;
        this.automationList = automationList;
        apiManager = ApiManager.getInstance(context);
        espApp = (EspApplication) context.getApplicationContext();
        this.fragment = fragment;
    }

    @Override
    public AutomationViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // inflate the item Layout
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_automation, parent, false);
        // set the view's size, margins, paddings and layout parameters
        AutomationViewHolder vh = new AutomationViewHolder(v); // pass the view to View Holder
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull final AutomationViewHolder automationViewHolder, final int position) {

        Automation automation = automationList.get(position);
        automationViewHolder.tvAutomationName.setText(automation.getName());
        automationViewHolder.switchAutomation.setOnCheckedChangeListener(null);
        automationViewHolder.switchAutomation.setChecked(automation.isEnabled());
        automationViewHolder.progressBar.setVisibility(View.GONE);
        automationViewHolder.switchAutomation.setVisibility(View.VISIBLE);

        StringBuilder eventString = new StringBuilder();
        eventString.append("If: ");

        if (automation.getEventDevice() != null) {
            Device device = automation.getEventDevice();
            eventString.append(device.getUserVisibleName());
            eventString.append(": ");
            eventString.append(Utils.getEventParamString(device.getParams(), automation.getCondition()));
        }
        automationViewHolder.tvEventDevice.setText(eventString.toString());

        StringBuilder actionString = new StringBuilder();
        actionString.append("Set: ");
        ArrayList<Action> actions = automation.getActions();

        if (automation.getActions() != null) {

            for (int actionIndex = 0; actionIndex < actions.size(); actionIndex++) {
                Action action = actions.get(actionIndex);
                Device device = action.getDevice();
                actionString.append(device.getUserVisibleName());
                actionString.append(": ");
                ArrayList<Param> params = device.getParams();
                actionString.append(Utils.getActionParamsString(params));

                if (actionIndex != (actions.size() - 1)) {
                    actionString.append("; ");
                }
            }
        }

        automationViewHolder.tvEventDevice.setText(eventString);
        automationViewHolder.tvActionDevices.setText(actionString);

        automationViewHolder.itemView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Automation automation = automationList.get(automationViewHolder.getAdapterPosition());
                Intent intent = new Intent(context, AutomationDetailActivity.class);
                intent.putExtra(AppConstants.KEY_AUTOMATION, automation);
                context.startActivity(intent);
            }
        });

        automationViewHolder.switchAutomation.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                automationViewHolder.switchAutomation.setVisibility(View.INVISIBLE);
                automationViewHolder.progressBar.setVisibility(View.VISIBLE);

                Automation automation1 = automationList.get(automationViewHolder.getAdapterPosition());
                enableAutomation(automationViewHolder, automation1, isChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return automationList.size();
    }

    public void updateList(ArrayList<Automation> updatedAutomationList) {
        ArrayList<Automation> newParamList = new ArrayList<>(updatedAutomationList);
        final AutomationDiffCallback diffCallback = new AutomationDiffCallback(this.automationList, newParamList);
        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        this.automationList.clear();
        this.automationList.addAll(newParamList);
        diffResult.dispatchUpdatesTo(this);
    }

    private void enableAutomation(AutomationViewHolder automationViewHolder, Automation automation, boolean enable) {

        JsonObject automationJson = new JsonObject();
        automationJson.addProperty(AppConstants.KEY_ENABLED, enable);

        apiManager.updateAutomation(automation, automationJson, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                if (enable) {
                    Toast.makeText(context, R.string.success_automation_enable, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, R.string.success_automation_disable, Toast.LENGTH_LONG).show();
                }
                updateAutomationEnableView(automationViewHolder);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                if (exception instanceof CloudException) {
                    Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, R.string.error_automation_update, Toast.LENGTH_SHORT).show();
                }
                updateAutomationEnableView(automationViewHolder);
                fragment.updateAutomationList();
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                if (exception instanceof CloudException) {
                    Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, R.string.error_automation_update, Toast.LENGTH_SHORT).show();
                }
                updateAutomationEnableView(automationViewHolder);
                fragment.updateAutomationList();
            }
        });
    }

    private void updateAutomationEnableView(AutomationViewHolder automationViewHolder) {

        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                automationViewHolder.switchAutomation.setVisibility(View.VISIBLE);
                automationViewHolder.progressBar.setVisibility(View.INVISIBLE);
            }
        });
    }

    static class AutomationViewHolder extends RecyclerView.ViewHolder {

        TextView tvAutomationName, tvEventDevice, tvActionDevices;
        ContentLoadingProgressBar progressBar;
        SwitchCompat switchAutomation;

        public AutomationViewHolder(View itemView) {
            super(itemView);

            tvAutomationName = itemView.findViewById(R.id.tv_automation_name);
            tvEventDevice = itemView.findViewById(R.id.tv_event);
            tvActionDevices = itemView.findViewById(R.id.tv_action_devices);
            progressBar = itemView.findViewById(R.id.auto_progress_indicator);
            switchAutomation = itemView.findViewById(R.id.automation_enable_switch);
        }
    }
}
