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

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.espressif.AppConstants;
import com.espressif.provisioning.DeviceConnectionEvent;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.listeners.ResponseListener;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.Utils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class ProofOfPossessionActivity extends AppCompatActivity {

    private static final String TAG = ProofOfPossessionActivity.class.getSimpleName();

    private MaterialCardView btnNext;
    private TextView txtNextBtn;

    private String deviceName = "";
    private TextView tvPopInstruction, tvPopError;
    private EditText etPop;
    private ESPProvisionManager provisionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pop);

        provisionManager = ESPProvisionManager.getInstance(getApplicationContext());
        initViews();
        EventBus.getDefault().register(this);

        deviceName = getIntent().getStringExtra(AppConstants.KEY_DEVICE_NAME);
        if (TextUtils.isEmpty(deviceName)) {
            if (provisionManager.getEspDevice() != null) {
                deviceName = provisionManager.getEspDevice().getDeviceName();
            }
        }

        if (!TextUtils.isEmpty(deviceName)) {
            String popText = getString(R.string.pop_instruction) + " " + deviceName;
            tvPopInstruction.setText(popText);
        }

        btnNext.setOnClickListener(nextBtnClickListener);

        if (!TextUtils.isEmpty(BuildConfig.POP)) {

            etPop.setText(BuildConfig.POP);
            etPop.setSelection(etPop.getText().length());
        }
        etPop.requestFocus();

        etPop.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

                if (actionId == EditorInfo.IME_ACTION_GO) {
                    nextBtnClick();
                }
                return false;
            }
        });
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

    private View.OnClickListener nextBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            nextBtnClick();
        }
    };

    private void initViews() {

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setTitle(R.string.title_activity_pop);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (provisionManager.getEspDevice() != null) {
                    provisionManager.getEspDevice().disconnectDevice();
                }
                finish();
            }
        });

        tvPopInstruction = findViewById(R.id.tv_pop);
        tvPopError = findViewById(R.id.tv_error_pop);
        etPop = findViewById(R.id.et_pop);

        btnNext = findViewById(R.id.btn_next);
        txtNextBtn = findViewById(R.id.text_btn);

        txtNextBtn.setText(R.string.btn_next);
        btnNext.setOnClickListener(nextBtnClickListener);
    }

    private void nextBtnClick() {

        final String pop = etPop.getText().toString();
        Log.d(TAG, "Set POP : " + pop);
        tvPopError.setVisibility(View.INVISIBLE);
        provisionManager.getEspDevice().setProofOfPossession(pop);

        provisionManager.getEspDevice().initSession(new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Utils.setSecurityTypeFromVersionInfo(getApplicationContext());

                        ArrayList<String> rmakerCaps = new ArrayList<>();
                        ArrayList<String> deviceCaps = provisionManager.getEspDevice().getDeviceCapabilities();

                        try {
                            String versionInfo = provisionManager.getEspDevice().getVersionInfo();
                            JSONObject jsonObject = new JSONObject(versionInfo);
                            JSONObject rmakerInfo = jsonObject.optJSONObject("rmaker");

                            if (rmakerInfo != null) {

                                JSONArray rmakerCapabilities = rmakerInfo.optJSONArray("cap");
                                if (rmakerCapabilities != null) {
                                    for (int i = 0; i < rmakerCapabilities.length(); i++) {
                                        String cap = rmakerCapabilities.getString(i);
                                        rmakerCaps.add(cap);
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Log.d(TAG, "Version Info JSON not available.");
                        }

                        if (rmakerCaps.size() > 0 && rmakerCaps.contains(AppConstants.CAPABILITY_CLAIM)) {
                            goToClaimingActivity();
                        } else {
                            if (deviceCaps != null) {
                                if (deviceCaps.contains(AppConstants.CAPABILITY_WIFI_SCAN)) {
                                    goToWiFiScanListActivity();
                                } else if (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_SCAN)) {
                                    goToThreadConfigActivity(true);
                                } else if (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_PROV)) {
                                    goToThreadConfigActivity(false);
                                } else {
                                    goToWiFiConfigActivity();
                                }
                            } else {
                                goToWiFiConfigActivity();
                            }
                        }
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvPopError.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void goToClaimingActivity() {

        finish();
        Intent claimingIntent = new Intent(getApplicationContext(), ClaimingActivity.class);
        claimingIntent.putExtras(getIntent());
        claimingIntent.putExtra(AppConstants.KEY_SSID, getIntent().getStringExtra(AppConstants.KEY_SSID));
        startActivity(claimingIntent);
    }

    private void goToWiFiScanListActivity() {

        finish();
        Intent wifiListIntent = new Intent(getApplicationContext(), WiFiScanActivity.class);
        wifiListIntent.putExtras(getIntent());
        wifiListIntent.putExtra(AppConstants.KEY_SSID, getIntent().getStringExtra(AppConstants.KEY_SSID));
        startActivity(wifiListIntent);
    }

    private void goToWiFiConfigActivity() {

        finish();
        Intent wifiConfigIntent = new Intent(getApplicationContext(), WiFiConfigActivity.class);
        wifiConfigIntent.putExtras(getIntent());
        wifiConfigIntent.putExtra(AppConstants.KEY_SSID, getIntent().getStringExtra(AppConstants.KEY_SSID));
        startActivity(wifiConfigIntent);
    }

    private void goToThreadConfigActivity(boolean scanCapAvailable) {
        finish();
        Intent threadConfigIntent = new Intent(getApplicationContext(), ThreadConfigActivity.class);
        threadConfigIntent.putExtras(getIntent());
        threadConfigIntent.putExtra(AppConstants.KEY_THREAD_SCAN_AVAILABLE, scanCapAvailable);
        startActivity(threadConfigIntent);
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
}
