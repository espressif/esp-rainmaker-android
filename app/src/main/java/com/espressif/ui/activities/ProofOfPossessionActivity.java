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
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.rainmaker.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class ProofOfPossessionActivity extends AppCompatActivity {

    private static final String TAG = ProofOfPossessionActivity.class.getSimpleName();

    private TextView tvTitle, tvBack, tvCancel;
    private CardView btnNext;
    private TextView txtNextBtn;

    private String deviceName;
    private TextView tvPopInstruction;
    private EditText etPop;
    private ESPProvisionManager provisionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pop);

        provisionManager = ESPProvisionManager.getInstance(getApplicationContext());
        initViews();

        deviceName = "";
        if (provisionManager.getEspDevice() != null) {
            deviceName = provisionManager.getEspDevice().getDeviceName();
        }

        if (!TextUtils.isEmpty(deviceName)) {
            String popText = getString(R.string.pop_instruction) + " " + deviceName;
            tvPopInstruction.setText(popText);
        }

        btnNext.setOnClickListener(nextBtnClickListener);
        String pop = getResources().getString(R.string.proof_of_possesion);

        if (!TextUtils.isEmpty(pop)) {

            etPop.setText(pop);
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
    public void onBackPressed() {
        if (provisionManager.getEspDevice() != null) {
            provisionManager.getEspDevice().disconnectDevice();
        }
        super.onBackPressed();
    }

    private View.OnClickListener nextBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            nextBtnClick();
        }
    };

    private View.OnClickListener cancelBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            if (provisionManager.getEspDevice() != null) {
                provisionManager.getEspDevice().disconnectDevice();
            }
            finish();
        }
    };

    private void initViews() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvCancel = findViewById(R.id.btn_cancel);
        tvPopInstruction = findViewById(R.id.tv_pop);
        etPop = findViewById(R.id.et_pop);

        tvTitle.setText(R.string.title_activity_pop);
        tvBack.setVisibility(View.GONE);
        tvCancel.setVisibility(View.VISIBLE);

        tvCancel.setOnClickListener(cancelBtnClickListener);

        btnNext = findViewById(R.id.btn_next);
        txtNextBtn = findViewById(R.id.text_btn);

        txtNextBtn.setText(R.string.btn_next);
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
}
