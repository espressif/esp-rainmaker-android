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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.espressif.AppConstants;
import com.espressif.provisioning.DeviceConnectionEvent;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPDevice;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.listeners.QRCodeScanListener;
import com.espressif.rainmaker.R;
import com.wang.avi.AVLoadingIndicatorView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

public class AddDeviceActivity extends AppCompatActivity {

    private static final String TAG = AddDeviceActivity.class.getSimpleName();

    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final int REQUEST_ACCESS_FINE_LOCATION = 2;

    private TextView tvTitle, tvBack, tvCancel;
    private CardView btnAddManually;
    private TextView txtAddManuallyBtn;

    private AVLoadingIndicatorView loader;
    private ESPDevice espDevice;
    private ESPProvisionManager provisionManager;
    //    private CameraSourcePreview cameraPreview;
    private CodeScanner codeScanner;
    private boolean isQrCodeDataReceived = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_device);
        provisionManager = ESPProvisionManager.getInstance(getApplicationContext());
        initViews();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

//            if (cameraPreview != null) {
//                try {
//                    cameraPreview.start();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }

            if (codeScanner != null && !isQrCodeDataReceived) {
                codeScanner.startPreview();
            }

            // This condition is to get event of cancel button of "try again" popup. Because Android 10 is not giving event on cancel button click if network is not found.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && espDevice != null && espDevice.getTransportType().equals(ESPConstants.TransportType.TRANSPORT_SOFTAP)) {

                String ssid = getWifiSsid();
                Log.d(TAG, "Currently connected WiFi SSID : " + ssid);
                Log.d(TAG, "Device Name  : " + espDevice.getDeviceName());
                if (!TextUtils.isEmpty(ssid) && !ssid.equals(espDevice.getDeviceName())) {
                    Log.e(TAG, "Device is not connected");
                    finish();
                }
            }
        }
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
//        if (cameraPreview != null) {
//            cameraPreview.stop();
//        }
        if (codeScanner != null) {
            codeScanner.stopPreview();
        }
    }

    @Override
    protected void onDestroy() {

        hideLoading();
        EventBus.getDefault().unregister(this);
//        if (cameraPreview != null) {
//            cameraPreview.release();
//        }
        if (codeScanner != null) {
            codeScanner.releaseResources();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {

        if (provisionManager.getEspDevice() != null) {
            provisionManager.getEspDevice().disconnectDevice();
        }
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Log.e(TAG, "onRequestPermissionsResult , requestCode : " + requestCode);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {

            initialiseDetectorsAndSources();

        } else if (requestCode == REQUEST_ACCESS_FINE_LOCATION) {

            initialiseDetectorsAndSources();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(DeviceConnectionEvent event) {

        Log.d(TAG, "On Device Connection Event RECEIVED : " + event.getEventType());

        switch (event.getEventType()) {

            case ESPConstants.EVENT_DEVICE_CONNECTED:

                Log.e(TAG, "Device Connected Event Received");
                ArrayList<String> deviceCaps = espDevice.getDeviceCapabilities();

                if (deviceCaps.contains("wifi_scan")) {

                    goToWiFiScanActivity();

                } else {

                    goToProvisionActivity();
                }
                break;

            case ESPConstants.EVENT_DEVICE_DISCONNECTED:
//                Toast.makeText(AddDeviceActivity.this, "Device disconnected", Toast.LENGTH_LONG).show();
                askForManualDeviceConnection();
                break;

            case ESPConstants.EVENT_DEVICE_CONNECTION_FAILED:
//                Toast.makeText(AddDeviceActivity.this, "Failed to connect with device", Toast.LENGTH_LONG).show();
                askForManualDeviceConnection();
                break;
        }
    }

    View.OnClickListener btnAddManuallyClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            boolean isSec1 = true;
            String securityType = getResources().getString(R.string.security_type);

            if (!TextUtils.isEmpty(securityType) && securityType.equals("sec0")) {
                isSec1 = false;
            }

            if (isSec1) {
                provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, ESPConstants.SecurityType.SECURITY_1);
            } else {
                provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, ESPConstants.SecurityType.SECURITY_0);
            }
            goToWiFiProvisionLanding(isSec1);
        }
    };

    private View.OnClickListener cancelBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            if (provisionManager.getEspDevice() != null) {
                provisionManager.getEspDevice().disconnectDevice();
            }
            setResult(RESULT_CANCELED, getIntent());
            finish();
        }
    };

    private void initViews() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvCancel = findViewById(R.id.btn_cancel);

        tvTitle.setText(R.string.title_activity_add_device);
        tvBack.setVisibility(View.GONE);
        tvCancel.setVisibility(View.VISIBLE);
        tvCancel.setOnClickListener(cancelBtnClickListener);

        CodeScannerView scannerView = findViewById(R.id.scanner_view);
        codeScanner = new CodeScanner(this, scannerView);

