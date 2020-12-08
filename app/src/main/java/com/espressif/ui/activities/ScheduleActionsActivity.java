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
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.ScheduleActionAdapter;
import com.espressif.ui.models.Device;
import com.espressif.ui.theme_manager.WindowThemeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

public class ScheduleActionsActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private ArrayList<Device> devices;
    private ScheduleActionAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actions);
        WindowThemeManager WindowTheme = new WindowThemeManager(this, false);
        WindowTheme.applyWindowTheme(getWindow());

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.title_activity_actions);
        toolbar.setNavigationIcon(R.drawable.ic_fluent_arrow_left);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_toolbar, menu);
        MenuItem tvDone = menu.findItem(R.id.action_done);
        tvDone.setVisible(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_done:
                doneBtnVoid();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initViews() {

        RecyclerView recyclerView = findViewById(R.id.rv_device_schedule);
        ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        adapter = new ScheduleActionAdapter(this, devices);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
    }

    private void doneBtnVoid() {

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
}
