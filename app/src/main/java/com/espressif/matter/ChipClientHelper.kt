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

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import chip.devicecontroller.ChipClusters
import com.espressif.AppConstants
import com.espressif.AppConstants.Companion.UpdateEventType
import com.espressif.EspApplication
import com.espressif.ui.Utils
import com.espressif.ui.models.Device
import com.espressif.ui.models.EspNode
import com.espressif.ui.models.Param
import com.espressif.ui.models.UpdateEvent
import com.espressif.utils.NodeUtils
import com.espressif.utils.ParamUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.greenrobot.eventbus.EventBus
import java.math.BigInteger
import java.util.concurrent.ExecutionException

class ChipClientHelper constructor(private val espApp: EspApplication) {

    companion object {
        const val TAG = "ChipClientHelper"
    }

    suspend fun initChipClient(matterNodeId: String) {

        Log.d(TAG, "Init ChipController for matter node id : $matterNodeId")
        if (TextUtils.isEmpty(matterNodeId)) {
            Log.e(
                TAG,
                "======= Init ChipController will not be done. Matter node id is not available"
            )
            EventBus.getDefault()
                .post(UpdateEvent(UpdateEventType.EVENT_MATTER_DEVICE_CONNECTIVITY))
            return;
        }

        try {

            for ((_, g) in espApp.groupMap.entries) {
                if (g.isMatter) {
                    val nodeDetails = g.nodeDetails
                    if (nodeDetails != null) {
                        for ((nodeId, mNodeId) in nodeDetails.entries) {
                            var fabricId = ""
                            var ipk = ""
                            var rootCa = ""
                            var groupCatIdOperate = ""
                            if (matterNodeId != mNodeId) {
                                continue
                            }
                            Log.d(
                                TAG,
                                "Node detail, node id : $nodeId and matter node id : $matterNodeId"
                            )
                            if (g.fabricDetails != null) {
                                fabricId = g.fabricDetails.fabricId
                                rootCa = g.fabricDetails.rootCa
                                ipk = g.fabricDetails.ipk
                                groupCatIdOperate = g.fabricDetails.groupCatIdOperate
                                if (!espApp.chipClientMap.containsKey(matterNodeId)) {
                                    if (!TextUtils.isEmpty(fabricId) && !TextUtils.isEmpty(rootCa)
                                        && !TextUtils.isEmpty(ipk) && !TextUtils.isEmpty(
                                            matterNodeId
                                        )
                                        && !TextUtils.isEmpty(matterNodeId)
                                    ) {
                                        val chipClient = ChipClient(
                                            espApp,
                                            g.groupId,
                                            fabricId,
                                            rootCa,
                                            ipk,
                                            groupCatIdOperate
                                        )
                                        espApp.chipClientMap.put(matterNodeId, chipClient)
                                    }
                                }
                                espApp.fetchDeviceMatterInfo(matterNodeId, nodeId)
                                val node: EspNode? = espApp.nodeMap.get(nodeId)
                                if (node != null) {
                                    if (node.devices == null || node.devices.isEmpty()) {
                                        Log.e(TAG, "Matter device list is empty for node $nodeId (matterNodeId : $matterNodeId)");
                                        continue
                                    }
                                    if (node.devices[0] == null || node.devices[0].params == null) {
                                        addParamsForMatterDevice(nodeId, matterNodeId, node)
                                    }
                                    getCurrentValues(nodeId, matterNodeId, node)
                                }
                                Log.d(TAG, "Init and fetch cluster info done for the device")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            var updateEvent = UpdateEvent(UpdateEventType.EVENT_MATTER_DEVICE_CONNECTIVITY)
            var data = Bundle()
            data.putString(AppConstants.KEY_MATTER_NODE_ID, matterNodeId)
            updateEvent.data = data
            EventBus.getDefault().post(updateEvent)
        }
    }

    fun initChipClientInBackground(matterNodeId: String) =
        GlobalScope.future { initChipClient(matterNodeId) }

    fun addParamsForMatterDevice(nodeId: String?, matterNodeId: String?, node: EspNode) {
        Log.d(TAG, "Adding Params for matter node id : $matterNodeId")
        val id = BigInteger(matterNodeId, 16)
        val deviceId = id.toLong()
        Log.d(TAG, "Device id : $deviceId")

        if (espApp.matterDeviceInfoMap.containsKey(matterNodeId)) {

            val matterDeviceInfo: MutableList<DeviceMatterInfo>? =
                espApp.matterDeviceInfoMap.get(matterNodeId)

            if (matterDeviceInfo != null) {
                for ((endpoint, types, serverClusters, clientClusters) in matterDeviceInfo) {
                    Log.d(TAG, "Endpoint : $endpoint")
                    Log.d(TAG, "Server Clusters : $serverClusters")
                    Log.d(TAG, "Client Clusters : $clientClusters")
                    Log.d(TAG, "Types : $types")

                    if (endpoint == AppConstants.ENDPOINT_1) {
                        var deviceType = ""
                        var devices = node.devices

                        if (devices == null || devices.size == 0) {
                            val device = Device(nodeId)
                            devices = java.util.ArrayList()
                            devices.add(device)
                            node.devices = devices
                        }

                        val properties = java.util.ArrayList<String>()
                        properties.add(AppConstants.KEY_PROPERTY_WRITE)
                        properties.add(AppConstants.KEY_PROPERTY_READ)

                        val clusters: List<Long> = serverClusters.mapNotNull {
                            (it as? Number)?.toLong() // Safely cast to Number and then to Long
                        }
                        val espNode = NodeUtils.addParamsForMatterClusters(node, clusters, types[0])
                        espApp.nodeMap.put(nodeId, espNode)

                        if (TextUtils.isEmpty(deviceType)) {
                            for (cluster in clientClusters) {
                                val clusterId = cluster as Long

                                if (clusterId == ChipClusters.OnOffCluster.CLUSTER_ID) {
                                    Log.d(TAG, "Found On Off Cluster in client clusters")

                                    if (devices == null || devices.size == 0) {
                                        val device = Device(nodeId)
                                        devices = java.util.ArrayList()
                                        devices.add(device)
                                        node.devices = devices
                                    }

                                    val device = devices[0]
                                    deviceType = AppConstants.ESP_DEVICE_SWITCH
                                    device.deviceType = deviceType
                                    var params = device.params
                                    if (params == null || params.size == 0) {
                                        params = java.util.ArrayList()
                                    }
                                    val isParamAvailable: Boolean =
                                        ParamUtils.isParamAvailableInList(
                                            params,
                                            AppConstants.PARAM_TYPE_POWER
                                        )

                                    if (!isParamAvailable) {
                                        // Add on/off param
                                        ParamUtils.addToggleParam(params, properties)
                                    }
                                    device.params = params
                                }
                            }
                            espApp.nodeMap.put(nodeId, node)
                        }
                    }
                }
            }
        }
    }

    @Throws(ExecutionException::class)
    fun getCurrentValues(nodeId: String?, matterNodeId: String?, node: EspNode) {

        val id = BigInteger(matterNodeId, 16)
        val deviceId = id.toLong()
        Log.d(TAG, "Device id : $deviceId")

        if (espApp.matterDeviceInfoMap.containsKey(matterNodeId)) {
            var matterDeviceInfo = espApp.matterDeviceInfoMap.get(matterNodeId)

            if (matterDeviceInfo != null && matterDeviceInfo.size > 0) {

                for (clusterInfo in matterDeviceInfo) {

                    if (node.devices[0].params == null) {
                        Log.e(TAG, "Matter device params are not available")
                        return
                    }

                    var params: ArrayList<Param> = node.devices[0].params

                    if (clusterInfo.endpoint == AppConstants.ENDPOINT_1 && clusterInfo.serverClusters != null
                    ) {
                        if (clusterInfo.serverClusters.contains(ChipClusters.OnOffCluster.CLUSTER_ID)) {

                            val espClusterHelper =
                                OnOffClusterHelper(espApp.chipClientMap[matterNodeId]!!)
                            val onOffStatus: Boolean? =
                                espClusterHelper.getDeviceStateOnOffClusterAsync(
                                    deviceId,
                                    AppConstants.ENDPOINT_1
                                ).get()

                            Log.d(TAG, "On off cluster value : : $onOffStatus")

                            for (param in params) {
                                if (AppConstants.PARAM_TYPE_POWER.equals(param.paramType)) {
                                    if (onOffStatus != null) {
                                        param.switchStatus = onOffStatus
                                    }
                                }
                            }
                        }

                        if (clusterInfo.serverClusters.contains(ChipClusters.LevelControlCluster.CLUSTER_ID)) {

                            val espClusterHelper =
                                LevelControlClusterHelper(espApp.chipClientMap[matterNodeId]!!)
                            var brightnessValue: Int? =
                                espClusterHelper.getCurrentLevelValueAsync(
                                    deviceId,
                                    AppConstants.ENDPOINT_1
                                ).get()

                            Log.d(TAG, "Level control cluster value : $brightnessValue")

                            for (param in params) {
                                if (AppConstants.PARAM_TYPE_BRIGHTNESS.equals(param.paramType)) {
                                    if (brightnessValue != null) {
                                        var temp = ((brightnessValue * 100f) / 255f)
                                        brightnessValue = temp.toInt()
                                        param.value = brightnessValue.toDouble()
                                    }
                                }
                            }
                        }

                        if (clusterInfo.serverClusters.contains(ChipClusters.ColorControlCluster.CLUSTER_ID)) {

                            val espClusterHelper =
                                ColorControlClusterHelper(espApp.chipClientMap[matterNodeId]!!)
                            var hueValue: Int? =
                                espClusterHelper.getCurrentHueValueAsync(
                                    deviceId,
                                    AppConstants.ENDPOINT_1
                                ).get()

                            var saturationValue: Int? =
                                espClusterHelper.getCurrentSaturationValueAsync(
                                    deviceId,
                                    AppConstants.ENDPOINT_1
                                ).get()

                            Log.d(TAG, "Color control cluster  hueValue : $hueValue")
                            Log.d(TAG, "Color control cluster saturationValue : $saturationValue")

                            for (param in params) {
                                if (AppConstants.PARAM_TYPE_HUE.equals(param.paramType)) {
                                    if (hueValue != null) {
                                        var temp = ((hueValue * 360f) / 255f)
                                        hueValue = temp.toInt()
                                        param.value = hueValue.toDouble()
                                    }
                                } else if (AppConstants.PARAM_TYPE_SATURATION.equals(param.paramType)) {
                                    if (saturationValue != null) {
                                        var temp = ((saturationValue * 100f) / 255f)
                                        saturationValue = temp.toInt()
                                        param.value = saturationValue.toDouble()
                                    }
                                }
                            }
                        }

                        if (clusterInfo.serverClusters.contains(ChipClusters.TemperatureMeasurementCluster.CLUSTER_ID)) {

                            val espClusterHelper =
                                TemperatureClusterHelper(espApp.chipClientMap[matterNodeId]!!)
                            var temperatureValue: Double? =
                                espClusterHelper.getTemperatureAsync(
                                    deviceId,
                                    AppConstants.ENDPOINT_1
                                ).get()

                            Log.d(TAG, "Temperature value : $temperatureValue")

                            for (param in params) {
                                if (AppConstants.PARAM_TYPE_TEMPERATURE.equals(param.paramType)) {
                                    if (temperatureValue != null) {
                                        param.value = temperatureValue
                                        param.labelValue = temperatureValue.toString()
                                    }
                                }
                            }
                        }

                        if (clusterInfo.serverClusters.contains(ChipClusters.DoorLockCluster.CLUSTER_ID)) {

                            val espClusterHelper =
                                DoorLockClusterHelper(espApp.chipClientMap[matterNodeId]!!)
                            var lockStateValue: Int? =
                                espClusterHelper.getLockStateAsync(
                                    deviceId,
                                    AppConstants.ENDPOINT_1
                                ).get()

                            Log.d(TAG, "Door lock state value : $lockStateValue")
                        }

                        if (clusterInfo.serverClusters.contains(ChipClusters.FanControlCluster.CLUSTER_ID)) {

                            val espClusterHelper =
                                FanControlClusterHelper(espApp.chipClientMap[matterNodeId]!!)
                            var fanSpeed: Int? =
                                espClusterHelper.getFanSpeedAsync(
                                    deviceId,
                                    AppConstants.ENDPOINT_1
                                ).get()

                            Log.d(TAG, "Fan speed value : $fanSpeed")

                            for (param in params) {
                                if (AppConstants.PARAM_TYPE_SPEED.equals(param.paramType)) {
                                    if (fanSpeed != null) {
                                        param.value = fanSpeed.toDouble()
                                        param.labelValue = fanSpeed.toString()
                                    }
                                }
                            }
                        }

                        if (clusterInfo.serverClusters.contains(ChipClusters.ThermostatCluster.CLUSTER_ID)) {

                            val espClusterHelper =
                                ThermostatClusterHelper(espApp.chipClientMap[matterNodeId]!!)
                            var systemMode: Int? =
                                espClusterHelper.getSystemModeAsync(
                                    deviceId,
                                    AppConstants.ENDPOINT_1
                                ).get()

                            var coolingSetpoint: Int? =
                                espClusterHelper.getOccupiedCoolingSetpointAsync(
                                    deviceId,
                                    AppConstants.ENDPOINT_1
                                ).get()

                            var heatingSetpoint: Int? =
                                espClusterHelper.getOccupiedHeatingSetpointAsync(
                                    deviceId,
                                    AppConstants.ENDPOINT_1
                                ).get()

                            var localTemp: Int? =
                                espClusterHelper.getLocalTemperatureAsync(
                                    deviceId,
                                    AppConstants.ENDPOINT_1
                                ).get()

                            Log.d(
                                TAG,
                                "Thermostat cluster param values : mode - $systemMode, cooling point -  $coolingSetpoint, heating point - $heatingSetpoint, temp - $localTemp"
                            )

                            for (param in params) {
                                if (AppConstants.PARAM_SYSTEM_MODE.equals(param.name)) {
                                    if (systemMode != null) {
                                        val mode =
                                            NodeUtils.getSystemModeStringFromValue(systemMode)
                                        param.value = mode.modeValue.toDouble()
                                        param.labelValue = mode.modeName
                                    }
                                } else if (AppConstants.PARAM_COOLING_POINT.equals(param.name)) {
                                    if (coolingSetpoint != null) {
                                        param.value =
                                            Utils.temperatureDeviceToAppConversion(coolingSetpoint)
                                                .toDouble()
                                    }
                                } else if (AppConstants.PARAM_HEATING_POINT.equals(param.name)) {
                                    if (heatingSetpoint != null) {
                                        param.value =
                                            Utils.temperatureDeviceToAppConversion(heatingSetpoint)
                                                .toDouble()
                                    }
                                } else if (AppConstants.PARAM_TEMPERATURE.equals(param.name)) {
                                    if (localTemp != null) {
                                        param.value =
                                            Utils.temperatureDeviceToAppConversion(localTemp)
                                                .toDouble()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
