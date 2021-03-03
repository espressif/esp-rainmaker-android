// Copyright 2020 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif;

import com.espressif.rainmaker.BuildConfig;

public class AppConstants {

    public static final String SECURITY_0 = "Sec0";
    public static final String SECURITY_1 = "Sec1";

    public static final String TRANSPORT_SOFTAP = "softap";
    public static final String TRANSPORT_BLE = "ble";
    public static final String TRANSPORT_BOTH = "both";

    // Device End point names
    public static final String HANDLER_RM_USER_MAPPING = "cloud_user_assoc";
    public static final String HANDLER_RM_CLAIM = "rmaker_claim";

    public static final String ESP_PREFERENCES = "Esp_Preferences";
    public static final String ESP_DATABASE_NAME = "esp_db";
    public static final String NODE_TABLE = "node_table";
    public static final String GROUP_TABLE = "group_table";
    public static final String MDNS_SERVICE_TYPE = "_esp_local_ctrl._tcp.";
    public static final String LOCAL_CONTROL_PATH = "esp_local_ctrl/control";

    public enum UpdateEventType {

        EVENT_DEVICE_ADDED,
        EVENT_DEVICE_REMOVED,
        EVENT_ADD_DEVICE_TIME_OUT,
        EVENT_DEVICE_STATUS_UPDATE
    }

    public static final String CURRENT_VERSION = "v1";
    public static final String PATH_SEPARATOR = "/";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    // Cloud API End point Urls
    public static final String URL_SUPPORTED_VERSIONS = BuildConfig.BASE_URL + "/apiversions";
    public static final String URL_OAUTH_LOGIN = BuildConfig.BASE_URL + AppConstants.PATH_SEPARATOR
            + AppConstants.CURRENT_VERSION + "/login";
    public static final String URL_USER_NODE_MAPPING = BuildConfig.BASE_URL + AppConstants.PATH_SEPARATOR
            + AppConstants.CURRENT_VERSION + "/user/nodes/mapping";
    public static final String URL_USER_NODES = BuildConfig.BASE_URL + AppConstants.PATH_SEPARATOR
            + AppConstants.CURRENT_VERSION + "/user/nodes";
    public static final String URL_USER_NODES_DETAILS = BuildConfig.BASE_URL + AppConstants.PATH_SEPARATOR
            + AppConstants.CURRENT_VERSION + "/user/nodes?node_details=true";
    public static final String URL_USER_NODE_STATUS = BuildConfig.BASE_URL + AppConstants.PATH_SEPARATOR
            + AppConstants.CURRENT_VERSION + "/user/nodes/status";
    public static final String URL_USER_NODES_PARAMS = BuildConfig.BASE_URL + AppConstants.PATH_SEPARATOR
            + AppConstants.CURRENT_VERSION + "/user/nodes/params";
    public static final String URL_CLAIM_INITIATE = BuildConfig.CLAIM_BASE_URL + "/claim/initiate";
    public static final String URL_CLAIM_VERIFY = BuildConfig.CLAIM_BASE_URL + "/claim/verify";
    public static final String URL_USER_NODE_GROUP = BuildConfig.BASE_URL + AppConstants.PATH_SEPARATOR
            + AppConstants.CURRENT_VERSION + "/user/node_group";
    public static final String URL_USER_NODES_SHARING_REQUESTS = BuildConfig.BASE_URL + AppConstants.PATH_SEPARATOR
            + AppConstants.CURRENT_VERSION + "/user/nodes/sharing/requests";
    public static final String URL_USER_NODES_SHARING = BuildConfig.BASE_URL + AppConstants.PATH_SEPARATOR
            + AppConstants.CURRENT_VERSION + "/user/nodes/sharing";

    // UI Types of Device
    public static final String UI_TYPE_TOGGLE = "esp.ui.toggle";
    public static final String UI_TYPE_SLIDER = "esp.ui.slider";
    public static final String UI_TYPE_HUE_SLIDER = "esp.ui.hue-slider";
    public static final String UI_TYPE_DROP_DOWN = "esp.ui.dropdown";

