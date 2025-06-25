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
        
        // CCT conversion constants
        const val MIN_CCT_KELVIN = 2700 // Warm white
        const val MAX_CCT_KELVIN = 6500 // Cool white
        
        /**
         * Convert Kelvin to Mireds (micro reciprocal degrees)
         * Formula: mireds = 1,000,000 / kelvin
         */
        fun kelvinToMireds(kelvin: Int): Int {
            return (1000000 / kelvin)
        }
        
        /**
         * Convert Mireds to Kelvin
         * Formula: kelvin = 1,000,000 / mireds
         */
        fun miredsToKelvin(mireds: Int): Int {
            return (1000000 / mireds)
        }
        
        /**
         * Clamp Kelvin value to supported range
         */
        fun clampKelvin(kelvin: Int): Int {
            return kelvin.coerceIn(MIN_CCT_KELVIN, MAX_CCT_KELVIN)
        }
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

    suspend fun getCurrentCCTValue(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "getCurrentCCTValue()")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return null
            }
        return suspendCoroutine { continuation ->
            getColorControlClusterForDevice(connectedDevicePtr, endpoint)
                .readColorTemperatureMiredsAttribute(
                    object : ChipClusters.IntegerAttributeCallback {

                        override fun onSuccess(value: Int) {
                            Log.d(TAG, "readColorTemperatureMiredsAttribute success: [$value] mireds")
                            continuation.resume(value)
                        }

                        override fun onError(ex: Exception) {
                            Log.e(TAG, "readColorTemperatureMiredsAttribute command failure")
                            continuation.resumeWithException(ex)
                        }
                    })
        }
    }

    fun getCurrentCCTValueAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { getCurrentCCTValue(deviceId, endpoint) }

    suspend fun setCCTValue(deviceId: Long, endpoint: Int, colorTemperatureMireds: Int): Unit {
        Log.d(TAG, "setCCTValue() - setting color temperature to $colorTemperatureMireds mireds")
        val connectedDevicePtr =
            try {
                chipClient.getConnectedDevicePointer(deviceId)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Can't get connectedDevicePointer.")
                return
            }
        return suspendCoroutine { continuation ->
            getColorControlClusterForDevice(connectedDevicePtr, endpoint)
                .moveToColorTemperature(
                    object : ChipClusters.DefaultClusterCallback {

                        override fun onSuccess() {
                            Log.d(TAG, "setCCTValue success - color temperature set to $colorTemperatureMireds mireds")
                            continuation.resume(Unit)
                        }

                        override fun onError(ex: java.lang.Exception) {
                            Log.e(TAG, "setCCTValue command failure")
                            continuation.resumeWithException(ex)
                        }
                    }, colorTemperatureMireds, 0, 0, 0
                )
        }
    }

    fun setCCTValueAsync(
        deviceId: Long,
        endpoint: Int,
        colorTemperatureMireds: Int
    ): CompletableFuture<Unit> =
        GlobalScope.future { setCCTValue(deviceId, endpoint, colorTemperatureMireds) }

    // Convenience methods for working with Kelvin values in UI
    
    suspend fun getCurrentCCTValueInKelvin(deviceId: Long, endpoint: Int): Int? {
        Log.d(TAG, "getCurrentCCTValueInKelvin()")
        val miredsValue = getCurrentCCTValue(deviceId, endpoint)
        return if (miredsValue != null) {
            val kelvinValue = miredsToKelvin(miredsValue)
            Log.d(TAG, "Converted $miredsValue mireds to $kelvinValue Kelvin")
            clampKelvin(kelvinValue)
        } else {
            null
        }
    }

    fun getCurrentCCTValueInKelvinAsync(
        deviceId: Long,
        endpoint: Int
    ): CompletableFuture<Int?> =
        GlobalScope.future { getCurrentCCTValueInKelvin(deviceId, endpoint) }

    suspend fun setCCTValueFromKelvin(deviceId: Long, endpoint: Int, kelvin: Int): Unit {
        val clampedKelvin = clampKelvin(kelvin)
        val miredsValue = kelvinToMireds(clampedKelvin)
        Log.d(TAG, "setCCTValueFromKelvin() - converting ${clampedKelvin}K to ${miredsValue} mireds")
        setCCTValue(deviceId, endpoint, miredsValue)
    }

    fun setCCTValueFromKelvinAsync(
        deviceId: Long,
        endpoint: Int,
        kelvin: Int
    ): CompletableFuture<Unit> =
        GlobalScope.future { setCCTValueFromKelvin(deviceId, endpoint, kelvin) }

    private fun getColorControlClusterForDevice(
        devicePtr: Long,
        endpoint: Int
    ): ChipClusters.ColorControlCluster {
        return ChipClusters.ColorControlCluster(devicePtr, endpoint)
    }
}
