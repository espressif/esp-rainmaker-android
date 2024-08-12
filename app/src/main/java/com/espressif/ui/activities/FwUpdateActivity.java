// Copyright 2023 Espressif Systems (Shanghai) PTE LTD
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
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.ui.models.EspOtaUpdate;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import pl.droidsonroids.gif.GifImageView;

public class FwUpdateActivity extends AppCompatActivity {

    private static final String TAG = FwUpdateActivity.class.getSimpleName();

    private final int TIMEOUT_IN_MILLIS = 3 * 60 * 1000; // 3 min timeout
    private final int POLLING_INTERVAL = 2000; // 2 seconds

    private MaterialCardView btnCheckUpdate;
    private TextView txtUpdateBtn;

    private TextView tvUpdateStatus, tvAdditionalInfo, tvDescription;
    private ImageView ivUpdateProgress;
    private GifImageView gifDownloading;

    private ApiManager apiManager;
    private String nodeId;
    private EspOtaUpdate otaUpdate;
    private EspApplication espApp;
    private Handler handler;
    private boolean isUpdateAvailable;
    private boolean updateInProgress;
    private boolean isTaskStarted;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fw_update);

        espApp = (EspApplication) getApplicationContext();
        apiManager = ApiManager.getInstance(getApplicationContext());
        nodeId = getIntent().getStringExtra(AppConstants.KEY_NODE_ID);
        handler = new Handler();
        initViews();
        checkUpdate();
    }

    private View.OnClickListener updateBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            if (otaUpdate == null) {
                checkUpdate();
            } else {
                String title = txtUpdateBtn.getText().toString();
                if (getString(R.string.btn_check_again).equals(title)) {
                    checkUpdate();
                } else {
                    pushOTAUpdate();
                }
            }
        }
    };

    private void initViews() {

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setTitle(R.string.title_activity_fw_update);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        tvUpdateStatus = findViewById(R.id.tv_update_status);
        tvAdditionalInfo = findViewById(R.id.tv_additional_info);
        tvDescription = findViewById(R.id.tv_description);
        ivUpdateProgress = findViewById(R.id.iv_update);
        gifDownloading = findViewById(R.id.iv_gif_update);

        btnCheckUpdate = findViewById(R.id.btn_check_update);
        txtUpdateBtn = btnCheckUpdate.findViewById(R.id.text_btn);

        txtUpdateBtn.setText(R.string.btn_check_again);
        btnCheckUpdate.setOnClickListener(updateBtnClickListener);
    }

    private void checkUpdate() {

        displayProgressAnimation();
        showLoadingForCheckUpdate();

        apiManager.checkFwUpdate(nodeId, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                if (data != null) {

                    otaUpdate = data.getParcelable(AppConstants.KEY_OTA_DETAILS);
                    isUpdateAvailable = otaUpdate.isOtaAvailable();

                    if (isUpdateAvailable) {
                        showLoadingForGetUpdateStatus();
                        getFwUpdateStatus();
                    } else {
                        stopProgressAnimation();
                        updateUi(otaUpdate);
                    }
                } else {
                    stopProgressAnimation();
                }
            }

            @Override
            public void onResponseFailure(Exception exception) {
                stopProgressAnimation();
                displayFailure(getString(R.string.error_check_update), "");
                if (exception instanceof CloudException) {
                    Toast.makeText(FwUpdateActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(FwUpdateActivity.this, R.string.error_check_update, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                stopProgressAnimation();
                displayFailure(getString(R.string.error_check_update), "");
                if (exception instanceof CloudException) {
                    Toast.makeText(FwUpdateActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(FwUpdateActivity.this, R.string.msg_no_internet, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void getFwUpdateStatus() {

        apiManager.getFwUpdateStatus(nodeId, otaUpdate.getOtaJobID(), new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                stopProgressAnimation();
                if (data != null) {
                    otaUpdate = data.getParcelable(AppConstants.KEY_OTA_DETAILS);
                    updateUi(otaUpdate);
                }
                if (isTaskStarted) {
                    handler.postDelayed(updateValuesTask, POLLING_INTERVAL);
                }
            }

            @Override
            public void onResponseFailure(Exception exception) {
                stopProgressAnimation();
                displayFailure(getString(R.string.error_check_update), "");
                if (exception instanceof CloudException) {
                    Toast.makeText(FwUpdateActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(FwUpdateActivity.this, R.string.error_check_update, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                stopProgressAnimation();
                displayFailure(getString(R.string.error_check_update), "");
                if (exception instanceof CloudException) {
                    Toast.makeText(FwUpdateActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(FwUpdateActivity.this, R.string.error_check_update, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void pushFwUpdate() {

        apiManager.pushFwUpdate(nodeId, otaUpdate.getOtaJobID(), new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                stopProgressAnimation();
                btnCheckUpdate.setVisibility(View.INVISIBLE);
                ivUpdateProgress.setVisibility(View.GONE);
                gifDownloading.setVisibility(View.VISIBLE);
                tvAdditionalInfo.setVisibility(View.INVISIBLE);

                if (data != null) {
                    otaUpdate = data.getParcelable(AppConstants.KEY_OTA_DETAILS);
                    Toast.makeText(FwUpdateActivity.this, otaUpdate.getOtaStatusDescription(), Toast.LENGTH_SHORT).show();
                }
                getFwUpdateStatus();
                startUpdateValueTask();
            }

            @Override
            public void onResponseFailure(Exception exception) {
                stopProgressAnimation();
                displayFailure(getString(R.string.error_check_update), "");
                if (exception instanceof CloudException) {
                    Toast.makeText(FwUpdateActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(FwUpdateActivity.this, R.string.error_check_update, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                stopProgressAnimation();
                displayFailure(getString(R.string.error_check_update), "");
                if (exception instanceof CloudException) {
                    Toast.makeText(FwUpdateActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(FwUpdateActivity.this, R.string.error_check_update, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateUi(EspOtaUpdate otaUpdate) {

        if (otaUpdate == null) {
            txtUpdateBtn.setText(R.string.btn_check_again);
            tvUpdateStatus.setText(R.string.error_check_update);
            btnCheckUpdate.setVisibility(View.VISIBLE);
            ivUpdateProgress.setImageResource(R.drawable.ic_update_failed);
        }

        String status = otaUpdate.getStatus();
        Log.d(TAG, "OTA update status : " + status);

        if (!TextUtils.isEmpty(status)) {

            switch (status) {

                case AppConstants.OTA_STATUS_TRIGGERED:
                    txtUpdateBtn.setText(R.string.btn_update);
                    tvUpdateStatus.setText(R.string.fw_update_available);
                    btnCheckUpdate.setVisibility(View.VISIBLE);
                    tvAdditionalInfo.setText(versionInfoString());
                    tvAdditionalInfo.setVisibility(View.VISIBLE);
                    ivUpdateProgress.setImageResource(R.drawable.ic_update);
                    ivUpdateProgress.setVisibility(View.VISIBLE);
                    break;

                case AppConstants.OTA_STATUS_IN_PROGRESS:
                case AppConstants.OTA_STATUS_STARTED:
                case AppConstants.OTA_STATUS_FAILED:
                    long ts1 = System.currentTimeMillis();
                    long ts2 = otaUpdate.getTimestamp();
                    long diff = ts1 - ts2;
                    if (diff > TIMEOUT_IN_MILLIS) {
                        stopUpdateValueTask();
                        updateInProgress = false;
                        txtUpdateBtn.setText(R.string.btn_update);
                        tvUpdateStatus.setText(R.string.fw_update_available);
                        btnCheckUpdate.setVisibility(View.VISIBLE);
                        tvAdditionalInfo.setText(versionInfoString());
                        tvAdditionalInfo.setVisibility(View.VISIBLE);
                        ivUpdateProgress.setImageResource(R.drawable.ic_update);
                        ivUpdateProgress.setVisibility(View.VISIBLE);
                        gifDownloading.setVisibility(View.GONE);
                        btnCheckUpdate.setVisibility(View.VISIBLE);
                    } else {
                        updateInProgress = true;
                        btnCheckUpdate.setVisibility(View.INVISIBLE);
                        String updateStatus = "Current status: " + status;
                        tvUpdateStatus.setText(updateStatus);
                        tvAdditionalInfo.setText(otaUpdate.getAdditionalInfo());
                        tvAdditionalInfo.setVisibility(View.VISIBLE);
                        gifDownloading.setVisibility(View.VISIBLE);
                        ivUpdateProgress.setVisibility(View.GONE);
                    }
                    break;

                case AppConstants.OTA_STATUS_COMPLETED:
                case AppConstants.OTA_STATUS_SUCCESS:
                    stopUpdateValueTask();
                    if (updateInProgress) {
                        tvUpdateStatus.setText(R.string.fw_update_success);
                    } else {
                        tvUpdateStatus.setText(R.string.fw_up_to_date);
                    }
                    updateInProgress = false;
                    gifDownloading.setVisibility(View.GONE);
                    btnCheckUpdate.setVisibility(View.VISIBLE);
                    ivUpdateProgress.setVisibility(View.VISIBLE);
                    tvAdditionalInfo.setVisibility(View.INVISIBLE);
                    ivUpdateProgress.setImageResource(R.drawable.ic_update);
                    txtUpdateBtn.setText(R.string.btn_check_again);
                    break;

                case AppConstants.OTA_STATUS_REJECTED:
                    stopUpdateValueTask();
                    updateInProgress = false;
                    displayFailure(otaUpdate.getAdditionalInfo(), getString(R.string.error_fw_update_rejected));
                    break;

                case AppConstants.OTA_STATUS_UNKNOWN:
                    stopUpdateValueTask();
                    updateInProgress = false;
                    displayFailure(getString(R.string.error_fw_update), getString(R.string.error_device_connection));
                    break;
            }
        } else {
            Log.e(TAG, "OTA update status is not available.");
        }

        if (isUpdateAvailable && !TextUtils.isEmpty(otaUpdate.getOtaStatusDescription())
                && updateInProgress) {
            tvDescription.setVisibility(View.VISIBLE);
            tvDescription.setText(otaUpdate.getOtaStatusDescription());
        } else {
            tvDescription.setVisibility(View.INVISIBLE);
        }
    }

    private void displayFailure(String statusMsg, String additionInfo) {
        tvUpdateStatus.setText(statusMsg);
        tvAdditionalInfo.setText(additionInfo);
        ivUpdateProgress.setImageResource(R.drawable.ic_update_failed);
        txtUpdateBtn.setText(R.string.btn_check_again);
        gifDownloading.setVisibility(View.GONE);
        ivUpdateProgress.setVisibility(View.VISIBLE);
        tvAdditionalInfo.setVisibility(View.VISIBLE);
        btnCheckUpdate.setVisibility(View.VISIBLE);
    }

    private void displayProgressAnimation() {
        ivUpdateProgress.setImageResource(R.drawable.ic_claiming);
        RotateAnimation rotate = new RotateAnimation(0, 180, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(3000);
        rotate.setRepeatCount(Animation.INFINITE);
        rotate.setInterpolator(new LinearInterpolator());
        ivUpdateProgress.startAnimation(rotate);
    }

    private void stopProgressAnimation() {
        ivUpdateProgress.clearAnimation();
    }

    private void showLoadingForCheckUpdate() {
        tvUpdateStatus.setVisibility(View.VISIBLE);
        tvUpdateStatus.setText(R.string.progress_check_update);
        btnCheckUpdate.setVisibility(View.INVISIBLE);
        tvAdditionalInfo.setVisibility(View.INVISIBLE);
        gifDownloading.setVisibility(View.GONE);
        ivUpdateProgress.setVisibility(View.VISIBLE);
    }

    private void showLoadingForGetUpdateStatus() {
        tvUpdateStatus.setVisibility(View.VISIBLE);
        tvUpdateStatus.setText(R.string.progress_check_fw_update_status);
        btnCheckUpdate.setVisibility(View.INVISIBLE);
        tvAdditionalInfo.setVisibility(View.INVISIBLE);
    }

    /// Method to push OTA update to node.
    private void pushOTAUpdate() {

        // Check if current Node is connected to network
        if (espApp.nodeMap.get(nodeId).isOnline()) {
            confirmOTAUpdate();
        } else {
            deviceOfflineError();
        }
    }

    private void confirmOTAUpdate() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_msg_proceed_confirmation);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
                displayProgressAnimation();
                btnCheckUpdate.setVisibility(View.INVISIBLE);
                tvUpdateStatus.setText(R.string.progress_push_fw_update);
                tvAdditionalInfo.setVisibility(View.INVISIBLE);
                gifDownloading.setVisibility(View.GONE);
                ivUpdateProgress.setVisibility(View.VISIBLE);
                pushFwUpdate();
            }
        });

        builder.setNegativeButton(R.string.btn_no, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        AlertDialog userDialog = builder.create();
        userDialog.show();
    }

    private void deviceOfflineError() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_error);
        builder.setMessage(R.string.dialog_msg_device_offline);
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });
        AlertDialog userDialog = builder.create();
        userDialog.show();
    }

    private String versionInfoString() {
        StringBuilder versionInfo = new StringBuilder();
        versionInfo.append(getString(R.string.current_version));
        versionInfo.append(": ");
        versionInfo.append(espApp.nodeMap.get(nodeId).getFwVersion());
        versionInfo.append("\n\n");
        versionInfo.append(getString(R.string.available_version));
        versionInfo.append(": ");
        versionInfo.append(otaUpdate.getFwVersion());
        return versionInfo.toString();
    }

    private void startUpdateValueTask() {
        isTaskStarted = true;
        handler.removeCallbacks(updateValuesTask);
        handler.postDelayed(updateValuesTask, POLLING_INTERVAL);
    }

    private void stopUpdateValueTask() {
        isTaskStarted = false;
        handler.removeCallbacks(updateValuesTask);
    }

    private Runnable updateValuesTask = new Runnable() {

        @Override
        public void run() {
            if (isUpdateAvailable) {
                getFwUpdateStatus();
            }
        }
    };
}
