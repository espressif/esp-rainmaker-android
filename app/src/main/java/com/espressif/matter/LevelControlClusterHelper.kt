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

class LevelControlClusterHelper constructor(private val chipClient: ChipClient) {

    companion object {
        const val TAG = "LevelControlClusterHelper"
    }

    suspend fun getCurrentLevelValue(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "getCurrentLevelValue()) : $deviceId")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getLevelControlClusterForDevice(connectedDevicePtr, endpoint)
                .readCurrentLevelAttribute(
                    object : ChipClusters.LevelControlCluster.CurrentLevelAttributeCallback {

                        override fun onSuccess(value: Int?) {
                            Log.d(TAG, "readCurrentLevelAttribute success: [$value]")
                            continuation.resume(value)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "readCurrentLevelAttribute command failure")
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    fun getCurrentLevelValueAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { getCurrentLevelValue(deviceId, endpoint) }

    suspend fun getMinLevelValue(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "getMinLevelValue())")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getLevelControlClusterForDevice(connectedDevicePtr, endpoint)
                .readMinLevelAttribute(
                    object : ChipClusters.IntegerAttributeCallback {

                        override fun onSuccess(value: Int) {
                            Log.d(TAG, "readMinLevelAttribute success: [$value]")
                            continuation.resume(value)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "readMinLevelAttribute command failure")
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    fun getMinLevelValueAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { getMinLevelValue(deviceId, endpoint) }

    suspend fun getMaxLevelValue(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "getMaxLevelValue())")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getLevelControlClusterForDevice(connectedDevicePtr, endpoint)
                .readMaxLevelAttribute(
                    object : ChipClusters.IntegerAttributeCallback {

                        override fun onSuccess(value: Int) {
                            Log.d(TAG, "getMaxLevelValue success: [$value]")
                            continuation.resume(value)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "getMaxLevelValue command failure")
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    fun getMaxLevelValueAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { getMaxLevelValue(deviceId, endpoint) }

    suspend fun setLevel(deviceId: Long, endpoint: Int, level: Int): Unit {
        Log.d(TAG, "setLevel())")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return
            }
        
        return suspendCoroutine { continuation ->
            getLevelControlClusterForDevice(connectedDevicePtr, endpoint)
                .moveToLevelWithOnOff(
                    object : ChipClusters.DefaultClusterCallback {

                        override fun onSuccess() {
                            Log.d(TAG, "setLevel success")
                            continuation.resume(Unit)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "setLevel command failure")
                            continuation.resumeWithException(ex)
                        }
                    }, level, 0, 0, 0
                )
        }
    }

    fun setLevelAsync(deviceId: Long, endpoint: Int, level: Int): CompletableFuture<Unit> =
        GlobalScope.future { setLevel(deviceId, endpoint, level) }

    private fun getLevelControlClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.LevelControlCluster {
        return ChipClusters.LevelControlCluster(devicePtr, endpoint)
    }
}
