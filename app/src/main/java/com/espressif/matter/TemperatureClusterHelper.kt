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
import chip.devicecontroller.ChipClusters.TemperatureMeasurementCluster.MeasuredValueAttributeCallback
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Temperature Measurement Cluster functions
 */
class TemperatureClusterHelper constructor(private val chipClient: ChipClient) {

    companion object {
        const val TAG = "TemperatureCluster"
    }

    suspend fun getTemperature(deviceId: Long, endpoint: Int): Double? {
        Log.d(TAG, "Get Temperature")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getTemperatureMeasurementClusterForDevice(connectedDevicePtr, endpoint)
                .readMeasuredValueAttribute(
                    object : MeasuredValueAttributeCallback {
                        override fun onSuccess(value: Int?) {
                            Log.d(TAG, "Get Temperature success : [$value]")
                            continuation.resume(value?.toDouble())
                        }

                        override fun onError(ex: java.lang.Exception) {
                            Log.e(TAG, "Get Temperature command failure")
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    fun getTemperatureAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Double?> =
        GlobalScope.future { getTemperature(deviceId, endpoint) }

    suspend fun subscribeForTemperatureValue(
        deviceId: Long,
        endpoint: Int,
        reportCallback: MeasuredValueAttributeCallback
    ) {
        Log.d(TAG, "Subscribe for Temperature value")
        val connectedDevicePtr = chipClient.getConnectedDevicePointer(deviceId)
        val cluster = getTemperatureMeasurementClusterForDevice(connectedDevicePtr, endpoint)

        return suspendCoroutine { continuation ->
            cluster.subscribeMeasuredValueAttribute(reportCallback, 5, 10)
            continuation.resume(Unit)
        }
    }

    private fun getTemperatureMeasurementClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.TemperatureMeasurementCluster {
        return ChipClusters.TemperatureMeasurementCluster(devicePtr, endpoint)
    }
}
