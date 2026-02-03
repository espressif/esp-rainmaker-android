// Copyright 2026 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.ble

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.espressif.AppConstants
import com.espressif.EspApplication
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPDevice
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.provisioning.listeners.BleScanListener
import com.espressif.provisioning.listeners.ResponseListener
import com.espressif.ui.models.EspNode
import com.google.gson.JsonObject
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized manager for BLE local control connections.
 *
 * Connection flow (lazy / on-demand):
 * 1. Single broad scan with "PROV_" prefix discovers all nearby ESP BLE devices
 * 2. Discovered devices are matched against nodes with ble_local_ctrl metadata
 *    and marked DISCOVERED (shown as "Reachable on BLE" — no GATT connection yet)
 * 3. Actual BLE connection + session init happens on demand when the user
 *    controls a param or opens the device detail screen
 */
class BleLocalControlManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "BleLocalCtrlMgr"
        private const val SCAN_TIMEOUT_MS = 10000L
        private const val SCAN_RETRY_DELAY_MS = 2000L
        private const val MAX_SCAN_RETRIES = 3
        private const val BLE_DEVICE_PREFIX = "PROV_"
        private const val BLE_OPERATION_TIMEOUT_MS = 5000L

        @Volatile
        private var instance: BleLocalControlManager? = null

        @JvmStatic
        fun getInstance(context: Context): BleLocalControlManager {
            return instance ?: synchronized(this) {
                instance ?: BleLocalControlManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    enum class ConnectionState {
        DISCONNECTED, DISCOVERED, CONNECTING, SESSION_INIT, CONNECTED
    }

    data class BleDeviceConnection(
        var espDevice: ESPDevice? = null,
        val bleInfo: EspNode.BleLocalCtrlInfo,
        var state: ConnectionState = ConnectionState.DISCONNECTED,
        val nodeId: String,
        var bluetoothDevice: BluetoothDevice? = null,
        var serviceUuid: String = ""
    )

    interface BleConnectionListener {
        fun onDeviceConnected(nodeId: String)
        fun onDeviceDisconnected(nodeId: String)
        fun onAllDevicesProcessed()
    }

    private val espApp = appContext as EspApplication
    private val provisionManager = ESPProvisionManager.getInstance(appContext)
    private val handler = Handler(Looper.getMainLooper())

    private val connectionMap = ConcurrentHashMap<String, BleDeviceConnection>()
    private var currentConnectingNodeId: String? = null
    private var isBleScanning = false
    private var scanRetryCount = 0

    private val listeners = mutableListOf<BleConnectionListener>()

    // Callback for on-demand connectDevice() / connectAndSendParams()
    private var connectCallback: ((Boolean) -> Unit)? = null

    // Tracks nodes that have getParamsWithTimestamp in progress to avoid concurrent BLE reads
    private val proxyReadInProgress = ConcurrentHashMap<String, AtomicBoolean>()

    // --- Public API ---

    fun addListener(listener: BleConnectionListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: BleConnectionListener) {
        listeners.remove(listener)
    }

    fun isConnected(nodeId: String): Boolean {
        return connectionMap[nodeId]?.state == ConnectionState.CONNECTED
    }

    fun isDiscovered(nodeId: String): Boolean {
        return connectionMap[nodeId]?.state == ConnectionState.DISCOVERED
    }

    fun getEspDevice(nodeId: String): ESPDevice? {
        val conn = connectionMap[nodeId]
        return if (conn?.state == ConnectionState.CONNECTED) conn.espDevice else null
    }

    fun getDeviceCapabilities(nodeId: String): ArrayList<String>? {
        return getEspDevice(nodeId)?.deviceCapabilities
    }

    /**
     * Re-apply BLE statuses on all tracked nodes after a cloud data refresh
     * which replaces EspNode objects in nodeMap and resets their status.
     */
    fun reapplyBleStatusToConnectedNodes() {
        for ((nodeId, conn) in connectionMap) {
            val node = espApp.nodeMap[nodeId] ?: continue
            when (conn.state) {
                ConnectionState.CONNECTED -> {
                    if (node.nodeStatus != AppConstants.NODE_STATUS_BLE_LOCAL) {
                        Log.d(TAG, "Re-applying BLE_LOCAL status for node $nodeId")
                        node.nodeStatus = AppConstants.NODE_STATUS_BLE_LOCAL
                    }
                }
                ConnectionState.DISCOVERED -> {
                    if (node.nodeStatus != AppConstants.NODE_STATUS_BLE_DISCOVERABLE) {
                        Log.d(TAG, "Re-applying BLE_DISCOVERABLE status for node $nodeId")
                        node.nodeStatus = AppConstants.NODE_STATUS_BLE_DISCOVERABLE
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * Scan for all nearby BLE devices, match with nodes that have
     * ble_local_ctrl metadata, and mark them as DISCOVERED.
     * No GATT connections are made here.
     */
    fun scanForDevices(activity: Activity) {
        if (isBleScanning) {
            Log.d(TAG, "Already scanning, skipping")
            return
        }

        if (!hasBlePermissions(activity)) {
            Log.d(TAG, "BLE permissions not granted")
            return
        }

        val bleDevices = collectBleDevices()
        if (bleDevices.isEmpty()) {
            Log.d(TAG, "No BLE local control devices found in node metadata")
            return
        }

        for ((nodeId, bleInfo) in bleDevices) {
            val existing = connectionMap[nodeId]
            if (existing == null || existing.state == ConnectionState.DISCONNECTED) {
                connectionMap[nodeId] = BleDeviceConnection(
                    bleInfo = bleInfo,
                    nodeId = nodeId
                )
            }
        }

        val pendingNodes = connectionMap.values.filter {
            it.state == ConnectionState.DISCONNECTED && it.bluetoothDevice == null
        }
        if (pendingNodes.isEmpty()) {
            Log.d(TAG, "All BLE devices already discovered or connected")
            notifyAllDevicesProcessed()
            return
        }

        Log.d(TAG, "Starting BLE scan for ${pendingNodes.size} devices")
        startBroadScan()
    }

    /**
     * Connect to a single discovered device on demand.
     * Call this when the user opens a device detail screen or needs BLE access.
     */
    fun connectDevice(nodeId: String, callback: (Boolean) -> Unit) {
        val conn = connectionMap[nodeId]
        if (conn == null || conn.bluetoothDevice == null) {
            Log.e(TAG, "connectDevice: no discovered device for node $nodeId")
            callback(false)
            return
        }

        if (conn.state == ConnectionState.CONNECTED) {
            Log.d(TAG, "connectDevice: already connected for $nodeId")
            callback(true)
            return
        }

        if (conn.state == ConnectionState.CONNECTING || conn.state == ConnectionState.SESSION_INIT) {
            Log.d(TAG, "connectDevice: connection already in progress for $nodeId")
            callback(false)
            return
        }

        if (currentConnectingNodeId != null) {
            Log.d(TAG, "connectDevice: another device is connecting ($currentConnectingNodeId), queuing")
            callback(false)
            return
        }

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }

        currentConnectingNodeId = nodeId
        connectCallback = callback
        connectToBleDevice(conn)
    }

    /**
     * Connect (if needed) and send params in one call.
     * If already connected, sends immediately. If discovered, connects first.
     */
    fun connectAndSendParams(nodeId: String, body: JsonObject, listener: ApiResponseListener) {

        // Priority 2: BLE local control
        if (isConnected(nodeId)) {
            sendParams(nodeId, body, listener)
            return
        }

        if (isDiscovered(nodeId)) {
            connectDevice(nodeId) { success ->
                if (success) {
                    sendParams(nodeId, body, listener)
                } else {
                    listener.onNetworkFailure(Exception("BLE connect failed for $nodeId"))
                }
            }
            return
        }

        listener.onNetworkFailure(Exception("BLE device not available for $nodeId"))
    }

    fun disconnectAll() {
        Log.d(TAG, "Disconnecting all BLE devices")
        stopBleScan()
        currentConnectingNodeId = null
        connectCallback = null

        for ((nodeId, conn) in connectionMap) {
            if (conn.state == ConnectionState.CONNECTED) {
                try {
                    conn.espDevice?.disconnectDevice()
                } catch (e: Exception) {
                    Log.e(TAG, "Error disconnecting device $nodeId: ${e.message}")
                }
                notifyDeviceDisconnected(nodeId)
            }
            val node = espApp.nodeMap[nodeId]
            if (node != null) {
                node.nodeStatus = AppConstants.NODE_STATUS_OFFLINE
            }
        }
        connectionMap.clear()

        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    fun disconnectDevice(nodeId: String) {
        val conn = connectionMap[nodeId] ?: return
        if (conn.state == ConnectionState.CONNECTED) {
            try {
                conn.espDevice?.disconnectDevice()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting device $nodeId: ${e.message}")
            }
            val node = espApp.nodeMap[nodeId]
            if (node != null) {
                node.nodeStatus = AppConstants.NODE_STATUS_OFFLINE
            }
            notifyDeviceDisconnected(nodeId)
        }
        conn.state = ConnectionState.DISCONNECTED
        conn.espDevice = null
        connectionMap.remove(nodeId)
    }

    fun sendParams(nodeId: String, body: JsonObject, listener: ApiResponseListener) {
        val espDevice = getEspDevice(nodeId)
        if (espDevice == null) {
            listener.onNetworkFailure(Exception("BLE not connected for node $nodeId"))
            return
        }

        val jsonStr = body.toString()
        val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)
        Log.d(TAG, "Sending params via BLE for $nodeId: $jsonStr")

        val callbackFired = AtomicBoolean(false)

        val timeoutRunnable = Runnable {
            if (callbackFired.compareAndSet(false, true)) {
                Log.e(TAG, "BLE set_params timed out for $nodeId, marking disconnected")
                handleBleOperationTimeout(nodeId)
                listener.onNetworkFailure(Exception("BLE operation timed out for $nodeId"))
            }
        }
        handler.postDelayed(timeoutRunnable, BLE_OPERATION_TIMEOUT_MS)

        espDevice.sendDataToCustomEndPoint(
            AppConstants.HANDLER_SET_PARAMS,
            jsonBytes,
            object : ResponseListener {
                override fun onSuccess(returnData: ByteArray?) {
                    if (callbackFired.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable)
                        Log.d(TAG, "BLE set_params success for $nodeId")
                        handler.post { listener.onSuccess(null) }
                    }
                }

                override fun onFailure(e: Exception) {
                    if (callbackFired.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeoutRunnable)
                        Log.e(TAG, "BLE set_params failed for $nodeId: ${e.message}")
                        handleBleOperationTimeout(nodeId)
                        handler.post { listener.onResponseFailure(e) }
                    }
                }
            }
        )
    }

    fun queryParams(
        nodeId: String,
        onResult: (org.json.JSONObject?) -> Unit
    ) {
        if (isProxyReadInProgress(nodeId)) {
            Log.w(TAG, "Skipping queryParams for $nodeId — getParamsWithTimestamp is in progress")
            onResult(null)
            return
        }

        val espDevice = getEspDevice(nodeId)
        if (espDevice == null) {
            Log.e(TAG, "Cannot query params: BLE not connected for node $nodeId")
            onResult(null)
            return
        }

        Log.d(TAG, "Querying params via BLE for $nodeId")
        val dataBuffer = ArrayList<Byte>()
        val callbackFired = AtomicBoolean(false)

        val wrappedResult: (org.json.JSONObject?) -> Unit = { json ->
            if (callbackFired.compareAndSet(false, true)) {
                onResult(json)
            }
        }

        val timeoutRunnable = Runnable {
            if (callbackFired.compareAndSet(false, true)) {
                Log.e(TAG, "BLE queryParams timed out for $nodeId, marking disconnected")
                handleBleOperationTimeout(nodeId)
                onResult(null)
            }
        }
        handler.postDelayed(timeoutRunnable, BLE_OPERATION_TIMEOUT_MS)

        getParamsChunk(espDevice, nodeId, 0, dataBuffer, null) { json ->
            handler.removeCallbacks(timeoutRunnable)
            wrappedResult(json)
        }
    }

    // --- Internal: scan phase ---

    private fun collectBleDevices(): Map<String, EspNode.BleLocalCtrlInfo> {
        val result = mutableMapOf<String, EspNode.BleLocalCtrlInfo>()
        for ((nodeId, node) in espApp.nodeMap) {
            val bleInfo = node.getBleLocalCtrlInfo()
            if (bleInfo != null) {
                result[nodeId] = bleInfo
            }
        }
        return result
    }

    private fun startBroadScan() {
        scanRetryCount = 0
        attemptBleScan()
    }

    private fun attemptBleScan() {
        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter is null - BLE not supported on this device")
            onScanPhaseComplete()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled. Please enable Bluetooth to scan for BLE devices.")
            onScanPhaseComplete()
            return
        }

        isBleScanning = true
        Log.d(TAG, "Starting broad BLE scan with prefix '$BLE_DEVICE_PREFIX' (attempt ${scanRetryCount + 1})")

        provisionManager.searchBleEspDevices(BLE_DEVICE_PREFIX, bleScanListener)

        handler.postDelayed({
            if (isBleScanning) {
                Log.d(TAG, "Broad scan timeout reached, stopping scan")
                stopBleScan()
                onScanPhaseComplete()
            }
        }, SCAN_TIMEOUT_MS)
    }

    private fun stopBleScan() {
        if (isBleScanning) {
            isBleScanning = false
            try {
                provisionManager.stopBleScan()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping BLE scan: ${e.message}")
            }
        }
    }

    /**
     * After scan completes, mark matched devices as DISCOVERED and set
     * their node status to BLE_DISCOVERABLE. No GATT connection is made.
     */
    private fun onScanPhaseComplete() {
        val matched = connectionMap.values.filter {
            it.state == ConnectionState.DISCONNECTED && it.bluetoothDevice != null
        }

        if (matched.isEmpty()) {
            Log.d(TAG, "No BLE devices discovered during scan")
        } else {
            for (conn in matched) {
                conn.state = ConnectionState.DISCOVERED
                val node = espApp.nodeMap[conn.nodeId]
                if (node != null) {
                    node.nodeStatus = AppConstants.NODE_STATUS_BLE_DISCOVERABLE
                }
                Log.d(TAG, "Marked DISCOVERED: ${conn.bleInfo.name} (node: ${conn.nodeId})")
            }
            Log.d(TAG, "Scan complete. ${matched.size} devices marked as discoverable")
        }

        notifyAllDevicesProcessed()
    }

    // --- Internal: on-demand connect ---

    private fun connectToBleDevice(conn: BleDeviceConnection) {
        val bluetoothDevice = conn.bluetoothDevice ?: return

        Log.d(TAG, "Connecting to BLE device: ${bluetoothDevice.name} for node ${conn.nodeId}")
        conn.state = ConnectionState.CONNECTING

        val espDevice = ESPDevice(appContext, ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_1)
        espDevice.proofOfPossession = conn.bleInfo.pop
        espDevice.bluetoothDevice = bluetoothDevice
        espDevice.primaryServiceUuid = conn.serviceUuid
        conn.espDevice = espDevice

        espDevice.connectBLEDevice(bluetoothDevice, conn.serviceUuid)
    }

    private fun initBleSession(nodeId: String) {
        val conn = connectionMap[nodeId] ?: return
        val espDevice = conn.espDevice ?: return

        conn.state = ConnectionState.SESSION_INIT
        Log.d(TAG, "Initializing BLE session for $nodeId")

        espDevice.initSession(object : ResponseListener {
            override fun onSuccess(returnData: ByteArray?) {
                Log.d(TAG, "BLE session success for $nodeId")
                handler.post {
                    conn.state = ConnectionState.CONNECTED

                    val espNode = espApp.nodeMap[nodeId]
                    if (espNode != null) {
                        espNode.nodeStatus = AppConstants.NODE_STATUS_BLE_LOCAL
                    }

                    notifyDeviceConnected(nodeId)

                    val cb = connectCallback
                    connectCallback = null
                    currentConnectingNodeId = null
                    cb?.invoke(true)
                }
            }

            override fun onFailure(e: Exception) {
                Log.e(TAG, "BLE session failed for $nodeId: ${e.message}")
                handler.post {
                    conn.state = ConnectionState.DISCOVERED
                    conn.espDevice = null

                    val cb = connectCallback
                    connectCallback = null
                    currentConnectingNodeId = null
                    cb?.invoke(false)
                }
            }
        })
    }

    // --- BLE scan listener ---

    private val bleScanListener = object : BleScanListener {
        override fun scanStartFailed() {
            isBleScanning = false
            scanRetryCount++
            if (scanRetryCount < MAX_SCAN_RETRIES) {
                Log.w(TAG, "BLE scan start failed, retrying in ${SCAN_RETRY_DELAY_MS}ms (attempt $scanRetryCount/$MAX_SCAN_RETRIES)")
                handler.postDelayed({ attemptBleScan() }, SCAN_RETRY_DELAY_MS)
            } else {
                Log.e(TAG, "BLE scan start failed after $MAX_SCAN_RETRIES attempts")
                handler.post { onScanPhaseComplete() }
            }
        }

        override fun onPeripheralFound(device: BluetoothDevice, scanResult: ScanResult) {
            val deviceName = scanResult.scanRecord?.deviceName ?: return

            for (conn in connectionMap.values) {
                if (conn.state == ConnectionState.DISCONNECTED
                    && conn.bluetoothDevice == null
                    && deviceName == conn.bleInfo.name
                ) {
                    val serviceUuid = if (scanResult.scanRecord?.serviceUuids?.isNotEmpty() == true) {
                        scanResult.scanRecord!!.serviceUuids!![0].toString()
                    } else {
                        ""
                    }
                    conn.bluetoothDevice = device
                    conn.serviceUuid = serviceUuid
                    Log.d(TAG, "Scan matched: $deviceName -> node ${conn.nodeId}")
                    break
                }
            }
        }

        override fun scanCompleted() {
            Log.d(TAG, "BLE scan completed")
            isBleScanning = false
            handler.post { onScanPhaseComplete() }
        }

        override fun onFailure(e: Exception) {
            Log.e(TAG, "BLE scan failure: ${e.message}")
            isBleScanning = false
            handler.post { onScanPhaseComplete() }
        }
    }

    // --- EventBus: DeviceConnectionEvent ---

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: DeviceConnectionEvent) {
        val nodeId = currentConnectingNodeId ?: return

        when (event.eventType) {
            ESPConstants.EVENT_DEVICE_CONNECTED -> {
                Log.d(TAG, "BLE device connected for node $nodeId - initializing session")
                initBleSession(nodeId)
            }
            ESPConstants.EVENT_DEVICE_DISCONNECTED -> {
                Log.d(TAG, "BLE device disconnected for node $nodeId")
                val conn = connectionMap[nodeId]
                if (conn != null && conn.state == ConnectionState.CONNECTED) {
                    conn.state = ConnectionState.DISCOVERED
                    conn.espDevice = null
                    val espNode = espApp.nodeMap[nodeId]
                    if (espNode != null) {
                        espNode.nodeStatus = AppConstants.NODE_STATUS_BLE_DISCOVERABLE
                    }
                    notifyDeviceDisconnected(nodeId)
                } else {
                    conn?.state = ConnectionState.DISCOVERED
                    conn?.espDevice = null
                    val cb = connectCallback
                    connectCallback = null
                    currentConnectingNodeId = null
                    cb?.invoke(false)
                }
            }
            ESPConstants.EVENT_DEVICE_CONNECTION_FAILED -> {
                Log.e(TAG, "BLE device connection failed for node $nodeId")
                val conn = connectionMap[nodeId]
                conn?.state = ConnectionState.DISCOVERED
                conn?.espDevice = null
                val cb = connectCallback
                connectCallback = null
                currentConnectingNodeId = null
                cb?.invoke(false)
            }
        }
    }

    // --- Chunked get_params via protobuf ---

    private fun getParamsChunk(
        espDevice: ESPDevice,
        nodeId: String,
        offset: Int,
        dataBuffer: ArrayList<Byte>,
        totalLen: Int?,
        onResult: (org.json.JSONObject?) -> Unit
    ) {
        val cmdGetData = rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.CmdGetData.newBuilder()
            .setDataType(rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlDataType.TypeParams)
            .setOffset(offset)
            .setHasTimestamp(false)
            .build()

        val payload = rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlPayload.newBuilder()
            .setMsg(rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlMsgType.TypeCmdGetData)
            .setCmdGetData(cmdGetData)
            .build()

        espDevice.sendDataToCustomEndPoint(
            AppConstants.HANDLER_GET_PARAMS,
            payload.toByteArray(),
            object : ResponseListener {
                override fun onSuccess(returnData: ByteArray?) {
                    if (returnData == null || returnData.isEmpty()) {
                        Log.w(TAG, "get_params returned empty data for $nodeId")
                        handler.post { onResult(null) }
                        return
                    }

                    try {
                        val response = rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlPayload.parseFrom(returnData)
                        if (response.msg != rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlMsgType.TypeRespGetData) {
                            Log.e(TAG, "Unexpected message type for $nodeId: ${response.msg}")
                            handler.post { onResult(null) }
                            return
                        }

                        val respGetData = response.respGetData
                        if (respGetData.status != rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlStatus.Success) {
                            Log.e(TAG, "Device returned error for $nodeId: ${respGetData.status}")
                            handler.post { onResult(null) }
                            return
                        }

                        val buf = respGetData.buf
                        val respOffset = buf.offset
                        val payloadBytes = buf.payload.toByteArray()
                        val respTotalLen = buf.totalLen

                        if (respOffset != offset) {
                            Log.e(TAG, "Offset mismatch for $nodeId: expected $offset, got $respOffset")
                            handler.post { onResult(null) }
                            return
                        }

                        val currentTotalLen = totalLen ?: respTotalLen

                        for (b in payloadBytes) {
                            dataBuffer.add(b)
                        }
                        val newOffset = offset + payloadBytes.size

                        Log.d(TAG, "Params chunk for $nodeId: offset=$respOffset, len=${payloadBytes.size}, progress=$newOffset/$currentTotalLen")

                        if (newOffset >= currentTotalLen) {
                            val completeData = ByteArray(dataBuffer.size)
                            for (i in dataBuffer.indices) {
                                completeData[i] = dataBuffer[i]
                            }
                            val jsonStr = String(completeData, Charsets.UTF_8)
                            Log.d(TAG, "Complete params JSON for $nodeId: $jsonStr")

                            try {
                                val jsonObject = org.json.JSONObject(jsonStr)
                                handler.post { onResult(jsonObject) }
                            } catch (e: org.json.JSONException) {
                                Log.e(TAG, "Failed to parse params JSON for $nodeId: ${e.message}")
                                handler.post { onResult(null) }
                            }
                        } else {
                            getParamsChunk(espDevice, nodeId, newOffset, dataBuffer, currentTotalLen, onResult)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse get_params response for $nodeId: ${e.message}")
                        handler.post { onResult(null) }
                    }
                }

                override fun onFailure(e: Exception) {
                    Log.e(TAG, "get_params chunk failed for $nodeId: ${e.message}")
                    handler.post { onResult(null) }
                }
            }
        )
    }

    // --- Chunked get_params with timestamp (for proxy reporting) ---

    fun isProxyReadInProgress(nodeId: String): Boolean {
        return proxyReadInProgress[nodeId]?.get() == true
    }

    fun getParamsWithTimestamp(nodeId: String, onResult: (org.json.JSONObject?) -> Unit) {
        val espDevice = getEspDevice(nodeId)
        if (espDevice == null) {
            Log.e(TAG, "Cannot get params with timestamp: BLE not connected for node $nodeId")
            onResult(null)
            return
        }

        val busy = proxyReadInProgress.getOrPut(nodeId) { AtomicBoolean(false) }
        if (!busy.compareAndSet(false, true)) {
            Log.w(TAG, "getParamsWithTimestamp already in progress for $nodeId, skipping duplicate call")
            onResult(null)
            return
        }

        val wrappedResult: (org.json.JSONObject?) -> Unit = { json ->
            busy.set(false)
            onResult(json)
        }

        val timestamp = System.currentTimeMillis() / 1000
        Log.d(TAG, "Getting params with timestamp=$timestamp for $nodeId")
        getParamsChunkWithTimestamp(espDevice, nodeId, 0, timestamp, ArrayList(), null, wrappedResult)
    }

    private fun getParamsChunkWithTimestamp(
        espDevice: ESPDevice,
        nodeId: String,
        offset: Int,
        timestamp: Long?,
        dataBuffer: ArrayList<Byte>,
        totalLen: Int?,
        onResult: (org.json.JSONObject?) -> Unit
    ) {
        val cmdBuilder = rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.CmdGetData.newBuilder()
            .setDataType(rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlDataType.TypeParams)
            .setOffset(offset)

        if (timestamp != null) {
            cmdBuilder.setTimestamp(timestamp)
            cmdBuilder.setHasTimestamp(true)
        } else {
            cmdBuilder.setTimestamp(0)
            cmdBuilder.setHasTimestamp(false)
        }

        val payload = rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlPayload.newBuilder()
            .setMsg(rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlMsgType.TypeCmdGetData)
            .setCmdGetData(cmdBuilder.build())
            .build()

        espDevice.sendDataToCustomEndPoint(
            AppConstants.HANDLER_GET_PARAMS,
            payload.toByteArray(),
            object : ResponseListener {
                override fun onSuccess(returnData: ByteArray?) {
                    if (returnData == null || returnData.isEmpty()) {
                        Log.w(TAG, "get_params (timestamped) returned empty data for $nodeId")
                        handler.post { onResult(null) }
                        return
                    }

                    try {
                        val response = rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlPayload.parseFrom(returnData)
                        if (response.msg != rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlMsgType.TypeRespGetData) {
                            Log.e(TAG, "Unexpected message type (timestamped) for $nodeId: ${response.msg}")
                            handler.post { onResult(null) }
                            return
                        }

                        val respGetData = response.respGetData
                        if (respGetData.status != rmaker_prov_local_ctrl.EspRmakerProvLocalCtrl.RMakerLocalCtrlStatus.Success) {
                            Log.e(TAG, "Device returned error (timestamped) for $nodeId: ${respGetData.status}")
                            handler.post { onResult(null) }
                            return
                        }

                        val buf = respGetData.buf
                        val respOffset = buf.offset
                        val payloadBytes = buf.payload.toByteArray()
                        val respTotalLen = buf.totalLen

                        if (respOffset != offset) {
                            Log.e(TAG, "Offset mismatch (timestamped) for $nodeId: expected $offset, got $respOffset")
                            handler.post { onResult(null) }
                            return
                        }

                        val currentTotalLen = totalLen ?: respTotalLen

                        for (b in payloadBytes) {
                            dataBuffer.add(b)
                        }
                        val newOffset = offset + payloadBytes.size

                        Log.d(TAG, "Timestamped params chunk for $nodeId: offset=$respOffset, len=${payloadBytes.size}, progress=$newOffset/$currentTotalLen")

                        if (newOffset >= currentTotalLen) {
                            val completeData = ByteArray(dataBuffer.size)
                            for (i in dataBuffer.indices) {
                                completeData[i] = dataBuffer[i]
                            }
                            val jsonStr = String(completeData, Charsets.UTF_8)
                            Log.d(TAG, "Complete timestamped params JSON for $nodeId: $jsonStr")

                            try {
                                val jsonObject = org.json.JSONObject(jsonStr)
                                handler.post { onResult(jsonObject) }
                            } catch (e: org.json.JSONException) {
                                Log.e(TAG, "Failed to parse timestamped params JSON for $nodeId: ${e.message}")
                                handler.post { onResult(null) }
                            }
                        } else {
                            getParamsChunkWithTimestamp(espDevice, nodeId, newOffset, null, dataBuffer, currentTotalLen, onResult)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse timestamped get_params response for $nodeId: ${e.message}")
                        handler.post { onResult(null) }
                    }
                }

                override fun onFailure(e: Exception) {
                    Log.e(TAG, "get_params (timestamped) chunk failed for $nodeId: ${e.message}")
                    handler.post { onResult(null) }
                }
            }
        )
    }

    // --- Permissions ---

    private fun hasBlePermissions(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Handles a BLE operation timeout or failure by cleaning up the stale
     * connection state. Sets the connection back to DISCOVERED so a
     * reconnect can be attempted on the next user action.
     */
    private fun handleBleOperationTimeout(nodeId: String) {
        val conn = connectionMap[nodeId] ?: return
        Log.w(TAG, "Cleaning up stale BLE connection for $nodeId")
        try {
            conn.espDevice?.disconnectDevice()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting stale device $nodeId: ${e.message}")
        }
        conn.espDevice = null
        if (conn.bluetoothDevice != null) {
            conn.state = ConnectionState.DISCOVERED
            val node = espApp.nodeMap[nodeId]
            if (node != null) {
                node.nodeStatus = AppConstants.NODE_STATUS_BLE_DISCOVERABLE
            }
        } else {
            conn.state = ConnectionState.DISCONNECTED
            connectionMap.remove(nodeId)
            val node = espApp.nodeMap[nodeId]
            if (node != null) {
                if (node.isOnline) {
                    node.nodeStatus = AppConstants.NODE_STATUS_ONLINE
                } else {
                    node.nodeStatus = AppConstants.NODE_STATUS_OFFLINE
                }
            }
        }
        notifyDeviceDisconnected(nodeId)
    }

    // --- Listener notifications ---

    private fun notifyDeviceConnected(nodeId: String) {
        for (listener in listeners) {
            listener.onDeviceConnected(nodeId)
        }
    }

    private fun notifyDeviceDisconnected(nodeId: String) {
        for (listener in listeners) {
            listener.onDeviceDisconnected(nodeId)
        }
    }

    private fun notifyAllDevicesProcessed() {
        for (listener in listeners) {
            listener.onAllDevicesProcessed()
        }
    }
}
