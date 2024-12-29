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
import chip.devicecontroller.ChipClusters.IntegerAttributeCallback
import chip.devicecontroller.ChipClusters.ThermostatCluster.LocalTemperatureAttributeCallback
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Thermostat Cluster functions
 */
class ThermostatClusterHelper constructor(private val chipClient: ChipClient) {

    companion object {
        const val TAG = "ThermostatCluster"
    }

    suspend fun getSystemMode(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "Get System Mode")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getThermostatClusterForDevice(connectedDevicePtr, endpoint)
                .readSystemModeAttribute(object : IntegerAttributeCallback {
                    override fun onSuccess(value: Int) {
                        Log.d(TAG, "readSystemModeAttribute success: [$value]")
                        continuation.resume(value)
                    }

                    override fun onError(error: Exception) {
                        Log.e(TAG, "readSystemModeAttribute command failure")
                        continuation.resumeWithException(error)
                    }
                })
        }
    }

    fun getSystemModeAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { getSystemMode(deviceId, endpoint) }

    suspend fun setSystemMode(deviceId: Long, endpoint: Int, value: Int) {
        Log.d(TAG, "Set System Mode")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return
            }
        return suspendCoroutine { continuation ->
            getThermostatClusterForDevice(connectedDevicePtr, endpoint)
                .writeSystemModeAttribute(object : ChipClusters.DefaultClusterCallback {

                    override fun onSuccess() {
                        Log.d(TAG, "writeSystemModeAttribute success")
                        continuation.resume(Unit)
                    }

                    override fun onError(error: Exception) {
                        Log.e(TAG, "writeSystemModeAttribute command failure")
                        continuation.resumeWithException(error)
                    }
                }, value)
        }
    }

    fun setSystemModeAsync(
        deviceId: Long,
        endpoint: Int,
        value: Int
    ) = GlobalScope.future { setSystemMode(deviceId, endpoint, value) }

    suspend fun subscribeForSystemMode(
        deviceId: Long,
        endpoint: Int,
        reportCallback: IntegerAttributeCallback
    ) {
        Log.d(TAG, "Subscribe for system mode")
        val connectedDevicePtr = chipClient.getConnectedDevicePointer(deviceId)
        val cluster = getThermostatClusterForDevice(connectedDevicePtr, endpoint)

        return suspendCoroutine { continuation ->
            cluster.subscribeSystemModeAttribute(reportCallback, 5, 10)
            continuation.resume(Unit)
        }
    }

    suspend fun getOccupiedCoolingSetpoint(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "Get Occupied Cooling Setpoint")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getThermostatClusterForDevice(connectedDevicePtr, endpoint)
                .readOccupiedCoolingSetpointAttribute(object : IntegerAttributeCallback {
                    override fun onSuccess(value: Int) {
                        Log.d(TAG, "readOccupiedCoolingSetpointAttribute success: [$value]")
                        continuation.resume(value)
                    }

                    override fun onError(error: Exception) {
                        Log.e(TAG, "readOccupiedCoolingSetpointAttribute command failure")
                        continuation.resumeWithException(error)
                    }
                })
        }
    }

    fun getOccupiedCoolingSetpointAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { getOccupiedCoolingSetpoint(deviceId, endpoint) }

    suspend fun setOccupiedCoolingSetpoint(deviceId: Long, endpoint: Int, value: Int): Int? {
        Log.d(TAG, "Set Occupied Cooling Setpoint")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getThermostatClusterForDevice(connectedDevicePtr, endpoint)
                .writeOccupiedCoolingSetpointAttribute(object : DefaultClusterCallback {
                    override fun onSuccess() {
                        Log.d(TAG, "writeOccupiedCoolingSetpointAttribute success: [$value]")
                        continuation.resume(value)
                    }

                    override fun onError(error: Exception) {
                        Log.e(TAG, "writeOccupiedCoolingSetpointAttribute command failure")
                        continuation.resumeWithException(error)
                    }
                }, value)
        }
    }

    fun setOccupiedCoolingSetpointAsync(
        deviceId: Long,
        endpoint: Int,
        value: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { setOccupiedCoolingSetpoint(deviceId, endpoint, value) }

    suspend fun subscribeForCoolingSetpoint(
        deviceId: Long,
        endpoint: Int,
        reportCallback: IntegerAttributeCallback
    ) {
        Log.d(TAG, "Subscribe for cooling setpoint")
        val connectedDevicePtr = chipClient.getConnectedDevicePointer(deviceId)
        val cluster = getThermostatClusterForDevice(connectedDevicePtr, endpoint)

        return suspendCoroutine { continuation ->
            cluster.subscribeOccupiedCoolingSetpointAttribute(reportCallback, 5, 10)
            continuation.resume(Unit)
        }
    }

    suspend fun getOccupiedHeatingSetpoint(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "Get Occupied Heating Setpoint")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getThermostatClusterForDevice(connectedDevicePtr, endpoint)
                .readOccupiedHeatingSetpointAttribute(object : IntegerAttributeCallback {
                    override fun onSuccess(value: Int) {
                        Log.d(TAG, "readOccupiedHeatingSetpointAttribute success: [$value]")
                        continuation.resume(value)
                    }

                    override fun onError(error: Exception) {
                        Log.e(TAG, "readOccupiedHeatingSetpointAttribute command failure")
                        continuation.resumeWithException(error)
                    }
                })
        }
    }

    fun getOccupiedHeatingSetpointAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { getOccupiedHeatingSetpoint(deviceId, endpoint) }

    suspend fun setOccupiedHeatingSetpoint(deviceId: Long, endpoint: Int, value: Int): Int? {
        Log.d(TAG, "Set Occupied Heating Setpoint")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getThermostatClusterForDevice(connectedDevicePtr, endpoint)
                .writeOccupiedHeatingSetpointAttribute(
                    object : ChipClusters.DefaultClusterCallback {

                        override fun onSuccess() {
                            Log.d(TAG, "writeOccupiedHeatingSetpointAttribute success")
                            continuation.resume(value)
                        }

                        override fun onError(error: Exception) {
                            Log.e(TAG, "writeOccupiedHeatingSetpointAttribute command failure")
                            continuation.resumeWithException(error)
                        }
                    }, value
                )
        }
    }

    fun setOccupiedHeatingSetpointAsync(
        deviceId: Long,
        endpoint: Int,
        value: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { setOccupiedHeatingSetpoint(deviceId, endpoint, value) }

    suspend fun subscribeForHeatingSetpoint(
        deviceId: Long,
        endpoint: Int,
        reportCallback: IntegerAttributeCallback
    ) {
        Log.d(TAG, "Subscribe for heating setpoint")
        val connectedDevicePtr = chipClient.getConnectedDevicePointer(deviceId)
        val cluster = getThermostatClusterForDevice(connectedDevicePtr, endpoint)

        return suspendCoroutine { continuation ->
            cluster.subscribeOccupiedHeatingSetpointAttribute(reportCallback, 5, 10)
            continuation.resume(Unit)
        }
    }

    suspend fun getLocalTemperature(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "Get Local Temperature")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getThermostatClusterForDevice(connectedDevicePtr, endpoint)
                .readLocalTemperatureAttribute(object : LocalTemperatureAttributeCallback {

                    override fun onSuccess(value: Int?) {
                        Log.d(TAG, "readLocalTemperatureAttribute success: [$value]")
                        continuation.resume(value)
                    }

                    override fun onError(error: Exception) {
                        Log.e(TAG, "readLocalTemperatureAttribute command failure")
                        continuation.resumeWithException(error)
                    }
                })
        }
    }

    fun getLocalTemperatureAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { getLocalTemperature(deviceId, endpoint) }

    suspend fun subscribeForLocalTemperature(
        deviceId: Long,
        endpoint: Int,
        reportCallback: LocalTemperatureAttributeCallback
    ) {
        Log.d(TAG, "Subscribe for local temperature")
        val connectedDevicePtr = chipClient.getConnectedDevicePointer(deviceId)
        val cluster = getThermostatClusterForDevice(connectedDevicePtr, endpoint)

        return suspendCoroutine { continuation ->
            cluster.subscribeLocalTemperatureAttribute(reportCallback, 5, 10)
            continuation.resume(Unit)
        }
    }

    private fun getThermostatClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.ThermostatCluster {
        return ChipClusters.ThermostatCluster(devicePtr, endpoint)
    }
}
