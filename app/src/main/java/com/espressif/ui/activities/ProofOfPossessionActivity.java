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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.espressif.provisioning.DeviceConnectionEvent;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.theme_manager.WindowThemeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class ProofOfPossessionActivity extends AppCompatActivity {

    private static final String TAG = ProofOfPossessionActivity.class.getSimpleName();

    private Toolbar toolbar;
    private MaterialButton btnNext;

    private String deviceName;
    private TextView tvPopInstruction;
    private EditText etPop;
    private ESPProvisionManager provisionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pop);
        WindowThemeManager WindowTheme = new WindowThemeManager(this, false);
        WindowTheme.applyWindowTheme(getWindow());

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.title_activity_pop);

        provisionManager = ESPProvisionManager.getInstance(getApplicationContext());
        initViews();
        EventBus.getDefault().register(this);

        deviceName = "";
        if (provisionManager.getEspDevice() != null) {
            deviceName = provisionManager.getEspDevice().getDeviceName();
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
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_toolbar, menu);
        MenuItem tvCancel = menu.findItem(R.id.action_cancel);
        tvCancel.setVisible(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_cancel:
                cancelBtnVoid();
                return true;
            default:
                return super.onOptionsItemSelected(item);
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

    private View.OnClickListener nextBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            nextBtnClick();
        }
    };

    private void cancelBtnVoid() {

            if (provisionManager.getEspDevice() != null) {
                provisionManager.getEspDevice().disconnectDevice();
            }
            finish();
    };

    private void initViews() {

        tvPopInstruction = findViewById(R.id.tv_pop);
        etPop = findViewById(R.id.et_pop);
        btnNext = findViewById(R.id.btn_material);

        btnNext.setText(R.string.btn_next);
        btnNext.setOnClickListener(nextBtnClickListener);
    }

    private void nextBtnClick() {

        final String pop = etPop.getText().toString();
        Log.d(TAG, "Set POP : " + pop);
        provisionManager.getEspDevice().setProofOfPossession(pop);
        String versionInfo = provisionManager.getEspDevice().getVersionInfo();
        ArrayList<String> rmakerCaps = new ArrayList<>();
        ArrayList<String> deviceCaps = provisionManager.getEspDevice().getDeviceCapabilities();

        try {
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

        if (rmakerCaps.size() > 0 && rmakerCaps.contains("claim")) {

            goToClaimingActivity();

        } else {

            if (deviceCaps != null && deviceCaps.contains("wifi_scan")) {

                goToWiFiScanListActivity();

            } else {
                goToWiFiConfigActivity();
            }
        }
    }

    private void goToClaimingActivity() {

        Intent claimingIntent = new Intent(getApplicationContext(), ClaimingActivity.class);
        startActivity(claimingIntent);
        finish();
    }

    private void goToWiFiScanListActivity() {

        Intent wifiListIntent = new Intent(getApplicationContext(), WiFiScanActivity.class);
        startActivity(wifiListIntent);
        finish();
    }

    private void goToWiFiConfigActivity() {

        Intent wifiConfigIntent = new Intent(getApplicationContext(), WiFiConfigActivity.class);
        startActivity(wifiConfigIntent);
        finish();
    }

    private void showAlertForDeviceDisconnected() {

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_MaterialAlertDialog);
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
