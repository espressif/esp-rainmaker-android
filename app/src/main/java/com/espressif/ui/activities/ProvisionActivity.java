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
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provisioning.DeviceConnectionEvent;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPDevice;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.listeners.ProvisionListener;
import com.espressif.provisioning.listeners.ResponseListener;
import com.espressif.rainmaker.R;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.Service;
import com.espressif.ui.models.UpdateEvent;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.UUID;

import rainmaker.EspRmakerUserMapping;
import rmaker_misc.EspRmakerChalResp.CmdCRPayload;
import rmaker_misc.EspRmakerChalResp.RMakerMiscMsgType;
import rmaker_misc.EspRmakerChalResp.RMakerMiscPayload;
import rmaker_misc.EspRmakerChalResp.RMakerMiscStatus;
import rmaker_misc.EspRmakerChalResp.RespCRPayload;

public class ProvisionActivity extends AppCompatActivity {

    private static final String TAG = ProvisionActivity.class.getSimpleName();

    private static final long ADD_DEVICE_REQ_TIME = 5000;
    private static final long NODE_STATUS_REQ_TIME = 35000;
    private static final long WIFI_CONNECT_TIMEOUT = 15000; // 15 seconds timeout

    private ImageView tick1, tick2, tick3, tick4, tick5;
    private ContentLoadingProgressBar progress1, progress2, progress3, progress4, progress5;
    private TextView tvErrAtStep1, tvErrAtStep2, tvErrAtStep3, tvErrAtStep4, tvErrAtStep5;
    private TextView tvProvSuccess, tvProvError;
    private TextView tvProvStep1, tvProvStep2;

    private MaterialCardView btnOk;
    private TextView txtOkBtn;

    private int addDeviceReqCount = 0;
    private String ssidValue, passphraseValue = "", dataset;
    private String receivedNodeId, secretKey;

