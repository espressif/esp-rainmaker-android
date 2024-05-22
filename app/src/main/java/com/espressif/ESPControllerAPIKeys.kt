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

package com.espressif

class ESPControllerAPIKeys {

    companion object {

        const val KEY_MATTER_CONTROLLER = "matter-controller"
        const val KEY_MATTER_CONTROLLER_DATA_VERSION = "matter-controller-data-version"
        const val KEY_MATTER_CONTROLLER_DATA = "matter-controller-data"
        const val KEY_DATA = "data"
        const val KEY_ENABLED = "enabled"
        const val KEY_REACHABLE = "reachable"
        const val KEY_MATTER_NODES = "matter-nodes"
        const val KEY_MATTER_NODE_ID = "matter-node-id"
        const val KEY_ENDPOINTS = "endpoints"
        const val KEY_ENDPOINT_ID = "endpoint-id"
        const val KEY_CLUSTERS = "clusters"
        const val KEY_CLUSTER_ID = "cluster-id"
        const val KEY_COMMANDS = "commands"
        const val KEY_COMMAND_ID = "command-id"

        const val ENDPOINT_ID_1 = "0x1"

        const val CLUSTER_ID_ON_OFF = "0x6"
        const val CLUSTER_ID_LEVEL_CONTROL = "0x8"
        const val CLUSTER_ID_COLOR_CONTROL = "0x300"
        const val CLUSTER_ID_THERMOSTAT = "0x201"
        const val CLUSTER_ID_TEMPERATURE_MEASUREMENT = "0x402"

        const val COMMAND_ID_OFF = "0x0"
        const val COMMAND_ID_ON = "0x1"
        const val COMMAND_ID_TOGGLE = "0x2"
        const val COMMAND_ID_MOVE_TO_LEVEL_WITH_ON_OFF = "0x0"
        const val COMMAND_ID_MOVE_TO_SATURATION = "0x3"
        const val COMMAND_ID_MOVE_TO_HUE = "0x0"

        const val ATTRIBUTE_ID_ON_OFF = "0x0"
        const val ATTRIBUTE_ID_BRIGHTNESS_LEVEL = "0x0"
        const val ATTRIBUTE_ID_CURRENT_HUE = "0x0"
        const val ATTRIBUTE_ID_CURRENT_SATURATION = "0x1"
        const val ATTRIBUTE_ID_LOCAL_TEMPERATURE = "0x0"
        const val ATTRIBUTE_ID_SYSTEM_MODE = "0x1c"
        const val ATTRIBUTE_ID_OCCUPIED_COOLING_SETPOINT = "0x11"
        const val ATTRIBUTE_ID_OCCUPIED_HEATING_SETPOINT = "0x12"
        const val ATTRIBUTE_ID_MEASURED_TEMPERATURE = "0x0"
    }
}