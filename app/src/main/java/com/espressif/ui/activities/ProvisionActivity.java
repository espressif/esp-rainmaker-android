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
import android.widget.EditText;
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
import com.espressif.local_control.EspLocalDevice;
import com.espressif.matter.ControllerLoginActivity;
import com.espressif.matter.GroupSelectionActivity;
import com.espressif.provisioning.DeviceConnectionEvent;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPDevice;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.listeners.ProvisionListener;
import com.espressif.provisioning.listeners.ResponseListener;
import com.espressif.rainmaker.R;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.OnNetworkDevice;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.Service;
import com.espressif.ui.models.UpdateEvent;
import com.espressif.utils.InAppReviewManager;
import com.espressif.utils.NodeUtils;
import com.espressif.utils.ParamUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
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
import rmaker_ch_resp.EspRmakerChalResp;
import rmaker_ch_resp.EspRmakerChalResp.CmdCRPayload;
import rmaker_ch_resp.EspRmakerChalResp.RMakerChRespMsgType;
import rmaker_ch_resp.EspRmakerChalResp.RMakerChRespPayload;
import rmaker_ch_resp.EspRmakerChalResp.RMakerChRespStatus;
import rmaker_ch_resp.EspRmakerChalResp.RespCRPayload;

import rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.CmdGetData;
import rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.PayloadBuf;
import rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlDataType;
import rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlMsgType;
import rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlPayload;
import rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlStatus;
import rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RespGetData;

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
    private String errorMessage;

    private ApiManager apiManager;
    private Handler handler;
    private ESPProvisionManager provisionManager;
    private boolean isProvisioningCompleted = false;
    private boolean isChallengeResponseFlow = false;
    private boolean isOnNetworkFlow = false;
    private OnNetworkDevice onNetworkDevice;
    private EspLocalDevice localDevice;

    private boolean isBleLocalCtrlFlow = false;
    private String bleLocalCtrlDeviceName = null;
    private String bleLocalCtrlPop = null;
    private Handler wifiConnectHandler = new Handler();
    private EspApplication espApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provision);
        espApp = (EspApplication) getApplicationContext();

        Intent intent = getIntent();
        ssidValue = intent.getStringExtra(AppConstants.KEY_SSID);
        passphraseValue = intent.getStringExtra(AppConstants.KEY_PASSWORD);
        dataset = intent.getStringExtra(AppConstants.KEY_THREAD_DATASET);
        isOnNetworkFlow = intent.getBooleanExtra(AppConstants.KEY_IS_ON_NETWORK_FLOW, false);
        if (isOnNetworkFlow) {
            onNetworkDevice = (com.espressif.ui.models.OnNetworkDevice) intent.getSerializableExtra(AppConstants.KEY_ON_NETWORK_DEVICE);
            if (onNetworkDevice != null) {
                // Create EspLocalDevice for local network communication
                localDevice = new com.espressif.local_control.EspLocalDevice(
                        onNetworkDevice.getNodeId(),
                        onNetworkDevice.getIpAddress(),
                        onNetworkDevice.getPort()
                );
                localDevice.setSecurityType(onNetworkDevice.getSecVersion());
                String pop = intent.getStringExtra(AppConstants.KEY_POP);
                if (!TextUtils.isEmpty(pop)) {
                    localDevice.setPop(pop);
                }
                isChallengeResponseFlow = true; // On-network flow always uses challenge-response
            }
        }
        isBleLocalCtrlFlow = intent.getBooleanExtra(AppConstants.KEY_BLE_LOCAL_CTRL, false);
        bleLocalCtrlDeviceName = intent.getStringExtra(AppConstants.KEY_DEVICE_NAME);
        bleLocalCtrlPop = intent.getStringExtra(AppConstants.KEY_PROOF_OF_POSSESSION);
        Log.d(TAG, "BLE Local Ctrl Flow: " + isBleLocalCtrlFlow);
        Log.d(TAG, "From Intent - deviceName: " + bleLocalCtrlDeviceName + ", pop: " + bleLocalCtrlPop);

        provisionManager = ESPProvisionManager.getInstance(getApplicationContext());

        /* Fallback: get PoP from ESPDevice if not in intent */
        if (TextUtils.isEmpty(bleLocalCtrlPop) && provisionManager.getEspDevice() != null) {
            bleLocalCtrlPop = provisionManager.getEspDevice().getProofOfPossession();
            Log.d(TAG, "Fallback - Got PoP from ESPDevice: " + bleLocalCtrlPop);
        }
        Log.d(TAG, "Final values - deviceName: " + bleLocalCtrlDeviceName + ", pop: " + bleLocalCtrlPop);

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
        if (provisionManager != null && provisionManager.getEspDevice() != null) {
            provisionManager.getEspDevice().disconnectDevice();
        }
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

            EspNode espNode = espApp.nodeMap.get(receivedNodeId);
            if (espNode != null && espNode.isOnline()) {
                Service rmakerCtrlService = NodeUtils.Companion.getService(espNode, AppConstants.SERVICE_TYPE_RMAKER_CONTROLLER);
                Service ctrlService = NodeUtils.Companion.getService(espNode, AppConstants.SERVICE_TYPE_MATTER_CONTROLLER);
                Service ctrlSetupService = NodeUtils.Companion.getService(espNode, AppConstants.SERVICE_TYPE_MATTER_CONTROLLER_SETUP);

                boolean isRmakerServiceAvailable = rmakerCtrlService != null;
                boolean isCtrlServiceAvailable = ctrlService != null;
                boolean isCtrlSetupServiceAvailable = ctrlSetupService != null;

                boolean hasGroupIdParam = hasGroupIdParam(rmakerCtrlService)
                        || hasGroupIdParam(ctrlService)
                        || hasGroupIdParam(ctrlSetupService);

                if (hasGroupIdParam) {
                    Intent intent = new Intent(ProvisionActivity.this, GroupSelectionActivity.class);
                    startPostProvisioningFlow(intent, receivedNodeId, isCtrlServiceAvailable, isCtrlSetupServiceAvailable, isRmakerServiceAvailable);
                } else if (isRmakerServiceAvailable || isCtrlServiceAvailable) {
                    Intent intent = new Intent(ProvisionActivity.this, ControllerLoginActivity.class);
                    startPostProvisioningFlow(intent, receivedNodeId, isCtrlServiceAvailable, isCtrlSetupServiceAvailable, isRmakerServiceAvailable);
                }
            }

            if (provisionManager != null && provisionManager.getEspDevice() != null) {
                provisionManager.getEspDevice().disconnectDevice();
            }
            finish();
        }
    };

    private void startPostProvisioningFlow(Intent intent, String provisionedNodeId, boolean isCtrlServiceAvailable, boolean isCtrlSetupServiceAvailable, boolean isRmakerServiceAvailable) {
        intent.putExtra(AppConstants.KEY_NODE_ID, provisionedNodeId);
        intent.putExtra(AppConstants.KEY_IS_CTRL_SERVICE, isCtrlServiceAvailable);
        intent.putExtra(AppConstants.KEY_IS_CTRL_SETUP_SERVICE, isCtrlSetupServiceAvailable);
        intent.putExtra(AppConstants.KEY_IS_RMAKER_CONTROLLER, isRmakerServiceAvailable);
        startActivity(intent);
    }

    private boolean hasGroupIdParam(Service service) {
        if (service == null) return false;
        return ParamUtils.Companion.isParamAvailableInList(service.getParams(), AppConstants.PARAM_TYPE_RMAKER_GROUP_ID)
                || ParamUtils.Companion.isParamAvailableInList(service.getParams(), AppConstants.PARAM_TYPE_GROUP_ID);
    }

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
            // For on-network flow, also hide WiFi provisioning steps
            if (isOnNetworkFlow) {
                View step2View = findViewById(R.id.layout_confirming_wifi_connection);
                if (step2View != null) {
                    step2View.setVisibility(View.GONE);
                }
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
            // For challenge-response flow, skip WiFi provisioning and go directly to step 5
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

        // Track device addition for in-app review
        InAppReviewManager.Companion.getInstance(ProvisionActivity.this)
                .trackDeviceAddition(ProvisionActivity.this);

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
                            boolean isDeviceConnected = true;

                            switch (failureReason) {

                                case AUTH_FAILED:
                                    tvErrAtStep2.setText(R.string.error_authentication_failed);
                                    errorMessage = getString(R.string.error_authentication_failed);
                                    displayFailureAtStep2();
                                    break;

                                case NETWORK_NOT_FOUND:
                                    tvErrAtStep2.setText(R.string.error_network_not_found);
                                    errorMessage = getString(R.string.error_network_not_found);
                                    displayFailureAtStep2();
                                    break;

                                case DEVICE_DISCONNECTED:
                                    doStep3(false);
                                    isDeviceConnected = false;
                                    break;

                                case UNKNOWN:
                                    tvErrAtStep2.setText(R.string.error_prov_step_3);
                                    errorMessage = getString(R.string.error_prov_step_3);
                                    displayFailureAtStep2();
                                    break;
                            }

                            if (isDeviceConnected) {
                                sendWifiResetCommand();
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

        if (isOnNetworkFlow && localDevice != null) {
            verifyNodeAssociationOnNetwork();
            return;
        }

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

                    RMakerChRespPayload payload = RMakerChRespPayload.newBuilder()
                            .setMsg(RMakerChRespMsgType.TypeCmdChallengeResponse)
                            .setStatus(RMakerChRespStatus.Success)
                            .setCmdChallengeResponsePayload(cmdPayload)
                            .build();

                    provisionManager.getEspDevice().sendDataToCustomEndPoint(AppConstants.HANDLER_RM_CH_RESP, payload.toByteArray(), new ResponseListener() {
                        @Override
                        public void onSuccess(byte[] returnData) {
                            if (returnData != null) {
                                try {
                                    RMakerChRespPayload response = RMakerChRespPayload.parseFrom(returnData);
                                    if (response.getStatus() == RMakerChRespStatus.Success) {
                                        RespCRPayload respPayload = response.getRespChallengeResponsePayload();
                                        ByteString signedChallenge = respPayload.getPayload();
                                        String nodeId = respPayload.getNodeId();
                                        receivedNodeId = nodeId;

                                        /* Call verify mapping API */
                                        byte[] bytes = signedChallenge.toByteArray();

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
                                                    if (isBleLocalCtrlFlow) {
                                                        /* BLE local control flow - skip Wi-Fi provisioning */
                                                        startBleLocalCtrlFlow();
                                                    } else {
                                                        provision();
                                                    }
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

    private void verifyNodeAssociationOnNetwork() {
        Log.d(TAG, "Verifying node association on local network");

        apiManager.initiateMapping(new ApiResponseListener() {
            @Override
            public void onSuccess(Bundle data) {
                try {
                    String jsonResponse = data.getString(AppConstants.KEY_RESPONSE);
                    JSONObject jsonObject = new JSONObject(jsonResponse);
                    String challenge = jsonObject.optString(AppConstants.KEY_CHALLENGE);
                    String requestId = jsonObject.optString(AppConstants.KEY_REQUEST_ID);
                    Log.d(TAG, "Got challenge: " + challenge + ", request_id: " + requestId);

                    /* Send challenge to device using proto via local network */
                    byte[] challengeBytes = challenge.getBytes(StandardCharsets.UTF_8);

                    CmdCRPayload cmdPayload = CmdCRPayload.newBuilder()
                            .setPayload(ByteString.copyFrom(challengeBytes))
                            .build();

                    RMakerChRespPayload payload = RMakerChRespPayload.newBuilder()
                            .setMsg(RMakerChRespMsgType.TypeCmdChallengeResponse)
                            .setStatus(RMakerChRespStatus.Success)
                            .setCmdChallengeResponsePayload(cmdPayload)
                            .build();

                    // Use local device to send data via local network
                    String endpoint = onNetworkDevice != null && !TextUtils.isEmpty(onNetworkDevice.getChRespEndpoint())
                            ? onNetworkDevice.getChRespEndpoint() : "ch_resp";

                    localDevice.sendData(endpoint, payload.toByteArray(), new com.espressif.provisioning.listeners.ResponseListener() {
                        @Override
                        public void onSuccess(byte[] returnData) {
                            if (returnData != null) {
                                try {
                                    RMakerChRespPayload response = RMakerChRespPayload.parseFrom(returnData);
                                    if (response.getStatus() == RMakerChRespStatus.Success) {
                                        RespCRPayload respPayload = response.getRespChallengeResponsePayload();
                                        ByteString signedChallenge = respPayload.getPayload();
                                        String nodeId = respPayload.getNodeId();
                                        receivedNodeId = nodeId;

                                        /* Call verify mapping API */
                                        byte[] bytes = signedChallenge.toByteArray();

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
                                                    // For on-network flow, challenge-response is done
                                                    // Mark step 1 complete
                                                    tick1.setImageResource(R.drawable.ic_checkbox_on);
                                                    tick1.setVisibility(View.VISIBLE);
                                                    progress1.setVisibility(View.GONE);

                                                    // Enable OK button after first step is done
                                                    hideLoading();

                                                    // Send disable challenge-response command before proceeding
                                                    sendDisableChallengeResponse();
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

    private void sendDisableChallengeResponse() {
        Log.d(TAG, "Sending disable challenge-response command");

        EspRmakerChalResp.CmdDisableChalRespPayload disablePayload = EspRmakerChalResp.CmdDisableChalRespPayload.newBuilder().build();

        RMakerChRespPayload payload = RMakerChRespPayload.newBuilder()
                .setMsg(RMakerChRespMsgType.TypeCmdDisableChalResp)
                .setStatus(RMakerChRespStatus.Success)
                .setCmdDisableChalRespPayload(disablePayload)
                .build();

        // Use local device to send data via local network
        String endpoint = onNetworkDevice != null && !TextUtils.isEmpty(onNetworkDevice.getChRespEndpoint())
                ? onNetworkDevice.getChRespEndpoint() : "ch_resp";

        localDevice.sendData(endpoint, payload.toByteArray(), new com.espressif.provisioning.listeners.ResponseListener() {
            @Override
            public void onSuccess(byte[] returnData) {
                if (returnData != null) {
                    try {
                        RMakerChRespPayload response = RMakerChRespPayload.parseFrom(returnData);
                        if (response.getStatus() == RMakerChRespStatus.Success) {
                            Log.d(TAG, "Challenge-response disabled successfully");
                            runOnUiThread(() -> {
                                // Mark step 4 as complete (node association confirmed)
                                tick4.setImageResource(R.drawable.ic_checkbox_on);
                                tick4.setVisibility(View.VISIBLE);
                                progress4.setVisibility(View.GONE);

                                // Now proceed to step 5 (setting up node)
                                doStep5();
                            });
                        } else {
                            Log.e(TAG, "Failed to disable challenge-response, status: " + response.getStatus());
                            // Still proceed to step 5 even if disable fails
                            runOnUiThread(() -> {
                                tick4.setImageResource(R.drawable.ic_checkbox_on);
                                tick4.setVisibility(View.VISIBLE);
                                progress4.setVisibility(View.GONE);
                                doStep5();
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing disable response", e);
                        // Still proceed to step 5 even if parsing fails
                        runOnUiThread(() -> {
                            tick4.setImageResource(R.drawable.ic_checkbox_on);
                            tick4.setVisibility(View.VISIBLE);
                            progress4.setVisibility(View.GONE);
                            doStep5();
                        });
                    }
                } else {
                    Log.e(TAG, "Disable challenge-response returned null");
                    // Still proceed to step 5
                    runOnUiThread(() -> {
                        tick4.setImageResource(R.drawable.ic_checkbox_on);
                        tick4.setVisibility(View.VISIBLE);
                        progress4.setVisibility(View.GONE);
                        doStep5();
                    });
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Failed to send disable challenge-response command", e);
                // Still proceed to step 5 even if disable command fails
                runOnUiThread(() -> {
                    tick4.setImageResource(R.drawable.ic_checkbox_on);
                    tick4.setVisibility(View.VISIBLE);
                    progress4.setVisibility(View.GONE);
                    // Enable OK button after first step is done
                    hideLoading();
                    doStep5();
                });
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
        // For on-network challenge-response flow, secretKey is not needed
        String keyToUse = isOnNetworkFlow ? null : secretKey;
        apiManager.addNode(receivedNodeId, keyToUse, new ApiResponseListener() {

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
                            // For on-network flow, go directly to step 5 (setting up node)
                            // For regular flow, step 4 is already done, so go to step 5
                            if (isOnNetworkFlow) {
                                doStep5();
                            } else {
                                doStep4();
                            }
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
                            EspNode espNode = espApp.nodeMap.get(receivedNodeId);
                            if (espNode != null && espNode.isOnline()) {

                                // Send time zone to device.
                                ArrayList<Service> services = espNode.getServices();
                                boolean isTimeZoneServiceAvailable = false;
                                String paramName = "";
                                String timestampParamName = null;

                                for (int i = 0; i < services.size(); i++) {

                                    Service s = services.get(i);
                                    if (!TextUtils.isEmpty(s.getType()) && s.getType().equals(AppConstants.SERVICE_TYPE_TIME)) {

                                        ArrayList<Param> timeParams = s.getParams();
                                        String tzName = null;
                                        String tsName = null;
                                        for (int index = 0; index < timeParams.size(); index++) {
                                            Param p = timeParams.get(index);
                                            if (AppConstants.PARAM_TYPE_TZ.equals(p.getParamType())) {
                                                tzName = p.getName();
                                            } else if (AppConstants.PARAM_TYPE_TIMESTAMP.equals(p.getParamType())) {
                                                tsName = p.getName();
                                            }
                                        }
                                        if (!TextUtils.isEmpty(tzName)) {
                                            isTimeZoneServiceAvailable = true;
                                            paramName = tzName;
                                            timestampParamName = tsName;
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
                                    if (!TextUtils.isEmpty(timestampParamName)) {
                                        long timestampSec = System.currentTimeMillis() / 1000L;
                                        jsonParam.addProperty(timestampParamName, timestampSec);
                                        Log.d(TAG, "Timestamp (s) : " + timestampSec);
                                    }
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

    /**
     * Send WiFi reset command to device when authentication failure error received in provisioning.
     * The resetWifiStatus method will check if session is established internally
     */
    private void sendWifiResetCommand() {
        ESPDevice espDevice = provisionManager.getEspDevice();
        if (espDevice != null) {
            espDevice.resetWifiStatus(new ResponseListener() {
                @Override
                public void onSuccess(byte[] returnData) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Success received for sending WiFi reset command");
                            showReenterPasswordAlert();
                        }
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Log error but don't block UI - reset is best effort
                            Log.e(TAG, "Failed to send WiFi reset command", e);
                            showResetPasswordFailedAlert("Failed to send WiFi reset command: " + e.getMessage());
                        }
                    });
                }
            });
        }
    }

    /**
     * Start BLE local control flow after challenge-response succeeds.
     * This flow:
     * 1. Gets config with timestamp, signs it, and reports to proxy/config
     * 2. Gets params with timestamp, signs it, and reports to proxy/initparams
     * 3. Updates node metadata with ble_local_ctrl object
     */
    private void startBleLocalCtrlFlow() {
        Log.d(TAG, "Starting BLE local control flow for node: " + receivedNodeId);
        tick1.setImageResource(R.drawable.ic_checkbox_on);
        tick1.setVisibility(View.VISIBLE);
        progress1.setVisibility(View.GONE);

        /* Step 1: Get config with timestamp and report to proxy */
        getConfigAndReportToProxy();
    }

    /**
     * Get config with timestamp and report to proxy/config
     * Device returns already-signed data, no need to use ch_resp
     */
    private void getConfigAndReportToProxy() {
        Log.d(TAG, "Getting config with timestamp...");
        tick2.setVisibility(View.GONE);
        progress2.setVisibility(View.VISIBLE);
        tvProvStep2.setText(R.string.getting_node_config);

        /* Get current timestamp */
        long timestamp = System.currentTimeMillis() / 1000;

        /* Get config with chunked transfer */
        getRawDataWithChunking(1, timestamp, new RawDataCallback() {
            @Override
            public void onSuccess(JSONObject deviceResponse) {
                /* Device returns: {"node_payload": {"data": {...}, "timestamp": ...}, "signature": "..."} */
                try {
                    Log.d(TAG, "Device config response: " + deviceResponse.toString());

                    /* Extract signature from TOP level (not inside node_payload) */
                    String signature = deviceResponse.optString("signature", "");
                    if (TextUtils.isEmpty(signature)) {
                        Log.e(TAG, "No signature in device response");
                        runOnUiThread(() -> getParamsAndReportToProxy(false));
                        return;
                    }

                    /* Extract node_payload object - this becomes the node_payload string for proxy */
                    JSONObject nodePayloadObj = deviceResponse.optJSONObject("node_payload");
                    if (nodePayloadObj == null) {
                        Log.e(TAG, "No node_payload in device response");
                        runOnUiThread(() -> getParamsAndReportToProxy(false));
                        return;
                    }

                    /* The node_payload itself is what we send to proxy as a string */
                    /* Note: JSONObject.toString() escapes forward slashes (/ -> \/), but the device
                     * signed the original JSON without escaped slashes, so we must unescape them */
                    String nodePayloadStr = nodePayloadObj.toString().replace("\\/", "/");

                    Log.d(TAG, "Reporting config to proxy - payload length: " + nodePayloadStr.length() + ", signature length: " + signature.length());

                    /* Report directly to proxy (device already signed it) */
                    reportToProxy(nodePayloadStr, signature, true, new ProxyReportCallback() {
                        @Override
                        public void onSuccess() {
                            /* Continue to get params - show success tick */
                            runOnUiThread(() -> getParamsAndReportToProxy(true));
                        }

                        @Override
                        public void onFailure(Exception e) {
                            Log.e(TAG, "Failed to report config to proxy: " + e.getMessage());
                            /* Continue but show failure */
                            runOnUiThread(() -> getParamsAndReportToProxy(false));
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error processing config: " + e.getMessage());
                    e.printStackTrace();
                    /* Continue but show failure */
                    runOnUiThread(() -> getParamsAndReportToProxy(false));
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Failed to get config: " + e.getMessage());
                e.printStackTrace();
                /* Continue but show failure */
                runOnUiThread(() -> getParamsAndReportToProxy(false));
            }
        });
    }

    /**
     * Get params with timestamp and report to proxy/initparams
     * Device returns already-signed data, no need to use ch_resp
     *
     * @param configSuccess true if previous config step succeeded, false otherwise
     */
    private void getParamsAndReportToProxy(boolean configSuccess) {
        Log.d(TAG, "Getting params with timestamp... (configSuccess: " + configSuccess + ")");
        /* Show success or failure for previous config step */
        tick2.setImageResource(configSuccess ? R.drawable.ic_checkbox_on : R.drawable.ic_alert);
        tick2.setVisibility(View.VISIBLE);
        progress2.setVisibility(View.GONE);
        tick3.setVisibility(View.GONE);
        progress3.setVisibility(View.VISIBLE);
        tvProvStep2.setText(R.string.getting_node_params);

        /* Get current timestamp */
        long timestamp = System.currentTimeMillis() / 1000;

        /* Get params with chunked transfer */
        getRawDataWithChunking(0, timestamp, new RawDataCallback() {
            @Override
            public void onSuccess(JSONObject deviceResponse) {
                /* Device returns: {"node_payload": {"data": {...}, "timestamp": ...}, "signature": "..."} */
                try {
                    Log.d(TAG, "Device params response: " + deviceResponse.toString());

                    /* Extract signature from TOP level (not inside node_payload) */
                    String signature = deviceResponse.optString("signature", "");
                    if (TextUtils.isEmpty(signature)) {
                        Log.e(TAG, "No signature in device response");
                        runOnUiThread(() -> updateNodeMetadataWithBleLocalCtrl(false));
                        return;
                    }

                    /* Extract node_payload object - this becomes the node_payload string for proxy */
                    JSONObject nodePayloadObj = deviceResponse.optJSONObject("node_payload");
                    if (nodePayloadObj == null) {
                        Log.e(TAG, "No node_payload in device response");
                        runOnUiThread(() -> updateNodeMetadataWithBleLocalCtrl(false));
                        return;
                    }

                    /* The node_payload itself is what we send to proxy as a string */
                    /* Note: JSONObject.toString() escapes forward slashes (/ -> \/), but the device
                     * signed the original JSON without escaped slashes, so we must unescape them */
                    String nodePayloadStr = nodePayloadObj.toString().replace("\\/", "/");

                    Log.d(TAG, "Reporting params to proxy - payload length: " + nodePayloadStr.length() + ", signature length: " + signature.length());

                    /* Report directly to proxy (device already signed it) */
                    reportToProxy(nodePayloadStr, signature, false, new ProxyReportCallback() {
                        @Override
                        public void onSuccess() {
                            /* Update metadata and complete - show success tick */
                            runOnUiThread(() -> updateNodeMetadataWithBleLocalCtrl(true));
                        }

                        @Override
                        public void onFailure(Exception e) {
                            Log.e(TAG, "Failed to report params to proxy: " + e.getMessage());
                            /* Update metadata anyway but show failure */
                            runOnUiThread(() -> updateNodeMetadataWithBleLocalCtrl(false));
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error processing params: " + e.getMessage());
                    e.printStackTrace();
                    /* Update metadata anyway but show failure */
                    runOnUiThread(() -> updateNodeMetadataWithBleLocalCtrl(false));
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Failed to get params: " + e.getMessage());
                e.printStackTrace();
                /* Update metadata anyway but show failure */
                runOnUiThread(() -> updateNodeMetadataWithBleLocalCtrl(false));
            }
        });
    }

    /**
     * Update node metadata with ble_local_ctrl object containing name and pop
     *
     * @param paramsSuccess true if previous params step succeeded, false otherwise
     */
    private void updateNodeMetadataWithBleLocalCtrl(boolean paramsSuccess) {
        Log.d(TAG, "Updating node metadata with BLE local control info... (paramsSuccess: " + paramsSuccess + ")");
        Log.d(TAG, "  bleLocalCtrlDeviceName = " + bleLocalCtrlDeviceName);
        Log.d(TAG, "  bleLocalCtrlPop = " + bleLocalCtrlPop);
        /* Show success or failure for previous params step */
        tick3.setImageResource(paramsSuccess ? R.drawable.ic_checkbox_on : R.drawable.ic_alert);
        tick3.setVisibility(View.VISIBLE);
        progress3.setVisibility(View.GONE);
        tick4.setVisibility(View.GONE);
        progress4.setVisibility(View.VISIBLE);

        try {
            JsonObject metadata = new JsonObject();
            JsonObject bleLocalCtrl = new JsonObject();
            bleLocalCtrl.addProperty("name", bleLocalCtrlDeviceName != null ? bleLocalCtrlDeviceName : "");
            bleLocalCtrl.addProperty("pop", bleLocalCtrlPop != null ? bleLocalCtrlPop : "");
            metadata.add("ble_local_ctrl", bleLocalCtrl);

            JsonObject body = new JsonObject();
            body.add(AppConstants.KEY_METADATA, metadata);

            Log.d(TAG, "Metadata body being sent: " + body.toString());

            apiManager.updateNodeMetadata(receivedNodeId, body, new ApiResponseListener() {
                @Override
                public void onSuccess(Bundle data) {
                    runOnUiThread(() -> {
                        Log.d(TAG, "Node metadata updated successfully");
                        tick4.setImageResource(R.drawable.ic_checkbox_on);
                        tick4.setVisibility(View.VISIBLE);
                        progress4.setVisibility(View.GONE);
                        tick5.setImageResource(R.drawable.ic_checkbox_on);
                        tick5.setVisibility(View.VISIBLE);
                        progress5.setVisibility(View.GONE);
                        tvProvSuccess.setVisibility(View.VISIBLE);
                        hideLoading();
                        isProvisioningCompleted = true;
                    });
                }

                @Override
                public void onResponseFailure(Exception e) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Failed to update node metadata: " + e.getMessage());
                        tick4.setImageResource(R.drawable.ic_alert);
                        tick4.setVisibility(View.VISIBLE);
                        progress4.setVisibility(View.GONE);
                        /* Still show success since main flow completed */
                        tick5.setImageResource(R.drawable.ic_checkbox_on);
                        tick5.setVisibility(View.VISIBLE);
                        progress5.setVisibility(View.GONE);
                        tvProvSuccess.setVisibility(View.VISIBLE);
                        hideLoading();
                        isProvisioningCompleted = true;
                    });
                }

                @Override
                public void onNetworkFailure(Exception e) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Network failure updating node metadata: " + e.getMessage());
                        tick4.setImageResource(R.drawable.ic_alert);
                        tick4.setVisibility(View.VISIBLE);
                        progress4.setVisibility(View.GONE);
                        /* Still show success since main flow completed */
                        tick5.setImageResource(R.drawable.ic_checkbox_on);
                        tick5.setVisibility(View.VISIBLE);
                        progress5.setVisibility(View.GONE);
                        tvProvSuccess.setVisibility(View.VISIBLE);
                        hideLoading();
                        isProvisioningCompleted = true;
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error updating node metadata: " + e.getMessage());
            e.printStackTrace();
            runOnUiThread(() -> {
                tick4.setImageResource(R.drawable.ic_alert);
                tick4.setVisibility(View.VISIBLE);
                progress4.setVisibility(View.GONE);
                tick5.setImageResource(R.drawable.ic_checkbox_on);
                tick5.setVisibility(View.VISIBLE);
                progress5.setVisibility(View.GONE);
                tvProvSuccess.setVisibility(View.VISIBLE);
                hideLoading();
                isProvisioningCompleted = true;
            });
        }
    }

    /**
     * Interface for raw data callback
     */
    private interface RawDataCallback {
        void onSuccess(JSONObject data);

        void onFailure(Exception e);
    }

    /**
     * Interface for proxy report callback
     */
    private interface ProxyReportCallback {
        void onSuccess();

        void onFailure(Exception e);
    }

    /**
     * Get raw data (params or config) with chunked transfer support
     *
     * @param dataType  0 for params, 1 for config
     * @param timestamp Optional timestamp for signed response (only sent on first request)
     * @param callback  Callback for success/failure
     */
    private void getRawDataWithChunking(int dataType, Long timestamp, RawDataCallback callback) {
        String endpointName = (dataType == 0) ? AppConstants.HANDLER_GET_PARAMS : AppConstants.HANDLER_GET_CONFIG;
        String dataName = (dataType == 0) ? "params" : "config";
        Log.d(TAG, "Getting " + dataName + " with chunked transfer...");

        getRawDataChunk(endpointName, dataType, 0, timestamp, new ArrayList<Byte>(), null, callback);
    }

    /**
     * Recursive method to get data chunks
     */
    private void getRawDataChunk(String endpointName, int dataType, int offset, Long timestamp,
                                 ArrayList<Byte> dataBuffer, Integer totalLen, RawDataCallback callback) {
        /* Use wrapper arrays for mutable values that need to be accessed from inner class */
        final int[] currentOffset = {offset};
        final Integer[] currentTotalLen = {totalLen};

        /* Create protobuf request */
        CmdGetData cmdGetData = CmdGetData.newBuilder()
                .setDataType(dataType == 0 ? RMakerLocalCtrlDataType.TypeParams : RMakerLocalCtrlDataType.TypeConfig)
                .setOffset(currentOffset[0])
                .setTimestamp(timestamp != null ? timestamp : 0)
                .setHasTimestamp(timestamp != null)
                .build();

        RMakerLocalCtrlPayload payload = RMakerLocalCtrlPayload.newBuilder()
                .setMsg(RMakerLocalCtrlMsgType.TypeCmdGetData)
                .setCmdGetData(cmdGetData)
                .build();

        /* Send request to device */
        provisionManager.getEspDevice().sendDataToCustomEndPoint(endpointName, payload.toByteArray(), new ResponseListener() {
            @Override
            public void onSuccess(byte[] returnData) {
                if (returnData != null) {
                    try {
                        /* Parse response */
                        RMakerLocalCtrlPayload response = RMakerLocalCtrlPayload.parseFrom(returnData);
                        if (response.getMsg() != RMakerLocalCtrlMsgType.TypeRespGetData) {
                            callback.onFailure(new Exception("Unexpected message type"));
                            return;
                        }

                        RespGetData respGetData = response.getRespGetData();
                        if (respGetData.getStatus() != RMakerLocalCtrlStatus.Success) {
                            callback.onFailure(new Exception("Device returned error status: " + respGetData.getStatus()));
                            return;
                        }

                        /* Get payload buffer */
                        PayloadBuf buf = respGetData.getBuf();
                        int respOffset = buf.getOffset();
                        byte[] payloadBytes = buf.getPayload().toByteArray();
                        int respTotalLen = buf.getTotalLen();

                        /* Validate offset */
                        if (respOffset != currentOffset[0]) {
                            callback.onFailure(new Exception("Offset mismatch: expected " + currentOffset[0] + ", got " + respOffset));
                            return;
                        }

                        /* Set total length from first response */
                        if (currentTotalLen[0] == null) {
                            currentTotalLen[0] = respTotalLen;
                            Log.d(TAG, "Total length: " + currentTotalLen[0] + " bytes");
                        }

                        /* Append payload to buffer */
                        for (byte b : payloadBytes) {
                            dataBuffer.add(b);
                        }
                        currentOffset[0] += payloadBytes.length;

                        Log.d(TAG, "Received fragment: offset=" + respOffset + ", len=" + payloadBytes.length + ", progress=" + currentOffset[0] + "/" + currentTotalLen[0]);

                        /* Check if we have all data */
                        if (currentOffset[0] >= currentTotalLen[0]) {
                            /* Convert buffer to byte array */
                            byte[] completeData = new byte[dataBuffer.size()];
                            for (int i = 0; i < dataBuffer.size(); i++) {
                                completeData[i] = dataBuffer.get(i);
                            }

                            /* Decode as UTF-8 string */
                            String dataStr = new String(completeData, StandardCharsets.UTF_8);

                            /* Parse as JSON */
                            try {
                                JSONObject dataJson = new JSONObject(dataStr);
                                callback.onSuccess(dataJson);
                            } catch (JSONException e) {
                                Log.e(TAG, "Failed to parse JSON: " + e.getMessage());
                                callback.onFailure(e);
                            }
                        } else {
                            /* Request next chunk (without timestamp) */
                            getRawDataChunk(endpointName, dataType, currentOffset[0], null, dataBuffer, currentTotalLen[0], callback);
                        }
                    } catch (InvalidProtocolBufferException e) {
                        Log.e(TAG, "Failed to parse protobuf response: " + e.getMessage());
                        e.printStackTrace();
                        callback.onFailure(e);
                    }
                } else {
                    callback.onFailure(new Exception("No response from device"));
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Failed to get data chunk: " + e.getMessage());
                e.printStackTrace();
                callback.onFailure(e);
            }
        });
    }

    /**
     * Report already-signed data to proxy API
     *
     * @param nodePayloadStr The node payload as a JSON string (data + timestamp)
     * @param signature      The signature from the device
     * @param isConfig       true for config (proxy/config), false for params (proxy/initparams)
     * @param callback       Callback for success/failure
     */
    private void reportToProxy(String nodePayloadStr, String signature, boolean isConfig, ProxyReportCallback callback) {
        Log.d(TAG, "Reporting to proxy - isConfig: " + isConfig);

        /* Create payload for proxy API */
        JsonObject proxyPayload = new JsonObject();
        proxyPayload.addProperty("node_payload", nodePayloadStr);
        proxyPayload.addProperty("signature", signature);

        Log.d(TAG, "Proxy payload: " + proxyPayload.toString());

        if (isConfig) {
            apiManager.reportProxyConfig(receivedNodeId, proxyPayload, new ApiResponseListener() {
                @Override
                public void onSuccess(Bundle data) {
                    Log.d(TAG, "Config reported to proxy successfully");
                    callback.onSuccess();
                }

                @Override
                public void onResponseFailure(Exception e) {
                    Log.e(TAG, "Failed to report config to proxy: " + e.getMessage());
                    callback.onFailure(e);
                }

                @Override
                public void onNetworkFailure(Exception e) {
                    Log.e(TAG, "Network failure reporting config to proxy: " + e.getMessage());
                    callback.onFailure(e);
                }
            });
        } else {
            apiManager.reportProxyInitParams(receivedNodeId, proxyPayload, new ApiResponseListener() {
                @Override
                public void onSuccess(Bundle data) {
                    Log.d(TAG, "Params reported to proxy successfully");
                    callback.onSuccess();
                }

                @Override
                public void onResponseFailure(Exception e) {
                    Log.e(TAG, "Failed to report params to proxy: " + e.getMessage());
                    callback.onFailure(e);
                }

                @Override
                public void onNetworkFailure(Exception e) {
                    Log.e(TAG, "Network failure reporting params to proxy: " + e.getMessage());
                    callback.onFailure(e);
                }
            });
        }
    }

    /**
     * <<<<<<< HEAD
     * Show alert dialog to re-enter WiFi password
     */
    private void showReenterPasswordAlert() {
        String title = getString(R.string.title_activity_provisioning);
        String wifiResetMsg = getString(R.string.wifi_reset_message);
        String alertMsg = wifiResetMsg;
        if (!TextUtils.isEmpty(errorMessage)) {
            alertMsg = errorMessage + " " + wifiResetMsg;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(alertMsg);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                showPasswordInputDialog();
            }
        });
        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    /**
     * Show password input dialog for re-provisioning
     */
    private void showPasswordInputDialog() {
        android.view.LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_wifi_network, null);
        final EditText etSsid = dialogView.findViewById(R.id.et_ssid);
        final EditText etPassword = dialogView.findViewById(R.id.et_password);
        final TextInputLayout passwordLayout = dialogView.findViewById(R.id.layout_password);

        // Hide SSID field since we already know it
        etSsid.setVisibility(View.GONE);

        // Set SSID as title
        String title = ssidValue != null ? ssidValue : getString(R.string.join_other_network);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setView(dialogView);
        alertDialogBuilder.setTitle(title);
        alertDialogBuilder.setPositiveButton(R.string.provision, null);
        alertDialogBuilder.setNegativeButton(R.string.btn_cancel, null);
        alertDialogBuilder.setCancelable(false);

        final AlertDialog alertDialog = alertDialogBuilder.create();

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                android.widget.Button buttonPositive = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
                buttonPositive.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String password = etPassword.getText().toString();

                        // Validate password if network is not open
                        // Note: We assume it's not open since authentication failed
                        if (TextUtils.isEmpty(password)) {
                            passwordLayout.setError(getString(R.string.error_password_empty));
                        } else {
                            alertDialog.dismiss();
                            // Update password and re-provision
                            passphraseValue = password;
                            // Reset UI elements
                            resetUIForRetry();
                            // Show loading and step 1 progress
                            showLoading();
                            progress1.setVisibility(View.VISIBLE);
                            // Skip step 1 (association/challenge-response already done)
                            // Go directly to provisioning with the new password
                            provision();
                        }
                    }
                });

                android.widget.Button buttonNegative = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_NEGATIVE);
                buttonNegative.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                    }
                });
            }
        });

        alertDialog.show();
    }

    /**
     * Reset UI state before retrying provisioning.
     * Only resets UI elements - does NOT trigger provisioning.
     * The caller is responsible for calling provision() after this.
     */
    private void resetUIForRetry() {
        // Hide error messages
        tvErrAtStep1.setVisibility(View.GONE);
        tvErrAtStep2.setVisibility(View.GONE);
        tvErrAtStep3.setVisibility(View.GONE);
        tvErrAtStep4.setVisibility(View.GONE);
        tvErrAtStep5.setVisibility(View.GONE);
        tvProvError.setVisibility(View.GONE);

        // Hide images
        tick1.setImageResource(R.drawable.ic_checkbox_unselected);
        tick2.setImageResource(R.drawable.ic_checkbox_unselected);
        tick3.setImageResource(R.drawable.ic_checkbox_unselected);
        tick4.setImageResource(R.drawable.ic_checkbox_unselected);
        tick5.setImageResource(R.drawable.ic_checkbox_unselected);

        // Hide progress indicators
        progress1.setVisibility(View.GONE);
        progress2.setVisibility(View.GONE);
        progress3.setVisibility(View.GONE);
        progress4.setVisibility(View.GONE);
        progress5.setVisibility(View.GONE);

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
        }
        
        // Reset error message
        errorMessage = null;
    }

    /**
     * Show alert dialog when reset command fails
     */
    private void showResetPasswordFailedAlert(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.title_activity_provisioning));
        builder.setMessage(message);
        builder.setCancelable(false);
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