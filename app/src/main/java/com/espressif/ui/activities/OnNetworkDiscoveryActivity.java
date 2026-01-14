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

package com.espressif.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.local_control.ChallengeRespServiceDiscovery;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.OnNetworkDeviceAdapter;
import com.espressif.ui.models.OnNetworkDevice;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;

public class OnNetworkDiscoveryActivity extends AppCompatActivity {

    private static final String TAG = OnNetworkDiscoveryActivity.class.getSimpleName();
    private static final long DISCOVERY_TIMEOUT = 10000; // 10 seconds

    private RecyclerView rvDevices;
    private RelativeLayout rlProgress;
    private RelativeLayout rlEmpty;
    private ContentLoadingProgressBar progressBar;
    private TextView tvLoading;
    private TextView tvEmpty;

    private OnNetworkDeviceAdapter adapter;
    private ArrayList<OnNetworkDevice> deviceList;
    private ChallengeRespServiceDiscovery discovery;
    private Handler handler;
    private Runnable discoveryTimeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_on_network_discovery);

        initViews();
        deviceList = new ArrayList<>();
        handler = new Handler();

        adapter = new OnNetworkDeviceAdapter(this, deviceList, new OnNetworkDeviceAdapter.OnDeviceClickListener() {
            @Override
            public void onDeviceClick(OnNetworkDevice device) {
                handleDeviceSelection(device);
            }
        });

        rvDevices.setLayoutManager(new LinearLayoutManager(this));
        rvDevices.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rvDevices.setAdapter(adapter);

        startDiscovery();
    }

    @Override
    protected void onDestroy() {
        stopDiscovery();
        if (handler != null && discoveryTimeoutRunnable != null) {
            handler.removeCallbacks(discoveryTimeoutRunnable);
        }
        super.onDestroy();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setTitle(R.string.title_on_network_discovery);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        rvDevices = findViewById(R.id.rv_devices);
        rlProgress = findViewById(R.id.rl_progress);
        rlEmpty = findViewById(R.id.rl_empty);
        progressBar = findViewById(R.id.progress_discovery);
        tvLoading = findViewById(R.id.tv_loading);
        tvEmpty = findViewById(R.id.tv_empty);
    }

    private void startDiscovery() {
        Log.d(TAG, "Starting mDNS discovery for challenge-response service");
        rlProgress.setVisibility(View.VISIBLE);
        rlEmpty.setVisibility(View.GONE);
        deviceList.clear();
        adapter.notifyDataSetChanged();

        ChallengeRespServiceDiscovery.ChallengeRespDiscoveryListener listener = new ChallengeRespServiceDiscovery.ChallengeRespDiscoveryListener() {
            @Override
            public void deviceFound(OnNetworkDevice device) {
                Log.d(TAG, "Device found: " + device.getNodeId());

                // Check if device already exists
                boolean exists = false;
                for (OnNetworkDevice d : deviceList) {
                    if (d.getNodeId().equals(device.getNodeId())) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    deviceList.add(device);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.notifyDataSetChanged();
                            if (rlProgress.getVisibility() == View.VISIBLE && !deviceList.isEmpty()) {
                                hideProgress();
                            }
                        }
                    });
                }
            }
        };

        discovery = new ChallengeRespServiceDiscovery(this, AppConstants.MDNS_CHALLENGE_RESP_SERVICE_TYPE, listener);
        discovery.initializeNsd();
        discovery.discoverServices();

        // Set timeout for discovery
        discoveryTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Discovery timeout reached");
                stopDiscovery();
                if (deviceList.isEmpty()) {
                    showEmptyState();
                } else {
                    hideProgress();
                }
            }
        };
        handler.postDelayed(discoveryTimeoutRunnable, DISCOVERY_TIMEOUT);
    }

    private void stopDiscovery() {
        if (discovery != null) {
            discovery.stopDiscovery();
        }
        hideProgress();
    }

    private void hideProgress() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                rlProgress.setVisibility(View.GONE);
            }
        });
    }

    private void showEmptyState() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                rlProgress.setVisibility(View.GONE);
                rlEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void handleDeviceSelection(OnNetworkDevice device) {
        Log.d(TAG, "Device selected: " + device.getNodeId());

        if (device.isPopRequired()) {
            // Show ProofOfPossessionActivity
            Intent popIntent = new Intent(this, ProofOfPossessionActivity.class);
            popIntent.putExtra(AppConstants.KEY_NODE_ID, device.getNodeId());
            popIntent.putExtra(AppConstants.KEY_SECURITY_TYPE, device.getSecVersion());
            popIntent.putExtra(AppConstants.KEY_ON_NETWORK_DEVICE, device);
            startActivityForResult(popIntent, AppConstants.REQUEST_CODE_POP);
        } else {
            // Go directly to ProvisionActivity
            goToProvisionActivity(device, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == AppConstants.REQUEST_CODE_POP && resultCode == RESULT_OK) {
            OnNetworkDevice device = (OnNetworkDevice) data.getSerializableExtra(AppConstants.KEY_ON_NETWORK_DEVICE);
            String pop = data.getStringExtra(AppConstants.KEY_POP);
            goToProvisionActivity(device, pop);
        }
    }

    private void goToProvisionActivity(OnNetworkDevice device, String pop) {
        Intent provisionIntent = new Intent(this, ProvisionActivity.class);
        provisionIntent.putExtra(AppConstants.KEY_ON_NETWORK_DEVICE, device);
        provisionIntent.putExtra(AppConstants.KEY_SECURITY_TYPE, device.getSecVersion());
        provisionIntent.putExtra(AppConstants.KEY_POP, pop);
        provisionIntent.putExtra(AppConstants.KEY_IS_ON_NETWORK_FLOW, true);
        startActivity(provisionIntent);
        finish();
    }

}
