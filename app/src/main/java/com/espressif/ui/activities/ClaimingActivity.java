package com.espressif.ui.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provisioning.DeviceConnectionEvent;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.listeners.ResponseListener;
import com.espressif.rainmaker.R;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

import rmaker_claim.EspRmakerClaim;

public class ClaimingActivity extends AppCompatActivity {

    private static final String TAG = ClaimingActivity.class.getSimpleName();

    private TextView tvTitle, tvBack, tvCancel;

    private CardView btnOk;
    private TextView txtOkBtn;

    private TextView tvClaimProgress, tvClaimError, tvClaimFailure, tvPleaseWait;
    private ImageView ivClaimingProgress;

    private int dataCount = 0;
    private String certificateData = "";
    private StringBuilder csrData = new StringBuilder();
    private boolean isClaimingAborted = false;

    private Handler handler;
    private ApiManager apiManager;
    private ESPProvisionManager provisionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_claiming);

        handler = new Handler();
        apiManager = ApiManager.getInstance(getApplicationContext());
        provisionManager = ESPProvisionManager.getInstance(getApplicationContext());
        initViews();
        EventBus.getDefault().register(this);
        displayClaimingProgress();
        handler.postDelayed(timeoutTask, 10000);
        sendClaimStartRequest();
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

    private View.OnClickListener okBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            if (provisionManager.getEspDevice() != null) {
                provisionManager.getEspDevice().disconnectDevice();
            }
            finish();
        }
    };

    private View.OnClickListener cancelBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            sendClaimAbortRequest();

            handler.postDelayed(new Runnable() {

                @Override
                public void run() {

                    if (provisionManager.getEspDevice() != null) {
                        provisionManager.getEspDevice().disconnectDevice();
                    }
                    finish();
                }
            }, 2000);
        }
    };

    private void initViews() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvCancel = findViewById(R.id.btn_cancel);

        tvTitle.setText(R.string.title_activity_claiming);
        tvBack.setVisibility(View.GONE);
        tvCancel.setVisibility(View.GONE);
        tvCancel.setOnClickListener(cancelBtnClickListener);

        tvClaimProgress = findViewById(R.id.tv_claiming_progress);
        tvClaimError = findViewById(R.id.tv_claiming_error);
        tvClaimFailure = findViewById(R.id.tv_claiming_failure);
        ivClaimingProgress = findViewById(R.id.iv_claiming);
        tvPleaseWait = findViewById(R.id.tv_please_wait);

        btnOk = findViewById(R.id.btn_ok);
        txtOkBtn = findViewById(R.id.text_btn);
        btnOk.findViewById(R.id.iv_arrow).setVisibility(View.GONE);
        btnOk.setVisibility(View.GONE);

        txtOkBtn.setText(R.string.btn_ok);
        btnOk.setOnClickListener(okBtnClickListener);
    }

    private void sendClaimStartRequest() {

        Log.d(TAG, "Claim Start Request");

        EspRmakerClaim.PayloadBuf payloadBuf = EspRmakerClaim.PayloadBuf.newBuilder()
                .build();

        EspRmakerClaim.RMakerClaimMsgType msgType = EspRmakerClaim.RMakerClaimMsgType.TypeCmdClaimStart;
        EspRmakerClaim.RMakerClaimPayload payload = EspRmakerClaim.RMakerClaimPayload.newBuilder()
                .setMsg(msgType)
                .setCmdPayload(payloadBuf)
                .build();

        provisionManager.getEspDevice().sendDataToCustomEndPoint(AppConstants.HANDLER_RM_CLAIM, payload.toByteArray(), new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Log.d(TAG, "Successfully sent claiming start command");
                processClaimingStartResponse(returnData);
            }

            @Override
            public void onFailure(Exception e) {

                Log.e(TAG, "Failed to start claiming");
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        tvClaimProgress.setText(R.string.error_claiming_progress);
                        tvClaimError.setText(R.string.error_claiming_start);
                        displayError();
                    }
                });
            }
        });
    }

    private void processClaimingStartResponse(byte[] responseData) {

        try {
            EspRmakerClaim.RMakerClaimPayload payload = EspRmakerClaim.RMakerClaimPayload.parseFrom(responseData);
            EspRmakerClaim.RespPayload response = payload.getRespPayload();

            if (response.getStatus() == EspRmakerClaim.RMakerClaimStatus.Success) {

                sendDeviceInfoToCloud(response.getBuf().getPayload().toStringUtf8());

            } else {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        tvClaimProgress.setText(R.string.error_claiming_progress);
                        tvClaimError.setText(R.string.error_claiming_start);
                        displayError();
                    }
                });
            }

        } catch (InvalidProtocolBufferException e) {

            e.printStackTrace();
            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    tvClaimProgress.setText(R.string.error_claiming_progress);
                    tvClaimError.setText(R.string.error_claiming_start);
                    displayError();
                }
            });
        }
    }

    private void sendClaimInitRequest(String data) {

        Log.e(TAG, "Claim Init Request");
        ByteString byteString = ByteString.copyFromUtf8(data);
        EspRmakerClaim.PayloadBuf payloadBuf = EspRmakerClaim.PayloadBuf.newBuilder()
                .setOffset(0)
                .setTotalLen(byteString.size())
                .setPayload(byteString)
                .build();

        EspRmakerClaim.RMakerClaimMsgType msgType = EspRmakerClaim.RMakerClaimMsgType.TypeCmdClaimInit;
        EspRmakerClaim.RMakerClaimPayload payload = EspRmakerClaim.RMakerClaimPayload.newBuilder()
                .setMsg(msgType)
                .setCmdPayload(payloadBuf)
                .build();

        provisionManager.getEspDevice().sendDataToCustomEndPoint(AppConstants.HANDLER_RM_CLAIM, payload.toByteArray(), new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Log.e(TAG, "Successfully sent claiming init command");
                getCSRFromDevice(returnData);
            }

            @Override
            public void onFailure(Exception e) {

                Log.e(TAG, "Send config data : Error : " + e.getMessage());
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        tvClaimProgress.setText(R.string.error_claiming_progress);
                        tvClaimError.setText(R.string.error_claiming_init);
                        displayError();
                    }
                });
            }
        });
    }

    private void getCSRFromDevice(byte[] responseData) {

        try {
            EspRmakerClaim.RMakerClaimPayload payload = EspRmakerClaim.RMakerClaimPayload.parseFrom(responseData);
            EspRmakerClaim.RespPayload response = payload.getRespPayload();

            if (response.getStatus() == EspRmakerClaim.RMakerClaimStatus.Success) {

                String data = response.getBuf().getPayload().toStringUtf8();
                int offset = response.getBuf().getOffset();
                int totalLen = response.getBuf().getTotalLen();
                Log.d(TAG, "Offset : " + offset + " and total length : " + totalLen);

                if (offset == 0) {
                    dataCount = data.length();
                    csrData = new StringBuilder();
                }
                csrData.append(data);
                Log.d(TAG, "Received CSR Length till now : " + csrData.length());
                Log.d(TAG, "dataCount : " + dataCount);

                if (csrData.length() >= totalLen) {
                    sendCSRToAPI(csrData.toString());
                } else {
                    requestCSRData();
                }
            } else {

                Log.d(TAG, "Claiming init status : " + response.getStatus());
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        tvClaimProgress.setText(R.string.error_claiming_progress);
                        tvClaimError.setText(R.string.error_claiming_init);
                        displayError();
                    }
                });
            }

        } catch (InvalidProtocolBufferException e) {

            e.printStackTrace();
            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    tvClaimProgress.setText(R.string.error_claiming_progress);
                    tvClaimError.setText(R.string.error_claiming_init);
                    displayError();
                }
            });
        }
    }

    private void requestCSRData() {

        if (isClaimingAborted) {
            return;
        }
        EspRmakerClaim.PayloadBuf payloadBuf = EspRmakerClaim.PayloadBuf.newBuilder()
                .build();

        EspRmakerClaim.RMakerClaimMsgType msgType = EspRmakerClaim.RMakerClaimMsgType.TypeCmdClaimInit;
        EspRmakerClaim.RMakerClaimPayload payload = EspRmakerClaim.RMakerClaimPayload.newBuilder()
                .setMsg(msgType)
                .setCmdPayload(payloadBuf)
                .build();

        provisionManager.getEspDevice().sendDataToCustomEndPoint(AppConstants.HANDLER_RM_CLAIM, payload.toByteArray(), new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                getCSRFromDevice(returnData);
            }

            @Override
            public void onFailure(Exception e) {

                Log.e(TAG, "Error : " + e.getMessage());
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        tvClaimProgress.setText(R.string.error_claiming_progress);
                        tvClaimError.setText(R.string.error_claiming_init);
                        displayError();
                    }
                });
            }
        });
    }

    private void sendCertificateToDevice(final int offset) {

        if (isClaimingAborted) {
            return;
        }
        Log.d(TAG, "Send certificate to device, offset : " + offset);
        String data = "";

        try {
            int totalLen = certificateData.length();
            int len = offset + dataCount;

            Log.d(TAG, "Length : " + len + " and total len : " + totalLen);

            if (len > totalLen) {
                Log.d(TAG, "Actual end index : " + totalLen);
                data = certificateData.substring(offset, totalLen);
            } else {
                Log.d(TAG, "Actual end index : " + len);
                data = certificateData.substring(offset, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        ByteString byteString = ByteString.copyFromUtf8(data);
        EspRmakerClaim.PayloadBuf payloadBuf = EspRmakerClaim.PayloadBuf.newBuilder()
                .setOffset(offset)
                .setTotalLen(certificateData.length())
                .setPayload(byteString)
                .build();

        EspRmakerClaim.RMakerClaimMsgType msgType = EspRmakerClaim.RMakerClaimMsgType.TypeCmdClaimVerify;
        EspRmakerClaim.RMakerClaimPayload payload = EspRmakerClaim.RMakerClaimPayload.newBuilder()
                .setMsg(msgType)
                .setCmdPayload(payloadBuf)
                .build();

        provisionManager.getEspDevice().sendDataToCustomEndPoint(AppConstants.HANDLER_RM_CLAIM, payload.toByteArray(), new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                if ((offset + dataCount) >= certificateData.length()) {

                    Log.e(TAG, "Certificate Sent to device successfully.");
                    ArrayList<String> deviceCaps = provisionManager.getEspDevice().getDeviceCapabilities();

                    if (deviceCaps.contains("wifi_scan")) {
                        goToWiFiScanActivity();
                    } else {
                        goToWiFiConfigActivity();
                    }

                } else {
                    int newOffset = offset + dataCount;
                    sendCertificateToDevice(newOffset);
                }
            }

            @Override
            public void onFailure(Exception e) {

                Log.e(TAG, "Error : " + e.getMessage());
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        tvClaimProgress.setText(R.string.error_claiming_progress);
                        tvClaimError.setText(R.string.error_claiming_verify);
                        displayError();
                    }
                });
            }
        });
    }

    private void sendClaimAbortRequest() {

        Log.d(TAG, "Claim Abort Request");
        isClaimingAborted = true;

        EspRmakerClaim.PayloadBuf payloadBuf = EspRmakerClaim.PayloadBuf.newBuilder()
                .build();

        EspRmakerClaim.RMakerClaimMsgType msgType = EspRmakerClaim.RMakerClaimMsgType.TypeCmdClaimAbort;
        EspRmakerClaim.RMakerClaimPayload payload = EspRmakerClaim.RMakerClaimPayload.newBuilder()
                .setMsg(msgType)
                .setCmdPayload(payloadBuf)
                .build();

        provisionManager.getEspDevice().sendDataToCustomEndPoint(AppConstants.HANDLER_RM_CLAIM, payload.toByteArray(), new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Log.d(TAG, "Successfully sent claiming abort command");
            }

            @Override
            public void onFailure(Exception e) {

                Log.e(TAG, "Failed to abort claiming");
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        tvClaimProgress.setText(R.string.error_claiming_progress);
                        displayError();
                    }
                });
            }
        });
    }

    private void sendDeviceInfoToCloud(String data) {

        if (isClaimingAborted) {
            return;
        }
        Gson gson = new Gson();
        JsonObject body = gson.fromJson(data, JsonObject.class);
        apiManager.initiateClaim(body, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                if (data != null) {
                    String res = data.getString("claim_initiate_response");
                    Log.d(TAG, "API Response : " + res);
                    sendClaimInitRequest(res);
                }
            }

            @Override
            public void onFailure(Exception e) {

                final String errMsg = e.getMessage();
                Log.e(TAG, "Failed to start claiming. Error : " + errMsg);
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        sendClaimAbortRequest();
                        tvClaimProgress.setText(R.string.error_claiming_progress);
                        tvClaimError.setText(errMsg);
                        displayError();
                    }
                });
            }
        });
    }

    private void sendCSRToAPI(String data) {

        if (isClaimingAborted) {
            return;
        }
        Gson gson = new Gson();
        JsonObject body = gson.fromJson(data, JsonObject.class);
        apiManager.verifyClaiming(body, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                if (data != null) {
                    certificateData = data.getString("claim_verify_response");
                    Log.e(TAG, "Data send to cloud for verify");
                    sendCertificateToDevice(0);
                }
            }

            @Override
            public void onFailure(Exception e) {

                final String errMsg = e.getMessage();
                Log.e(TAG, "Failed to verify claiming. Error : " + errMsg);
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        sendClaimAbortRequest();
                        tvClaimProgress.setText(R.string.error_claiming_progress);
                        tvClaimError.setText(errMsg);
                        displayError();
                    }
                });
            }
        });
    }

    private void goToWiFiScanActivity() {
        finish();
        Intent wifiListIntent = new Intent(getApplicationContext(), WiFiScanActivity.class);
        startActivity(wifiListIntent);
    }

    private void goToWiFiConfigActivity() {
        finish();
        Intent wifiConfigIntent = new Intent(getApplicationContext(), WiFiConfigActivity.class);
        startActivity(wifiConfigIntent);
    }

    private void displayClaimingProgress() {

        RotateAnimation rotate = new RotateAnimation(0, 180, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(3000);
        rotate.setRepeatCount(Animation.INFINITE);
        rotate.setInterpolator(new LinearInterpolator());
        ivClaimingProgress.startAnimation(rotate);
    }

    private void stopClaimingProgress() {
        ivClaimingProgress.clearAnimation();
    }

    private void displayError() {

        Log.e(TAG, "Claiming error occurred");
        stopClaimingProgress();
        tvPleaseWait.setVisibility(View.GONE);
        btnOk.setVisibility(View.VISIBLE);
        tvClaimError.setVisibility(View.VISIBLE);
        tvClaimFailure.setVisibility(View.VISIBLE);
    }

    private Runnable timeoutTask = new Runnable() {

        @Override
        public void run() {
            tvCancel.setVisibility(View.VISIBLE);
        }
    };

    private void showAlertForDeviceDisconnected() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
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