//        cameraPreview = findViewById(R.id.preview);
        btnAddManually = findViewById(R.id.btn_add_device_manually);
        txtAddManuallyBtn = findViewById(R.id.text_btn);
        loader = findViewById(R.id.loader);

        txtAddManuallyBtn.setText(R.string.btn_no_qr_code);
        btnAddManually.setOnClickListener(btnAddManuallyClickListener);

        initialiseDetectorsAndSources();
    }

    private void initialiseDetectorsAndSources() {

        if (ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            provisionManager.scanQRCode(codeScanner, qrCodeScanListener);
//            cameraPreview.setVisibility(View.VISIBLE);
            findViewById(R.id.scanner_view).setVisibility(View.VISIBLE);

            if (codeScanner != null) {
                codeScanner.startPreview();
            }
        } else {
            Log.e(TAG, "All permissions are not granted.");
            askForPermissions();
        }
    }

    private void askForPermissions() {

        if (ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(AddDeviceActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);

        } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(AddDeviceActivity.this, new
                    String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ACCESS_FINE_LOCATION);
        } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(AddDeviceActivity.this, new
                    String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    private void showLoading() {
        loader.setVisibility(View.VISIBLE);
        loader.show();
    }

    private void hideLoading() {
        loader.hide();
    }

    private QRCodeScanListener qrCodeScanListener = new QRCodeScanListener() {

        @Override
        public void qrCodeScanned() {

            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    showLoading();
                    Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                    vib.vibrate(50);
                    isQrCodeDataReceived = true;
                }
            });
        }

        @Override
        public void deviceDetected(final ESPDevice device) {

            Log.e(TAG, "Device detected");
            espDevice = device;

            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    if (ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "Location Permission not granted.");
                        return;
                    }

                    if (espDevice.getTransportType().equals(ESPConstants.TransportType.TRANSPORT_SOFTAP)
                            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                        if (!wifiManager.isWifiEnabled()) {
                            alertForWiFi();
                            return;
                        }
                    }
                    device.connectToDevice();
                }
            });
        }

        @Override
        public void onFailure(final Exception e) {

            Log.e(TAG, "Error : " + e.getMessage());

            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    hideLoading();
                    Toast.makeText(AddDeviceActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        }
    };

    private void alertForWiFi() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setCancelable(false);
        builder.setMessage(R.string.error_wifi_off);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
                espDevice = null;
                hideLoading();
                if (codeScanner != null) {
                    codeScanner.releaseResources();
                    codeScanner.startPreview();
                    provisionManager.scanQRCode(codeScanner, qrCodeScanListener);
                }
            }
        });

        builder.show();
    }

    private void goToWiFiProvisionLanding(boolean isSec1) {

        finish();
        Intent intent = new Intent(getApplicationContext(), ProvisionLanding.class);
        if (isSec1) {
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, AppConstants.SECURITY_TYPE_1);
        } else {
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, AppConstants.SECURITY_TYPE_0);
        }

        if (espDevice != null) {
            intent.putExtra(AppConstants.KEY_DEVICE_NAME, espDevice.getDeviceName());
            intent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, espDevice.getProofOfPossession());
        }
        startActivity(intent);
    }

    private void goToWiFiScanActivity() {

        finish();
        Intent wifiListIntent = new Intent(getApplicationContext(), WiFiScanActivity.class);
        startActivity(wifiListIntent);
    }

    private void goToProvisionActivity() {

        finish();
        Intent provisionIntent = new Intent(getApplicationContext(), ProvisionActivity.class);
        startActivity(provisionIntent);
    }

    private void askForManualDeviceConnection() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setCancelable(true);
        builder.setMessage("Device connection failed. \nDo you want to connect device manually ?");

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
                if (espDevice != null) {
                    if (espDevice.getSecurityType().equals(ESPConstants.SecurityType.SECURITY_0)) {
                        goToWiFiProvisionLanding(false);
                    } else {
                        goToWiFiProvisionLanding(true);
                    }
                } else {
                    finish();
                }
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
                finish();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private String getWifiSsid() {

        String ssid = null;
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {

            ssid = wifiInfo.getSSID();
            ssid = ssid.replace("\"", "");
        }
        return ssid;
    }
}