    private ApiManager apiManager;
    private Handler handler;
    private ESPProvisionManager provisionManager;
    private boolean isProvisioningCompleted = false;
    private boolean isChallengeResponseFlow = false;
    private Handler wifiConnectHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provision);

        Intent intent = getIntent();
        ssidValue = intent.getStringExtra(AppConstants.KEY_SSID);
        passphraseValue = intent.getStringExtra(AppConstants.KEY_PASSWORD);
        dataset = intent.getStringExtra(AppConstants.KEY_THREAD_DATASET);
        provisionManager = ESPProvisionManager.getInstance(getApplicationContext());

        handler = new Handler();
        apiManager = ApiManager.getInstance(getApplicationContext());
        initViews();
        checkDeviceCapabilities();

        Log.d(TAG, "Selected AP - " + ssidValue);
        EventBus.getDefault().register(this);
        showLoading();
        doStep1();
    }

    @Override
    public void onBackPressed() {
        provisionManager.getEspDevice().disconnectDevice();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {

        wifiConnectHandler.removeCallbacks(wifiConnectTimeoutTask);
        apiManager.cancelRequestStatusPollingTask();
        handler.removeCallbacks(getNodeStatusTask);
        handler.removeCallbacks(nodeStatusReqFailed);
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UpdateEvent event) {
        Log.d(TAG, "ON UPDATE EVENT RECEIVED : " + event.getEventType());

        switch (event.getEventType()) {

            case EVENT_DEVICE_ADDED:
                if (!isChallengeResponseFlow) {
                    doStep5();
                }
                break;

            case EVENT_ADD_DEVICE_TIME_OUT:
                if (!isChallengeResponseFlow) {
                    tick4.setImageResource(R.drawable.ic_error);
                    tick4.setVisibility(View.VISIBLE);
                    progress4.setVisibility(View.GONE);
                    tvErrAtStep4.setVisibility(View.VISIBLE);
                    tvErrAtStep4.setText(R.string.error_prov_step_4);
                    tvProvError.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(DeviceConnectionEvent event) {

        Log.d(TAG, "On Device Connection Event RECEIVED : " + event.getEventType());

        switch (event.getEventType()) {

            case ESPConstants.EVENT_DEVICE_DISCONNECTED:
                if (!isFinishing() && !isProvisioningCompleted) {
                    showAlertForDeviceDisconnected();
                }
                break;
        }
    }


    private View.OnClickListener okBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            provisionManager.getEspDevice().disconnectDevice();
            finish();
        }
    };

    private void initViews() {

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setDisplayShowHomeEnabled(false);
        getSupportActionBar().setTitle(R.string.title_activity_provisioning);

        tick1 = findViewById(R.id.iv_tick_1);
        tick2 = findViewById(R.id.iv_tick_2);
        tick3 = findViewById(R.id.iv_tick_3);
        tick4 = findViewById(R.id.iv_tick_4);
        tick5 = findViewById(R.id.iv_tick_5);

        progress1 = findViewById(R.id.prov_progress_1);
        progress2 = findViewById(R.id.prov_progress_2);
        progress3 = findViewById(R.id.prov_progress_3);
        progress4 = findViewById(R.id.prov_progress_4);
        progress5 = findViewById(R.id.prov_progress_5);

        tvErrAtStep1 = findViewById(R.id.tv_prov_error_1);
        tvErrAtStep2 = findViewById(R.id.tv_prov_error_2);
        tvErrAtStep3 = findViewById(R.id.tv_prov_error_3);
        tvErrAtStep4 = findViewById(R.id.tv_prov_error_4);
        tvErrAtStep5 = findViewById(R.id.tv_prov_error_5);
        tvProvSuccess = findViewById(R.id.tv_prov_success);
        tvProvError = findViewById(R.id.tv_prov_error);

        tvProvStep1 = findViewById(R.id.tv_prov_step_1);
        tvProvStep2 = findViewById(R.id.tv_prov_step_2);

        btnOk = findViewById(R.id.btn_ok);
        txtOkBtn = findViewById(R.id.text_btn);
        btnOk.findViewById(R.id.iv_arrow).setVisibility(View.GONE);

        txtOkBtn.setText(R.string.btn_done);
        btnOk.setOnClickListener(okBtnClickListener);

        if (!TextUtils.isEmpty(dataset)) {
            tvProvStep1.setText(R.string.thread_prov_step_1);
            tvProvStep2.setText(R.string.thread_prov_step_2);
        }
    }

    private void checkDeviceCapabilities() {

        ESPDevice espDevice = provisionManager.getEspDevice();
        if (espDevice != null && espDevice.getTransportType().equals(ESPConstants.TransportType.TRANSPORT_BLE)) {
            String versionInfo = espDevice.getVersionInfo();
            ArrayList<String> rmakerExtraCaps = new ArrayList<>();

            try {
                JSONObject jsonObject = new JSONObject(versionInfo);
                JSONObject rmakerExtraInfo = jsonObject.optJSONObject("rmaker_extra");

                /* Check rmaker_extra capabilities */
                if (rmakerExtraInfo != null) {
                    JSONArray extraCapabilities = rmakerExtraInfo.optJSONArray("cap");
                    if (extraCapabilities != null) {
                        for (int i = 0, len = extraCapabilities.length(); i < len; i++) {
                            rmakerExtraCaps.add(extraCapabilities.optString(i));
                        }
                    }
                }
                isChallengeResponseFlow = rmakerExtraCaps.contains(AppConstants.CAPABILITY_CHALLENGE_RESP);

            } catch (JSONException e) {
                e.printStackTrace();
                Log.e(TAG, "Version Info JSON not available.");
                finish();
            }
        }
    }

    private void doStep1() {

        tick1.setVisibility(View.GONE);
        progress1.setVisibility(View.VISIBLE);

        if (isChallengeResponseFlow) {
            // Update UI for challenge-response flow
            tvProvStep1.setText(R.string.confirming_node_association);
            View step3View = findViewById(R.id.layout_configuring_wifi_creds);
            View step4View = findViewById(R.id.layout_confirming_node_association);
            if (step3View != null) {
                step3View.setVisibility(View.GONE);
            }
            if (step4View != null) {
                step4View.setVisibility(View.GONE);
            }
            verifyNodeAssociation();
        } else {
            associateDevice();
        }
    }

    private void doStep2() {

        tick1.setImageResource(R.drawable.ic_checkbox_on);
        tick1.setVisibility(View.VISIBLE);
        progress1.setVisibility(View.GONE);
        tick2.setVisibility(View.GONE);
        progress2.setVisibility(View.VISIBLE);
    }

    private void doStep3(boolean isSuccessInStep2) {

        if (isSuccessInStep2) {
            tick2.setImageResource(R.drawable.ic_checkbox_on);
        } else {
            tick2.setImageResource(R.drawable.ic_alert);
        }
        tick2.setVisibility(View.VISIBLE);
        progress2.setVisibility(View.GONE);

        if (!isChallengeResponseFlow) {
            tick3.setVisibility(View.GONE);
            progress3.setVisibility(View.VISIBLE);
            handler.postDelayed(addDeviceTask, ADD_DEVICE_REQ_TIME);
        } else {
            hideLoading();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    doStep5();
                }
            }, 500);
        }
    }

    private void doStep4() {

        hideLoading();
        tick3.setImageResource(R.drawable.ic_checkbox_on);
        tick3.setVisibility(View.VISIBLE);
        progress3.setVisibility(View.GONE);
        tick4.setVisibility(View.GONE);
        progress4.setVisibility(View.VISIBLE);
    }

    private void doStep5() {

        Log.d(TAG, "================= Do step 5 =================");
        Log.d(TAG, "Received node id : " + receivedNodeId);
        tick4.setImageResource(R.drawable.ic_checkbox_on);
        tick4.setVisibility(View.VISIBLE);
        progress4.setVisibility(View.GONE);
        tick5.setVisibility(View.GONE);
        progress5.setVisibility(View.VISIBLE);
        handler.postDelayed(nodeStatusReqFailed, NODE_STATUS_REQ_TIME);

        // Try to get node details even if local control failed
        apiManager.getNodeDetails(receivedNodeId, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                Log.e(TAG, "Get node details - success");
                handler.postDelayed(getNodeStatusTask, 1000);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                Log.e(TAG, "Get node details - failure");
                // Even if we fail to get details, proceed with status check
                handler.postDelayed(getNodeStatusTask, 1000);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                Log.e(TAG, "Get node details - failure");
                // Even if we fail to get details, proceed with status check
                handler.postDelayed(getNodeStatusTask, 1000);
            }
        });
    }

    private void provision() {

        Log.d(TAG, "+++++++++++++++++++++++++++++ PROVISION +++++++++++++++++++++++++++++");

        if (!TextUtils.isEmpty(dataset)) {
            provisionManager.getEspDevice().provision(dataset, new ProvisionListener() {

                @Override
                public void createSessionFailed(Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ProvisionActivity.this, R.string.error_session, Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void wifiConfigSent() {
                    // Nothing to do here
                    Log.d(TAG, "Thread Config sent");
                }

                @Override
                public void wifiConfigFailed(Exception e) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            tick1.setImageResource(R.drawable.ic_error);
                            tick1.setVisibility(View.VISIBLE);
                            progress1.setVisibility(View.GONE);
                            tvErrAtStep2.setVisibility(View.VISIBLE);
                            tvErrAtStep2.setText(R.string.error_prov_thread_step_2);
                            hideLoading();
                        }
                    });
                }

                @Override
                public void wifiConfigApplied() {
                    Log.d(TAG, "WiFi Config Applied");
                    runOnUiThread(() -> {
                        doStep2();
                        // Start WiFi connection confirmation timeout
                        wifiConnectHandler.postDelayed(wifiConnectTimeoutTask, WIFI_CONNECT_TIMEOUT);
                    });
                }

                @Override
                public void wifiConfigApplyFailed(Exception e) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            Log.e(TAG, "WiFi Config Apply failed");
                            tick1.setImageResource(R.drawable.ic_error);
                            tick1.setVisibility(View.VISIBLE);
                            progress1.setVisibility(View.GONE);
                            tvErrAtStep2.setVisibility(View.VISIBLE);
                            tvErrAtStep2.setText(R.string.error_prov_thread_step_2_);
                            hideLoading();
                        }
                    });
                }

                @Override
                public void provisioningFailedFromDevice(final ESPConstants.ProvisionFailureReason failureReason) {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            switch (failureReason) {
                                case AUTH_FAILED:
                                    tvErrAtStep2.setText(R.string.error_dataset_invalid);
                                    displayFailureAtStep2();
                                    break;
                                case NETWORK_NOT_FOUND:
                                    tvErrAtStep2.setText(R.string.error_network_not_found);
                                    displayFailureAtStep2();
                                    break;
                                case DEVICE_DISCONNECTED:
                                    doStep3(false);
                                    break;
                                case UNKNOWN:
                                    tvErrAtStep2.setText(R.string.error_prov_step_3);
                                    break;
                            }
                        }
                    });
                }

                @Override
                public void deviceProvisioningSuccess() {
                    runOnUiThread(() -> {
                        wifiConnectHandler.removeCallbacks(wifiConnectTimeoutTask);
                        isProvisioningCompleted = true;
                        doStep3(true);
                    });
                }

                @Override
                public void onProvisioningFailed(Exception e) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Device Provisioning Failed");
                        wifiConnectHandler.removeCallbacks(wifiConnectTimeoutTask);
                        doStep3(false);
                    });
                }
            });

        } else {
            provisionManager.getEspDevice().provision(ssidValue, passphraseValue, new ProvisionListener() {

                @Override
                public void createSessionFailed(Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ProvisionActivity.this, R.string.error_session, Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void wifiConfigSent() {
                    // Nothing to do here
                    Log.d(TAG, "WiFi Config sent");
                }

                @Override
                public void wifiConfigFailed(Exception e) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            tick1.setImageResource(R.drawable.ic_error);
                            tick1.setVisibility(View.VISIBLE);
                            progress1.setVisibility(View.GONE);
                            tvErrAtStep2.setVisibility(View.VISIBLE);
                            tvErrAtStep2.setText(R.string.error_prov_step_2);
                            hideLoading();
                        }
                    });
                }

                @Override
                public void wifiConfigApplied() {

                    Log.d(TAG, "WiFi Config Applied");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            doStep2();
                        }
                    });
                }

                @Override
                public void wifiConfigApplyFailed(Exception e) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            tick1.setImageResource(R.drawable.ic_error);
                            tick1.setVisibility(View.VISIBLE);
                            progress1.setVisibility(View.GONE);
                            tvErrAtStep2.setVisibility(View.VISIBLE);
                            tvErrAtStep2.setText(R.string.error_prov_step_2);
                            hideLoading();
                        }
                    });
                }

                @Override
                public void provisioningFailedFromDevice(final ESPConstants.ProvisionFailureReason failureReason) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            switch (failureReason) {

                                case AUTH_FAILED:
                                    tvErrAtStep2.setText(R.string.error_authentication_failed);
                                    displayFailureAtStep2();
                                    break;

                                case NETWORK_NOT_FOUND:
                                    tvErrAtStep2.setText(R.string.error_network_not_found);
                                    displayFailureAtStep2();
                                    break;

                                case DEVICE_DISCONNECTED:
                                    doStep3(false);
                                    break;

                                case UNKNOWN:
                                    tvErrAtStep2.setText(R.string.error_prov_step_3);
                                    displayFailureAtStep2();
                                    break;
                            }
                        }
                    });
                }

                @Override
                public void deviceProvisioningSuccess() {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            isProvisioningCompleted = true;
                            doStep3(true);
                        }
                    });
                }

                @Override
                public void onProvisioningFailed(Exception e) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            doStep3(false);
                        }
                    });
                }
            });
        }
    }

    private void displayFailureAtStep2() {

        tick2.setImageResource(R.drawable.ic_error);
        tick2.setVisibility(View.VISIBLE);
        progress2.setVisibility(View.GONE);
        tvErrAtStep2.setVisibility(View.VISIBLE);
        tvProvError.setVisibility(View.VISIBLE);
        hideLoading();
    }

    private void verifyNodeAssociation() {

        apiManager.initiateMapping(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                try {
                    String jsonResponse = data.getString(AppConstants.KEY_RESPONSE);
                    JSONObject jsonObject = new JSONObject(jsonResponse);
                    String challenge = jsonObject.optString(AppConstants.KEY_CHALLENGE);
                    String requestId = jsonObject.optString(AppConstants.KEY_REQUEST_ID);
                    Log.d(TAG, "Got challenge: " + challenge + ", request_id: " + requestId);

                    /* Send challenge to device using proto */
                    byte[] challengeBytes = challenge.getBytes(StandardCharsets.UTF_8);

                    CmdCRPayload cmdPayload = CmdCRPayload.newBuilder()
                            .setPayload(ByteString.copyFrom(challengeBytes))
                            .build();

                    RMakerMiscPayload payload = RMakerMiscPayload.newBuilder()
                            .setMsg(RMakerMiscMsgType.TypeCmdChallengeResponse)
                            .setStatus(RMakerMiscStatus.Success)
                            .setCmdChallengeResponsePayload(cmdPayload)
                            .build();

                    provisionManager.getEspDevice().sendDataToCustomEndPoint(AppConstants.HANDLER_RM_CH_RESP, payload.toByteArray(), new ResponseListener() {
                        @Override
                        public void onSuccess(byte[] returnData) {
                            if (returnData != null) {
                                try {
                                    RMakerMiscPayload response = RMakerMiscPayload.parseFrom(returnData);
                                    if (response.getStatus() == RMakerMiscStatus.Success) {
                                        RespCRPayload respPayload = response.getRespChallengeResponsePayload();
                                        ByteString signedChallenge = respPayload.getPayload();
                                        String nodeId = respPayload.getNodeId();
                                        receivedNodeId = nodeId;

                                        /* Call verify mapping API */
                                        byte[] bytes = signedChallenge.toByteArray();

                                        if (bytes.length != 256) {
                                            showMappingError();
                                            return;
                                        }

                                        /* Convert bytes to hex string */
                                        StringBuilder hexString = new StringBuilder(512);
                                        for (byte b : bytes) {
                                            hexString.append(String.format("%02x", b & 0xFF));
                                        }
                                        String challengeResponse = hexString.toString();

                                        JsonObject body = new JsonObject();
                                        body.addProperty(AppConstants.KEY_REQUEST_ID, requestId);
                                        body.addProperty(AppConstants.KEY_NODE_ID, nodeId);
                                        body.addProperty(AppConstants.KEY_CHALLENGE_RESP, challengeResponse);

                                        apiManager.verifyUserNodeMapping(requestId, nodeId, challengeResponse, new ApiResponseListener() {
                                            @Override
                                            public void onSuccess(Bundle data) {
                                                runOnUiThread(() -> {
                                                    provision();
                                                });
                                            }

                                            @Override
                                            public void onResponseFailure(Exception e) {
                                                showMappingError();
                                            }

                                            @Override
                                            public void onNetworkFailure(Exception e) {
                                                showMappingError();
                                            }
                                        });
                                    } else {
                                        showMappingError();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    showMappingError();
                                }
                            }
                        }

                        @Override
                        public void onFailure(Exception e) {
                            e.printStackTrace();
                            showMappingError();
                        }
                    });
                } catch (JSONException e) {
                    e.printStackTrace();
                    showMappingError();
                }
            }

            @Override
            public void onResponseFailure(Exception e) {
                showMappingError();
            }

            @Override
            public void onNetworkFailure(Exception e) {
                showMappingError();
            }
        });
    }

    private void showMappingError() {
        runOnUiThread(() -> {
            tick1.setImageResource(R.drawable.ic_error);
            tick1.setVisibility(View.VISIBLE);
            progress1.setVisibility(View.GONE);
            tvErrAtStep1.setVisibility(View.VISIBLE);
            tvErrAtStep1.setText(R.string.error_node_association);
            tvProvError.setVisibility(View.VISIBLE);
            tvProvError.setText(R.string.error_node_association_msg);
            hideLoading();
        });
    }

    private void associateDevice() {

        Log.d(TAG, "Associate device");

        if (isChallengeResponseFlow) {
            Log.d(TAG, "Challenge response was already done, skipping cloud user association");
            doStep4();
            return;
        }

        /* Proceed with traditional cloud user association if challenge response was not done */
        final String secretKey = UUID.randomUUID().toString();

        EspRmakerUserMapping.CmdSetUserMapping deviceSecretRequest = EspRmakerUserMapping.CmdSetUserMapping.newBuilder()
                .setUserID(ApiManager.userId)
                .setSecretKey(secretKey)
                .build();
        EspRmakerUserMapping.RMakerConfigMsgType msgType = EspRmakerUserMapping.RMakerConfigMsgType.TypeCmdSetUserMapping;
        EspRmakerUserMapping.RMakerConfigPayload payload = EspRmakerUserMapping.RMakerConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdSetUserMapping(deviceSecretRequest)
                .build();

        provisionManager.getEspDevice().sendDataToCustomEndPoint(AppConstants.HANDLER_RM_USER_MAPPING, payload.toByteArray(), new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Log.d(TAG, "Successfully sent user id and secrete key");
                processDetails(returnData, secretKey);
            }

            @Override
            public void onFailure(Exception e) {

                Log.e(TAG, "Send config data : Error : " + e.getMessage());

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        tick1.setImageResource(R.drawable.ic_error);
                        tick1.setVisibility(View.VISIBLE);
                        progress1.setVisibility(View.GONE);
                        tvErrAtStep1.setVisibility(View.VISIBLE);
                        tvErrAtStep1.setText(R.string.error_prov_step_1);
                        hideLoading();
                    }
                });

                e.printStackTrace();
            }
        });
    }

    private void processDetails(byte[] responseData, String secretKey) {

        try {
            EspRmakerUserMapping.RMakerConfigPayload payload = EspRmakerUserMapping.RMakerConfigPayload.parseFrom(responseData);
            EspRmakerUserMapping.RespSetUserMapping response = payload.getRespSetUserMapping();

            if (response.getStatus() == EspRmakerUserMapping.RMakerConfigStatus.Success) {

                receivedNodeId = response.getNodeId();
                this.secretKey = secretKey;

                provision();
            }

        } catch (InvalidProtocolBufferException e) {

            e.printStackTrace();
            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    tick1.setImageResource(R.drawable.ic_error);
                    tick1.setVisibility(View.VISIBLE);
                    progress1.setVisibility(View.GONE);
                    tvErrAtStep1.setVisibility(View.VISIBLE);
                    tvErrAtStep1.setText(R.string.error_prov_step_1);
                    hideLoading();
                }
            });
        }
    }

    private void addDeviceToCloud(final ApiResponseListener responseListener) {

        Log.d(TAG, "Add device to cloud, count : " + addDeviceReqCount);
        apiManager.addNode(receivedNodeId, secretKey, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                responseListener.onSuccess(null);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                exception.printStackTrace();
                responseListener.onNetworkFailure(exception);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                exception.printStackTrace();
                responseListener.onNetworkFailure(exception);
            }
        });
    }

    private Runnable addDeviceTask = new Runnable() {

        @Override
        public void run() {

            addDeviceReqCount++;

            addDeviceToCloud(new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            doStep4();
                        }
                    });
                }

                @Override
                public void onResponseFailure(Exception exception) {

                    if (addDeviceReqCount == 7) {

                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                tick3.setImageResource(R.drawable.ic_error);
                                tick3.setVisibility(View.VISIBLE);
                                progress3.setVisibility(View.GONE);
                                tvErrAtStep3.setVisibility(View.VISIBLE);
                                tvErrAtStep3.setText(R.string.error_prov_step_3);
                                tvProvError.setVisibility(View.VISIBLE);
                                hideLoading();
                            }
                        });
                    } else {
                        handler.postDelayed(addDeviceTask, ADD_DEVICE_REQ_TIME);
                    }
                }

                @Override
                public void onNetworkFailure(Exception exception) {

                    if (addDeviceReqCount == 7) {

                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                tick3.setImageResource(R.drawable.ic_error);
                                tick3.setVisibility(View.VISIBLE);
                                progress3.setVisibility(View.GONE);
                                tvErrAtStep3.setVisibility(View.VISIBLE);
                                tvErrAtStep3.setText(R.string.error_prov_step_3);
                                tvProvError.setVisibility(View.VISIBLE);
                                hideLoading();
                            }
                        });
                    } else {
                        handler.postDelayed(addDeviceTask, ADD_DEVICE_REQ_TIME);
                    }
                }
            });
        }
    };

    private Runnable getNodeStatusTask = new Runnable() {

        @Override
        public void run() {

            if (isFinishing()) {
                return;
            }
            apiManager.getNodeStatus(receivedNodeId, new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            EspApplication espApp = (EspApplication) getApplicationContext();
                            EspNode espNode = espApp.nodeMap.get(receivedNodeId);
                            if (espNode != null && espNode.isOnline()) {

                                // Send time zone to device.
                                ArrayList<Service> services = espNode.getServices();
                                boolean isTimeZoneServiceAvailable = false;
                                String paramName = "";

                                for (int i = 0; i < services.size(); i++) {

                                    Service s = services.get(i);
                                    if (!TextUtils.isEmpty(s.getType()) && s.getType().equals(AppConstants.SERVICE_TYPE_TIME)) {

                                        ArrayList<Param> timeParams = s.getParams();
                                        for (int index = 0; index < timeParams.size(); index++) {
                                            if (AppConstants.PARAM_TYPE_TZ.equals(timeParams.get(index).getParamType())) {
                                                isTimeZoneServiceAvailable = true;
                                                paramName = timeParams.get(index).getName();
                                                break;
                                            }
                                        }
                                        if (isTimeZoneServiceAvailable) {
                                            break;
                                        }
                                    }
                                }

                                if (isTimeZoneServiceAvailable) {

                                    Log.e(TAG, "Time zone service is available");
                                    TimeZone tz = TimeZone.getDefault();
                                    String timeZoneId = tz.getID();
                                    Log.e(TAG, "Time zone id : " + timeZoneId);

                                    JsonObject body = new JsonObject();
                                    JsonObject jsonParam = new JsonObject();
                                    jsonParam.addProperty(paramName, timeZoneId);
                                    body.add(AppConstants.KEY_TIME, jsonParam);
                                    apiManager.updateParamValue(espNode.getNodeId(), body, new ApiResponseListener() {

                                        @Override
                                        public void onSuccess(Bundle data) {
                                            handler.removeCallbacks(nodeStatusReqFailed);
                                            tick5.setImageResource(R.drawable.ic_checkbox_on);
                                            tick5.setVisibility(View.VISIBLE);
                                            progress5.setVisibility(View.GONE);
                                            tvProvSuccess.setVisibility(View.VISIBLE);
                                        }

                                        @Override
                                        public void onResponseFailure(Exception exception) {
                                            Log.e(TAG, "Failed to send time zone value");
                                            handler.removeCallbacks(getNodeStatusTask);
                                            tick5.setImageResource(R.drawable.ic_alert);
                                            tick5.setVisibility(View.VISIBLE);
                                            progress5.setVisibility(View.GONE);
                                        }

                                        @Override
                                        public void onNetworkFailure(Exception exception) {
                                            Log.e(TAG, "Failed to send time zone value");
                                            handler.removeCallbacks(getNodeStatusTask);
                                            tick5.setImageResource(R.drawable.ic_alert);
                                            tick5.setVisibility(View.VISIBLE);
                                            progress5.setVisibility(View.GONE);
                                        }
                                    });
                                } else {
                                    Log.e(TAG, "Time zone service is not available");
                                    tick5.setImageResource(R.drawable.ic_checkbox_on);
                                    tick5.setVisibility(View.VISIBLE);
                                    progress5.setVisibility(View.GONE);
                                    tvProvSuccess.setVisibility(View.VISIBLE);
                                    handler.removeCallbacks(nodeStatusReqFailed);
                                }
                            } else {
                                handler.removeCallbacks(getNodeStatusTask);
                                handler.postDelayed(getNodeStatusTask, 2000);
                            }
                        }
                    });
                }

                @Override
                public void onResponseFailure(Exception exception) {
                    handler.removeCallbacks(getNodeStatusTask);
                    handler.postDelayed(getNodeStatusTask, 2000);
                }

                @Override
                public void onNetworkFailure(Exception exception) {
                    handler.removeCallbacks(getNodeStatusTask);
                    handler.postDelayed(getNodeStatusTask, 2000);
                }
            });
        }
    };

    private Runnable nodeStatusReqFailed = new Runnable() {

        @Override
        public void run() {

            if (isFinishing()) {
                return;
            }
            Log.d(TAG, "Stop node status polling. Timeout");
            handler.removeCallbacks(getNodeStatusTask);
            tick5.setImageResource(R.drawable.ic_alert);
            tick5.setVisibility(View.VISIBLE);
            progress5.setVisibility(View.GONE);
        }
    };

    private Runnable wifiConnectTimeoutTask = new Runnable() {
        @Override
        public void run() {
            Log.e(TAG, "WiFi connection confirmation timed out");
            runOnUiThread(() -> {
                tick2.setImageResource(R.drawable.ic_error);
                tick2.setVisibility(View.VISIBLE);
                progress2.setVisibility(View.GONE);
                tvErrAtStep2.setVisibility(View.VISIBLE);
                tvErrAtStep2.setText(R.string.error_wifi_connection_failed);
                tvProvError.setVisibility(View.VISIBLE);
                hideLoading();
            });
        }
    };

    private void showLoading() {

        btnOk.setEnabled(false);
        btnOk.setAlpha(0.5f);
    }

    public void hideLoading() {

        btnOk.setEnabled(true);
        btnOk.setAlpha(1f);
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

    /* Helper method to print bytes in hex */
    private String bytesToHex(byte[] bytes, int offset, int length) {
        StringBuilder result = new StringBuilder();
        for (int i = offset; i < offset + length && i < bytes.length; i++) {
            result.append(String.format("%02x", bytes[i]));
        }
        return result.toString();
    }
}
