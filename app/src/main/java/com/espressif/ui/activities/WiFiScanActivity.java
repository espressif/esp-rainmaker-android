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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.espressif.AppConstants;
import com.espressif.provisioning.DeviceConnectionEvent;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.WiFiAccessPoint;
import com.espressif.provisioning.listeners.WiFiScanListener;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.rainmaker.databinding.ActivityWifiScanListBinding;
import com.espressif.ui.adapters.WiFiListAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class WiFiScanActivity extends AppCompatActivity {

    private static final String TAG = WiFiScanActivity.class.getSimpleName();

    private static final int REQUEST_ACCESS_FINE_LOCATION = 1;

    private ActivityWifiScanListBinding binding;

    private MaterialCardView btnNext, btnRescan;

    private ESPProvisionManager provisionManager;
    private String ssid, password;
    private WifiManager wifiManager;
    private List<ScanResult> results;
    private ArrayList<WiFiAccessPoint> wifiAPList;
    private WiFiListAdapter wiFiListAdapter;
    private SharedPreferences sharedPreferences;
    private boolean shouldSavePassword;
    private Handler handler;
    private String previousNetwork;
    private WiFiAccessPoint selectedWiFi;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWifiScanListBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        provisionManager = ESPProvisionManager.getInstance(getApplicationContext());
        shouldSavePassword = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(AppConstants.KEY_SHOULD_SAVE_PWD, true);

        sharedPreferences = getSharedPreferences(AppConstants.PREF_FILE_WIFI_NETWORKS, Context.MODE_PRIVATE);
        handler = new Handler();
        wifiAPList = new ArrayList<>();
        previousNetwork = getIntent().getStringExtra(AppConstants.KEY_SSID);
        initViews();
        EventBus.getDefault().register(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new
                    String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ACCESS_FINE_LOCATION);
        } else {
            startScan();
        }
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (provisionManager.getEspDevice() != null) {
            provisionManager.getEspDevice().disconnectDevice();
        }
        super.onBackPressed();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(DeviceConnectionEvent event) {

        Log.d(TAG, "On Device Connection Event RECEIVED : " + event.getEventType());

        switch (event.getEventType()) {

            case ESPConstants.EVENT_DEVICE_DISCONNECTED:
                if (!isFinishing()) {
                    showAlertForDeviceDisconnected();
                }
                break;
        }
    }

    private void initViews() {

        setSupportActionBar(binding.toolbarLayout.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        binding.toolbarLayout.toolbar.setTitle(R.string.title_activity_wifi_scan_list);
        binding.toolbarLayout.toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        binding.toolbarLayout.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ESPProvisionManager provisionManager = ESPProvisionManager.getInstance(getApplicationContext());
                provisionManager.getEspDevice().disconnectDevice();
                finish();
            }
        });

        binding.cbSavePwd.setChecked(shouldSavePassword);

        WiFiAccessPoint dummyAP = new WiFiAccessPoint();
        dummyAP.setWifiName(getString(R.string.select_network));
        wifiAPList.add(dummyAP);
        wiFiListAdapter = new WiFiListAdapter(this, wifiAPList);
        binding.spinnerNetworks.setAdapter(wiFiListAdapter);

        btnNext = findViewById(R.id.btn_start);
        binding.btnStart.textBtn.setText(R.string.btn_start);
        btnNext.findViewById(R.id.iv_arrow).setVisibility(View.GONE);

        btnRescan = findViewById(R.id.btn_rescan);
        btnRescan.setStrokeColor(getColor(android.R.color.transparent));
        binding.btnRescan.textBtn.setText(R.string.btn_scan_again);

        btnNext.setOnClickListener(startBtnClickListener);
        btnRescan.setOnClickListener(scanAgainClickListener);
        binding.tvAddNetwork.setOnClickListener(otherNetworkClickListener);

        binding.spinnerNetworks.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedWiFi = wifiAPList.get(position);
                ssid = selectedWiFi.getWifiName();
                binding.etPassword.setText("");

                if (ssid.equals(getString(R.string.select_network))) {
                    btnNext.setVisibility(View.INVISIBLE);
                    binding.layoutPassword.setVisibility(View.INVISIBLE);
                    binding.layoutSavePassword.setVisibility(View.INVISIBLE);
                } else {
                    btnNext.setVisibility(View.VISIBLE);
                    if (selectedWiFi.getSecurity() == ESPConstants.WIFI_OPEN) {
                        binding.layoutPassword.setVisibility(View.INVISIBLE);
                        binding.layoutSavePassword.setVisibility(View.INVISIBLE);
                    } else {
                        binding.layoutPassword.setVisibility(View.VISIBLE);
                        binding.layoutSavePassword.setVisibility(View.VISIBLE);

                        if (shouldSavePassword) {
                            if (sharedPreferences.contains(ssid)) {
                                String password = sharedPreferences.getString(ssid, "");
                                binding.etPassword.setText(password);
                                binding.etPassword.setSelection(binding.etPassword.getText().length());
                            } else {
                                binding.etPassword.setText("");
                            }
                        }
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        binding.etPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

                if (actionId == EditorInfo.IME_ACTION_GO) {
                    startBtnClick();
                }
                return false;
            }
        });

        if (BuildConfig.WIFI_SCAN_SRC.equals(AppConstants.WIFI_SCAN_FROM_PHONE)) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            registerReceiver(wifiScanReceiver, intentFilter);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.e(TAG, "onRequestPermissionsResult , requestCode : " + requestCode);

        if (requestCode == REQUEST_ACCESS_FINE_LOCATION) {
            startScan();
        }
    }

    private void startScan() {

        updateProgressAndScanBtn(true);
        if (BuildConfig.WIFI_SCAN_SRC.equals(AppConstants.WIFI_SCAN_FROM_DEVICE)) {
            showLoading();
            startWifiScanUsingDevice();
        } else {
            displayWifiList();
            boolean success = wifiManager.startScan();
            if (!success) {
                Log.e(TAG, "Failed to start Wi-Fi Scanning using phone");
            }
        }
    }

    private void startBtnClick() {

        String password = binding.etPassword.getText().toString();

        // Store save password setting in main preferences file.
        SharedPreferences.Editor mainPrefEditor = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE).edit();
        mainPrefEditor.putBoolean(AppConstants.KEY_SHOULD_SAVE_PWD, binding.cbSavePwd.isChecked());
        mainPrefEditor.apply();

        SharedPreferences.Editor networksPrefEditor = sharedPreferences.edit();
        if (binding.cbSavePwd.isChecked()) {
            networksPrefEditor.putString(ssid, password);
        } else {
            networksPrefEditor.remove(ssid);
        }
        networksPrefEditor.apply();

        if (TextUtils.isEmpty(ssid) || ssid.equals(getString(R.string.select_network))) {
            Toast.makeText(WiFiScanActivity.this, R.string.error_network_select, Toast.LENGTH_LONG).show();
        } else {
            goToProvisionActivity(ssid, password);
        }
    }

    private void startWifiScanUsingDevice() {

        Log.d(TAG, "Start Wi-Fi Scan");
        wifiAPList.clear();
        WiFiAccessPoint selectNw = new WiFiAccessPoint();
        selectNw.setWifiName(getString(R.string.select_network));
        wifiAPList.add(selectNw);
        handler.postDelayed(stopScanningTask, 15000);

        provisionManager.getEspDevice().scanNetworks(new WiFiScanListener() {

            @Override
            public void onWifiListReceived(final ArrayList<WiFiAccessPoint> wifiList) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        wifiAPList.addAll(wifiList);
                        displayWifiList();
                    }
                });
            }

            @Override
            public void onWiFiScanFailed(Exception e) {

                Log.e(TAG, "onWiFiScanFailed");
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideLoading();
                        updateProgressAndScanBtn(false);
                        Toast.makeText(WiFiScanActivity.this, "Failed to get Wi-Fi scan list", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private View.OnClickListener startBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            startBtnClick();
        }
    };

    View.OnClickListener otherNetworkClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            askForNetwork();
        }
    };

    View.OnClickListener scanAgainClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            startScan();
        }
    };

    private Runnable stopScanningTask = new Runnable() {

        @Override
        public void run() {
            updateProgressAndScanBtn(false);
        }
    };

    BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context c, Intent intent) {

            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                displayWifiList();
            } else {
                Log.e(TAG, "Failed to start Wi-Fi Scanning using phone");
            }
        }
    };

    private void displayWifiList() {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                hideLoading();
                handler.removeCallbacks(stopScanningTask);
                ssid = getConnectedNetwork();
                if (!TextUtils.isEmpty(previousNetwork)
                        && provisionManager.getEspDevice().getTransportType().equals(ESPConstants.TransportType.TRANSPORT_SOFTAP)) {
                    ssid = previousNetwork;
                }

                if (BuildConfig.WIFI_SCAN_SRC.equals(AppConstants.WIFI_SCAN_FROM_PHONE)) {

                    results = wifiManager.getScanResults();

                    for (int i = 0; i < results.size(); i++) {
                        ScanResult network = results.get(i);
                        String networkName = network.SSID;

                        Log.e(TAG, "Network name : " + networkName);
                        networkName = networkName.replace("\"", "");
                        WiFiAccessPoint wifiAp = new WiFiAccessPoint();
                        wifiAp.setWifiName(networkName);
                        wifiAp.setRssi(network.level);

                        String capabilities = network.capabilities;
                        Log.w(TAG, network.SSID + " capabilities : " + capabilities);
                        if (capabilities.toUpperCase().contains("WEP")) {
                            wifiAp.setSecurity(ESPConstants.WIFI_WEP);
                        } else if (capabilities.toUpperCase().contains("WPA")) {
                            wifiAp.setSecurity(ESPConstants.WIFI_WPA_PSK);
                        } else if (capabilities.toUpperCase().contains("WPA2")) {
                            wifiAp.setSecurity(ESPConstants.WIFI_WPA2_PSK);
                        } else {
                            wifiAp.setSecurity(ESPConstants.WIFI_OPEN);
                        }
                    }
                }

                if (!TextUtils.isEmpty(ssid) && !provisionManager.getEspDevice().getTransportType().
                        equals(ESPConstants.TransportType.TRANSPORT_SOFTAP)) {

                    for (int i = 0; i < wifiAPList.size(); i++) {
                        if (ssid.equals(wifiAPList.get(i).getWifiName())) {
                            binding.spinnerNetworks.setSelection(i);
                        }
                    }
                }
                wiFiListAdapter.notifyDataSetChanged();

                if (shouldSavePassword && sharedPreferences.contains(ssid)) {
                    String password = sharedPreferences.getString(ssid, "");
                    binding.etPassword.setText(password);
                    binding.etPassword.setSelection(binding.etPassword.getText().length());
                }
                updateProgressAndScanBtn(false);
            }
        });
    }

    private void askForNetwork() {

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_wifi_network, null);
        builder.setView(dialogView);

        final EditText etSsid = dialogView.findViewById(R.id.et_ssid);
        final EditText etPassword = dialogView.findViewById(R.id.et_password);
        builder.setTitle(R.string.dialog_title_network_info);

        builder.setPositiveButton(R.string.btn_join, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                String password = etPassword.getText().toString();
                String networkName = etSsid.getText().toString();

                if (TextUtils.isEmpty(networkName)) {

                    etSsid.setError(getString(R.string.error_ssid_empty));

                } else {

                    dialog.dismiss();
                    goToProvisionActivity(networkName, password);
                }
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void goToProvisionActivity(String networkName, String password) {
        finish();
        Intent provisionIntent = new Intent(getApplicationContext(), ProvisionActivity.class);
        provisionIntent.putExtras(getIntent());
        provisionIntent.putExtra(AppConstants.KEY_SSID, networkName);
        provisionIntent.putExtra(AppConstants.KEY_PASSWORD, password);
        startActivity(provisionIntent);
    }

    private String getConnectedNetwork() {

        String connectedNetwork = null;
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
            connectedNetwork = wifiInfo.getSSID();
        }

        if (!TextUtils.isEmpty(connectedNetwork)) {
            connectedNetwork = connectedNetwork.replace("\"", "");
        }
        return connectedNetwork;
    }

    /**
     * This method will update UI (Scan button enable / disable and progressbar visibility)
     */
    private void updateProgressAndScanBtn(boolean isScanning) {

        btnRescan.setEnabled(!isScanning);
        if (isScanning) {
            binding.btnRescan.textBtn.setText(R.string.progress_scan_networks);
        } else {
            binding.btnRescan.textBtn.setText(R.string.btn_scan_again);
        }
    }

    private void showAlertForDeviceDisconnected() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle(R.string.error_title);
        builder.setMessage(R.string.dialog_msg_ble_device_disconnection);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });
        builder.show();
    }

    private void showLoading() {
        binding.rlWifiScan.setAlpha(0.3f);
        binding.rlProgress.setVisibility(View.VISIBLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private void hideLoading() {
        binding.rlWifiScan.setAlpha(1);
        binding.rlProgress.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }
}
