// Copyright 2025 Espressif Systems (Shanghai) PTE LTD
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
import chip.devicecontroller.ReportCallback
import chip.devicecontroller.ResubscriptionAttemptCallback
import chip.devicecontroller.SubscriptionEstablishedCallback
import chip.devicecontroller.model.ChipAttributePath
import chip.devicecontroller.model.ChipEventPath
import chip.devicecontroller.model.ChipPathId
import com.espressif.AppConstants
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SubscriptionHelper constructor(private val chipClient: ChipClient) {

    companion object {
        const val TAG = "SubscriptionHelper"
    }

    suspend fun awaitSubscribeToPeriodicUpdates(
        connectedDevicePtr: Long,
        endpointId: Long,
        clusterId: Long,
        attributeId: Long,
        subscriptionEstablishedCallback: SubscriptionEstablishedCallback,
        resubscriptionAttemptCallback: ResubscriptionAttemptCallback,
        reportCallback: ReportCallback
    ) {
        return suspendCoroutine { continuation ->
            Log.d(
                TAG,
                "subscribeToPeriodicUpdates() - Device: $connectedDevicePtr, Endpoint: $endpointId, Cluster: $clusterId, Attribute: $attributeId"
            )

            val endpoint = ChipPathId.forId(endpointId)
            val cluster = ChipPathId.forId(clusterId)
            val attribute = ChipPathId.forId(attributeId)
            val minInterval = 1 // seconds
            val maxInterval = 10 // seconds
            val attributePath = ChipAttributePath.newInstance(endpoint, cluster, attribute)
            val eventPath = ChipEventPath.newInstance(endpoint, cluster, attribute)

            chipClient.chipDeviceController.subscribeToPath(
                subscriptionEstablishedCallback,
                resubscriptionAttemptCallback,
                reportCallback,
                connectedDevicePtr,
                listOf(attributePath),
                listOf(eventPath),
                minInterval,
                maxInterval,
                true,
                false,
                0
            )
            continuation.resume(Unit)
        }
    }

    suspend fun subscribeToMultipleAttributes(
        connectedDevicePtr: Long,
        subscriptions: List<AttributeSubscription>,
        subscriptionEstablishedCallback: SubscriptionEstablishedCallback,
        resubscriptionAttemptCallback: ResubscriptionAttemptCallback,
        reportCallback: ReportCallback
    ) {
        return suspendCoroutine { continuation ->
            Log.d(
                TAG,
                "subscribeToMultipleAttributes() - Device: $connectedDevicePtr, Subscriptions: ${subscriptions.size}"
            )

            val attributePaths = subscriptions.map { subscription ->
                val endpoint = ChipPathId.forId(subscription.endpointId)
                val cluster = ChipPathId.forId(subscription.clusterId)
                val attribute = ChipPathId.forId(subscription.attributeId)
                ChipAttributePath.newInstance(endpoint, cluster, attribute)
            }

            val eventPaths = subscriptions.map { subscription ->
                val endpoint = ChipPathId.forId(subscription.endpointId)
                val cluster = ChipPathId.forId(subscription.clusterId)
                val attribute = ChipPathId.forId(subscription.attributeId)
                ChipEventPath.newInstance(endpoint, cluster, attribute)
            }

            val minInterval = 1 // seconds
            val maxInterval = 10 // seconds

            chipClient.chipDeviceController.subscribeToPath(
                subscriptionEstablishedCallback,
                resubscriptionAttemptCallback,
                reportCallback,
                connectedDevicePtr,
                attributePaths,
                eventPaths,
                minInterval,
                maxInterval,
                true,
                false,
                0
            )
            continuation.resume(Unit)
        }
    }

    // Callback classes for different scenarios
    class SubscriptionEstablishedCallbackForDevice(private val deviceId: Long) :
        SubscriptionEstablishedCallback {
        override fun onSubscriptionEstablished(subscriptionId: Long) {
            Log.d(
                TAG,
                "Subscription established for device $deviceId with subscription ID: $subscriptionId"
            )
        }
    }

    class ResubscriptionAttemptCallbackForDevice(private val deviceId: Long) :
        ResubscriptionAttemptCallback {
        override fun onResubscriptionAttempt(
            terminationCause: Long,
            nextResubscribeIntervalMsec: Long
        ) {
            Log.d(
                TAG,
                "Resubscription attempt for device $deviceId. Cause: $terminationCause, Next interval: $nextResubscribeIntervalMsec ms"
            )
//            return
        }
    }

    // Data class to represent an attribute subscription
    data class AttributeSubscription(
        val endpointId: Long,
        val clusterId: Long,
        val attributeId: Long,
        val paramType: String? = null,
        val paramName: String? = null
    )

    // Helper function to create common subscriptions for different device types
    fun createSubscriptionsForDevice(
        deviceType: String,
        endpointId: Long = 1
    ): List<AttributeSubscription> {
        return when (deviceType) {
            "esp.device.lightbulb" -> listOf(
                AttributeSubscription(
                    endpointId,
                    6L,
                    0L,
                    "power",
                    AppConstants.PARAM_POWER
                ), // OnOff cluster - OnOff attribute
                AttributeSubscription(
                    endpointId,
                    8L,
                    0L,
                    "brightness",
                    AppConstants.PARAM_BRIGHTNESS
                ), // Level Control cluster - CurrentLevel attribute
                AttributeSubscription(
                    endpointId,
                    768L,
                    0L,
                    "hue",
                    AppConstants.PARAM_HUE
                ), // Color Control cluster - CurrentHue attribute
                AttributeSubscription(
                    endpointId,
                    768L,
                    1L,
                    "saturation",
                    AppConstants.PARAM_SATURATION
                ), // Color Control cluster - CurrentSaturation attribute
                AttributeSubscription(
                    endpointId,
                    768L,
                    7L,
                    "cct",
                    AppConstants.PARAM_CCT
                ) // Color Control cluster - ColorTemperature attribute
            )

            "esp.device.fan" -> listOf(
                AttributeSubscription(endpointId, 6L, 0L, "power", "Power"), // OnOff cluster
                AttributeSubscription(
                    endpointId,
                    514L,
                    0L,
                    "speed",
                    AppConstants.PARAM_SPEED
                ) // Fan Control cluster - FanMode attribute
            )

            "esp.device.switch" -> listOf(
                AttributeSubscription(
                    endpointId,
                    6L,
                    0L,
                    "power",
                    AppConstants.PARAM_POWER
                ) // OnOff cluster
            )

            "esp.device.lock" -> listOf(
                AttributeSubscription(
                    endpointId,
                    257L,
                    0L,
                    "lock_state",
                    "Lock State"
                ) // Door Lock cluster - LockState attribute
            )

            "esp.device.thermostat" -> listOf(
                AttributeSubscription(
                    endpointId,
                    513L,
                    0L,
                    "local_temperature",
                    "Local Temperature"
                ), // Thermostat cluster - LocalTemperature
                AttributeSubscription(
                    endpointId,
                    513L,
                    17L,
                    "occupied_cooling_setpoint",
                    "Cooling Setpoint"
                ), // OccupiedCoolingSetpoint
                AttributeSubscription(
                    endpointId,
                    513L,
                    18L,
                    "occupied_heating_setpoint",
                    "Heating Setpoint"
                ), // OccupiedHeatingSetpoint
                AttributeSubscription(
                    endpointId,
                    513L,
                    28L,
                    "system_mode",
                    AppConstants.PARAM_SYSTEM_MODE
                ) // SystemMode
            )

            "esp.device.temperature-sensor" -> listOf(
                AttributeSubscription(
                    endpointId,
                    1026L,
                    0L,
                    "temperature",
                    AppConstants.PARAM_TEMPERATURE
                ) // Temperature Measurement cluster - MeasuredValue
            )

            else -> {
                Log.w(TAG, "Unknown device type: $deviceType, using default subscriptions")
                listOf(
                    AttributeSubscription(
                        endpointId,
                        6L,
                        0L,
                        "power",
                        "Power"
                    ) // Default to OnOff cluster
                )
            }
        }
    }
}
