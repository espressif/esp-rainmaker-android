/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.espressif.matter

import android.util.Log
import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipClusters.BasicInformationCluster
import chip.devicecontroller.ChipStructs
import chip.devicecontroller.model.ChipAttributePath
import matter.tlv.AnonymousTag
import matter.tlv.TlvWriter
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Encapsulates the information of interest when querying a Matter device just after it has been
 * commissioned.
 */
data class DeviceMatterInfo(
    val endpoint: Int,
    val types: List<Long>,
    val serverClusters: List<Any>,
    val clientClusters: List<Any>
)

/** Class to facilitate access to Clusters functionality. */
class ClustersHelper constructor(private val chipClient: ChipClient) {

    companion object {
        const val TAG = "ClustersHelper"
    }

    // -----------------------------------------------------------------------------------------------
    // Convenience functions

    /** Fetches MatterDeviceInfo for each endpoint supported by the device. */
    suspend fun fetchDeviceMatterInfo(nodeId: Long): List<DeviceMatterInfo> {
        Log.d(TAG, "fetchDeviceMatterInfo(): nodeId [${nodeId}]")
        val matterDeviceInfoList = arrayListOf<DeviceMatterInfo>()
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(nodeId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                e.printStackTrace()
                return emptyList()
            }
        fetchDeviceMatterInfo(nodeId, connectedDevicePtr, 0, matterDeviceInfoList)
        return matterDeviceInfoList
    }

    // To call fetchDeviceMatterInfo function from Java
    fun fetchDeviceMatterInfoAsync(nodeId: Long): CompletableFuture<List<DeviceMatterInfo>> =
        GlobalScope.future { fetchDeviceMatterInfo(nodeId) }

    /** Fetches MatterDeviceInfo for a specific endpoint. */
    private suspend fun fetchDeviceMatterInfo(
        nodeId: Long,
        connectedDevicePtr: Long,
        endpointInt: Int,
        matterDeviceInfoList: ArrayList<DeviceMatterInfo>
    ) {
        Log.d(TAG, "fetchDeviceMatterInfo(): nodeId [${nodeId}] endpoint [$endpointInt]")

        val partsListAttribute =
            readDescriptorClusterPartsListAttribute(connectedDevicePtr, endpointInt)
        Log.d(TAG, "partsListAttribute [${partsListAttribute}]")

        // DeviceListAttribute
        val deviceListAttribute =
            readDescriptorClusterDeviceListAttribute(connectedDevicePtr, endpointInt)
        val types = arrayListOf<Long>()
        deviceListAttribute.forEach { types.add(it.deviceType) }

        // ServerListAttribute
        val serverListAttribute =
            readDescriptorClusterServerListAttribute(connectedDevicePtr, endpointInt)
        val serverClusters = arrayListOf<Any>()
        serverListAttribute.forEach { serverClusters.add(it) }

        // ClientListAttribute
        val clientListAttribute =
            readDescriptorClusterClientListAttribute(connectedDevicePtr, endpointInt)
        val clientClusters = arrayListOf<Any>()
        clientListAttribute.forEach { clientClusters.add(it) }

        // Build the DeviceMatterInfo
        val deviceMatterInfo = DeviceMatterInfo(endpointInt, types, serverClusters, clientClusters)
        matterDeviceInfoList.add(deviceMatterInfo)

        // Recursive call for the parts supported by the endpoint.
        // For each part (endpoint)
        partsListAttribute?.forEach { part ->
            Log.d(TAG, "part [$part] is [${part.javaClass}]")
            val endpointInt =
                when (part) {
                    is Int -> part.toInt()
                    else -> return@forEach
                }
            Log.d(TAG, "Processing part [$part]")
            fetchDeviceMatterInfo(nodeId, connectedDevicePtr, endpointInt, matterDeviceInfoList)
        }
    }

    // -----------------------------------------------------------------------------------------------
    // DescriptorCluster functions

