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

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.widget.ContentLoadingProgressBar;

import com.espressif.AppConstants;
import com.espressif.ble.BleLocalControlManager;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPDevice;
import com.espressif.provisioning.WiFiAccessPoint;
import com.espressif.provisioning.listeners.ProvisionListener;
import com.espressif.provisioning.listeners.WiFiScanListener;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.WiFiListAdapter;
import com.espressif.ui.widgets.EspDropDown;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

/**
 * Re-provisions Wi-Fi credentials on a device that is already connected
 * via BLE local control. Uses the existing ESPDevice from BleLocalControlManager
 * to scan networks and provision — no disconnect/reconnect needed.
 * Skips cloud association since the device is already registered.
 */
public class BleWifiProvisionActivity extends AppCompatActivity {

    private static final String TAG = "BleWifiProvision";

    private String nodeId;
    private ESPDevice espDevice;

    private RelativeLayout layoutWifiSelect;
    private RelativeLayout layoutProvisionProgress;
    private RelativeLayout rlProgress;

    private EspDropDown spinnerNetworks;
    private TextInputLayout layoutPassword;
    private EditText etPassword;
    private View btnProvision;
    private TextView tvAddNetwork;

    private ImageView ivTick1, ivTick2, ivTick3;
    private ContentLoadingProgressBar progress1, progress2, progress3;
    private TextView tvProvError, tvProvSuccess;
    private View btnDone;

