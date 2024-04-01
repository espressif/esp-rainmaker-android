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
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.rainmaker.R;
import com.espressif.ui.EventSelectionListener;
import com.espressif.ui.Utils;
import com.espressif.ui.adapters.EventDeviceAdapter;
import com.espressif.ui.models.Automation;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Iterator;

public class EventDeviceActivity extends AppCompatActivity implements EventSelectionListener {

    private Automation automation;
    private ArrayList<Device> devices;
    private EventDeviceAdapter adapter;
    private String operation;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_devices);

        automation = getIntent().getParcelableExtra(AppConstants.KEY_AUTOMATION);
        operation = getIntent().getStringExtra(AppConstants.KEY_OPERATION);
        devices = new ArrayList<>();

        ArrayList<Device> receivedDevices = getIntent().getParcelableArrayListExtra(AppConstants.KEY_DEVICES);

        if (receivedDevices != null) {
            Iterator<Device> iterator = receivedDevices.iterator();

            while (iterator.hasNext()) {
                Device d = iterator.next();
                if (d != null) {
                    devices.add(new Device(d));
                }
            }
        }
        initViews();
    }

    private void initViews() {

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(R.string.title_activity_select_event);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                finish();
            }
        });

        RecyclerView recyclerView = findViewById(R.id.rv_device_list);
        ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        adapter = new EventDeviceAdapter(this, automation, devices, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onEventSelected(Device device, Param param, String condition) {

        if (!TextUtils.isEmpty(operation) && operation.equals(AppConstants.KEY_OPERATION_EDIT)) {
            Intent intent = getIntent();
            intent.putExtra(AppConstants.KEY_AUTOMATION, automation);
            intent.putExtra(AppConstants.KEY_ESP_DEVICE, device);
            setResult(RESULT_OK, intent);
            finish();
        } else {
            gotoActionsScreen();
        }
    }

    private void gotoActionsScreen() {
        Intent intent = new Intent(this, AutomationActionsActivity.class);
        EspApplication espApp = (EspApplication) getApplicationContext();
        ArrayList<Device> devices = espApp.getEventDevices();
        Iterator deviceIterator = devices.iterator();
        while (deviceIterator.hasNext()) {

            Device device = (Device) deviceIterator.next();
            ArrayList<Param> params = Utils.getWritableParams(device.getParams());

            if (params.size() <= 0) {
                deviceIterator.remove();
            }
        }
        intent.putExtra(AppConstants.KEY_AUTOMATION, automation);
        intent.putParcelableArrayListExtra(AppConstants.KEY_DEVICES, devices);
        startActivity(intent);
    }
}
