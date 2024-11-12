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
import android.net.Uri;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.espressif.AppConstants;
import com.espressif.matter.GroupSelectionActivity;
import com.espressif.provisioning.DeviceConnectionEvent;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPDevice;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.listeners.QRCodeScanListener;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.Utils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.wang.avi.AVLoadingIndicatorView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class AddDeviceActivity extends AppCompatActivity {

    private static final String TAG = AddDeviceActivity.class.getSimpleName();

    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final int REQUEST_ACCESS_FINE_LOCATION = 2;

    private CodeScanner codeScanner;
    //    private CameraSourcePreview cameraPreview;
    private MaterialCardView btnAddManually, btnGetPermission;
    private TextView txtAddManuallyBtn;
    private AVLoadingIndicatorView loader;
    private LinearLayout layoutQrCode, layoutPermissionErr;
    private TextView tvPermissionErr;
    private ImageView ivPermissionErr;

    private ESPDevice espDevice;
    private ESPProvisionManager provisionManager;
    private boolean isQrCodeDataReceived = false;
    private boolean buttonClicked = false;
    private String connectedNetwork;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_device);
        provisionManager = ESPProvisionManager.getInstance(getApplicationContext());
        initViews();
        EventBus.getDefault().register(this);
        connectedNetwork = getWifiSsid();
    }

    @Override
    protected void onResume() {
        super.onResume();

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
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        //        if (cameraPreview != null) {
//            cameraPreview.stop();
//        }
        if (codeScanner != null) {
            codeScanner.releaseResources();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {

        hideLoading();
        EventBus.getDefault().unregister(this);
//        if (cameraPreview != null) {
//            cameraPreview.release();
//        }
//        if (codeScanner != null) {
//            codeScanner.releaseResources();
//        }
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
        Log.d(TAG, "onRequestPermissionsResult , requestCode : " + requestCode);

        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                findViewById(R.id.scanner_view).setVisibility(View.GONE);
                layoutQrCode.setVisibility(View.GONE);
                layoutPermissionErr.setVisibility(View.VISIBLE);
                tvPermissionErr.setText(R.string.error_camera_permission);
                ivPermissionErr.setImageResource(R.drawable.ic_no_camera_permission);
                if (buttonClicked) {
                    // Call navigateToAppSettings only when the button is clicked
                    navigateToAppSettings();
                }
            } else {
                layoutQrCode.setVisibility(View.VISIBLE);
                layoutPermissionErr.setVisibility(View.GONE);
                openCamera();
            }
        } else if (requestCode == REQUEST_ACCESS_FINE_LOCATION && grantResults.length > 0) {

            boolean permissionGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    Log.e(TAG, "User has denied permission");
                    permissionGranted = false;
                }
            }

            if (!permissionGranted) {
                findViewById(R.id.scanner_view).setVisibility(View.GONE);
                layoutQrCode.setVisibility(View.GONE);
                layoutPermissionErr.setVisibility(View.VISIBLE);
                tvPermissionErr.setText(R.string.error_location_permission);
                ivPermissionErr.setImageResource(R.drawable.ic_no_location_permission);
                showLocationPermissionAlertDialog();
            } else {
                findViewById(R.id.scanner_view).setVisibility(View.VISIBLE);
                layoutQrCode.setVisibility(View.VISIBLE);
                layoutPermissionErr.setVisibility(View.GONE);
                scanQrCode();
            }
        }
    }

    private void navigateToAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(DeviceConnectionEvent event) {

        Log.d(TAG, "On Device Connection Event RECEIVED : " + event.getEventType());

        switch (event.getEventType()) {

            case ESPConstants.EVENT_DEVICE_CONNECTED:
                Log.e(TAG, "Device Connected Event Received");
                Utils.setSecurityTypeFromVersionInfo(getApplicationContext());
                checkDeviceCapabilities();
                break;

            case ESPConstants.EVENT_DEVICE_DISCONNECTED:
                if (!isFinishing()) {
                    askForManualDeviceConnection();
                }
                break;

            case ESPConstants.EVENT_DEVICE_CONNECTION_FAILED:
                if (espDevice != null && espDevice.getTransportType().equals(ESPConstants.TransportType.TRANSPORT_BLE)) {
                    alertForDeviceNotSupported("Failed to connect with device");
                } else {
                    if (!isFinishing()) {
                        askForManualDeviceConnection();
                    }
                }
                break;
        }
    }

    View.OnClickListener btnAddManuallyClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            int securityType = Integer.parseInt(BuildConfig.SECURITY);

            if (AppConstants.TRANSPORT_SOFTAP.equalsIgnoreCase(BuildConfig.TRANSPORT)) {

                Utils.createESPDevice(getApplicationContext(), ESPConstants.TransportType.TRANSPORT_SOFTAP, securityType);
                goToWiFiProvisionLanding(securityType);

            } else if (AppConstants.TRANSPORT_BLE.equalsIgnoreCase(BuildConfig.TRANSPORT)) {

                Utils.createESPDevice(getApplicationContext(), ESPConstants.TransportType.TRANSPORT_BLE, securityType);
                goToBLEProvisionLanding(securityType);

            } else if (AppConstants.TRANSPORT_BOTH.equalsIgnoreCase(BuildConfig.TRANSPORT)) {

                askForDeviceType(securityType);

            } else {
                Toast.makeText(AddDeviceActivity.this, R.string.error_device_type_not_supported, Toast.LENGTH_LONG).show();
            }
        }
    };

    View.OnClickListener btnGetPermissionClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            buttonClicked = true;

            if (ContextCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(AddDeviceActivity.this, Manifest.permission.CAMERA)) {
                    showCameraPermissionExplanation();
                } else {
                    requestCameraPermission();
                }

            } else {

                if (ContextCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                    if (ActivityCompat.shouldShowRequestPermissionRationale(AddDeviceActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                        showLocationAndBluetoothPermissionExplanation(false);
                    } else {
                        showLocationPermissionAlertDialog();
                    }

                } else {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                        if (ContextCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                                || ContextCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                            if (ActivityCompat.shouldShowRequestPermissionRationale(AddDeviceActivity.this, Manifest.permission.BLUETOOTH_SCAN)
                                    || ActivityCompat.shouldShowRequestPermissionRationale(AddDeviceActivity.this, Manifest.permission.BLUETOOTH_CONNECT)) {

                                showLocationAndBluetoothPermissionExplanation(true);

                            } else {
                                requestLocationPermission();
                            }
                        }
                    }
                }
            }
        }
    };

    private void initViews() {

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(R.string.title_activity_add_device);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (provisionManager.getEspDevice() != null) {
                    provisionManager.getEspDevice().disconnectDevice();
                }
                setResult(RESULT_CANCELED, getIntent());
                finish();
            }
        });

        CodeScannerView scannerView = findViewById(R.id.scanner_view);
        codeScanner = new CodeScanner(this, scannerView);