    // ESP Device Types
    public static final String ESP_DEVICE_SWITCH = "esp.device.switch";
    public static final String ESP_DEVICE_BULB = "esp.device.lightbulb";
    public static final String ESP_DEVICE_BULB_CCT = "esp.device.lightbulb-cct";
    public static final String ESP_DEVICE_BULB_RGB = "esp.device.lightbulb-rgb";
    public static final String ESP_DEVICE_LOCK = "esp.device.lock";
    public static final String ESP_DEVICE_THERMOSTAT = "esp.device.thermostat";
    public static final String ESP_DEVICE_FAN = "esp.device.fan";
    public static final String ESP_DEVICE_SENSOR = "esp.device.sensor";
    public static final String ESP_DEVICE_TEMP_SENSOR = "esp.device.temperature-sensor";
    public static final String ESP_DEVICE_OUTLET = "esp.device.outlet";

    // Service Types
    public static final String SERVICE_TYPE_SCHEDULE = "esp.service.schedule";
    public static final String SERVICE_TYPE_TIME = "esp.service.time";

    // Param Types
    public static final String PARAM_TYPE_NAME = "esp.param.name";
    public static final String PARAM_TYPE_CCT = "esp.param.cct";
    public static final String PARAM_TYPE_POWER = "esp.param.power";
    public static final String PARAM_TYPE_OUTPUT = "esp.param.output";
    public static final String PARAM_TYPE_SATURATION = "esp.param.saturation";
    public static final String PARAM_TYPE_BRIGHTNESS = "esp.param.brightness";
    public static final String PARAM_TYPE_TEMPERATURE = "esp.param.temperature";
    public static final String PARAM_TYPE_TZ = "esp.param.tz";

    // Keys used to pass data between activities and to store data in SharedPreference.
    public static final String KEY_DEVICE_NAME_PREFIX = "device_prefix";
    public static final String KEY_PROOF_OF_POSSESSION = "proof_of_possession";
    public static final String KEY_DEVICE_NAME = "device_name";
    public static final String KEY_ESP_DEVICE = "esp_device";
    public static final String KEY_NODE_ID = "node_id";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_ID_TOKEN = "id_token";
    public static final String KEY_ACCESS_TOKEN = "access_token";
    public static final String KEY_REFRESH_TOKEN = "refresh_token";
    public static final String KEY_IS_OAUTH_LOGIN = "is_github_login";
    public static final String KEY_ERROR_MSG = "err_msg";
    public static final String KEY_SSID = "ssid";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_SECURITY_TYPE = "security_type";

    // Keys used in JSON responses and used to pass data between activities.
    public static final String KEY_NAME = "name";
    public static final String KEY_TYPE = "type";
    public static final String KEY_DATA_TYPE = "data_type";
    public static final String KEY_UI_TYPE = "ui_type";

    public static final String KEY_NODE_DETAILS = "node_details";
    public static final String KEY_DEVICES = "devices";
    public static final String KEY_PARAMS = "params";
    public static final String KEY_PROPERTIES = "properties";
    public static final String KEY_ATTRIBUTES = "attributes";
    public static final String KEY_SERVICES = "services";
    public static final String KEY_SCHEDULES = "Schedules";
    public static final String KEY_TRIGGERS = "triggers";
    public static final String KEY_BOUNDS = "bounds";
    public static final String KEY_SELECTED_DEVICES = "selected_devices";

