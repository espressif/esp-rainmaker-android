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
import chip.devicecontroller.ChipStructs
import chip.devicecontroller.ChipStructs.AccessControlClusterAccessControlEntryStruct
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class AccessControlClusterHelper constructor(private val chipClient: ChipClient) {

    companion object {
        const val TAG = "AccessControlClusterHelper"
    }

    suspend fun readAclAttribute(
        deviceId: Long,
        endpoint: Int
    ): MutableList<ChipStructs.AccessControlClusterAccessControlEntryStruct>? {
        Log.d(TAG, "readAclAttribute : $deviceId")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getAccessControlClusterForDevice(connectedDevicePtr, endpoint)
                .readAclAttribute(
                    object : ChipClusters.AccessControlCluster.AclAttributeCallback {

                        override fun onSuccess(valueList: MutableList<ChipStructs.AccessControlClusterAccessControlEntryStruct>?) {
                            Log.d(TAG, "readAclAttribute success: [$valueList]")
                            continuation.resume(valueList)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "readAclAttribute command failure")
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    fun readAclAttributeAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<MutableList<ChipStructs.AccessControlClusterAccessControlEntryStruct>?> =
        GlobalScope.future { readAclAttribute(deviceId, endpoint) }

    suspend fun writeAclAttribute(
        deviceId: Long,
        endpoint: Int,
        entries: ArrayList<AccessControlClusterAccessControlEntryStruct>
    ) {
        Log.d(TAG, "writeAclAttribute : $deviceId")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return
            }

        return suspendCoroutine { continuation ->
            getAccessControlClusterForDevice(connectedDevicePtr, endpoint)
                .writeAclAttribute(
                    object : ChipClusters.DefaultClusterCallback {

                        override fun onSuccess() {
                            Log.d(TAG, "writeAclAttribute success")
                            continuation.resume(Unit)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "writeAclAttribute command failure")
                            continuation.resumeWithException(ex)
                        }
                    }, entries
                )
        }
    }

    fun writeAclAttributeAsync(
        deviceId: Long,
        endpoint: Int,
        level: ArrayList<ChipStructs.AccessControlClusterAccessControlEntryStruct>
    ): CompletableFuture<Unit> =
        GlobalScope.future { writeAclAttribute(deviceId, endpoint, level) }

    private fun getAccessControlClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.AccessControlCluster {
        return ChipClusters.AccessControlCluster(devicePtr, endpoint)
    }
}
