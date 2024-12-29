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
import chip.devicecontroller.ChipClusters.DoorLockCluster.SetCredentialResponseCallback
import chip.devicecontroller.ChipStructs.DoorLockClusterCredentialStruct
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Door Lock Cluster functions
 */
class DoorLockClusterHelper constructor(private val chipClient: ChipClient) {

    companion object {
        const val TAG = "DoorLockClusterHelper"
    }

    suspend fun setUser(deviceId: Long, endpoint: Int) {
        Log.d(TAG, "Set User")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return
            }
        return suspendCoroutine { continuation ->
            getDoorLockClusterForDevice(connectedDevicePtr, endpoint)
                .setUser(
                    object : ChipClusters.DefaultClusterCallback {
                        override fun onSuccess() {
                            Log.d(TAG, "Set user success")
                            continuation.resume(Unit)
                        }

                        override fun onError(error: java.lang.Exception) {
                            Log.e(TAG, "Set user failure")
                            continuation.resumeWithException(error)
                        }
                    },
                    0, // operation type add
                    1,
                    "Home",
                    123,
                    1,
                    0,
                    0,
                    5000
                )
        }
    }

    fun setUserAsync(
        deviceId: Long,
        endpoint: Int
    ) =
        GlobalScope.future { setUser(deviceId, endpoint) }

    suspend fun setCredential(deviceId: Long, endpoint: Int, pincode: String) {
        Log.d(TAG, "Set Credential")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return
            }
        var creds = DoorLockClusterCredentialStruct(1, 1);

        return suspendCoroutine { continuation ->
            getDoorLockClusterForDevice(connectedDevicePtr, endpoint)
                .setCredential(
                    object : SetCredentialResponseCallback {

                        override fun onSuccess(
                            status: Int?,
                            userIndex: Int?,
                            nextCredentialIndex: Int?
                        ) {
                            Log.d(TAG, "Set credential success")
                            continuation.resume(Unit)
                        }

                        override fun onError(error: java.lang.Exception) {
                            Log.e(TAG, "Set credential failure")
                            continuation.resumeWithException(error)
                        }
                    },
                    0,
                    creds,
                    pincode.toByteArray(StandardCharsets.UTF_8),
                    1,
                    null,
                    null,
                    5000
                )
        }
    }

    fun setCredentialAsync(
        deviceId: Long,
        endpoint: Int,
        pincode: String
    ) =
        GlobalScope.future { setCredential(deviceId, endpoint, pincode) }

    suspend fun getLockState(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "Get Lock state")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getDoorLockClusterForDevice(connectedDevicePtr, endpoint)
                .readLockStateAttribute(
                    object :
                        ChipClusters.DoorLockCluster.LockStateAttributeCallback {
                        override fun onSuccess(value: Int?) {
                            Log.d(TAG, "Get Lock state success : [$value]")
                            continuation.resume(value)
                        }

                        override fun onError(ex: java.lang.Exception) {
                            Log.e(TAG, "Get Lock state command failure")
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    fun getLockStateAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { getLockState(deviceId, endpoint) }

    suspend fun subscribeForLockState(
        deviceId: Long,
        endpoint: Int,
        reportCallback: ChipClusters.DoorLockCluster.LockStateAttributeCallback
    ) {
        Log.d(TAG, "Subscribe for lock state")
        val connectedDevicePtr = chipClient.getConnectedDevicePointer(deviceId)
        var doorLockCluster =
            getDoorLockClusterForDevice(connectedDevicePtr, endpoint)

        return suspendCoroutine { continuation ->
            doorLockCluster.subscribeLockStateAttribute(reportCallback, 5, 10)
            continuation.resume(Unit)
        }
    }

    suspend fun lockDoor(deviceId: Long, endpoint: Int, pincode: String): Unit? {
        Log.d(TAG, "Lock door")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getDoorLockClusterForDevice(connectedDevicePtr, endpoint)
                .lockDoor(
                    object : ChipClusters.DefaultClusterCallback {
                        override fun onSuccess() {
                            Log.d(TAG, "Lock door success")
                            continuation.resume(Unit)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "Lock door command failure")
                            continuation.resumeWithException(ex)
                        }
                    }, stringToOptionalByteArray(pincode), 1000
                )
        }
    }

    fun lockDoorAsync(
        deviceId: Long,
        endpoint: Int,
        pincode: String
    ): CompletableFuture<Unit> =
        GlobalScope.future { lockDoor(deviceId, endpoint, pincode) }

    suspend fun unlockDoor(deviceId: Long, endpoint: Int, pincode: String): Unit? {
        Log.d(TAG, "Unlock door")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getDoorLockClusterForDevice(connectedDevicePtr, endpoint)
                .unlockDoor(
                    object : ChipClusters.DefaultClusterCallback {
                        override fun onSuccess() {
                            Log.d(TAG, "Unlock door success")
                            continuation.resume(Unit)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "Unlock door command failure")
                            continuation.resumeWithException(ex)
                        }
                    }, stringToOptionalByteArray(pincode), 5000
                )
        }
    }

    fun unlockDoorAsync(
        deviceId: Long,
        endpoint: Int,
        pincode: String
    ): CompletableFuture<Unit> = GlobalScope.future { unlockDoor(deviceId, endpoint, pincode) }
    
    fun stringToOptionalByteArray(input: String): Optional<ByteArray> {
        return Optional.ofNullable(input.toByteArray(StandardCharsets.UTF_8))
    }

    private fun getDoorLockClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.DoorLockCluster {
        return ChipClusters.DoorLockCluster(devicePtr, endpoint)
    }
}
