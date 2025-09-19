// Copyright 2025 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.budiyev.android.codescanner.CodeScanner
import com.espressif.AppConstants
import com.espressif.matter.GroupSelectionActivity
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPDevice
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.provisioning.listeners.QRCodeScanListener
import com.espressif.rainmaker.BuildConfig
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityAddDeviceBinding
import com.espressif.ui.Utils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONException
import org.json.JSONObject

class AddDeviceActivity : AppCompatActivity() {

    companion object {
        private val TAG = AddDeviceActivity::class.java.simpleName
        private const val REQUEST_CAMERA_PERMISSION = 1
        private const val REQUEST_ACCESS_FINE_LOCATION = 2
    }

    private lateinit var binding: ActivityAddDeviceBinding

    private lateinit var provisionManager: ESPProvisionManager
    private var espDevice: ESPDevice? = null

    private var codeScanner: CodeScanner? = null

    private var isQrCodeDataReceived = false
    private var buttonClicked = false
    private var connectedNetwork: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        provisionManager = ESPProvisionManager.getInstance(applicationContext)
        initViews()
        EventBus.getDefault().register(this)
        connectedNetwork = wifiSsid
    }

    override fun onResume() {
        super.onResume()
        if (codeScanner != null && !isQrCodeDataReceived) {
            codeScanner!!.startPreview()
        }
    }

    override fun onPause() {
        codeScanner?.releaseResources()
        super.onPause()
    }

    override fun onDestroy() {
        hideLoading()
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onBackPressed() {
        provisionManager.espDevice?.disconnectDevice()
        super.onBackPressed()
    }

    private fun areAllPermissionsGranted(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val locationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothScanPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            val bluetoothConnectPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            cameraPermission && locationPermission && bluetoothScanPermission && bluetoothConnectPermission
        } else {
            cameraPermission && locationPermission
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult , requestCode : $requestCode")

        if (areAllPermissionsGranted()) {
            binding.layoutQrCodeTxt.visibility = View.VISIBLE
            binding.layoutPermissionError.visibility = View.GONE
            binding.btnGetPermission.layoutBtnRemove.visibility = View.GONE
            openCamera()
        } else {
            binding.scannerView.visibility = View.GONE
            binding.layoutQrCodeTxt.visibility = View.GONE
            binding.layoutPermissionError.visibility = View.VISIBLE
            binding.btnGetPermission.layoutBtnRemove.visibility = View.VISIBLE

            if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.size > 0) {
                if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    binding.scannerView.visibility = View.GONE
                    binding.layoutQrCodeTxt.visibility = View.GONE
                    binding.layoutPermissionError.visibility = View.VISIBLE
                    binding.tvPermissionError.setText(R.string.error_camera_permission)
                    binding.ivPermissionError.setImageResource(R.drawable.ic_no_camera_permission)
                    if (buttonClicked) {
                        // Call navigateToAppSettings only when the button is clicked
                        navigateToAppSettings()
                    }
                } else {
                    binding.layoutQrCodeTxt.visibility = View.VISIBLE
                    binding.layoutPermissionError.visibility = View.GONE
                    openCamera()
                }
            } else if (requestCode == REQUEST_ACCESS_FINE_LOCATION && grantResults.size > 0) {
                var permissionGranted = true
                for (grantResult in grantResults) {
                    if (grantResult == PackageManager.PERMISSION_DENIED) {
                        Log.e(TAG, "User has denied permission")
                        permissionGranted = false
                    }
                }

                if (!permissionGranted) {
                    binding.scannerView.visibility = View.GONE
                    binding.layoutQrCodeTxt.visibility = View.GONE
                    binding.layoutPermissionError.visibility = View.VISIBLE
                    binding.tvPermissionError.setText(R.string.error_location_permission)
                    binding.ivPermissionError.setImageResource(R.drawable.ic_no_location_permission)
                    showLocationPermissionAlertDialog()
                } else {
                    binding.layoutQrCodeTxt.visibility = View.VISIBLE
                    binding.layoutPermissionError.visibility = View.GONE
                    openCamera()
                }
            }
        }
    }

    private fun navigateToAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.setData(uri)
        startActivity(intent)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: DeviceConnectionEvent) {
        Log.d(TAG, "On Device Connection Event RECEIVED : " + event.eventType)

        when (event.eventType) {
            ESPConstants.EVENT_DEVICE_CONNECTED -> {
                Log.e(TAG, "Device Connected Event Received")
                Utils.setSecurityTypeFromVersionInfo(applicationContext)
                checkDeviceCapabilities()
            }

            ESPConstants.EVENT_DEVICE_DISCONNECTED -> if (!isFinishing) {
                askForManualDeviceConnection()
            }

            ESPConstants.EVENT_DEVICE_CONNECTION_FAILED -> if (espDevice != null && espDevice!!.transportType == ESPConstants.TransportType.TRANSPORT_BLE) {
                alertForDeviceNotSupported("Failed to connect with device")
            } else {
                if (!isFinishing) {
                    askForManualDeviceConnection()
                }
            }
        }
    }

    var btnAddManuallyClickListener: View.OnClickListener = View.OnClickListener {
        val securityType = BuildConfig.SECURITY.toInt()
        if (AppConstants.TRANSPORT_SOFTAP.equals(
                BuildConfig.TRANSPORT,
                ignoreCase = true
            )
        ) {
            Utils.createESPDevice(
                applicationContext,
                ESPConstants.TransportType.TRANSPORT_SOFTAP,
                securityType
            )
            goToWiFiProvisionLanding(securityType)
        } else if (AppConstants.TRANSPORT_BLE.equals(
                BuildConfig.TRANSPORT,
                ignoreCase = true
            )
        ) {
            Utils.createESPDevice(
                applicationContext,
                ESPConstants.TransportType.TRANSPORT_BLE,
                securityType
            )
            goToBLEProvisionLanding(securityType)
        } else if (AppConstants.TRANSPORT_BOTH.equals(
                BuildConfig.TRANSPORT,
                ignoreCase = true
            )
        ) {
            askForDeviceType(securityType)
        } else {
            Toast.makeText(
                this@AddDeviceActivity,
                R.string.error_device_type_not_supported,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    var btnGetPermissionClickListener: View.OnClickListener = View.OnClickListener {
        buttonClicked = true
        if (ContextCompat.checkSelfPermission(
                this@AddDeviceActivity,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this@AddDeviceActivity,
                    Manifest.permission.CAMERA
                )
            ) {
                showCameraPermissionExplanation()
            } else {
                requestCameraPermission()
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this@AddDeviceActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this@AddDeviceActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    showLocationAndBluetoothPermissionExplanation(false)
                } else {
                    showLocationPermissionAlertDialog()
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(
                            this@AddDeviceActivity,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) != PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(
                            this@AddDeviceActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(
                                this@AddDeviceActivity,
                                Manifest.permission.BLUETOOTH_SCAN
                            )
                            || ActivityCompat.shouldShowRequestPermissionRationale(
                                this@AddDeviceActivity,
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        ) {
                            showLocationAndBluetoothPermissionExplanation(true)
                        } else {
                            requestLocationPermission()
                        }
                    }
                }
            }
        }
    }

    private fun initViews() {

        setToolbar()
        codeScanner = CodeScanner(this, binding.scannerView)

        binding.btnAddDeviceManually.textBtn.setText(R.string.btn_no_qr_code)
        binding.btnAddDeviceManually.layoutBtnRemove.setOnClickListener(btnAddManuallyClickListener)

        binding.btnGetPermission.textBtn.setText(R.string.btn_get_permission)
        binding.btnGetPermission.layoutBtnRemove.setOnClickListener(btnGetPermissionClickListener)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openCamera()
            } else {
                if (BuildConfig.isChinaRegion) {
                    displayPermissionPurpose()
                } else {
                    checkCameraPermission()
                }
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openCamera()
            } else {
                if (BuildConfig.isChinaRegion) {
                    displayPermissionPurpose()
                } else {
                    checkCameraPermission()
                }
            }
        }
    }

    private fun setToolbar() {
        setSupportActionBar(binding.titleBar.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        supportActionBar!!.setTitle(R.string.title_activity_add_device)
        binding.titleBar.toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left)
        binding.titleBar.toolbar.setNavigationOnClickListener {
            if (provisionManager.espDevice != null) {
                provisionManager.espDevice.disconnectDevice()
            }
            setResult(RESULT_CANCELED, intent)
            finish()
        }
    }

    private fun displayPermissionPurpose() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setMessage(R.string.permission_purpose_for_new_version)
        } else {
            builder.setMessage(R.string.permission_purpose)
        }

        builder.setPositiveButton(
            R.string.btn_ok
        ) { dialog, which ->
            dialog.dismiss()
            checkCameraPermission()
        }

        builder.show()
    }

    /**
     * Used to check and request camera permission.
     */
    private fun checkCameraPermission() {
        // Check if the permission is already granted

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // If the permission is not granted, show rationale.

            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.CAMERA
                )
            ) {
                // Show an explanation to the user
                showCameraPermissionExplanation()
            } else {
                // Directly request for permission
                requestCameraPermission()
            }
        } else {
            // Permission already granted, proceed with camera operation
            openCamera()
        }
    }

    private fun checkLocationAndBluetoothPermission() {
        // Check if the permission is already granted

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // If the permission is not granted, show rationale.

            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                // Show an explanation to the user
                showLocationAndBluetoothPermissionExplanation(false)
            } else {
                // Directly request for permission
                requestLocationPermission()
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.BLUETOOTH_SCAN
                        )
                        || ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                    ) {
                        showLocationAndBluetoothPermissionExplanation(true)
                    } else {
                        requestLocationPermission()
                    }
                } else {
                    openCamera()
                }
            } else {
                openCamera()
            }
        }
    }

    private fun showCameraPermissionExplanation() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)

        builder.setTitle(R.string.dialog_title_camera_permission)
        builder.setMessage(R.string.dialog_msg_camera_permission_use)
        builder.setPositiveButton(
            R.string.btn_ok
        ) { dialog, which -> // Request the permission after the user sees the explanation
            requestCameraPermission()
            dialog.dismiss()
        }

        builder.setNegativeButton(
            R.string.btn_cancel
        ) { dialog, which ->
            dialog.dismiss()
            finish()
        }

        builder.show()
    }

    private fun showLocationAndBluetoothPermissionExplanation(includeBtPermission: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)

        if (includeBtPermission) {
            builder.setTitle(R.string.dialog_title_location_bt_permission)
        } else {
            builder.setTitle(R.string.dialog_title_location_permission)
        }
        builder.setMessage(R.string.dialog_msg_location_permission_use)

        builder.setPositiveButton(
            R.string.btn_ok
        ) { dialog, which -> // Request the permission after the user sees the explanation
            requestLocationPermission()
            dialog.dismiss()
        }

        builder.setNegativeButton(
            R.string.btn_cancel
        ) { dialog, which ->
            dialog.dismiss()
            finish()
        }

        builder.show()
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_CAMERA_PERMISSION
        )
    }

    private fun requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                REQUEST_ACCESS_FINE_LOCATION
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_ACCESS_FINE_LOCATION
            )
        }
    }

    private fun openCamera() {

        val isPlayServicesAvailable = Utils.isPlayServicesAvailable(applicationContext)

        if (isPlayServicesAvailable) {
            binding.scannerView.visibility = View.GONE
            binding.cameraPreview.visibility = View.VISIBLE
            binding.overlay.visibility = View.VISIBLE
            binding.clearWindow.visibility = View.VISIBLE
            binding.qrFrame.layoutQrCodeFrame.visibility = View.VISIBLE
        } else {
            binding.scannerView.visibility = View.VISIBLE
            binding.cameraPreview.visibility = View.GONE
            binding.overlay.visibility = View.GONE
            binding.clearWindow.visibility = View.GONE
            binding.qrFrame.layoutQrCodeFrame.visibility = View.GONE
            codeScanner?.startPreview()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (isPlayServicesAvailable) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

                    cameraProviderFuture.addListener({
                        try {
                            // Start QR code scanning using ESPProvisionManager
                            provisionManager.scanQRCode(
                                binding.cameraPreview,
                                this,
                                qrCodeScanListener
                            )
                        } catch (exc: Exception) {
                            Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }, ContextCompat.getMainExecutor(this))
                } else {
                    provisionManager.scanQRCode(codeScanner, qrCodeScanListener)
                }
            } else {
                checkLocationAndBluetoothPermission()
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (isPlayServicesAvailable) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

                    cameraProviderFuture.addListener({
                        try {
                            // Start QR code scanning using ESPProvisionManager
                            provisionManager.scanQRCode(
                                binding.cameraPreview,
                                this,
                                qrCodeScanListener
                            )
                        } catch (exc: Exception) {
                            Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }, ContextCompat.getMainExecutor(this))
                } else {
                    provisionManager.scanQRCode(codeScanner, qrCodeScanListener)
                }
            } else {
                checkLocationAndBluetoothPermission()
            }
        }
    }

    private fun askForDeviceType(securityType: Int) {
        val deviceTypes = resources.getStringArray(R.array.prov_transport_types)
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(true)
        builder.setTitle(R.string.dialog_msg_device_selection)

        builder.setItems(
            deviceTypes
        ) { dialog, position ->
            when (position) {
                0 -> {
                    Utils.createESPDevice(
                        applicationContext,
                        ESPConstants.TransportType.TRANSPORT_BLE,
                        securityType
                    )
                    goToBLEProvisionLanding(securityType)
                }

                1 -> {
                    Utils.createESPDevice(
                        applicationContext,
                        ESPConstants.TransportType.TRANSPORT_SOFTAP,
                        securityType
                    )
                    goToWiFiProvisionLanding(securityType)
                }

                2 -> if (Utils.isPlayServicesAvailable(applicationContext)) {
                    goToGroupSelectionActivity("")
                } else {
                    Log.e(
                        TAG,
                        "Google Play Services not available."
                    )
                    Utils.showPlayServicesWarning(this@AddDeviceActivity)
                }
            }
            dialog.dismiss()
        }
        builder.show()
    }

    private fun showLoading() {
        binding.loader.visibility = View.VISIBLE
        binding.loader.show()
    }

    private fun hideLoading() {
        binding.loader.hide()

        // Hide QR scanning UI
        binding.overlay.visibility = View.GONE
        binding.clearWindow.visibility = View.GONE
        binding.qrFrame.layoutQrCodeFrame.visibility = View.GONE
    }

    private val qrCodeScanListener: QRCodeScanListener = object : QRCodeScanListener {
        override fun qrCodeScanned() {
            runOnUiThread {
                showLoading()
                val vib = getSystemService(VIBRATOR_SERVICE) as Vibrator
                vib.vibrate(50)
                isQrCodeDataReceived = true
            }
        }

        override fun deviceDetected(device: ESPDevice) {
            Log.e(TAG, "Device detected")
            espDevice = device

            runOnUiThread(Runnable {
                if (ActivityCompat.checkSelfPermission(
                        this@AddDeviceActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(
                        TAG,
                        "Location Permission not granted."
                    )
                    return@Runnable
                }
                if (espDevice!!.transportType == ESPConstants.TransportType.TRANSPORT_SOFTAP
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ) {
                    val wifiManager =
                        applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

                    if (!wifiManager.isWifiEnabled) {
                        alertForWiFi()
                        return@Runnable
                    }
                }
                device.connectToDevice()
            })
        }

        override fun onFailure(e: Exception) {
            Log.e(TAG, "Error : " + e.message)

            runOnUiThread {
                hideLoading()
                Toast.makeText(this@AddDeviceActivity, e.message, Toast.LENGTH_LONG)
                    .show()
                finish()
            }
        }

        override fun onFailure(e: Exception, qrCodeData: String) {
            // Called when QR code is not in supported format.
            // Comment below error handling and do whatever you want to do with your QR code data.
            Log.e(TAG, "Error : " + e.message)
            Log.e(TAG, "QR code data : $qrCodeData")

            runOnUiThread {
                hideLoading()
                if (BuildConfig.isMatterSupported && !TextUtils.isEmpty(
                        qrCodeData
                    ) && qrCodeData.contains("MT:")
                ) {
                    // Display group selection screen.
                    if (Utils.isPlayServicesAvailable(applicationContext)) {
                        goToGroupSelectionActivity(qrCodeData)
                    } else {
                        Utils.showPlayServicesWarning(this@AddDeviceActivity)
                    }
                } else {
                    val msg = e.message
                    Toast.makeText(this@AddDeviceActivity, msg, Toast.LENGTH_LONG)
                        .show()
                    finish()
                }
            }
        }
    }

    private fun checkDeviceCapabilities() {
        val versionInfo = espDevice!!.versionInfo
        val rmakerCaps = ArrayList<String>()
        val deviceCaps = espDevice!!.deviceCapabilities

        try {
            val jsonObject = JSONObject(versionInfo)
            val rmakerInfo = jsonObject.optJSONObject("rmaker")

            if (rmakerInfo != null) {
                val rmakerCapabilities = rmakerInfo.optJSONArray("cap")
                if (rmakerCapabilities != null) {
                    for (i in 0 until rmakerCapabilities.length()) {
                        val cap = rmakerCapabilities.getString(i)
                        rmakerCaps.add(cap)
                    }
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            Log.d(TAG, "Version Info JSON not available.")
        }

        var hasClaimCap = false
        var hasCameraClaimCap = false
        if (rmakerCaps.isNotEmpty()) {
            hasClaimCap = rmakerCaps.contains(AppConstants.CAPABILITY_CLAIM)
            hasCameraClaimCap = rmakerCaps.contains(AppConstants.CAPABILITY_CAMERA_CLAIM)
        }

        if (hasClaimCap || hasCameraClaimCap) {
            if (espDevice!!.transportType == ESPConstants.TransportType.TRANSPORT_BLE) {
                goToClaimingActivity(hasCameraClaimCap)
            } else {
                alertForClaimingNotSupported()
            }
        } else {
            if (deviceCaps != null) {
                if (deviceCaps.contains(AppConstants.CAPABILITY_WIFI_SCAN)) {
                    goToWiFiScanActivity()
                } else if (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_SCAN)) {
                    goToThreadConfigActivity(true)
                } else if (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_PROV)) {
                    goToThreadConfigActivity(false)
                } else {
                    goToWiFiConfigActivity()
                }
            } else {
                goToWiFiConfigActivity()
            }
        }
    }

    private fun alertForWiFi() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setMessage(R.string.error_wifi_off)

        builder.setPositiveButton(
            R.string.btn_ok
        ) { dialog, which ->
            dialog.dismiss()
            espDevice = null
            hideLoading()
            if (codeScanner != null) {
                codeScanner!!.releaseResources()
                codeScanner!!.startPreview()

                if (ActivityCompat.checkSelfPermission(
                        this@AddDeviceActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(
                        this@AddDeviceActivity,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    provisionManager!!.scanQRCode(codeScanner, qrCodeScanListener)
                }
            }
        }

        builder.show()
    }

    private fun alertForClaimingNotSupported() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setMessage(R.string.error_claiming_not_supported)

        builder.setPositiveButton(
            R.string.btn_ok
        ) { dialog, which ->
            if (provisionManager!!.espDevice != null) {
                provisionManager!!.espDevice.disconnectDevice()
            }
            dialog.dismiss()
            espDevice = null
            hideLoading()
            finish()
        }

        builder.show()
    }

    private fun showLocationPermissionAlertDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setMessage(R.string.error_location_permission)

        builder.setPositiveButton(
            R.string.action_settings
        ) { dialog, which -> navigateToAppSettings() }

        builder.setNegativeButton(
            R.string.btn_cancel
        ) { dialog, which -> dialog.dismiss() }

        val alertDialog = builder.create()
        if (!isFinishing) {
            alertDialog.show()
        }
    }

    private fun goToBLEProvisionLanding(secType: Int) {
        if (ActivityCompat.checkSelfPermission(
                this@AddDeviceActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            val intent = Intent(applicationContext, BLEProvisionLanding::class.java)
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, secType)

            if (espDevice != null) {
                intent.putExtra(AppConstants.KEY_DEVICE_NAME, espDevice!!.deviceName)
                intent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, espDevice!!.proofOfPossession)
                intent.putExtra(AppConstants.KEY_SSID, connectedNetwork)
            }
            startActivity(intent)
        } else {
            checkLocationAndBluetoothPermission()
        }
    }

    private fun goToWiFiProvisionLanding(secType: Int) {
        if (ActivityCompat.checkSelfPermission(
                this@AddDeviceActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            val intent = Intent(applicationContext, ProvisionLanding::class.java)
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, secType)

            if (espDevice != null) {
                intent.putExtra(AppConstants.KEY_DEVICE_NAME, espDevice!!.deviceName)
                intent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, espDevice!!.proofOfPossession)
                intent.putExtra(AppConstants.KEY_SSID, connectedNetwork)
            }
            startActivity(intent)
        } else {
            checkLocationAndBluetoothPermission()
        }
    }

    private fun goToWiFiScanActivity() {
        finish()
        val wifiListIntent = Intent(applicationContext, WiFiScanActivity::class.java)
        wifiListIntent.putExtra(AppConstants.KEY_SSID, connectedNetwork)
        startActivity(wifiListIntent)
    }

    private fun goToWiFiConfigActivity() {
        finish()
        val wifiConfigIntent = Intent(applicationContext, WiFiConfigActivity::class.java)
        wifiConfigIntent.putExtra(AppConstants.KEY_SSID, connectedNetwork)
        startActivity(wifiConfigIntent)
    }

    private fun goToThreadConfigActivity(scanCapAvailable: Boolean) {
        finish()
        val threadConfigIntent = Intent(applicationContext, ThreadConfigActivity::class.java)
        threadConfigIntent.putExtras(intent)
        threadConfigIntent.putExtra(AppConstants.KEY_THREAD_SCAN_AVAILABLE, scanCapAvailable)
        startActivity(threadConfigIntent)
    }

    private fun goToClaimingActivity(isCameraClaim: Boolean) {
        finish()
        val claimingIntent = Intent(applicationContext, ClaimingActivity::class.java)
        claimingIntent.putExtra(AppConstants.KEY_SSID, connectedNetwork)
        claimingIntent.putExtra(AppConstants.KEY_IS_CAMERA_CLAIM, isCameraClaim)
        startActivity(claimingIntent)
    }

    private fun goToGroupSelectionActivity(qrCodeData: String) {
        finish()
        val intent = Intent(applicationContext, GroupSelectionActivity::class.java)
        intent.putExtra(AppConstants.KEY_ON_BOARD_PAYLOAD, qrCodeData)
        startActivity(intent)
    }

    private fun askForManualDeviceConnection() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(true)
        builder.setMessage(R.string.dialog_msg_connect_device_manually)

        // Set up the buttons
        builder.setPositiveButton(
            R.string.btn_yes
        ) { dialog, which ->
            dialog.dismiss()
            if (espDevice != null) {
                if (espDevice!!.transportType == ESPConstants.TransportType.TRANSPORT_SOFTAP) {
                    goToWiFiProvisionLanding(espDevice!!.securityType.ordinal)
                } else {
                    goToBLEProvisionLanding(espDevice!!.securityType.ordinal)
                }
            } else {
                finish()
            }
        }

        builder.setNegativeButton(
            R.string.btn_cancel
        ) { dialog, which ->
            dialog.dismiss()
            finish()
        }

        val alertDialog = builder.create()
        if (!isFinishing) {
            alertDialog.show()
        }
    }

    private fun alertForDeviceNotSupported(msg: String) {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)

        builder.setTitle(R.string.error_title)
        builder.setMessage(msg)

        // Set up the buttons
        builder.setPositiveButton(
            R.string.btn_ok
        ) { dialog, which ->
            if (provisionManager!!.espDevice != null) {
                provisionManager!!.espDevice.disconnectDevice()
            }
            dialog.dismiss()
            finish()
        }
        builder.show()
    }

    private val wifiSsid: String?
        get() {
            var ssid: String? = null
            val wifiManager =
                applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
                ssid = wifiInfo.ssid
                ssid = ssid.replace("\"", "")
            }
            return ssid
        }
}