    private ArrayList<WiFiAccessPoint> wifiAPList;
    private WiFiListAdapter wiFiListAdapter;
    private WiFiAccessPoint selectedWiFi;
    private String ssid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_wifi_provision);

        nodeId = getIntent().getStringExtra(AppConstants.KEY_NODE_ID);
        if (TextUtils.isEmpty(nodeId)) {
            Log.e(TAG, "Node ID is null");
            finish();
            return;
        }

        BleLocalControlManager bleManager = BleLocalControlManager.getInstance(this);
        espDevice = bleManager.getEspDevice(nodeId);
        if (espDevice == null) {
            Log.e(TAG, "ESPDevice is null — BLE not connected for " + nodeId);
            Toast.makeText(this, R.string.ble_prov_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        startWifiScan();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_layout).findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(R.string.title_activity_ble_wifi_provision);
        toolbar.setNavigationIcon(AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left));
        toolbar.setNavigationOnClickListener(v -> finish());

        layoutWifiSelect = findViewById(R.id.layout_wifi_select);
        layoutProvisionProgress = findViewById(R.id.layout_provision_progress);
        rlProgress = findViewById(R.id.rl_progress);

        spinnerNetworks = findViewById(R.id.spinner_networks);
        layoutPassword = findViewById(R.id.layout_password);
        etPassword = findViewById(R.id.et_password);
        tvAddNetwork = findViewById(R.id.tv_add_network);

        btnProvision = findViewById(R.id.btn_provision);
        TextView btnProvText = btnProvision.findViewById(R.id.text_btn);
        btnProvText.setText(R.string.provision);
        btnProvision.setVisibility(View.GONE);

        ivTick1 = findViewById(R.id.iv_tick_1);
        ivTick2 = findViewById(R.id.iv_tick_2);
        ivTick3 = findViewById(R.id.iv_tick_3);
        progress1 = findViewById(R.id.prov_progress_1);
        progress2 = findViewById(R.id.prov_progress_2);
        progress3 = findViewById(R.id.prov_progress_3);
        tvProvError = findViewById(R.id.tv_prov_error);
        tvProvSuccess = findViewById(R.id.tv_prov_success);
        btnDone = findViewById(R.id.btn_done);
        TextView btnDoneText = btnDone.findViewById(R.id.text_btn);
        btnDoneText.setText(R.string.btn_done);

        wifiAPList = new ArrayList<>();
        WiFiAccessPoint placeholder = new WiFiAccessPoint();
        placeholder.setWifiName(getString(R.string.select_network));
        wifiAPList.add(placeholder);
        wiFiListAdapter = new WiFiListAdapter(this, wifiAPList);
        spinnerNetworks.setAdapter(wiFiListAdapter);

        spinnerNetworks.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (pos == 0) {
                    selectedWiFi = null;
                    ssid = null;
                    layoutPassword.setVisibility(View.INVISIBLE);
                    btnProvision.setVisibility(View.GONE);
                    return;
                }
                selectedWiFi = wifiAPList.get(pos);
                ssid = selectedWiFi.getWifiName();

                if (selectedWiFi.getSecurity() == ESPConstants.WIFI_OPEN) {
                    layoutPassword.setVisibility(View.INVISIBLE);
                    etPassword.setText("");
                    btnProvision.setVisibility(View.VISIBLE);
                } else {
                    layoutPassword.setVisibility(View.VISIBLE);
                    btnProvision.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        btnProvision.setOnClickListener(v -> onProvisionClicked());

        tvAddNetwork.setOnClickListener(v -> askForNetwork());

        btnDone.setOnClickListener(v -> finish());
    }

    private void startWifiScan() {
        showScanLoading();

        ArrayList<String> caps = BleLocalControlManager.getInstance(this).getDeviceCapabilities(nodeId);
        boolean hasWifiScan = caps != null && caps.contains(AppConstants.CAPABILITY_WIFI_SCAN);

        if (hasWifiScan) {
            espDevice.scanNetworks(new WiFiScanListener() {
                @Override
                public void onWifiListReceived(ArrayList<WiFiAccessPoint> wifiList) {
                    runOnUiThread(() -> {
                        wifiAPList.addAll(wifiList);
                        wiFiListAdapter.notifyDataSetChanged();
                        hideScanLoading();
                    });
                }

                @Override
                public void onWiFiScanFailed(Exception e) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Wi-Fi scan failed: " + e.getMessage());
                        hideScanLoading();
                        Toast.makeText(BleWifiProvisionActivity.this,
                                "Wi-Fi scan failed", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            hideScanLoading();
            askForNetwork();
        }
    }

    private void askForNetwork() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_wifi_network, null);
        final EditText etSsid = dialogView.findViewById(R.id.et_ssid);
        final EditText etPwd = dialogView.findViewById(R.id.et_password);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_network_info)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_join, (dialog, which) -> {
                    String networkName = etSsid.getText().toString().trim();
                    String password = etPwd.getText().toString();
                    if (!TextUtils.isEmpty(networkName)) {
                        ssid = networkName;
                        startProvisioning(ssid, password);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void onProvisionClicked() {
        if (TextUtils.isEmpty(ssid)) {
            Toast.makeText(this, R.string.error_ssid_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        String password = etPassword.getText().toString();
        if (selectedWiFi != null && selectedWiFi.getSecurity() != ESPConstants.WIFI_OPEN
                && password.length() < AppConstants.MIN_LEN_PASSWORD) {
            etPassword.setError(getString(R.string.error_password_empty));
            return;
        }

        startProvisioning(ssid, password);
    }

    private void startProvisioning(String ssidValue, String password) {
        layoutWifiSelect.setVisibility(View.GONE);
        layoutProvisionProgress.setVisibility(View.VISIBLE);

        progress1.setVisibility(View.VISIBLE);
        ivTick1.setVisibility(View.GONE);

        espDevice.provision(ssidValue, password, new ProvisionListener() {

            @Override
            public void createSessionFailed(Exception e) {
                runOnUiThread(() -> showProvisionError("Session failed: " + e.getMessage()));
            }

            @Override
            public void wifiConfigSent() {
                runOnUiThread(() -> {
                    progress1.setVisibility(View.GONE);
                    ivTick1.setVisibility(View.VISIBLE);
                    ivTick1.setImageResource(R.drawable.ic_checkbox_on);
                    progress2.setVisibility(View.VISIBLE);
                    ivTick2.setVisibility(View.GONE);
                });
            }

            @Override
            public void wifiConfigFailed(Exception e) {
                runOnUiThread(() -> showProvisionError("Config send failed: " + e.getMessage()));
            }

            @Override
            public void wifiConfigApplied() {
                runOnUiThread(() -> {
                    progress2.setVisibility(View.GONE);
                    ivTick2.setVisibility(View.VISIBLE);
                    ivTick2.setImageResource(R.drawable.ic_checkbox_on);
                    progress3.setVisibility(View.VISIBLE);
                    ivTick3.setVisibility(View.GONE);
                });
            }

            @Override
            public void wifiConfigApplyFailed(Exception e) {
                runOnUiThread(() -> showProvisionError("Apply failed: " + e.getMessage()));
            }

            @Override
            public void provisioningFailedFromDevice(ESPConstants.ProvisionFailureReason reason) {
                runOnUiThread(() -> showProvisionError("Device error: " + reason.name()));
            }

            @Override
            public void deviceProvisioningSuccess() {
                runOnUiThread(() -> {
                    progress3.setVisibility(View.GONE);
                    ivTick3.setVisibility(View.VISIBLE);
                    ivTick3.setImageResource(R.drawable.ic_checkbox_on);

                    tvProvSuccess.setVisibility(View.VISIBLE);
                    btnDone.setVisibility(View.VISIBLE);

                    BleLocalControlManager.getInstance(BleWifiProvisionActivity.this)
                            .disconnectDevice(nodeId);
                });
            }

            @Override
            public void onProvisioningFailed(Exception e) {
                runOnUiThread(() -> showProvisionError("Provisioning failed: " + e.getMessage()));
            }
        });
    }

    private void showProvisionError(String detail) {
        Log.e(TAG, detail);
        progress1.setVisibility(View.GONE);
        progress2.setVisibility(View.GONE);
        progress3.setVisibility(View.GONE);

        tvProvError.setVisibility(View.VISIBLE);
        btnDone.setVisibility(View.VISIBLE);
    }

    private void showScanLoading() {
        rlProgress.setVisibility(View.VISIBLE);
        layoutWifiSelect.setVisibility(View.GONE);
    }

    private void hideScanLoading() {
        rlProgress.setVisibility(View.GONE);
        layoutWifiSelect.setVisibility(View.VISIBLE);
        btnProvision.setVisibility(View.GONE);
    }
}