//        cameraPreview = findViewById(R.id.preview);
        loader = findViewById(R.id.loader);
        layoutQrCode = findViewById(R.id.layout_qr_code_txt);
        layoutPermissionErr = findViewById(R.id.layout_permission_error);
        tvPermissionErr = findViewById(R.id.tv_permission_error);
        ivPermissionErr = findViewById(R.id.iv_permission_error);

        btnAddManually = findViewById(R.id.btn_add_device_manually);
        txtAddManuallyBtn = btnAddManually.findViewById(R.id.text_btn);
        txtAddManuallyBtn.setText(R.string.btn_no_qr_code);
        btnAddManually.setOnClickListener(btnAddManuallyClickListener);

        btnGetPermission = findViewById(R.id.btn_get_permission);
        TextView btnPermissionText = btnGetPermission.findViewById(R.id.text_btn);
        btnPermissionText.setText(R.string.btn_get_permission);
        btnGetPermission.setOnClickListener(btnGetPermissionClickListener);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                if (BuildConfig.isChinaRegion) {
                    displayPermissionPurpose();
                } else {
                    checkCameraPermission();
                }
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                if (BuildConfig.isChinaRegion) {
                    displayPermissionPurpose();
                } else {
                    checkCameraPermission();
                }
            }
        }
    }

    private void displayPermissionPurpose() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setMessage(R.string.permission_purpose_for_new_version);
        } else {
            builder.setMessage(R.string.permission_purpose);
        }

        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
                checkCameraPermission();
            }
        });

        builder.show();
    }

    /**
     * Used to check and request camera permission.
     */
    private void checkCameraPermission() {

        // Check if the permission is already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            // If the permission is not granted, show rationale.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                // Show an explanation to the user
                showCameraPermissionExplanation();
            } else {
                // Directly request for permission
                requestCameraPermission();
            }
        } else {
            // Permission already granted, proceed with camera operation
            openCamera();
        }
    }

    private void checkLocationAndBluetoothPermission() {

        // Check if the permission is already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // If the permission is not granted, show rationale.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user
                showLocationAndBluetoothPermissionExplanation(false);
            } else {
                // Directly request for permission
                requestLocationPermission();
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_SCAN)
                            || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_CONNECT)) {

                        showLocationAndBluetoothPermissionExplanation(true);

                    } else {
                        requestLocationPermission();
                    }
                } else {
                    scanQrCode();
                }
            } else {
                scanQrCode();
            }
        }
    }

    private void showCameraPermissionExplanation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        builder.setTitle(R.string.dialog_title_camera_permission);
        builder.setMessage(R.string.dialog_msg_camera_permission_use);
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                // Request the permission after the user sees the explanation
                requestCameraPermission();
                dialog.dismiss();
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
                finish();
            }
        });

        builder.show();
    }

    private void showLocationAndBluetoothPermissionExplanation(boolean includeBtPermission) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        if (includeBtPermission) {
            builder.setTitle(R.string.dialog_title_location_bt_permission);
        } else {
            builder.setTitle(R.string.dialog_title_location_permission);
        }
        builder.setMessage(R.string.dialog_msg_location_permission_use);

        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Request the permission after the user sees the explanation
                requestLocationPermission();
                dialog.dismiss();
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });

        builder.show();
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new
                    String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_ACCESS_FINE_LOCATION);
        } else {
            ActivityCompat.requestPermissions(this, new
                    String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    private void openCamera() {
        findViewById(R.id.scanner_view).setVisibility(View.VISIBLE);
        if (codeScanner != null) {
            codeScanner.startPreview();
        }
        scanQrCode();
    }

    private void scanQrCode() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                provisionManager.scanQRCode(codeScanner, qrCodeScanListener);
            } else {
                checkLocationAndBluetoothPermission();
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                provisionManager.scanQRCode(codeScanner, qrCodeScanListener);
            } else {
                checkLocationAndBluetoothPermission();
            }
        }
    }

    private void askForDeviceType(int securityType) {

        final String[] deviceTypes = getResources().getStringArray(R.array.prov_transport_types);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle(R.string.dialog_msg_device_selection);

        builder.setItems(deviceTypes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int position) {

                switch (position) {
                    case 0:
                        Utils.createESPDevice(getApplicationContext(), ESPConstants.TransportType.TRANSPORT_BLE, securityType);
                        goToBLEProvisionLanding(securityType);
                        break;

                    case 1:
                        Utils.createESPDevice(getApplicationContext(), ESPConstants.TransportType.TRANSPORT_SOFTAP, securityType);
                        goToWiFiProvisionLanding(securityType);
                        break;

                    case 2:
                        if (Utils.isPlayServicesAvailable(getApplicationContext())) {
                            goToGroupSelectionActivity("");
                        } else {
                            Log.e(TAG, "Google Play Services not available.");
                            Utils.showPlayServicesWarning(AddDeviceActivity.this);
                        }
                        break;
                }
                dialog.dismiss();
            }
        });
        builder.show();
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

        @Override
        public void onFailure(Exception e, String qrCodeData) {
            // Called when QR code is not in supported format.
            // Comment below error handling and do whatever you want to do with your QR code data.
            Log.e(TAG, "Error : " + e.getMessage());
            Log.e(TAG, "QR code data : " + qrCodeData);

            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    hideLoading();

                    if (BuildConfig.isMatterSupported && !TextUtils.isEmpty(qrCodeData) && qrCodeData.contains("MT:")) {
                        // Display group selection screen.
                        if (Utils.isPlayServicesAvailable(getApplicationContext())) {
                            goToGroupSelectionActivity(qrCodeData);
                        } else {
                            Utils.showPlayServicesWarning(AddDeviceActivity.this);
                        }
                    } else {
                        String msg = e.getMessage();
                        Toast.makeText(AddDeviceActivity.this, msg, Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            });
        }
    };

    private void checkDeviceCapabilities() {

        String versionInfo = espDevice.getVersionInfo();
        ArrayList<String> rmakerCaps = new ArrayList<>();
        ArrayList<String> deviceCaps = espDevice.getDeviceCapabilities();

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

        if (rmakerCaps.size() > 0 && rmakerCaps.contains(AppConstants.CAPABILITY_CLAIM)) {

            if (espDevice.getTransportType().equals(ESPConstants.TransportType.TRANSPORT_BLE)) {
                goToClaimingActivity();
            } else {
                alertForClaimingNotSupported();
            }
        } else {
            if (deviceCaps != null) {
                if (deviceCaps.contains(AppConstants.CAPABILITY_WIFI_SCAN)) {
                    goToWiFiScanActivity();
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

    private void alertForWiFi() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage(R.string.error_wifi_off);

        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
                espDevice = null;
                hideLoading();

                if (codeScanner != null) {

                    codeScanner.releaseResources();
                    codeScanner.startPreview();

                    if (ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            && ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

                        provisionManager.scanQRCode(codeScanner, qrCodeScanListener);
                    }
                }
            }
        });

        builder.show();
    }

    private void alertForClaimingNotSupported() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage(R.string.error_claiming_not_supported);

        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                if (provisionManager.getEspDevice() != null) {
                    provisionManager.getEspDevice().disconnectDevice();
                }
                dialog.dismiss();
                espDevice = null;
                hideLoading();
                finish();
            }
        });

        builder.show();
    }

    private void showLocationPermissionAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage(R.string.error_location_permission);

        builder.setPositiveButton(R.string.action_settings, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                navigateToAppSettings();
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        if (!isFinishing()) {
            alertDialog.show();
        }
    }

    private void goToBLEProvisionLanding(int secType) {

        if (ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            finish();
            Intent intent = new Intent(getApplicationContext(), BLEProvisionLanding.class);
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, secType);

            if (espDevice != null) {
                intent.putExtra(AppConstants.KEY_DEVICE_NAME, espDevice.getDeviceName());
                intent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, espDevice.getProofOfPossession());
                intent.putExtra(AppConstants.KEY_SSID, connectedNetwork);
            }
            startActivity(intent);
        } else {
            checkLocationAndBluetoothPermission();
        }
    }

    private void goToWiFiProvisionLanding(int secType) {

        if (ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            finish();
            Intent intent = new Intent(getApplicationContext(), ProvisionLanding.class);
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, secType);

            if (espDevice != null) {
                intent.putExtra(AppConstants.KEY_DEVICE_NAME, espDevice.getDeviceName());
                intent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, espDevice.getProofOfPossession());
                intent.putExtra(AppConstants.KEY_SSID, connectedNetwork);
            }
            startActivity(intent);
        } else {
            checkLocationAndBluetoothPermission();
        }
    }

    private void goToWiFiScanActivity() {
        finish();
        Intent wifiListIntent = new Intent(getApplicationContext(), WiFiScanActivity.class);
        wifiListIntent.putExtra(AppConstants.KEY_SSID, connectedNetwork);
        startActivity(wifiListIntent);
    }

    private void goToWiFiConfigActivity() {
        finish();
        Intent wifiConfigIntent = new Intent(getApplicationContext(), WiFiConfigActivity.class);
        wifiConfigIntent.putExtra(AppConstants.KEY_SSID, connectedNetwork);
        startActivity(wifiConfigIntent);
    }

    private void goToThreadConfigActivity(boolean scanCapAvailable) {
        finish();
        Intent threadConfigIntent = new Intent(getApplicationContext(), ThreadConfigActivity.class);
        threadConfigIntent.putExtras(getIntent());
        threadConfigIntent.putExtra(AppConstants.KEY_THREAD_SCAN_AVAILABLE, scanCapAvailable);
        startActivity(threadConfigIntent);
    }

    private void goToClaimingActivity() {
        finish();
        Intent claimingIntent = new Intent(getApplicationContext(), ClaimingActivity.class);
        claimingIntent.putExtra(AppConstants.KEY_SSID, connectedNetwork);
        startActivity(claimingIntent);
    }

    private void goToGroupSelectionActivity(String qrCodeData) {
        finish();
        Intent intent = new Intent(getApplicationContext(), GroupSelectionActivity.class);
        intent.putExtra(AppConstants.KEY_ON_BOARD_PAYLOAD, qrCodeData);
        startActivity(intent);
    }

    private void askForManualDeviceConnection() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(R.string.dialog_msg_connect_device_manually);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
                if (espDevice != null) {
                    if (espDevice.getTransportType().equals(ESPConstants.TransportType.TRANSPORT_SOFTAP)) {
                        goToWiFiProvisionLanding(espDevice.getSecurityType().ordinal());
                    } else {
                        goToBLEProvisionLanding(espDevice.getSecurityType().ordinal());
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
        if (!isFinishing()) {
            alertDialog.show();
        }
    }

    private void alertForDeviceNotSupported(String msg) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        builder.setTitle(R.string.error_title);
        builder.setMessage(msg);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (provisionManager.getEspDevice() != null) {
                    provisionManager.getEspDevice().disconnectDevice();
                }
                dialog.dismiss();
                finish();
            }
        });
        builder.show();
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
