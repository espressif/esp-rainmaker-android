// Copyright 2024 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.matter

import android.util.Log
import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipClusters.DefaultClusterCallback
import chip.devicecontroller.ChipClusters.GeneralCommissioningCluster.CommissioningCompleteResponseCallback
import chip.devicecontroller.ChipClusters.LongAttributeCallback
import com.espressif.AppConstants
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.util.Optional
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ThreadBRClusterHelper constructor(
    private val chipClient: ChipClient
) {

    companion object {
        const val TAG = "TbrClusterHelper"
    }

    fun configureThreadBRAsync(
        nodeId: Long,
        endpointId: Int,
        datasetStr: ByteArray
    ) = GlobalScope.future {
        configureThreadBR(
            nodeId,
            endpointId,
            datasetStr
        )
    }

    suspend fun configureThreadBR(
        nodeId: Long,
        endpointId: Int,
        datasetStr: ByteArray
    ) {
        setArmFailSafe(nodeId, AppConstants.ENDPOINT_0)
        sendDataset(nodeId, endpointId, datasetStr)
        setCommissioningComplete(nodeId, AppConstants.ENDPOINT_0)
    }

    suspend fun setArmFailSafe(
        deviceId: Long,
        endpointId: Int,
    ) {
        Log.d(TAG, "setArmFailSafe")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return
            }
        val cluster =
            getGeneralCommissioningClusterForDevice(connectedDevicePtr, endpointId)

        return suspendCoroutine { continuation ->
            cluster.armFailSafe(object :
                ChipClusters.GeneralCommissioningCluster.ArmFailSafeResponseCallback {
                override fun onSuccess(errorCode: Int?, debugText: String?) {
                    Log.d(TAG, "armFailSafe success")
                    continuation.resume(Unit)
                }

                override fun onError(error: Exception) {
                    Log.e(TAG, "armFailSafe command failure")
                    continuation.resumeWithException(error)
                }
            }, 300, 1)
        }
    }

    suspend fun sendDataset(
        nodeId: Long,
        endpointId: Int,
        datasetStr: ByteArray
    ): Unit? {
        Log.d(TAG, "Send Dataset")
        val breadcrumb: Optional<Long> = Optional.of(1L)

        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(nodeId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getThreadBorderRouterManagementCluster(connectedDevicePtr, endpointId)
                .setActiveDatasetRequest(object : DefaultClusterCallback {

                    override fun onSuccess() {
                        Log.d(TAG, "setDataset success")
                        continuation.resume(Unit)
                    }

                    override fun onError(error: Exception) {
                        Log.e(TAG, "setDataset command failure")
                        continuation.resumeWithException(error)
                    }
                }, datasetStr, breadcrumb)
        }
    }

    fun sendPendingDatasetAsync(
        nodeId: Long,
        endpointId: Int,
        datasetStr: ByteArray
    ) = GlobalScope.future {
        sendPendingDataset(
            nodeId,
            endpointId,
            datasetStr
        )
    }

    suspend fun sendPendingDataset(
        nodeId: Long,
        endpointId: Int,
        datasetStr: ByteArray
    ): Unit? {
        Log.d(TAG, "Send pending Dataset")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(nodeId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getThreadBorderRouterManagementCluster(connectedDevicePtr, endpointId)
                .setPendingDatasetRequest(object : DefaultClusterCallback {

                    override fun onSuccess() {
                        Log.d(TAG, "Pending dataset send command success")
                        continuation.resume(Unit)
                    }

                    override fun onError(error: Exception) {
                        Log.e(TAG, "Pending dataset send command failure")
                        continuation.resumeWithException(error)
                    }
                }, datasetStr)
        }
    }

    suspend fun setCommissioningComplete(
        deviceId: Long,
        endpointId: Int,
    ) {
        Log.d(TAG, "setCommissioningComplete")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return
            }
        val cluster =
            getGeneralCommissioningClusterForDevice(connectedDevicePtr, endpointId)

        return suspendCoroutine { continuation ->
            cluster.commissioningComplete(
                object :
                    CommissioningCompleteResponseCallback {
                    override fun onSuccess(errorCode: Int?, debugText: String?) {
                        Log.d(TAG, "commissioningComplete success")
                        continuation.resume(Unit)
                    }

                    override fun onError(error: Exception) {
                        Log.e(TAG, "commissioningComplete command failure")
                        continuation.resumeWithException(error)
                    }
                }
            )
        }
    }

    // Read from device
    fun readDatasetAsync(
        nodeId: Long,
    ): CompletableFuture<ArrayList<String>> = GlobalScope.future {
        readDataset(nodeId)
    }

    fun readActiveDatasetAsync(
        nodeId: Long,
    ): CompletableFuture<String?> = GlobalScope.future {
        readActiveDataset(nodeId)
    }

    fun readBorderAgentIdAsync(
        nodeId: Long,
    ): CompletableFuture<String?> = GlobalScope.future {
        readBorderAgentId(nodeId, AppConstants.ENDPOINT_1)
    }

    suspend fun readDataset(
        nodeId: Long
    ): ArrayList<String> {

        Log.d(TAG, "Reading Active dataset from Device")
        val tbrData: ArrayList<String> = ArrayList()

        try {
            val dataset = readActiveDatasetFromDevice(nodeId, AppConstants.ENDPOINT_1)
            if (dataset != null) {
                tbrData.add(dataset)
            }

            val baId = readBorderAgentId(nodeId, AppConstants.ENDPOINT_1)
            if (baId != null) {
                tbrData.add(baId)
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
        return tbrData
    }

    suspend fun readActiveDataset(
        nodeId: Long
    ): String? {

        Log.d(TAG, "Reading Active dataset from Device")
        var dataset: String? = null

        try {
            dataset = readActiveDatasetFromDevice(nodeId, AppConstants.ENDPOINT_1)
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
        return dataset
    }

    suspend fun readActiveDatasetFromDevice(deviceId: Long, endpoint: Int): String? {
        Log.d(TAG, "readActiveDataset")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getThreadBorderRouterManagementCluster(connectedDevicePtr, endpoint)
                .getActiveDatasetRequest(object :
                    ChipClusters.ThreadBorderRouterManagementCluster.DatasetResponseCallback {

                    override fun onSuccess(dataset: ByteArray?) {
                        val datasetStr = dataset?.byteArrayToDs()
                        Log.d(TAG, "readActiveDataset command success")
                        continuation.resume(datasetStr)
                    }

                    override fun onError(error: Exception) {
                        Log.e(TAG, "readActiveDataset command failure")
                        continuation.resumeWithException(error)
                    }
                })
        }
    }

    suspend fun readBorderAgentId(deviceId: Long, endpoint: Int): String? {
        Log.d(TAG, "readBorderAgentId")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getThreadBorderRouterManagementCluster(connectedDevicePtr, endpoint)
                .readBorderAgentIDAttribute(object : ChipClusters.OctetStringAttributeCallback {

                    override fun onSuccess(value: ByteArray?) {
                        val baId = value?.byteArrayToDs()
                        Log.d(TAG, "readBorderAgentId command success : $baId")
                        continuation.resume(baId)
                    }

                    override fun onError(error: Exception) {
                        Log.e(TAG, "readBorderAgentId command failure")
                        continuation.resumeWithException(error)
                    }
                })
        }
    }

    fun readFeatureMapAsync(
        nodeId: Long,
    ): CompletableFuture<Long?> = GlobalScope.future {
        readFeatureMap(nodeId, AppConstants.ENDPOINT_1)
    }

    suspend fun readFeatureMap(deviceId: Long, endpoint: Int): Long? {
        Log.d(TAG, "readFeatureMap")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getThreadBorderRouterManagementCluster(connectedDevicePtr, endpoint)
                .readFeatureMapAttribute(object : LongAttributeCallback {

                    override fun onSuccess(value: Long) {
                        Log.d(TAG, "readFeatureMap command success : $value")
                        continuation.resume(value)
                    }

                    override fun onError(error: Exception) {
                        Log.e(TAG, "readFeatureMap command failure")
                        continuation.resumeWithException(error)
                    }
                })
        }
    }

    private fun getThreadBorderRouterManagementCluster(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.ThreadBorderRouterManagementCluster {
        return ChipClusters.ThreadBorderRouterManagementCluster(devicePtr, endpoint)
    }

    private fun getGeneralCommissioningClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.GeneralCommissioningCluster {
        return ChipClusters.GeneralCommissioningCluster(devicePtr, endpoint)
    }

    private fun ByteArray.byteArrayToDs(): String =
        joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}
