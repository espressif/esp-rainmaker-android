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

package com.espressif.ui.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.rainmaker.databinding.ActivityActionsBinding;
import com.espressif.ui.Utils;
import com.espressif.ui.adapters.AutomationActionAdapter;
import com.espressif.ui.models.Action;
import com.espressif.ui.models.Automation;
import com.espressif.ui.models.Device;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

public class AutomationActionsActivity extends AppCompatActivity {

    private Automation automation;
    private ArrayList<Device> devices;
    private AutomationActionAdapter adapter;
    private EspApplication espApp;
    private String operation;

    private ActivityActionsBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityActionsBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        devices = new ArrayList<>();
        espApp = (EspApplication) getApplicationContext();

        automation = getIntent().getParcelableExtra(AppConstants.KEY_AUTOMATION);
        operation = getIntent().getStringExtra(AppConstants.KEY_OPERATION);
        ArrayList<Device> receivedDevices = getIntent().getParcelableArrayListExtra(AppConstants.KEY_DEVICES);
        ArrayList<Device> selectedDevice = getIntent().getParcelableArrayListExtra(AppConstants.KEY_SELECTED_DEVICES);

        if (receivedDevices != null) {
            Iterator<Device> iterator = receivedDevices.iterator();

            while (iterator.hasNext()) {
                Device d = iterator.next();
                if (d != null) {
                    String nodeId = d.getNodeId();
                    if (espApp.nodeMap.get(nodeId).isOnline()) {
                        devices.add(0, new Device(d));
                    } else {
                        devices.add(new Device(d));
                    }
                }
            }
        }

        if (selectedDevice != null && selectedDevice.size() > 0) {

            for (int i = 0; i < devices.size(); i++) {

                Device d1 = devices.get(i);
                for (int j = 0; j < selectedDevice.size(); j++) {

                    Device d2 = selectedDevice.get(j);

                    if (d1.getNodeId().equals(d2.getNodeId()) && d1.getDeviceName().equals(d2.getDeviceName())) {
                        devices.set(i, d2);
                    }
                }
            }
        }

        // Sort device list
        Collections.sort(devices, new DeviceSelectionComparator());
        initViews();
    }

    static class DeviceSelectionComparator implements Comparator<Device> {

        @Override
        public int compare(Device d1, Device d2) {
            return Integer.valueOf(d2.getSelectedState()).compareTo(Integer.valueOf(d1.getSelectedState()));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (!TextUtils.isEmpty(operation) && operation.equals(AppConstants.KEY_OPERATION_EDIT)) {
            menu.add(Menu.NONE, 1, Menu.NONE, R.string.btn_done).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
            menu.add(Menu.NONE, 1, Menu.NONE, R.string.btn_save).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case 1:
                ArrayList<Action> actions = new ArrayList<>();
                for (Device device : devices) {
                    if (device.getSelectedState() != AppConstants.ACTION_SELECTED_NONE) {
                        Action action = new Action();
                        action.setDevice(device);
                        action.setNodeId(device.getNodeId());
                        actions.add(action);
                    }
                }

                if (!TextUtils.isEmpty(operation) && operation.equals(AppConstants.KEY_OPERATION_EDIT)) {

                    if (actions.size() > 0) {
                        Intent intent = getIntent();
                        automation.setActions(actions);
                        intent.putExtra(AppConstants.KEY_AUTOMATION, automation);
                        setResult(RESULT_OK, intent);
                        finish();
                    } else {
                        Toast.makeText(AutomationActionsActivity.this, "Select one action device", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    if (actions.size() > 0) {
                        automation.setActions(actions);
                        saveAutomation();
                    } else {
                        Toast.makeText(AutomationActionsActivity.this, "Select one action device", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initViews() {

        setSupportActionBar(binding.toolbarLayout.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(R.string.title_activity_actions);
        binding.toolbarLayout.toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        binding.toolbarLayout.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ((SimpleItemAnimator) binding.layoutActionDevices.rvDevice.getItemAnimator()).setSupportsChangeAnimations(false);

        StringBuilder eventString = new StringBuilder();
        eventString.append(getString(R.string.event));
        eventString.append(": ");
        eventString.append(automation.getEventDevice().getUserVisibleName());
        eventString.append(": ");
        eventString.append(Utils.getEventParamString(automation.getEventDevice().getParams(), automation.getCondition()));
        binding.layoutActionDevices.tvEventText.setText(eventString.toString());
        adapter = new AutomationActionAdapter(this, devices);
        binding.layoutActionDevices.rvDevice.setLayoutManager(new LinearLayoutManager(this));
        binding.layoutActionDevices.rvDevice.setAdapter(adapter);
        binding.layoutActionDevices.rvDevice.setHasFixedSize(true);
    }

    private void saveAutomation() {

        showLoading(getString(R.string.progress_add_automation));

        JsonObject automationJson = new JsonObject();
        JsonArray eventArr = new JsonArray();
        JsonArray actionArr = new JsonArray();

        automationJson.addProperty(AppConstants.KEY_NAME, automation.getName());
        automationJson.addProperty(AppConstants.KEY_ENABLED, true);
        Device eventDevice = automation.getEventDevice();

        // Event
        if (eventDevice != null) {
            automationJson.addProperty(AppConstants.KEY_NODE_ID, eventDevice.getNodeId());

            JsonObject paramJson = Utils.getParamJson(eventDevice);
            JsonObject deviceJson = new JsonObject();
            deviceJson.add(eventDevice.getDeviceName(), paramJson);

            JsonObject eventJson = new JsonObject();
            eventJson.add(AppConstants.KEY_PARAMS, deviceJson);
            eventJson.addProperty(AppConstants.KEY_CHECK, automation.getCondition());

            eventArr.add(eventJson);
            automationJson.add(AppConstants.KEY_EVENTS, eventArr);
        }

        // Actions
        ArrayList<Action> actions = automation.getActions();

        for (int i = 0; i < actions.size(); i++) {

            Action action = actions.get(i);
            Device device = action.getDevice();

            JsonObject paramJson = Utils.getParamJson(device);
            JsonObject deviceJson = new JsonObject();
            deviceJson.add(device.getDeviceName(), paramJson);

            JsonObject actionJson = new JsonObject();
            actionJson.add(AppConstants.KEY_PARAMS, deviceJson);
            actionJson.addProperty(AppConstants.KEY_NODE_ID, device.getNodeId());
            actionArr.add(actionJson);
        }
        automationJson.add(AppConstants.KEY_ACTIONS, actionArr);

        ApiManager apiManager = ApiManager.getInstance(espApp);
        apiManager.addAutomations(automationJson, new ApiResponseListener() {
            @Override
            public void onSuccess(Bundle data) {
                Toast.makeText(AutomationActionsActivity.this, R.string.success_automation_create, Toast.LENGTH_LONG).show();
                hideLoading();
                Intent intent = new Intent(AutomationActionsActivity.this, EspMainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                intent.putExtra(AppConstants.KEY_LOAD_AUTOMATION_PAGE, true);
                startActivity(intent);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                hideLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(AutomationActionsActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AutomationActionsActivity.this, R.string.error_automation_create, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                hideLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(AutomationActionsActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AutomationActionsActivity.this, R.string.error_automation_create, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showLoading(String msg) {
        binding.layoutActionDevices.layoutActions.setAlpha(0.3f);
        binding.layoutProgress.setVisibility(View.VISIBLE);
        binding.tvLoading.setText(msg);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private void hideLoading() {
        binding.layoutActionDevices.layoutActions.setAlpha(1);
        binding.layoutProgress.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }
}
