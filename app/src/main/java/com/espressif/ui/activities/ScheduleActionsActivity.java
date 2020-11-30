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

package com.espressif.ui.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.ScheduleActionAdapter;
import com.espressif.ui.models.Device;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

public class ScheduleActionsActivity extends AppCompatActivity {

    private ArrayList<Device> devices;
    private ScheduleActionAdapter adapter;

    private TextView tvTitle, tvBack, tvDone;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actions);

        Window window = ScheduleActionsActivity.this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(ContextCompat.getColor(ScheduleActionsActivity.this,R.color.color_actionbar_bg));
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (currentNightMode){
            case Configuration.UI_MODE_NIGHT_NO:
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                break;
            case Configuration.UI_MODE_NIGHT_YES:
                window.getDecorView().setSystemUiVisibility(window.getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                break;
        }

        ArrayList<Device> receivedDevices = getIntent().getParcelableArrayListExtra("devices");
        ArrayList<Device> selectedDevice = getIntent().getParcelableArrayListExtra("selected_devices");

        devices = new ArrayList<>();

        if (receivedDevices != null) {
            Iterator<Device> iterator = receivedDevices.iterator();

            while (iterator.hasNext()) {
                devices.add(new Device(iterator.next()));
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

        // Sort device list to display selected devices first in list.
        Collections.sort(devices, new Comparator<Device>() {

            @Override
            public int compare(Device d1, Device d2) {
                return Integer.valueOf(d2.getSelectedState()).compareTo(Integer.valueOf(d1.getSelectedState()));
            }
        });

        initViews();
    }

    private void initViews() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvDone = findViewById(R.id.btn_cancel);

        tvTitle.setText(R.string.title_activity_actions);
        tvDone.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_fluent_checkmark,0,0,0);
        tvBack.setVisibility(View.VISIBLE);
        tvDone.setVisibility(View.VISIBLE);
        tvBack.setOnClickListener(backBtnClickListener);
        tvDone.setOnClickListener(doneBtnClickListener);

        RecyclerView recyclerView = findViewById(R.id.rv_device_schedule);
        ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        adapter = new ScheduleActionAdapter(this, devices);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
    }

    private View.OnClickListener backBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            finish();
        }
    };

    private View.OnClickListener doneBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Iterator itr = devices.iterator();
            while (itr.hasNext()) {

                Device d = (Device) itr.next();
                if (d.getSelectedState() == 0) {
                    itr.remove();
                }
            }
            Intent intent = getIntent();
            intent.putParcelableArrayListExtra("actions", devices);
            setResult(RESULT_OK, intent);
            finish();
        }
    };
}
