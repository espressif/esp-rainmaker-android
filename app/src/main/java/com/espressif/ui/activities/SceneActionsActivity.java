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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.SceneActionAdapter;
import com.espressif.ui.models.Device;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

public class SceneActionsActivity extends AppCompatActivity {

    private ArrayList<Device> devices;
    private SceneActionAdapter adapter;
    private EspApplication espApp;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actions);

        devices = new ArrayList<>();
        espApp = (EspApplication) getApplicationContext();

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
        Collections.sort(devices, new OnlineDeviceComparator()
                .thenComparing(new DeviceSelectionComparator())
                .thenComparing(new MaxCountComparator()));
        initViews();
    }

    class OnlineDeviceComparator implements Comparator<Device> {

        @Override
        public int compare(Device d1, Device d2) {

            String node1 = d1.getNodeId();
            String node2 = d2.getNodeId();

            if ((espApp.nodeMap.get(node1).isOnline() && espApp.nodeMap.get(node2).isOnline())
                    || (!espApp.nodeMap.get(node1).isOnline() && !espApp.nodeMap.get(node2).isOnline())) {
                return 0;
            } else {
                if (espApp.nodeMap.get(node2).isOnline()) {
                    return 1;
                } else {
                    return -1;
                }
            }
        }
    }

    static class DeviceSelectionComparator implements Comparator<Device> {

        @Override
        public int compare(Device d1, Device d2) {
            return Integer.valueOf(d2.getSelectedState()).compareTo(Integer.valueOf(d1.getSelectedState()));
        }
    }

    class MaxCountComparator implements Comparator<Device> {

        @Override
        public int compare(Device d1, Device d2) {

            String node1 = d1.getNodeId();
            String node2 = d2.getNodeId();

            if ((espApp.nodeMap.get(node1).getSceneCurrentCnt() < espApp.nodeMap.get(node1).getSceneMaxCnt())
                    && (espApp.nodeMap.get(node2).getSceneCurrentCnt() < espApp.nodeMap.get(node2).getSceneMaxCnt())) {
                return 0;
            } else if ((espApp.nodeMap.get(node1).getSceneCurrentCnt() >= espApp.nodeMap.get(node1).getSceneMaxCnt())
                    && (espApp.nodeMap.get(node2).getSceneCurrentCnt() >= espApp.nodeMap.get(node2).getSceneMaxCnt())) {
                return 0;
            } else {
                if (espApp.nodeMap.get(node1).getSceneCurrentCnt() >= espApp.nodeMap.get(node1).getSceneMaxCnt()) {
                    return 1;
                } else {
                    return -1;
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, 1, Menu.NONE, R.string.btn_done).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case 1:
                Iterator itr = devices.iterator();
                while (itr.hasNext()) {

                    Device d = (Device) itr.next();
                    if (d.getSelectedState() == AppConstants.ACTION_SELECTED_NONE) {
                        itr.remove();
                    }
                }
                Intent intent = getIntent();
                intent.putParcelableArrayListExtra(AppConstants.KEY_ACTIONS, devices);
                setResult(RESULT_OK, intent);
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initViews() {

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(R.string.title_activity_actions);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                finish();
            }
        });

        RecyclerView recyclerView = findViewById(R.id.rv_device);
        ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        adapter = new SceneActionAdapter(this, devices);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
    }
}
