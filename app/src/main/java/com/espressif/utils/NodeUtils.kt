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

package com.espressif.utils

import android.text.TextUtils
import android.util.Log
import chip.devicecontroller.ChipClusters
import com.espressif.AppConstants
import com.espressif.AppConstants.Companion.SystemMode.COOL
import com.espressif.AppConstants.Companion.SystemMode.HEAT
import com.espressif.AppConstants.Companion.SystemMode.OFF
import com.espressif.EspApplication
import com.espressif.ui.models.Device
import com.espressif.ui.models.EspNode
import com.espressif.ui.models.Param
import com.espressif.ui.models.Service

class NodeUtils {

    companion object {

        const val TAG = "NodeUtils"

        // Color Control Cluster Attribute IDs
        const val COLOR_CONTROL_CURRENT_HUE_ATTR = 0x0L
        const val COLOR_CONTROL_CURRENT_SATURATION_ATTR = 0x1L
        private const val COLOR_CONTROL_CURRENT_X_ATTR = 0x3L
        private const val COLOR_CONTROL_CURRENT_Y_ATTR = 0x4L
        const val COLOR_CONTROL_COLOR_TEMPERATURE_ATTR = 0x7L
        private const val COLOR_CONTROL_COLOR_MODE_ATTR = 0x8L
        private const val COLOR_CONTROL_ENHANCED_CURRENT_HUE_ATTR = 0x4000L
        private const val COLOR_CONTROL_COLOR_LOOP_ACTIVE_ATTR = 0x4002L
        private const val COLOR_CONTROL_COLOR_CAPABILITIES_ATTR = 0x400AL

        /**
         * Helper method to check if a specific attribute exists in the Matter device info.
         */
        fun hasAttribute(
            matterDeviceInfo: List<com.espressif.matter.DeviceMatterInfo>?,
            endpoint: Int,
            clusterId: Long,
            attributeId: Long,
            isServerCluster: Boolean = true
        ): Boolean {
            if (matterDeviceInfo == null) return false

            for (info in matterDeviceInfo) {
                if (info.endpoint == endpoint) {
                    val clusters = if (isServerCluster) info.serverClusters else info.clientClusters
                    val clusterAttributes = info.clusterAttributes[clusterId.toString()]
                    Log.d(TAG, "clusterAttributes : " + clusterAttributes);
                    if (clusterAttributes != null) {
                        return clusterAttributes.contains(attributeId)
                    }
                }
            }
            return false
        }

        /**
         * Helper method to get attribute value from Matter device info.
         */
        private fun getAttributeValue(
            matterDeviceInfo: List<com.espressif.matter.DeviceMatterInfo>?,
            endpoint: Int,
            clusterId: Long,
            attributeId: Long,
            isServerCluster: Boolean = true
        ): Any? {
            if (matterDeviceInfo == null) return null

            for (info in matterDeviceInfo) {
                if (info.endpoint == endpoint) {
                    val clusters = if (isServerCluster) info.serverClusters else info.clientClusters
                    val clusterAttributes = info.clusterAttributes[clusterId.toString()]
                    if (clusterAttributes != null) {
                        return clusterAttributes[attributeId.toInt()]
                    }
                }
            }
            return null
        }

        fun getService(node: EspNode, serviceType: String): Service? {

            if (node?.services != null) {
                for (service in node.services) {
                    if (!TextUtils.isEmpty(service.type) && service.type.equals(serviceType)) {
                        return service
                    }
                }
            }
            return null
        }

        fun getEspDeviceTypeForMatterDevice(deviceType: Int): String {
            when (deviceType) {
                AppConstants.MATTER_DEVICE_ON_OFF_LIGHT, AppConstants.MATTER_DEVICE_DIMMABLE_LIGHT, AppConstants.MATTER_DEVICE_LIGHT_BULB -> return AppConstants.ESP_DEVICE_LIGHT_BULB
                AppConstants.MATTER_DEVICE_SWITCH -> return AppConstants.ESP_DEVICE_SWITCH
                AppConstants.MATTER_DEVICE_CONTACT_SENSOR -> return AppConstants.ESP_DEVICE_CONTACT_SENSOR
                AppConstants.MATTER_DEVICE_OUTLET -> return AppConstants.ESP_DEVICE_OUTLET
                AppConstants.MATTER_DEVICE_BULB_RGB -> return AppConstants.ESP_DEVICE_BULB_RGB
                AppConstants.MATTER_DEVICE_THERMOSTAT -> return AppConstants.ESP_DEVICE_THERMOSTAT
                AppConstants.MATTER_DEVICE_TEMP_SENSOR -> return AppConstants.ESP_DEVICE_TEMP_SENSOR
                AppConstants.MATTER_DEVICE_AC -> return AppConstants.ESP_DEVICE_AIR_CONDITIONER
                AppConstants.MATTER_DEVICE_DOOR_LOCK -> return AppConstants.ESP_DEVICE_LOCK
            }
            return AppConstants.ESP_DEVICE_OTHER
        }

        fun addParamsForMatterClusters(
            node: EspNode,
            clusters: List<Long>,
            deviceType: Long,
            isAttributesAvailable: Boolean
        ): EspNode {
            Log.e("Test", "Add params 1 for node : ${node.nodeId}")
            if (isAttributesAvailable) {
                Log.e("Test", "Add params 2")
                return addParamsForMatterClustersWithAttributes(node, clusters, deviceType, null)
            } else {
                Log.e("Test", "Add params 3")
                return addParamsForMatterClusters(node, clusters, deviceType)
            }
        }

        private fun addParamsForMatterClusters(
            node: EspNode,
            clusters: List<Long>,
            deviceType: Long
        ): EspNode {

            if (node.nodeStatus == AppConstants.NODE_STATUS_MATTER_LOCAL) {
                return node
            }

            var devices = node.devices

            if (devices == null || devices.size == 0) {
                val device = Device(node.nodeId)
                devices = java.util.ArrayList()
                devices.add(device)
                node.devices = devices
            }

            val properties = java.util.ArrayList<String>()
            properties.add(AppConstants.KEY_PROPERTY_WRITE)
            properties.add(AppConstants.KEY_PROPERTY_READ)

            val device = devices[0]
            var params = device.params
            if (params == null || params.size == 0) {
                params = java.util.ArrayList()
            }

            if (AppConstants.NODE_TYPE_PURE_MATTER.equals(node.newNodeType)) {

                var nameParam = ParamUtils.getParamIfAvailableInList(
                    params, AppConstants.PARAM_TYPE_NAME,
                    AppConstants.PARAM_NAME, AppConstants.UI_TYPE_TEXT,
                )

                if (nameParam == null) {
                    nameParam = Param()
                    nameParam.isDynamicParam = true
                    nameParam.dataType = "String"
                    nameParam.paramType = AppConstants.PARAM_TYPE_NAME
                    nameParam.name = AppConstants.PARAM_NAME
                    nameParam.uiType = AppConstants.UI_TYPE_TEXT
                    nameParam.properties = properties
                    params.add(nameParam)
                }

                if (!TextUtils.isEmpty(node.nodeMetadata.deviceName)) {
                    nameParam.labelValue = node.nodeMetadata.deviceName
                } else {
                    nameParam.labelValue = getDefaultNameForMatterDevice(deviceType.toInt())
                }
                device.userVisibleName = nameParam.labelValue
            }
            device.params = params

            for (cluster in clusters) {
                val clusterId = cluster

                if (clusterId == ChipClusters.OnOffCluster.CLUSTER_ID) {
                    Log.d(TAG, "Found On Off cluster")
                    val device = devices[0]
                    var params = device.params
                    if (params == null || params.size == 0) {
                        params = java.util.ArrayList()
                    }

                    val isParamAvailable: Boolean =
                        ParamUtils.isParamAvailableInList(
                            params,
                            AppConstants.PARAM_TYPE_POWER,
                        )

                    if (!isParamAvailable) {
                        // Add on/off param
                        ParamUtils.addToggleParam(params, properties)
                    }
                    device.params = params
                } else if (clusterId == ChipClusters.LevelControlCluster.CLUSTER_ID) {
                    Log.d(TAG, "Found level control cluster")
                    val device = devices[0]
                    var params = device.params
                    if (params == null || params.size == 0) {
                        params = java.util.ArrayList()
                    }
                    val isParamAvailable: Boolean =
                        ParamUtils.isParamAvailableInList(
                            params,
                            AppConstants.PARAM_TYPE_BRIGHTNESS
                        )
                    var brightnessParam: Param? = null

                    if (!isParamAvailable) {
                        // Add brightness param
                        brightnessParam = Param()
                        brightnessParam.isDynamicParam = true
                        brightnessParam.dataType = "int"
                        brightnessParam.paramType = AppConstants.PARAM_TYPE_BRIGHTNESS
                        brightnessParam.name = AppConstants.PARAM_BRIGHTNESS
                        brightnessParam.uiType = AppConstants.UI_TYPE_SLIDER
                        brightnessParam.minBounds = 0
                        brightnessParam.maxBounds = 100
                        brightnessParam.properties = properties
                        params.add(brightnessParam)
                    }
                    device.params = params

                } else if (clusterId == ChipClusters.ColorControlCluster.CLUSTER_ID) {
                    Log.d(TAG, "Found color control cluster")
                    val device = devices[0]
                    var params = device.params
                    if (params == null || params.size == 0) {
                        params = java.util.ArrayList()
                    }
                    val isSatParamAvailable: Boolean =
                        ParamUtils.isParamAvailableInList(
                            params,
                            AppConstants.PARAM_TYPE_SATURATION
                        )
                    val isHueParamAvailable: Boolean =
                        ParamUtils.isParamAvailableInList(
                            params,
                            AppConstants.PARAM_TYPE_HUE
                        )

                    if (!isSatParamAvailable) {
                        // Add saturation param
                        val saturation = Param()
                        saturation.isDynamicParam = true
                        saturation.dataType = "int"
                        saturation.paramType = AppConstants.PARAM_TYPE_SATURATION
                        saturation.name = AppConstants.PARAM_SATURATION
                        saturation.uiType = AppConstants.UI_TYPE_SLIDER
                        saturation.properties = properties
                        saturation.minBounds = 0
                        saturation.maxBounds = 100
                        params.add(saturation)
                    }

                    if (!isHueParamAvailable) {
                        // Add hue param
                        val hue = Param()
                        hue.isDynamicParam = true
                        hue.dataType = "int"
                        hue.paramType = AppConstants.PARAM_TYPE_HUE
                        hue.name = AppConstants.PARAM_HUE
                        hue.uiType = AppConstants.UI_TYPE_HUE_SLIDER
                        hue.properties = properties
                        params.add(hue)
                    }
                    device.params = params

                } else if (clusterId == ChipClusters.TemperatureMeasurementCluster.CLUSTER_ID) {
                    Log.d(TAG, "Found Temperature Measurement cluster")
                    val device = devices[0]
                    var params = device.params
                    if (params == null || params.size == 0) {
                        params = java.util.ArrayList()
                    }

                    var temperatureParam = ParamUtils.getParamIfAvailableInList(
                        params,
                        AppConstants.PARAM_TYPE_TEMPERATURE,
                        AppConstants.PARAM_TEMPERATURE,
                        AppConstants.UI_TYPE_TEXT
                    )

                    if (temperatureParam == null) {
                        temperatureParam = Param()
                        temperatureParam.isDynamicParam = true
                        temperatureParam.dataType = "int"
                        temperatureParam.paramType = AppConstants.PARAM_TYPE_TEMPERATURE
                        temperatureParam.name = AppConstants.PARAM_TEMPERATURE
                        temperatureParam.uiType = AppConstants.UI_TYPE_TEXT
                        val tempParamProperties = java.util.ArrayList<String>()
                        properties.add(AppConstants.KEY_PROPERTY_READ)
                        temperatureParam.properties = tempParamProperties
                        params.add(temperatureParam)
                        device.primaryParamName = AppConstants.PARAM_TYPE_TEMPERATURE
                    }
                    device.params = params

                } else if (clusterId == ChipClusters.DoorLockCluster.CLUSTER_ID) {
                    Log.d(TAG, "Found Door Lock cluster")
                    val device = devices[0]
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
                } else if (clusterId == ChipClusters.ThermostatCluster.CLUSTER_ID) {

                    Log.d(TAG, "Found Thermostat cluster")
                    val device = devices[0]
                    var params = device.params
                    if (params == null || params.size == 0) {
                        params = java.util.ArrayList()
                    }

                    // System Mode param

                    var modeParam = ParamUtils.getParamIfAvailableInList(
                        params, AppConstants.PARAM_TYPE_AC_MODE,
                        AppConstants.PARAM_SYSTEM_MODE, AppConstants.UI_TYPE_DROP_DOWN,
                    )

                    if (modeParam == null) {
                        modeParam = Param()
                        modeParam.isDynamicParam = true
                        modeParam.dataType = "String"
                        modeParam.paramType = AppConstants.PARAM_TYPE_AC_MODE
                        modeParam.name = AppConstants.PARAM_SYSTEM_MODE
                        modeParam.uiType = AppConstants.UI_TYPE_DROP_DOWN
                        modeParam.properties = properties
                        modeParam.value = OFF.modeValue.toDouble()
                        modeParam.labelValue = OFF.modeName
                        modeParam.validStrings = getValidStrings(device.deviceType);
                        params.add(modeParam)
                    }

                    // LocalTemperature attribute / param

                    var localTempParam = ParamUtils.getParamIfAvailableInList(
                        params,
                        AppConstants.PARAM_TYPE_TEMPERATURE, AppConstants.PARAM_LOCAL_TEMPERATURE,
                        AppConstants.UI_TYPE_TEXT
                    )

                    if (localTempParam == null) {
                        localTempParam = Param()
                        localTempParam.isDynamicParam = true
                        localTempParam.dataType = "int"
                        localTempParam.paramType = AppConstants.PARAM_TYPE_TEMPERATURE
                        localTempParam.name = AppConstants.PARAM_LOCAL_TEMPERATURE
                        localTempParam.uiType = AppConstants.UI_TYPE_TEXT
                        localTempParam.properties = properties
                        params.add(localTempParam)
                    }

                    // OccupiedCoolingSetpoint attribute / param

                    var coolingPointParam = ParamUtils.getParamIfAvailableInList(
                        params,
                        AppConstants.PARAM_TYPE_SETPOINT_TEMPERATURE,
                        AppConstants.PARAM_COOLING_POINT,
                        AppConstants.UI_TYPE_SLIDER
                    )

                    if (coolingPointParam == null) {
                        coolingPointParam = Param()
                        coolingPointParam.isDynamicParam = true
                        coolingPointParam.dataType = "int"
                        coolingPointParam.paramType = AppConstants.PARAM_TYPE_SETPOINT_TEMPERATURE
                        coolingPointParam.name = AppConstants.PARAM_COOLING_POINT
                        coolingPointParam.uiType = AppConstants.UI_TYPE_SLIDER
                        coolingPointParam.minBounds = 16
                        coolingPointParam.maxBounds = 32
                        coolingPointParam.properties = properties
                        params.add(coolingPointParam)
                    }

                    if (AppConstants.ESP_DEVICE_THERMOSTAT.equals(device.deviceType)) {

                        // OccupiedHeatingSetpoint attribute / param
                        var heatingPointParam = ParamUtils.getParamIfAvailableInList(
                            params,
                            AppConstants.PARAM_TYPE_SETPOINT_TEMPERATURE,
                            AppConstants.PARAM_HEATING_POINT,
                            AppConstants.UI_TYPE_SLIDER
                        )

                        if (heatingPointParam == null) {
                            heatingPointParam = Param()
                            heatingPointParam.isDynamicParam = true
                            heatingPointParam.dataType = "int"
                            heatingPointParam.paramType =
                                AppConstants.PARAM_TYPE_SETPOINT_TEMPERATURE
                            heatingPointParam.name = AppConstants.PARAM_HEATING_POINT
                            heatingPointParam.uiType = AppConstants.UI_TYPE_SLIDER
                            heatingPointParam.minBounds = 7
                            heatingPointParam.maxBounds = 30
                            heatingPointParam.properties = properties
                            params.add(heatingPointParam)
                        }
                    }

                    device.params = params

                } else if (clusterId == ChipClusters.FanControlCluster.CLUSTER_ID) {

                    Log.d(TAG, "Found Fan control cluster")
                    val device = devices[0]
                    var params = device.params
                    if (params == null || params.size == 0) {
                        params = java.util.ArrayList()
                    }

                    // Fan speed attribute / param
                    var speedParam = ParamUtils.getParamIfAvailableInList(
                        params,
                        AppConstants.PARAM_SPEED, AppConstants.PARAM_TYPE_SPEED,
                        AppConstants.UI_TYPE_SLIDER
                    )

                    if (speedParam == null) {
                        speedParam = Param()
                        speedParam.isDynamicParam = true
                        speedParam.dataType = "int"
                        speedParam.paramType = AppConstants.PARAM_TYPE_SPEED
                        speedParam.name = AppConstants.PARAM_SPEED
                        speedParam.uiType = AppConstants.UI_TYPE_SLIDER
                        speedParam.minBounds = 0
                        speedParam.maxBounds = 5
                        speedParam.properties = properties
                        params.add(speedParam)
                    }

                    device.params = params
                }
            }
            return node
        }

        /**
         * Enhanced version that creates parameters based on actual available attributes.
         * This method uses comprehensive Matter device information to create only the parameters
         * for attributes that actually exist on the device.
         */
        fun addParamsForMatterClustersWithAttributes(
            node: EspNode,
            clusters: List<Long>,
            deviceType: Long,
            matterDeviceInfo: List<com.espressif.matter.DeviceMatterInfo>?
        ): EspNode {

            var devices = node.devices

            if (devices == null || devices.size == 0) {
                val device = Device(node.nodeId)
                devices = java.util.ArrayList()
                devices.add(device)
                node.devices = devices
            }

            val properties = java.util.ArrayList<String>()
            properties.add(AppConstants.KEY_PROPERTY_WRITE)
            properties.add(AppConstants.KEY_PROPERTY_READ)

            val device = devices[0]
            var params = device.params
            if (params == null || params.size == 0) {
                params = java.util.ArrayList()
            }

            if (AppConstants.NODE_TYPE_PURE_MATTER.equals(node.newNodeType)) {

                var nameParam = ParamUtils.getParamIfAvailableInList(
                    params, AppConstants.PARAM_TYPE_NAME,
                    AppConstants.PARAM_NAME, AppConstants.UI_TYPE_TEXT,
                )

                if (nameParam == null) {
                    nameParam = Param()
                    nameParam.isDynamicParam = true
                    nameParam.dataType = "String"
                    nameParam.paramType = AppConstants.PARAM_TYPE_NAME
                    nameParam.name = AppConstants.PARAM_NAME
                    nameParam.uiType = AppConstants.UI_TYPE_TEXT
                    nameParam.properties = properties
                    params.add(nameParam)
                }

                if (!TextUtils.isEmpty(node.nodeMetadata.deviceName)) {
                    nameParam.labelValue = node.nodeMetadata.deviceName
                } else {
                    nameParam.labelValue = getDefaultNameForMatterDevice(deviceType.toInt())
                }
                device.userVisibleName = nameParam.labelValue
            }
            device.params = params

            for (cluster in clusters) {
                val clusterId = cluster

                if (clusterId == ChipClusters.OnOffCluster.CLUSTER_ID) {
                    Log.d(TAG, "Found On Off cluster")
                    val device = devices[0]
                    var params = device.params
                    if (params == null || params.size == 0) {
                        params = java.util.ArrayList()
                    }

                    val isParamAvailable: Boolean =
                        ParamUtils.isParamAvailableInList(
                            params,
                            AppConstants.PARAM_TYPE_POWER,
                        )

                    if (!isParamAvailable) {
                        // Add on/off param
                        ParamUtils.addToggleParam(params, properties)
                    }
                    device.params = params
                } else if (clusterId == ChipClusters.LevelControlCluster.CLUSTER_ID) {
                    Log.e(TAG, "Found level control cluster")
                    val device = devices[0]
                    var params = device.params
                    if (params == null || params.size == 0) {
                        params = java.util.ArrayList()
                    }
                    val isParamAvailable: Boolean =
                        ParamUtils.isParamAvailableInList(
                            params,
                            AppConstants.PARAM_TYPE_BRIGHTNESS
                        )
                    var brightnessParam: Param? = null

                    if (!isParamAvailable) {
                        // Add brightness param
                        brightnessParam = Param()
                        brightnessParam.isDynamicParam = true
                        brightnessParam.dataType = "int"
                        brightnessParam.paramType = AppConstants.PARAM_TYPE_BRIGHTNESS
                        brightnessParam.name = AppConstants.PARAM_BRIGHTNESS
                        brightnessParam.uiType = AppConstants.UI_TYPE_SLIDER
                        brightnessParam.minBounds = 0
                        brightnessParam.maxBounds = 100
                        brightnessParam.properties = properties
                        params.add(brightnessParam)
                    }
                    device.params = params

                } else if (clusterId == ChipClusters.ColorControlCluster.CLUSTER_ID) {
                    processColorControlClusterAttributes(devices[0], matterDeviceInfo, properties)

                } else if (clusterId == ChipClusters.TemperatureMeasurementCluster.CLUSTER_ID) {
                    Log.d(TAG, "Found Temperature Measurement cluster")
                    val device = devices[0]
                    var params = device.params
                    if (params == null || params.size == 0) {
                        params = java.util.ArrayList()
                    }

                    var temperatureParam = ParamUtils.getParamIfAvailableInList(
                        params,
                        AppConstants.PARAM_TYPE_TEMPERATURE,
                        AppConstants.PARAM_TEMPERATURE,
                        AppConstants.UI_TYPE_TEXT
                    )

                    if (temperatureParam == null) {
                        temperatureParam = Param()
                        temperatureParam.isDynamicParam = true
                        temperatureParam.dataType = "int"
                        temperatureParam.paramType = AppConstants.PARAM_TYPE_TEMPERATURE
                        temperatureParam.name = AppConstants.PARAM_TEMPERATURE
                        temperatureParam.uiType = AppConstants.UI_TYPE_TEXT
                        val tempParamProperties = java.util.ArrayList<String>()
                        properties.add(AppConstants.KEY_PROPERTY_READ)
                        temperatureParam.properties = tempParamProperties
                        params.add(temperatureParam)
                        device.primaryParamName = AppConstants.PARAM_TYPE_TEMPERATURE
                    }
                    device.params = params

                } else if (clusterId == ChipClusters.DoorLockCluster.CLUSTER_ID) {
                    Log.d(TAG, "Found Door Lock cluster")
                    val device = devices[0]
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
                } else if (clusterId == ChipClusters.ThermostatCluster.CLUSTER_ID) {

                    Log.d(TAG, "Found Thermostat cluster")
                    val device = devices[0]
                    var params = device.params
                    if (params == null || params.size == 0) {
                        params = java.util.ArrayList()
                    }

                    // System Mode param

                    var modeParam = ParamUtils.getParamIfAvailableInList(
                        params, AppConstants.PARAM_TYPE_AC_MODE,
                        AppConstants.PARAM_SYSTEM_MODE, AppConstants.UI_TYPE_DROP_DOWN,
                    )

                    if (modeParam == null) {
                        modeParam = Param()
                        modeParam.isDynamicParam = true
                        modeParam.dataType = "String"
                        modeParam.paramType = AppConstants.PARAM_TYPE_AC_MODE
                        modeParam.name = AppConstants.PARAM_SYSTEM_MODE
                        modeParam.uiType = AppConstants.UI_TYPE_DROP_DOWN
                        modeParam.properties = properties
                        modeParam.value = OFF.modeValue.toDouble()
                        modeParam.labelValue = OFF.modeName
                        modeParam.validStrings = getValidStrings(device.deviceType);
                        params.add(modeParam)
                    }

                    // LocalTemperature attribute / param

                    var localTempParam = ParamUtils.getParamIfAvailableInList(
                        params,
                        AppConstants.PARAM_TYPE_TEMPERATURE, AppConstants.PARAM_LOCAL_TEMPERATURE,
                        AppConstants.UI_TYPE_TEXT
                    )

                    if (localTempParam == null) {
                        localTempParam = Param()
                        localTempParam.isDynamicParam = true
                        localTempParam.dataType = "int"
                        localTempParam.paramType = AppConstants.PARAM_TYPE_TEMPERATURE
                        localTempParam.name = AppConstants.PARAM_LOCAL_TEMPERATURE
                        localTempParam.uiType = AppConstants.UI_TYPE_TEXT
                        localTempParam.properties = properties
                        params.add(localTempParam)
                    }

                    // OccupiedCoolingSetpoint attribute / param

                    var coolingPointParam = ParamUtils.getParamIfAvailableInList(
                        params,
                        AppConstants.PARAM_TYPE_SETPOINT_TEMPERATURE,
                        AppConstants.PARAM_COOLING_POINT,
                        AppConstants.UI_TYPE_SLIDER
                    )

                    if (coolingPointParam == null) {
                        coolingPointParam = Param()
                        coolingPointParam.isDynamicParam = true
                        coolingPointParam.dataType = "int"
                        coolingPointParam.paramType = AppConstants.PARAM_TYPE_SETPOINT_TEMPERATURE
                        coolingPointParam.name = AppConstants.PARAM_COOLING_POINT
                        coolingPointParam.uiType = AppConstants.UI_TYPE_SLIDER
                        coolingPointParam.minBounds = 16
                        coolingPointParam.maxBounds = 32
                        coolingPointParam.properties = properties
                        params.add(coolingPointParam)
                    }

                    if (AppConstants.ESP_DEVICE_THERMOSTAT.equals(device.deviceType)) {

                        // OccupiedHeatingSetpoint attribute / param
                        var heatingPointParam = ParamUtils.getParamIfAvailableInList(
                            params,
                            AppConstants.PARAM_TYPE_SETPOINT_TEMPERATURE,
                            AppConstants.PARAM_HEATING_POINT,
                            AppConstants.UI_TYPE_SLIDER
                        )

                        if (heatingPointParam == null) {
                            heatingPointParam = Param()
                            heatingPointParam.isDynamicParam = true
                            heatingPointParam.dataType = "int"
                            heatingPointParam.paramType =
                                AppConstants.PARAM_TYPE_SETPOINT_TEMPERATURE
                            heatingPointParam.name = AppConstants.PARAM_HEATING_POINT
                            heatingPointParam.uiType = AppConstants.UI_TYPE_SLIDER
                            heatingPointParam.minBounds = 7
                            heatingPointParam.maxBounds = 30
                            heatingPointParam.properties = properties
                            params.add(heatingPointParam)
                        }
                    }

                    device.params = params

                } else if (clusterId == ChipClusters.FanControlCluster.CLUSTER_ID) {

                    Log.d(TAG, "Found Fan control cluster")
                    val device = devices[0]
                    var params = device.params
                    if (params == null || params.size == 0) {
                        params = java.util.ArrayList()
                    }

                    // Fan speed attribute / param
                    var speedParam = ParamUtils.getParamIfAvailableInList(
                        params,
                        AppConstants.PARAM_SPEED, AppConstants.PARAM_TYPE_SPEED,
                        AppConstants.UI_TYPE_SLIDER
                    )

                    if (speedParam == null) {
                        speedParam = Param()
                        speedParam.isDynamicParam = true
                        speedParam.dataType = "int"
                        speedParam.paramType = AppConstants.PARAM_TYPE_SPEED
                        speedParam.name = AppConstants.PARAM_SPEED
                        speedParam.uiType = AppConstants.UI_TYPE_SLIDER
                        speedParam.minBounds = 0
                        speedParam.maxBounds = 5
                        speedParam.properties = properties
                        params.add(speedParam)
                    }

                    device.params = params
                }
            }
            return node
        }

        private fun getValidStrings(deviceType: String?): ArrayList<String>? {
            // It is assumed that device type is already set before adding this params
            if (AppConstants.ESP_DEVICE_THERMOSTAT.equals(deviceType)) {
                return arrayListOf(OFF.modeName, COOL.modeName, HEAT.modeName)
            } else if (AppConstants.ESP_DEVICE_AIR_CONDITIONER.equals(deviceType)) {
                return arrayListOf(OFF.modeName, COOL.modeName)
            } else {
                return arrayListOf("-", OFF.modeName, COOL.modeName, HEAT.modeName)
            }
        }

        fun getSystemModeStringFromValue(mode: Int): AppConstants.Companion.SystemMode {
            return when (mode) {
                AppConstants.Companion.SystemMode.OFF.modeValue -> AppConstants.Companion.SystemMode.OFF
                AppConstants.Companion.SystemMode.COOL.modeValue -> AppConstants.Companion.SystemMode.COOL
                AppConstants.Companion.SystemMode.HEAT.modeValue -> AppConstants.Companion.SystemMode.HEAT
                else -> AppConstants.Companion.SystemMode.OFF
            }
        }

        fun getDefaultNameForMatterDevice(deviceType: Int): String {
            when (deviceType) {
                AppConstants.MATTER_DEVICE_ON_OFF_LIGHT, AppConstants.MATTER_DEVICE_DIMMABLE_LIGHT, AppConstants.MATTER_DEVICE_LIGHT_BULB -> return "Light"
                AppConstants.MATTER_DEVICE_SWITCH -> return "Switch"
                AppConstants.MATTER_DEVICE_CONTACT_SENSOR -> return "Contact Sensor"
                AppConstants.MATTER_DEVICE_OUTLET -> return "Outlet"
                AppConstants.MATTER_DEVICE_BULB_RGB -> return "Light"
                AppConstants.MATTER_DEVICE_THERMOSTAT -> return "Thermostat"
                AppConstants.MATTER_DEVICE_TEMP_SENSOR -> return "Temperature Sensor"
                AppConstants.MATTER_DEVICE_AC -> return "AC"
                AppConstants.MATTER_DEVICE_DOOR_LOCK -> return "Door Lock"
            }
            return ""
        }

        fun getTbrServiceParamName(
            nodeId: String,
            paramType: String,
            espApp: EspApplication
        ): String {
            val threadBrService =
                getService(espApp.nodeMap[nodeId]!!, AppConstants.SERVICE_TYPE_TBR)
            if (threadBrService != null) {
                for (p in threadBrService.params) {
                    if (p.paramType == paramType) {
                        return p.name
                    }
                }
            }
            return ""
        }

        fun getActiveDatasetFromTbrService(threadBrService: Service): String {
            for (p in threadBrService.params) {
                if (p.paramType == AppConstants.PARAM_TYPE_ACTIVE_DATASET) {
                    return p.labelValue
                }
            }
            return ""
        }

        fun getBorderAgentIdFromTbrService(threadBrService: Service): String {
            for (p in threadBrService.params) {
                if (p.paramType == AppConstants.PARAM_TYPE_BORDER_AGENT_ID) {
                    return p.labelValue
                }
            }
            return ""
        }

        /**
         * Process Color Control cluster attributes and create parameters only for available attributes.
         * This method checks for specific color control attributes and creates appropriate UI parameters.
         */
        private fun processColorControlClusterAttributes(
            device: Device,
            matterDeviceInfo: List<com.espressif.matter.DeviceMatterInfo>?,
            properties: ArrayList<String>
        ) {
            var params = device.params
            if (params == null || params.size == 0) {
                params = java.util.ArrayList()
            }

            val endpoint = 1 // Most color devices use endpoint 1
            val colorControlClusterId = ChipClusters.ColorControlCluster.CLUSTER_ID

            Log.d("Test", "🔍 Checking Color Control attributes on endpoint $endpoint")
            Log.e("Test", "processColorControlClusterAttributes : ${device.nodeId}")

            var hueParam = ParamUtils.getParamIfAvailableInList(
                params,
                AppConstants.PARAM_TYPE_HUE,
                AppConstants.PARAM_HUE, AppConstants.UI_TYPE_HUE_SLIDER
            )

            var saturationParam = ParamUtils.getParamIfAvailableInList(
                params,
                AppConstants.PARAM_TYPE_SATURATION,
                AppConstants.PARAM_SATURATION, AppConstants.UI_TYPE_SLIDER
            )

            var cctParam = ParamUtils.getParamIfAvailableInList(
                params,
                AppConstants.PARAM_TYPE_CCT,
                AppConstants.PARAM_CCT, AppConstants.UI_TYPE_HUE_SLIDER
            )

            // Check for Color Temperature attribute (0x0007) - CCT support
            if (hasAttribute(
                    matterDeviceInfo,
                    endpoint,
                    colorControlClusterId,
                    COLOR_CONTROL_COLOR_TEMPERATURE_ATTR
                )
            ) {
                if (cctParam == null) {
                    Log.d(
                        "Test",
                        "✅ Creating CCT parameter - ColorTemperature attribute (0x7) found"
                    )
                    cctParam = Param()
                    cctParam.isDynamicParam = true
                    cctParam.dataType = "int"
                    cctParam.paramType = AppConstants.PARAM_TYPE_CCT
                    cctParam.name = AppConstants.PARAM_CCT
                    cctParam.uiType = AppConstants.UI_TYPE_SLIDER
                    cctParam.properties = properties
                    cctParam.minBounds = 2700  // Warm white (2700K)
                    cctParam.maxBounds = 6500  // Cool white (6500K)
                    Log.e("Test", "Adding cct param at position : ${params.size}")
                    params.add(cctParam)
                } else {
                    Log.d("Test", "ℹ️ CCT parameter already exists")
                }

                // Set initial value from attribute if available
                val currentCCT = getAttributeValue(
                    matterDeviceInfo,
                    endpoint,
                    colorControlClusterId,
                    COLOR_CONTROL_COLOR_TEMPERATURE_ATTR
                )
                if (currentCCT is Number) {
                    // Convert mireds to Kelvin for UI display
                    val miredsValue = currentCCT.toInt()
                    val kelvinValue = (1000000 / miredsValue).coerceIn(2700, 6500)
                    cctParam.value = kelvinValue.toDouble()
                    Log.d(
                        "Test",
                        "🎨 Set initial CCT value: ${miredsValue} mireds -> ${kelvinValue}K"
                    )
                } else {
                    cctParam.value = 4000.0 // Default to neutral white
                }
            } else {
                Log.d(
                    "Test",
                    "❌ ColorTemperature attribute (0x7) not found - skipping CCT parameter"
                )
            }

            // Check for Hue attribute (0x0000)
            if (hasAttribute(
                    matterDeviceInfo,
                    endpoint,
                    colorControlClusterId,
                    COLOR_CONTROL_CURRENT_HUE_ATTR
                )
            ) {
                if (hueParam == null) {
                    Log.d("Test", "✅ Creating Hue parameter - CurrentHue attribute (0x0) found")
                    hueParam = Param()
                    hueParam.isDynamicParam = true
                    hueParam.dataType = "int"
                    hueParam.paramType = AppConstants.PARAM_TYPE_HUE
                    hueParam.name = AppConstants.PARAM_HUE
                    hueParam.uiType = AppConstants.UI_TYPE_HUE_SLIDER
                    hueParam.properties = properties
                    hueParam.minBounds = 0
                    hueParam.maxBounds = 360
                    Log.e("Test", "Adding hue param at position : ${params.size}")
                    params.add(hueParam)
                } else {
                    Log.d("Test", "ℹ️ Hue parameter already exists")
                }

                // Set initial value from attribute if available
                val currentHue = getAttributeValue(
                    matterDeviceInfo,
                    endpoint,
                    colorControlClusterId,
                    COLOR_CONTROL_CURRENT_HUE_ATTR
                )
                if (currentHue is Number) {
                    // Convert from Matter scale (0-254) to degrees (0-360)
                    val hueValue = (currentHue.toInt() * 360) / 254
                    hueParam.value = hueValue.toDouble()
                    Log.d(
                        "Test",
                        "🎨 Set initial hue value: $hueValue° (from Matter value: ${currentHue})"
                    )
                }
            } else {
                Log.d("Test", "❌ CurrentHue attribute (0x0) not found - skipping Hue parameter")
                if (hueParam != null) {
                    params.remove(hueParam)
                }
            }

            // Check for Saturation attribute (0x0001)
            if (hasAttribute(
                    matterDeviceInfo,
                    endpoint,
                    colorControlClusterId,
                    COLOR_CONTROL_CURRENT_SATURATION_ATTR
                )
            ) {
                if (saturationParam == null) {
                    Log.d(
                        "Test",
                        "✅ Creating Saturation parameter - CurrentSaturation attribute (0x1) found"
                    )
                    saturationParam = Param()
                    saturationParam.isDynamicParam = true
                    saturationParam.dataType = "int"
                    saturationParam.paramType = AppConstants.PARAM_TYPE_SATURATION
                    saturationParam.name = AppConstants.PARAM_SATURATION
                    saturationParam.uiType = AppConstants.UI_TYPE_SLIDER
                    saturationParam.properties = properties
                    saturationParam.minBounds = 0
                    saturationParam.maxBounds = 100
                    Log.e("Test", "Adding saturation param at position : ${params.size}")
                    params.add(saturationParam)
                } else {
                    Log.d("Test", "ℹ️ Saturation parameter already exists")
                }

                // Set initial value from attribute if available
                val currentSaturation = getAttributeValue(
                    matterDeviceInfo,
                    endpoint,
                    colorControlClusterId,
                    COLOR_CONTROL_CURRENT_SATURATION_ATTR
                )
                if (currentSaturation is Number) {
                    // Convert from Matter scale (0-254) to percentage (0-100)
                    val satValue = (currentSaturation.toInt() * 100) / 254
                    saturationParam.value = satValue.toDouble()
                    Log.d(
                        "Test",
                        "🎨 Set initial saturation value: $satValue% (from Matter value: ${currentSaturation})"
                    )
                }
            } else {
                Log.d(
                    "Test",
                    "❌ CurrentSaturation attribute (0x1) not found - skipping Saturation parameter"
                )
                if (saturationParam != null) {
                    params.remove(saturationParam)
                }
            }

            // Check Color Capabilities to determine what color modes are supported
            val colorCapabilities = getAttributeValue(
                matterDeviceInfo,
                endpoint,
                colorControlClusterId,
                COLOR_CONTROL_COLOR_CAPABILITIES_ATTR
            )
            if (colorCapabilities is Number) {
                val capabilities = colorCapabilities.toInt()
                Log.d(TAG, "🎯 Color Capabilities: 0x${capabilities.toString(16)}")

                // Bit 0: Hue and Saturation
                if ((capabilities and 0x01) != 0) {
                    Log.d(TAG, "  ✅ Supports Hue/Saturation mode")
                }

                // Bit 1: Enhanced Hue
                if ((capabilities and 0x02) != 0) {
                    Log.d(TAG, "  ✅ Supports Enhanced Hue mode")
                }

                // Bit 2: Color Loop
                if ((capabilities and 0x04) != 0) {
                    Log.d(TAG, "  ✅ Supports Color Loop mode")
                }

                // Bit 3: XY
                if ((capabilities and 0x08) != 0) {
                    Log.d(TAG, "  ✅ Supports XY color mode")
                }

                // Bit 4: Color Temperature
                if ((capabilities and 0x10) != 0) {
                    Log.d(TAG, "  ✅ Supports Color Temperature mode")
                }
            }

            // Log summary of created parameters
            val colorParams = params.filter {
                it.paramType == AppConstants.PARAM_TYPE_HUE ||
                        it.paramType == AppConstants.PARAM_TYPE_SATURATION ||
                        it.paramType == AppConstants.PARAM_TYPE_CCT
            }

            Log.d(TAG, "📊 Color Control Summary: Created ${colorParams.size} color parameters")
            colorParams.forEach { param ->
                Log.d(TAG, "  🎨 ${param.name} (${param.paramType}): ${param.value}")
            }

            device.params = params
        }
    }
}