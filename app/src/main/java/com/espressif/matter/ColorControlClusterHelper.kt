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

class ColorControlClusterHelper constructor(private val chipClient: ChipClient) {

    companion object {
        const val TAG = "ColorControlClusterHelper"
    }

    suspend fun getCurrentSaturationValue(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "getCurrentLevelValue())")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getColorControlClusterForDevice(connectedDevicePtr, endpoint)
                .readCurrentSaturationAttribute(
                    object : ChipClusters.IntegerAttributeCallback {

                        override fun onSuccess(value: Int) {
                            Log.d(TAG, "readCurrentSaturationAttribute success: [$value]")
                            continuation.resume(value)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "readCurrentSaturationAttribute command failure")
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    fun getCurrentSaturationValueAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { getCurrentSaturationValue(deviceId, endpoint) }

    suspend fun getMinSaturationValue(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "getCurrentLevelValue())")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getColorControlClusterForDevice(connectedDevicePtr, endpoint)
                .readCurrentSaturationAttribute(
                    object : ChipClusters.IntegerAttributeCallback {

                        override fun onSuccess(value: Int) {
                            Log.d(TAG, "readCurrentSaturationAttribute success: [$value]")
                            continuation.resume(value)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "readCurrentSaturationAttribute command failure")
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    suspend fun setSaturationValue(deviceId: Long, endpoint: Int, level: Int): Unit {
        Log.d(TAG, "setSaturationValue())")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return
            }
        return suspendCoroutine { continuation ->
            getColorControlClusterForDevice(connectedDevicePtr, endpoint)
                .moveToSaturation(
                    object : ChipClusters.DefaultClusterCallback {

                        override fun onSuccess() {
                            Log.d(TAG, "setSaturationValue success")
                            continuation.resume(Unit)
                        }

                        override fun onError(ex: java.lang.Exception) {
                            Log.e(TAG, "setSaturationValue command failure")
                            continuation.resumeWithException(ex)
                        }
                    }, level, 0, 0, 0
                )
        }
    }

    fun setSaturationValueAsync(
        deviceId: Long,
        endpoint: Int,
        level: Int
    ): CompletableFuture<Unit> =
        GlobalScope.future { setSaturationValue(deviceId, endpoint, level) }

    suspend fun getCurrentHueValue(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "getCurrentHueValue())")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getColorControlClusterForDevice(connectedDevicePtr, endpoint)
                .readCurrentHueAttribute(
                    object : ChipClusters.IntegerAttributeCallback {

                        override fun onSuccess(value: Int) {
                            Log.d(TAG, "getCurrentHueValue success: [$value]")
                            continuation.resume(value)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "getCurrentHueValue command failure")
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    fun getCurrentHueValueAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { getCurrentHueValue(deviceId, endpoint) }

    suspend fun setHueValue(deviceId: Long, endpoint: Int, level: Int): Unit {
        Log.d(TAG, "setHueValue())")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return
            }
        return suspendCoroutine { continuation ->
            getColorControlClusterForDevice(connectedDevicePtr, endpoint)
                .moveToHue(
                    object : ChipClusters.DefaultClusterCallback {

                        override fun onSuccess() {
                            Log.d(TAG, "setHueValue success")
                            continuation.resume(Unit)
                        }

                        override fun onError(ex: java.lang.Exception) {
                            Log.e(TAG, "setHueValue command failure")
                            continuation.resumeWithException(ex)
                        }
                    }, level, 0, 0, 0, 0
                )
        }
    }

    fun setHueValueAsync(
        deviceId: Long,
        endpoint: Int,
        level: Int
    ): CompletableFuture<Unit> =
        GlobalScope.future { setHueValue(deviceId, endpoint, level) }


    private fun getColorControlClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.ColorControlCluster {
        return ChipClusters.ColorControlCluster(devicePtr, endpoint)
    }
}