    /**
     * PartsListAttribute. These are the endpoints supported.
     *
     * ```
     * For example, on endpoint 0:
     *     sendReadPartsListAttribute part: [1]
     *     sendReadPartsListAttribute part: [2]
     * ```
     */
    suspend fun readDescriptorClusterPartsListAttribute(
        devicePtr: Long,
        endpoint: Int
    ): List<Any>? {
        return suspendCoroutine { continuation ->
            getDescriptorClusterForDevice(devicePtr, endpoint)
                .readPartsListAttribute(
                    object : ChipClusters.DescriptorCluster.PartsListAttributeCallback {
                        override fun onSuccess(values: MutableList<Int>?) {
                            continuation.resume(values)
                        }

                        override fun onError(ex: Exception) {
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    /**
     * DeviceListAttribute
     *
     * ```
     * For example, on endpoint 0:
     *   device: [long type: 22, int revision: 1] -> maps to Root node (0x0016) (utility device type)
     * on endpoint 1:
     *   device: [long type: 256, int revision: 1] -> maps to On/Off Light (0x0100)
     * ```
     */
    suspend fun readDescriptorClusterDeviceListAttribute(
        devicePtr: Long,
        endpoint: Int
    ): List<ChipStructs.DescriptorClusterDeviceTypeStruct> {
        return suspendCoroutine { continuation ->
            getDescriptorClusterForDevice(devicePtr, endpoint)
                .readDeviceTypeListAttribute(
                    object : ChipClusters.DescriptorCluster.DeviceTypeListAttributeCallback {
                        override fun onSuccess(
                            values: List<ChipStructs.DescriptorClusterDeviceTypeStruct>
                        ) {
                            continuation.resume(values)
                        }

                        override fun onError(ex: Exception) {
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    /**
     * ServerListAttribute See
     * https://github.com/project-chip/connectedhomeip/blob/master/zzz_generated/app-common/app-common/zap-generated/ids/Clusters.h
     *
     * ```
     * For example: on endpoint 0
     *     sendReadServerListAttribute: [3]
     *     sendReadServerListAttribute: [4]
     *     sendReadServerListAttribute: [29]
     *     ... and more ...
     * on endpoint 1:
     *     sendReadServerListAttribute: [3]
     *     sendReadServerListAttribute: [4]
     *     sendReadServerListAttribute: [5]
     *     sendReadServerListAttribute: [6]
     *     sendReadServerListAttribute: [7]
     *     ... and more ...
     * on endpoint 2:
     *     sendReadServerListAttribute: [4]
     *     sendReadServerListAttribute: [6]
     *     sendReadServerListAttribute: [29]
     *     sendReadServerListAttribute: [1030]
     *
     * Some mappings:
     *     namespace Groups = 0x00000004 (4)
     *     namespace OnOff = 0x00000006 (6)
     *     namespace Descriptor = 0x0000001D (29)
     *     namespace OccupancySensing = 0x00000406 (1030)
     * ```
     */
    suspend fun readDescriptorClusterServerListAttribute(
        devicePtr: Long,
        endpoint: Int
    ): List<Long> {
        return suspendCoroutine { continuation ->
            getDescriptorClusterForDevice(devicePtr, endpoint)
                .readServerListAttribute(
                    object : ChipClusters.DescriptorCluster.ServerListAttributeCallback {
                        override fun onSuccess(values: MutableList<Long>) {
                            continuation.resume(values)
                        }

                        override fun onError(ex: Exception) {
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    /** ClientListAttribute */
    suspend fun readDescriptorClusterClientListAttribute(
        devicePtr: Long,
        endpoint: Int
    ): List<Long> {
        return suspendCoroutine { continuation ->
            getDescriptorClusterForDevice(devicePtr, endpoint)
                .readClientListAttribute(
                    object : ChipClusters.DescriptorCluster.ClientListAttributeCallback {
                        override fun onSuccess(values: MutableList<Long>) {
                            continuation.resume(values)
                        }

                        override fun onError(ex: Exception) {
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    private fun getDescriptorClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.DescriptorCluster {
        return ChipClusters.DescriptorCluster(devicePtr, endpoint)
    }

    // -----------------------------------------------------------------------------------------------
    // ApplicationCluster functions

    suspend fun readApplicationBasicClusterAttributeList(
        deviceId: Long,
        endpoint: Int
    ): List<Long> {
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e("TAG", "Can't get connectedDevicePointer.")
                return emptyList()
            }
        return suspendCoroutine { continuation ->
            getApplicationBasicClusterForDevice(connectedDevicePtr, endpoint)
                .readAttributeListAttribute(
                    object : ChipClusters.ApplicationBasicCluster.AttributeListAttributeCallback {
                        override fun onSuccess(value: MutableList<Long>) {
                            continuation.resume(value)
                        }

                        override fun onError(ex: Exception) {
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    private fun getApplicationBasicClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.ApplicationBasicCluster {
        return ChipClusters.ApplicationBasicCluster(devicePtr, endpoint)
    }

    // -----------------------------------------------------------------------------------------------
    // BasicCluster functions

    suspend fun readBasicClusterVendorIDAttribute(deviceId: Long, endpoint: Int): Int? {
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e("TAG", "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getBasicClusterForDevice(connectedDevicePtr, endpoint)
                .readVendorIDAttribute(
                    object : ChipClusters.IntegerAttributeCallback {
                        override fun onSuccess(value: Int) {
                            continuation.resume(value)
                        }

                        override fun onError(ex: Exception) {
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    suspend fun readBasicClusterAttributeList(deviceId: Long, endpoint: Int): List<Long> {
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e("TAG", "Can't get connectedDevicePointer.")
                return emptyList()
            }

        return suspendCoroutine { continuation ->
            getBasicClusterForDevice(connectedDevicePtr, endpoint)
                .readAttributeListAttribute(
                    object : ChipClusters.ApplicationBasicCluster.AttributeListAttributeCallback {
                        override fun onSuccess(values: MutableList<Long>) {
                            continuation.resume(values)
                        }

                        override fun onError(ex: Exception) {
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    private fun getBasicClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.ApplicationBasicCluster {
        return ChipClusters.ApplicationBasicCluster(devicePtr, endpoint)
    }

    /**
     * Writes NodeLabel attribute. See spec section "11.1.6.3. Attributes" of the "Basic Information
     * Cluster".
     *
     * @param deviceId device identifier
     * @param nodeLabel device name/node label
     */
    suspend fun writeBasicClusterNodeLabelAttribute(deviceId: Long, nodeLabel: String) {
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e("TAG", "Can't get connectedDevicePointer.")
                return
            }

        return suspendCoroutine { continuation ->
            val callback =
                object : ChipClusters.DefaultClusterCallback {
                    override fun onSuccess() {
                        continuation.resume(Unit)
                    }

                    override fun onError(ex: Exception) {
                        continuation.resumeWithException(ex)
                    }
                }

            BasicInformationCluster(connectedDevicePtr, 0).writeNodeLabelAttribute(
                callback,
                nodeLabel
            )
        }
    }

    /**
     * Reads the vendor name attribute. See spec section "11.1.6.3. Attributes" of the "Basic
     * Information Cluster".
     *
     * @param deviceId the device identifier.
     * @return the vendor name
     */
    suspend fun readBasicClusterVendorNameAttribute(deviceId: Long): String {
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e("TAG", "Can't get connectedDevicePointer.")
                return ""
            }

        return suspendCoroutine { continuation ->
            val callback =
                object : ChipClusters.CharStringAttributeCallback {
                    override fun onSuccess(value: String) {
                        continuation.resume(value)
                    }

                    override fun onError(ex: Exception) {
                        continuation.resumeWithException(ex)
                    }
                }

            BasicInformationCluster(connectedDevicePtr, 0).readVendorNameAttribute(callback)
        }
    }

    /**
     * Reads node's product name attribute. See spec section "11.1.6.3. Attributes" of the "Basic
     * Information Cluster".
     *
     * @param deviceId the device identifier
     * @return the product name
     */
    suspend fun readBasicClusterProductNameAttribute(deviceId: Long): String {
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e("TAG", "Can't get connectedDevicePointer.")
                return ""
            }

        return suspendCoroutine { continuation ->
            val callback =
                object : ChipClusters.CharStringAttributeCallback {
                    override fun onSuccess(value: String) {
                        continuation.resume(value)
                    }

                    override fun onError(ex: Exception) {
                        continuation.resumeWithException(ex)
                    }
                }

            BasicInformationCluster(connectedDevicePtr, 0).readProductNameAttribute(callback)
        }
    }

    /**
     * Reads NodeLabel attribute. See spec section "11.1.6.3. Attributes" of the "Basic Information
     * Cluster".
     *
     * @param deviceId device identifier
     * @return the NodeLabel
     */
    suspend fun readBasicClusterNodeLabelAttribute(deviceId: Long): String? {
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e("TAG", "Can't get connectedDevicePointer.")
                return null
            }

        return suspendCoroutine { continuation ->
            val callback =
                object : ChipClusters.CharStringAttributeCallback {
                    override fun onSuccess(value: String?) {
                        continuation.resume(value)
                    }

                    override fun onError(ex: Exception) {
                        continuation.resumeWithException(ex)
                    }
                }

            BasicInformationCluster(connectedDevicePtr, 0).readNodeLabelAttribute(callback)
        }
    }

    // -----------------------------------------------------------------------------------------------

    suspend fun writeEspDeviceAttribute(
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
        attributeId: Long,
        matterNodeId: String
    ) {
        val tlvWriter = TlvWriter()
        tlvWriter.put(AnonymousTag, matterNodeId)

        val devicePtr = chipClient.awaitGetConnectedDevicePointer(nodeId)
        val attributePath = ChipAttributePath.newInstance(endpointId, clusterId, attributeId)
        chipClient.writeAttribute(devicePtr, attributePath, tlvWriter.getEncoded())
    }

    private fun getOperationalCredentialsCluster(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.OperationalCredentialsCluster {
        return ChipClusters.OperationalCredentialsCluster(devicePtr, endpoint)
    }

    // -----------------------------------------------------------------------------------------------
    // Administrator Commissioning Cluster (11.19)

    suspend fun openCommissioningWindowAdministratorCommissioningCluster(
        deviceId: Long,
        endpoint: Int,
        timeoutSeconds: Int,
        pakeVerifier: ByteArray,
        discriminator: Int,
        iterations: Long,
        salt: ByteArray,
        timedInvokeTimeoutMs: Int
    ) {
        Log.d("TAG", "openCommissioningWindowAdministratorCommissioningCluster())")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e("TAG", "Can't get connectedDevicePointer.")
                e.printStackTrace()
                return
            }

        /*
        ChipClusters.DefaultClusterCallback var1, Integer var2, byte[] var3, Integer var4, Long var5, byte[] var6, int var7
         */
        return suspendCoroutine { continuation ->
            getAdministratorCommissioningClusterForDevice(connectedDevicePtr, endpoint)
                .openCommissioningWindow(
                    object : ChipClusters.DefaultClusterCallback {
                        override fun onSuccess() {
                            continuation.resume(Unit)
                        }

                        override fun onError(ex: java.lang.Exception?) {
                            Log.e(
                                "TAG",
                                "getAdministratorCommissioningClusterForDevice.openCommissioningWindow command failure"
                            )
                            ex?.printStackTrace()
                            continuation.resumeWithException(ex!!)
                        }
                    },
                    timeoutSeconds,
                    pakeVerifier,
                    discriminator,
                    iterations,
                    salt,
                    timedInvokeTimeoutMs
                )
        }
    }

    /**
     * Closes a node's commissioning window. See spec section "11.18.8.3. RevokeCommissioning
     * Command".
     *
     * @param devicePtr connected device pointer.
     */
    suspend fun closeCommissioningWindow(devicePtr: Long) {
        return suspendCoroutine { continuation ->
            val callback =
                object : ChipClusters.DefaultClusterCallback {
                    override fun onSuccess() {
                        Log.d("TAG", "Window is closed successfully")
                        continuation.resume(Unit)
                    }

                    override fun onError(ex: Exception) {
                        Log.e("TAG", "Failed to close window. Cause: ${ex.localizedMessage}")
                        ex.printStackTrace()
                    }
                }
            ChipClusters.AdministratorCommissioningCluster(devicePtr, 0)
                .revokeCommissioning(callback, 100)
        }
    }

    /**
     * Checks if a device has an open commissioning window. See spec section "11.18.7. Attributes" of
     * the "Administrator Commissioning Cluster".
     *
     * @param devicePtr connected device pointer.
     * @return true if a window is open, false otherwise.
     */
    suspend fun isCommissioningWindowOpen(devicePtr: Long): Boolean {
        return suspendCoroutine { continuation ->
            val callback =
                object : ChipClusters.IntegerAttributeCallback {
                    override fun onSuccess(value: Int) {
                        when (value) {
                            CommissioningWindowStatus.WindowNotOpen.status -> {
                                continuation.resume(false)
                            }

                            CommissioningWindowStatus.EnhancedWindowOpen.status,
                            CommissioningWindowStatus.BasicWindowOpen.status -> {
                                continuation.resume(true)
                            }
                        }
                    }

                    override fun onError(ex: Exception) {
                        Log.e("TAG", "Failed to check window status. Cause: ${ex.localizedMessage}")
                        continuation.resumeWithException(ex)
                    }
                }

            ChipClusters.AdministratorCommissioningCluster(devicePtr, 0)
                .readWindowStatusAttribute(callback)
        }
    }

    private fun getAdministratorCommissioningClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.AdministratorCommissioningCluster {
        return ChipClusters.AdministratorCommissioningCluster(devicePtr, endpoint)
    }
}
