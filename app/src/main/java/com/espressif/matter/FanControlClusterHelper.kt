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
 * Fan Control Cluster functions
 */
class FanControlClusterHelper constructor(private val chipClient: ChipClient) {

    companion object {
        const val TAG = "FanControlCluster"
    }

    suspend fun getFanSpeed(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "Get fan speed")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getFanControlClusterForDevice(connectedDevicePtr, endpoint)
                .readSpeedCurrentAttribute(
                    object : ChipClusters.IntegerAttributeCallback {
                        override fun onSuccess(value: Int) {
                            Log.d(TAG, "Get fan speed success : [$value]")
                            continuation.resume(value)
                        }

                        override fun onError(ex: java.lang.Exception) {
                            Log.e(TAG, "Get fan speed command failure")
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    fun getFanSpeedAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { getFanSpeed(deviceId, endpoint) }

    suspend fun setFanSpeed(deviceId: Long, endpoint: Int, value: Int): Int? {
        Log.d(TAG, "Set fan speed with value: [$value]")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getFanControlClusterForDevice(connectedDevicePtr, endpoint)
                .writeSpeedSettingAttribute(object :
                    ChipClusters.DefaultClusterCallback {
                    override fun onSuccess() {
                        Log.d(
                            ThermostatClusterHelper.TAG,
                            "writeSpeedSettingAttribute success: [$value]"
                        )
                        continuation.resume(value)
                    }

                    override fun onError(error: Exception) {
                        Log.e(
                            ThermostatClusterHelper.TAG,
                            "writeSpeedSettingAttribute command failure"
                        )
                        continuation.resumeWithException(error)
                    }
                }, value)
        }
    }

    fun setFanSpeedAsync(
        deviceId: Long,
        endpoint: Int,
        value: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { setFanSpeed(deviceId, endpoint, value) }

    suspend fun subscribeForFanSpeedValue(
        deviceId: Long,
        endpoint: Int,
        reportCallback: ChipClusters.IntegerAttributeCallback
    ) {
        Log.d(TAG, "Subscribe for fan speed value")
        val connectedDevicePtr = chipClient.getConnectedDevicePointer(deviceId)
        val cluster = getFanControlClusterForDevice(connectedDevicePtr, endpoint)

        return suspendCoroutine { continuation ->
            cluster.subscribeSpeedCurrentAttribute(reportCallback, 5, 10)
            continuation.resume(Unit)
        }
    }

    private fun getFanControlClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.FanControlCluster {
        return ChipClusters.FanControlCluster(devicePtr, endpoint)
    }
}