    public static final String KEY_SUPPORTED_VERSIONS = "supported_versions";
    public static final String KEY_ADDITIONAL_INFO = "additional_info";
    public static final String KEY_CONFIG = "config";
    public static final String KEY_CONFIG_VERSION = "config_version";
    public static final String KEY_FW_VERSION = "fw_version";
    public static final String KEY_INFO = "info";
    public static final String KEY_PRIMARY = "primary";
    public static final String KEY_MAX = "max";
    public static final String KEY_MIN = "min";
    public static final String KEY_STEP = "step";
    public static final String KEY_VALID_STRS = "valid_strs";
    public static final String KEY_VALUE = "value";
    public static final String KEY_SCHEDULE = "Schedule";
    public static final String KEY_ACTION = "action";
    public static final String KEY_ACTIONS = "actions";
    public static final String KEY_DAYS = "d";
    public static final String KEY_MINUTES = "m";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_ID = "id";
    public static final String KEY_ROLE = "role";
    public static final String KEY_STATUS = "status";
    public static final String KEY_TIME = "Time";
    public static final String KEY_CONNECTIVITY = "connectivity";
    public static final String KEY_CONNECTED = "connected";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_PROPERTY_WRITE = "write";
    public static final String KEY_FAILURE_RESPONSE = "failure";
    public static final String KEY_SECRET_KEY = "secret_key";
    public static final String KEY_PROPERTY_COUNT = "property_count";
    public static final String KEY_CLAIM_VERIFY_RESPONSE = "claim_verify_response";
    public static final String KEY_CLAIM_INIT_RESPONSE = "claim_initiate_response";
    public static final String KEY_REQ_ID = "request_id";
    public static final String KEY_REQ_STATUS = "request_status";
    public static final String KEY_REQ_TIME = "request_timestamp";
    public static final String KEY_REQ_CONFIRMED = "confirmed";
    public static final String KEY_REQ_TIMEDOUT = "timedout";
    public static final String KEY_REQ_TIMESTAMP = "request_timestamp";
    public static final String KEY_USER_REQUEST = "user_request";
    public static final String KEY_GROUP_ID = "group_id";
    public static final String KEY_GROUP_NAME = "group_name";
    public static final String KEY_GROUPS = "groups";
    public static final String KEY_NODES = "nodes";
    public static final String KEY_NODE_LIST = "node_list";
    public static final String KEY_GROUP = "group";
    public static final String KEY_NODE_IDS = "node_ids";
    public static final String KEY_NODE_SHARING = "node_sharing";
    public static final String KEY_USERS = "users";
    public static final String KEY_PRIMARY_USER = "primary_user";
    public static final String KEY_START_REQ_ID = "start_request_id";
    public static final String KEY_START_USER_NAME = "start_user_name";
    public static final String KEY_NEXT_REQ_ID = "next_request_id";
    public static final String KEY_NEXT_USER_NAME = "next_user_name";
    public static final String KEY_PRIMARY_USER_NAME = "primary_user_name";
    public static final String KEY_USER_NAME = "user_name";
    public static final String KEY_REQ_ACCEPT = "accept";
    public static final String KEY_SHARING_REQUESTS = "sharing_requests";
    public static final String KEY_METADATA = "metadata";
    public static final String KEY_START_ID = "start_id";
    public static final String KEY_NEXT_ID = "next_id";

    public static final String KEY_OPERATION = "operation";
    public static final String KEY_OPERATION_ADD = "add";
    public static final String KEY_OPERATION_EDIT = "edit";
    public static final String KEY_OPERATION_REMOVE = "remove";
    public static final String KEY_OPERATION_ENABLE = "enable";
    public static final String KEY_OPERATION_DISABLE = "disable";

    public static final String KEY_REQ_STATUS_DECLINED = "declined";
    public static final String KEY_REQ_STATUS_PENDING = "pending";

    public static final String KEY_USER_ROLE_PRIMARY = "primary";
    public static final String KEY_USER_ROLE_SECONDARY = "secondary";
    public static final String KEY_PRIMARY_USERS = "primary_users";
    public static final String KEY_SECONDARY_USERS = "secondary_users";

    // Device capability
    public static final String CAPABILITY_WIFI_SACN = "wifi_scan";
    public static final String CAPABILITY_NO_POP = "no_pop";
    public static final String CAPABILITY_CLAIM = "claim";
}
