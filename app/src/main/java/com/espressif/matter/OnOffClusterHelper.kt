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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * OnOffCluster functions
 */
class OnOffClusterHelper constructor(private val chipClient: ChipClient) {

    companion object {
        const val TAG = "OnOffClusterHelper"
    }

    suspend fun getDeviceStateOnOffCluster(deviceId: Long, endpoint: Int): Boolean? {
        Log.d(TAG, "getDeviceStateOnOffCluster())")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getOnOffClusterForDevice(connectedDevicePtr, endpoint)
                .readOnOffAttribute(
                    object : ChipClusters.BooleanAttributeCallback {
                        override fun onSuccess(value: Boolean) {
                            Log.d(TAG, "readOnOffAttribute success: [$value]")
                            continuation.resume(value)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "readOnOffAttribute command failure")
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    suspend fun setOnOffDeviceStateOnOffCluster(deviceId: Long, isOn: Boolean, endpoint: Int) {
        Log.d(
            TAG,
            "setOnOffDeviceStateOnOffCluster() [${deviceId}] isOn [${isOn}] endpoint [${endpoint}]"
        )
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return
            }
        if (isOn) {
            // ON
            return suspendCoroutine { continuation ->
                getOnOffClusterForDevice(connectedDevicePtr, endpoint)
                    .on(
                        object : ChipClusters.DefaultClusterCallback {
                            override fun onSuccess() {
                                Log.d(TAG, "Success for setOnOffDeviceStateOnOffCluster")
                                continuation.resume(Unit)
                            }

                            override fun onError(ex: Exception) {
                                Log.e(TAG, "Failure for setOnOffDeviceStateOnOffCluster")
                                ex.printStackTrace()
                                continuation.resumeWithException(ex)
                            }
                        })
            }
        } else {
            // OFF
            return suspendCoroutine { continuation ->
                getOnOffClusterForDevice(connectedDevicePtr, endpoint)
                    .off(
                        object : ChipClusters.DefaultClusterCallback {
                            override fun onSuccess() {
                                Log.d(TAG, "Success for getOnOffDeviceStateOnOffCluster")
                                continuation.resume(Unit)
                            }

                            override fun onError(ex: Exception) {
                                Log.e(TAG, "Failure for getOnOffDeviceStateOnOffCluster")
                                continuation.resumeWithException(ex)
                            }
                        })
            }
        }
    }

    suspend fun toggleDeviceStateOnOffCluster(deviceId: Long, endpoint: Int) {
        Log.d(TAG, "toggleDeviceStateOnOffCluster())")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                e.printStackTrace()
                return
            }
        return suspendCoroutine { continuation ->
            getOnOffClusterForDevice(connectedDevicePtr, endpoint)
                .toggle(
                    object : ChipClusters.DefaultClusterCallback {
                        override fun onSuccess() {
                            continuation.resume(Unit)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "readOnOffAttribute command failure")
                            ex.printStackTrace()
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    // To call fetchDeviceMatterInfo function from Java

    fun getDeviceStateOnOffClusterAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Boolean?> =
        GlobalScope.future { getDeviceStateOnOffCluster(deviceId, endpoint) }

    fun setOnOffDeviceStateOnOffClusterAsync(
        deviceId: Long,
        isOn: Boolean,
        endpoint: Int
    ) = GlobalScope.future { setOnOffDeviceStateOnOffCluster(deviceId, isOn, endpoint) }

    private fun getOnOffClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.OnOffCluster {
        return ChipClusters.OnOffCluster(devicePtr, endpoint)
    }
}
