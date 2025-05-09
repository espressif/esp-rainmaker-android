// Copyright 2023 Espressif Systems (Shanghai) PTE LTD
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

class AppConstants {

    companion object {

        /* Device related constants */

        // UI Types of the device
        const val UI_TYPE_TOGGLE = "esp.ui.toggle"
        const val UI_TYPE_PUSH_BTN_BIG = "esp.ui.push-btn-big"
        const val UI_TYPE_SLIDER = "esp.ui.slider"
        const val UI_TYPE_HUE_SLIDER = "esp.ui.hue-slider"
        const val UI_TYPE_HUE_CIRCLE = "esp.ui.hue-circle"
        const val UI_TYPE_DROP_DOWN = "esp.ui.dropdown"
        const val UI_TYPE_HIDDEN = "esp.ui.hidden"
        const val UI_TYPE_TRIGGER = "esp.ui.trigger"
        const val UI_TYPE_TEXT = "esp.ui.text"

        // Device Types
        const val ESP_DEVICE_SWITCH = "esp.device.switch"
        const val ESP_DEVICE_LIGHT_BULB = "esp.device.lightbulb"
        const val ESP_DEVICE_BULB_CCT = "esp.device.lightbulb-cct"
        const val ESP_DEVICE_BULB_RGB = "esp.device.lightbulb-rgb"
        const val ESP_DEVICE_LOCK = "esp.device.lock"
        const val ESP_DEVICE_THERMOSTAT = "esp.device.thermostat"
        const val ESP_DEVICE_FAN = "esp.device.fan"
        const val ESP_DEVICE_SENSOR = "esp.device.sensor"
        const val ESP_DEVICE_TEMP_SENSOR = "esp.device.temperature-sensor"
        const val ESP_DEVICE_OUTLET = "esp.device.outlet"
        const val ESP_DEVICE_LIGHT = "esp.device.light"
        const val ESP_DEVICE_PLUG = "esp.device.plug"
        const val ESP_DEVICE_SOCKET = "esp.device.socket"
        const val ESP_DEVICE_BLINDS_INTERNAL = "esp.device.blinds-internal"
        const val ESP_DEVICE_BLINDS_EXTERNAL = "esp.device.blinds-external"
        const val ESP_DEVICE_GARAGE_DOOR = "esp.device.garage-door"
        const val ESP_DEVICE_SPEAKER = "esp.device.speaker"
        const val ESP_DEVICE_AIR_CONDITIONER = "esp.device.air-conditioner"
        const val ESP_DEVICE_TV = "esp.device.tv"
        const val ESP_DEVICE_WASHER = "esp.device.washer"
        const val ESP_DEVICE_CONTACT_SENSOR = "esp.device.contact-sensor"
        const val ESP_DEVICE_MOTION_SENSOR = "esp.device.motion-sensor"
        const val ESP_DEVICE_DOORBELL = "esp.device.doorbell"
        const val ESP_DEVICE_SECURITY_PANEL = "esp.device.security-panel"
        const val ESP_DEVICE_OTHER = "esp.device.other"
        const val ESP_DEVICE_MATTER_CONTROLLER = "esp.device.matter-controller"
        const val ESP_DEVICE_THREAD_BR = "esp.device.thread-br"

        const val MATTER_DEVICE_ON_OFF_LIGHT = 256
        const val MATTER_DEVICE_DIMMABLE_LIGHT = 257
        const val MATTER_DEVICE_LIGHT_BULB = 269
        const val MATTER_DEVICE_SWITCH = 259
        const val MATTER_DEVICE_CONTACT_SENSOR = 21
        const val MATTER_DEVICE_OUTLET = 266
        const val MATTER_DEVICE_BULB_RGB = 268
        const val MATTER_DEVICE_AC = 114
        const val MATTER_DEVICE_THERMOSTAT = 769
        const val MATTER_DEVICE_TEMP_SENSOR = 770
        const val MATTER_DEVICE_DOOR_LOCK = 10

        // Service Types
        const val SERVICE_TYPE_SCHEDULE = "esp.service.schedule"
        const val SERVICE_TYPE_SCENES = "esp.service.scenes"
        const val SERVICE_TYPE_TIME = "esp.service.time"
        const val SERVICE_TYPE_LOCAL_CONTROL = "esp.service.local_control"
        const val SERVICE_TYPE_SYSTEM = "esp.service.system"
        const val SERVICE_TYPE_MATTER_CONTROLLER = "esp.service.matter-controller"
        const val SERVICE_TYPE_TBR = "esp.service.thread-br"

        // Param Types
        const val PARAM_TYPE_NAME = "esp.param.name"
        const val PARAM_TYPE_CCT = "esp.param.cct"
        const val PARAM_TYPE_POWER = "esp.param.power"
        const val PARAM_TYPE_SATURATION = "esp.param.saturation"
        const val PARAM_TYPE_BRIGHTNESS = "esp.param.brightness"
        const val PARAM_TYPE_HUE = "esp.param.hue"
        const val PARAM_TYPE_TEMPERATURE = "esp.param.temperature"
        const val PARAM_TYPE_TZ = "esp.param.tz"
        const val PARAM_TYPE_TZ_POSIX = "esp.param.tz_posix"
        const val PARAM_TYPE_LOCAL_CONTROL_POP = "esp.param.local_control_pop"
        const val PARAM_TYPE_LOCAL_CONTROL_SEC_TYPE = "esp.param.local_control_type"
        const val PARAM_TYPE_LOCAL_CONTROL_USERNAME = "esp.param.local_control_username"
        const val PARAM_TYPE_REBOOT = "esp.param.reboot"
        const val PARAM_TYPE_FACTORY_RESET = "esp.param.factory-reset"
        const val PARAM_TYPE_WIFI_RESET = "esp.param.wifi-reset"
        const val PARAM_TYPE_MATTER_DEVICES = "esp.param.matter-devices"
        const val PARAM_TYPE_MATTER_CTRL_DATA_VERSION =
            "esp.param.matter-controller-data-version"
        const val PARAM_TYPE_AC_MODE = "esp.param.ac-mode"
        const val PARAM_TYPE_SETPOINT_TEMPERATURE = "esp.param.setpoint_temperature"
        const val PARAM_TYPE_SPEED = "esp.param.speed"
        const val PARAM_TYPE_BORDER_AGENT_ID = "esp.param.tbr-border-agent-id"
        const val PARAM_TYPE_ACTIVE_DATASET = "esp.param.tbr-active-dataset"
        const val PARAM_TYPE_PENDING_DATASET = "esp.param.tbr-pending-dataset"
        const val PARAM_TYPE_DEVICE_ROLE = "esp.param.tbr-device-role"
        const val PARAM_TYPE_TBR_CMD = "esp.param.tbr-cmd"
        const val PARAM_TYPE_BASE_URL = "esp.param.base-url"
        const val PARAM_TYPE_USER_TOKEN = "esp.param.user-token"
        const val PARAM_TYPE_RMAKER_GROUP_ID = "esp.param.rmaker-group-id"
        const val PARAM_TYPE_MATTER_NODE_ID = "esp.param.matter-node-id"
        const val PARAM_TYPE_MATTER_CTL_CMD = "esp.param.matter-ctl-cmd"
        const val PARAM_TYPE_MATTER_CTL_STATUS = "esp.param.matter-ctl-status"

        // Param names
        const val PARAM_NAME = "Name"
        const val PARAM_POWER = "Power"
        const val PARAM_BRIGHTNESS = "Brightness"
        const val PARAM_HUE = "Hue"
        const val PARAM_SATURATION = "Saturation"
        const val PARAM_TEMPERATURE = "Temperature"
        const val PARAM_SYSTEM_MODE = "System Mode"
        const val PARAM_COOLING_POINT = "Cool-Temperature"
        const val PARAM_HEATING_POINT = "Heat-Temperature"
        const val PARAM_LOCAL_TEMPERATURE = "Local Temperature"
        const val PARAM_SPEED = "Speed"
        const val PARAM_BASE_URL = "BaseURL"
        const val PARAM_USER_TOKEN = "UserToken"
        const val PARAM_RMAKER_GROUP_ID = "RMakerGroupID"
        const val PARAM_BORDER_AGENT_ID = "Border Agent ID"
        const val PARAM_ACTIVE_DATASET = "Active Dataset"
        const val PARAM_PENDING_DATASET = "Pending Dataset"
        const val PARAM_DEVICE_ROLE = "Device Role"
        const val PARAM_THREAD_CMD = "Thread Command"

        // Transport types
        const val TRANSPORT_SOFTAP = "softap"
        const val TRANSPORT_BLE = "ble"
        const val TRANSPORT_BOTH = "both"

        // Security types
        const val KEY_SEC_VER = "sec_ver"
        const val SEC_TYPE_0 = 0
        const val SEC_TYPE_1 = 1
        const val SEC_TYPE_2 = 2
        const val SEC_TYPE_DEFAULT = SEC_TYPE_2
        const val DEFAULT_SEC2_USER_NAME_WIFI = "wifiprov"
        const val DEFAULT_SEC2_USER_NAME_THREAD = "threadprov"

        // OTA status
        const val OTA_STATUS_TRIGGERED = "triggered"
        const val OTA_STATUS_IN_PROGRESS = "in-progress"
        const val OTA_STATUS_STARTED = "started"
        const val OTA_STATUS_COMPLETED = "completed"
        const val OTA_STATUS_SUCCESS = "success"
        const val OTA_STATUS_REJECTED = "rejected"
        const val OTA_STATUS_FAILED = "failed"
        const val OTA_STATUS_UNKNOWN = "unknown"

        // Device capability
        const val CAPABILITY_WIFI_SCAN = "wifi_scan"
        const val CAPABILITY_WIFI_PROV = "wifi_prov"
        const val CAPABILITY_NO_POP = "no_pop"
        const val CAPABILITY_CLAIM = "claim"
        const val CAPABILITY_THREAD_SCAN = "thread_scan"
        const val CAPABILITY_THREAD_PROV = "thread_prov"

        /* RainMaker Extra Capabilities */
        const val CAPABILITY_CHALLENGE_RESP = "ch_resp"

        // Device End point names
        const val HANDLER_RM_USER_MAPPING = "cloud_user_assoc"
        const val HANDLER_RM_CLAIM = "rmaker_claim"
        const val HANDLER_RM_CH_RESP = "ch_resp"

        /* Notification related constants */

        // Notification channel IDs
        const val CHANNEL_NODE_ONLINE_ID = "notify_node_online_id"
        const val CHANNEL_NODE_OFFLINE_ID = "notify_node_offline_id"
        const val CHANNEL_NODE_ADDED = "notify_node_added_id"
        const val CHANNEL_NODE_REMOVED = "notify_node_removed_id"
        const val CHANNEL_ALERT = "notify_alert_id"
        const val CHANNEL_NODE_SHARING = "notify_node_sharing_id"
        const val CHANNEL_NODE_AUTOMATION_TRIGGER = "notify_node_automation_trigger"
        const val CHANNEL_GROUP_SHARING = "notify_group_sharing_id"
        const val CHANNEL_ADMIN = "notify_admin"

        // Notification button actions
        const val ACTION_ACCEPT = "com.espressif.rainmaker.ACTION_ACCEPT"
        const val ACTION_DECLINE = "com.espressif.rainmaker.ACTION_DECLINE"

        // Event types
        const val EVENT_NODE_CONNECTED = "rmaker.event.node_connected"
        const val EVENT_NODE_DISCONNECTED = "rmaker.event.node_disconnected"
        const val EVENT_NODE_ADDED = "rmaker.event.user_node_added"
        const val EVENT_NODE_REMOVED = "rmaker.event.user_node_removed"
        const val EVENT_NODE_SHARING_ADD = "rmaker.event.user_node_sharing_add"
        const val EVENT_NODE_PARAM_MODIFIED = "rmaker.event.node_params_changed"
        const val EVENT_ALERT = "rmaker.event.alert"
        const val EVENT_NODE_AUTOMATION_TRIGGER = "rmaker.event.node_automation_trigger"
        const val EVENT_GROUP_SHARING_ADD = "rmaker.event.user_node_group_sharing_add"
        const val EVENT_GROUP_SHARE_ADDED = "rmaker.event.user_node_group_added"
        const val EVENT_GROUP_SHARE_REMOVED = "rmaker.event.user_node_group_removed"
        const val EVENT_NODE_OTA = "rmaker.event.user_node_ota"

        /* App related constants */

        const val ESP_PREFERENCES = "Esp_Preferences"
        const val PREF_FILE_WIFI_NETWORKS = "wifi_networks"
        const val ESP_DATABASE_NAME = "esp_db"
        const val NODE_TABLE = "node_table"
        const val GROUP_TABLE = "group_table"
        const val NOTIFICATION_TABLE = "notification_table"

        const val MDNS_SERVICE_TYPE = "_esp_local_ctrl._tcp."
        const val MDNS_TBR_SERVICE_TYPE = "_meshcop._udp."
        const val MDNS_MATTER_SERVICE_TYPE = "_matter._tcp."
        const val LOCAL_CONTROL_ENDPOINT = "esp_local_ctrl/control"
        const val LOCAL_SESSION_ENDPOINT = "esp_local_ctrl/session"
        const val MDNS_ATTR_NETWORK_NAME = "nn"

        const val CURRENT_VERSION = "v1"
        const val PATH_SEPARATOR = "/"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val USER_POOL_1 = 1
        const val USER_POOL_2 = 2

        const val WIFI_SCAN_FROM_DEVICE = "Device"
        const val WIFI_SCAN_FROM_PHONE = "Phone"

        const val MIN_THROTTLE_DELAY = 50
        const val MAX_THROTTLE_DELAY = 300
        const val MID_THROTTLE_DELAY = 100

        const val ACTION_SELECTED_NONE = 0
        const val ACTION_SELECTED_PARTIAL = 1
        const val ACTION_SELECTED_ALL = 2

        // Cloud API End point Urls
        const val URL_LOGIN = "/login"
        const val URL_USER = "/user"
        const val URL_FORGOT_PASSWORD = "/forgotpassword"
        const val URL_CHANGE_PASSWORD = "/password"
        const val URL_LOGOUT = "/logout"

        const val URL_LOGIN_2 = "/login2"
        const val URL_USER_2 = "/user2"
        const val URL_FORGOT_PASSWORD_2 = "/forgotpassword2"
        const val URL_CHANGE_PASSWORD_2 = "/password2"
        const val URL_LOGOUT_2 = "/logout2"

        const val URL_SUPPORTED_VERSIONS = "/apiversions"
        const val URL_USER_NODE_MAPPING = "/user/nodes/mapping"
        const val URL_USER_NODES = "/user/nodes"
        const val URL_USER_NODES_DETAILS = "/user/nodes?node_details=true"
        const val URL_USER_NODE_STATUS = "/user/nodes/status"
        const val URL_USER_NODES_PARAMS = "/user/nodes/params"

        const val URL_CLAIM_INITIATE = "/claim/initiate"
        const val URL_CLAIM_VERIFY = "/claim/verify"
        const val URL_USER_NODE_GROUP = "/user/node_group"
        const val URL_USER_NODES_SHARING_REQUESTS = "/user/nodes/sharing/requests"
        const val URL_USER_NODES_SHARING = "/user/nodes/sharing"
        const val URL_USER_NODES_TS = "/user/nodes/tsdata"
        const val URL_USER_NODES_TS_SIMPLE = "/user/nodes/simple_tsdata"
        const val URL_USER_NODE_AUTOMATION = "/user/node_automation"
        const val URL_NODE_OTA_UPDATE = "/user/nodes/ota_update"
        const val URL_NODE_OTA_STATUS = "/user/nodes/ota_status"

        const val URL_USER_NODE_GROUP_SHARING = "/user/node_group/sharing"
        const val URL_USER_NODE_GROUP_SHARING_REQUESTS = "/user/node_group/sharing/requests"
        const val URL_USER_MAPPING_INITIATE = "/user/nodes/mapping/initiate"
        const val URL_USER_MAPPING_VERIFY = "/user/nodes/mapping/verify"

        // Alexa account linking constants
        const val ALEXA_API_ENDPOINTS_URL = "https://api.amazonalexa.com/v1/alexaApiEndpoint"
        const val ALEXA_REFRESH_TOKEN_URL = "https://api.amazon.com/auth/o2/token"
        const val ALEXA_APP_URL =
            "https://alexa.amazon.com/spa/skill-account-linking-consent?fragment=skill-account-linking-consent"
        const val LWA_URL = "https://www.amazon.com/ap/oa"
        const val LWA_SCOPE = "alexa::skills:account_linking"
        const val STATE = "temp" // Place holder string

        const val ALEXA_PACKAGE_NAME = "com.amazon.dee.app"
        const val KEY_ALEXA_ACCESS_TOKEN = "alexa_access_token"
        const val KEY_ALEXA_REFRESH_TOKEN = "alexa_refresh_token"

        const val EVENT_ENABLE_SKILL = "enable_skill"
        const val EVENT_DISABLE_SKILL = "disable_skill"
        const val EVENT_GET_STATUS = "get_status"

        // Keys used in JSON responses and used to pass data between activities.
        const val KEY_NAME = "name"
        const val KEY_TYPE = "type"
        const val KEY_DATA_TYPE = "data_type"
        const val KEY_UI_TYPE = "ui_type"

        const val KEY_NODE_DETAILS = "node_details"
        const val KEY_DEVICES = "devices"
        const val KEY_PARAMS = "params"
        const val KEY_PROPERTIES = "properties"
        const val KEY_ATTRIBUTES = "attributes"
        const val KEY_SERVICES = "services"
        const val KEY_SCHEDULES = "Schedules"
        const val KEY_SCENES = "Scenes"
        const val KEY_TRIGGERS = "triggers"
        const val KEY_BOUNDS = "bounds"
        const val KEY_SELECTED_DEVICES = "selected_devices"
        const val KEY_MATTER_CTL = "MatterCTL"

        const val KEY_SUPPORTED_VERSIONS = "supported_versions"
        const val KEY_ADDITIONAL_INFO = "additional_info"
        const val KEY_CONFIG = "config"
        const val KEY_CONFIG_VERSION = "config_version"
        const val KEY_FW_VERSION = "fw_version"
        const val KEY_INFO = "info"
        const val KEY_PRIMARY = "primary"
        const val KEY_MAX = "max"
        const val KEY_MIN = "min"
        const val KEY_STEP = "step"
        const val KEY_VALID_STRS = "valid_strs"
        const val KEY_VALUE = "value"
        const val KEY_SCHEDULE = "Schedule"
        const val KEY_SCENE = "Scene"
        const val KEY_ACTION = "action"
        const val KEY_ACTIONS = "actions"
        const val KEY_EVENTS = "events"
        const val KEY_CHECK = "check"
        const val KEY_DAYS = "d"
        const val KEY_MINUTES = "m"
        const val KEY_ENABLED = "enabled"
        const val KEY_ID = "id"
        const val KEY_ROLE = "role"
        const val KEY_STATUS = "status"
        const val KEY_TIME = "Time"
        const val KEY_CONNECTIVITY = "connectivity"
        const val KEY_CONNECTED = "connected"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_DESCRIPTION = "description"
        const val KEY_PROPERTY_READ = "read"
        const val KEY_PROPERTY_WRITE = "write"
        const val KEY_PROPERTY_TS = "time_series"
        const val KEY_PROPERTY_TS_SIMPLE = "simple_ts"
        const val KEY_PROPERTY_TS_TYPE = "time_series_type"
        const val KEY_FAILURE_RESPONSE = "failure"
        const val KEY_SECRET_KEY = "secret_key"
        const val KEY_PROPERTY_COUNT = "property_count"
        const val KEY_CLAIM_VERIFY_RESPONSE = "claim_verify_response"
        const val KEY_CLAIM_INIT_RESPONSE = "claim_initiate_response"
        const val KEY_REQ_ID = "request_id"
        const val KEY_REQ_STATUS = "request_status"
        const val KEY_REQ_TIME = "request_timestamp"
        const val KEY_REQ_CONFIRMED = "confirmed"
        const val KEY_REQ_TIMEDOUT = "timedout"
        const val KEY_REQ_TIMESTAMP = "request_timestamp"
        const val KEY_USER_REQUEST = "user_request"
        const val KEY_GROUP_ID = "group_id"
        const val KEY_GROUP_NAME = "group_name"
        const val KEY_GROUPS = "groups"
        const val KEY_NODES = "nodes"
        const val KEY_NODE_LIST = "node_list"
        const val KEY_GROUP = "group"
        const val KEY_NODE_IDS = "node_ids"
        const val KEY_NODE_SHARING = "node_sharing"
        const val KEY_GROUP_SHARING = "group_sharing"
        const val KEY_SOURCES = "sources"
        const val KEY_USERS = "users"
        const val KEY_PRIMARY_USER = "primary_user"
        const val KEY_START_REQ_ID = "start_request_id"
        const val KEY_START_USER_NAME = "start_user_name"
        const val KEY_NEXT_REQ_ID = "next_request_id"
        const val KEY_NEXT_USER_NAME = "next_user_name"
        const val KEY_PRIMARY_USER_NAME = "primary_user_name"
        const val KEY_SECONDARY_USER_NAME = "secondary_user_name"
        const val KEY_USER_NAME = "user_name"
        const val KEY_REQ_ACCEPT = "accept"
        const val KEY_SHARING_REQUESTS = "sharing_requests"
        const val KEY_METADATA = "metadata"
        const val KEY_START_ID = "start_id"
        const val KEY_NEXT_ID = "next_id"
        const val KEY_EVENT_VERSION = "event_version"
        const val KEY_EVENT_TYPE = "event_type"
        const val KEY_EVENT_DATA = "event_data"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_EVENT_DATA_PAYLOAD = "event_data_payload"
        const val KEY_NOTIFICATION_MSG = "notification_msg"
        const val KEY_PAYLOAD = "payload"
        const val KEY_NOTIFICATION_ID = "notification_id"
        const val KEY_LOCAL_CONTROL = "Local Control"
        const val KEY_REDIRECT_URI = "redirect_uri"
        const val KEY_CODE = "code"
        const val KEY_AUTH_CODE = "authCode"
        const val KEY_ACCOUNT_LINK_REQUEST = "accountLinkRequest"
        const val KEY_STAGE = "stage"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_CLIENT_SECRET = "client_secret"
        const val KEY_GRANT_TYPE = "grant_type"
        const val KEY_CONTENT_TYPE = "Content-type"
        const val KEY_ENDPOINTS = "endpoints"
        const val KEY_ACCOUNTLINK = "accountLink"
        const val KEY_SKILL = "skill"
        const val KEY_STATUS_LINKED = "LINKED"
        const val KEY_RESPONSE = "response"
        const val KEY_AUTOMATION_ID = "automation_id"
        const val KEY_AUTOMATION_NAME = "automation_name"
        const val KEY_AUTOMATION_TRIGGER_ACTIONS = "automation_trigger_actions"
        const val KEY_AUTOMATION = "automation"
        const val KEY_LOAD_AUTOMATION_PAGE = "load_automation"
        const val KEY_SYSTEM = "System"
        const val KEY_MATTER_CONTROLLER = "Matter-Controller"
        const val KEY_REACHABLE = "reachable"
        const val KEY_SHARED_FROM = "shared_from"
        const val KEY_SHARED_TO = "shared_to"
        const val KEY_SELF_REMOVAL = "self_removal"
        const val KEY_NODE = "NODE"
        const val KEY_TBR_SERVICE = "TBRService"

        const val KEY_OPERATION = "operation"
        const val KEY_OPERATION_ADD = "add"
        const val KEY_OPERATION_EDIT = "edit"
        const val KEY_OPERATION_REMOVE = "remove"
        const val KEY_OPERATION_ENABLE = "enable"
        const val KEY_OPERATION_DISABLE = "disable"
        const val KEY_OPERATION_ACTIVATE = "activate"

        const val KEY_REQ_STATUS_DECLINED = "declined"
        const val KEY_REQ_STATUS_PENDING = "pending"

        const val KEY_USER_ROLE_PRIMARY = "primary"
        const val KEY_USER_ROLE_SECONDARY = "secondary"
        const val KEY_PRIMARY_USERS = "primary_users"
        const val KEY_SECONDARY_USERS = "secondary_users"

        const val KEY_BASE_URL = "base_url"
        const val KEY_DEVICE_NAME_PREFIX = "device_prefix"
        const val KEY_PROOF_OF_POSSESSION = "proof_of_possession"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_ESP_DEVICE = "esp_device"
        const val KEY_NODE_ID = "node_id"
        const val KEY_NODE_TYPE = "node_type"
        const val KEY_EMAIL = "email"
        const val KEY_USER_ID = "user_id"
        const val KEY_ID_TOKEN = "id_token"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_IS_OAUTH_LOGIN = "is_github_login"
        const val KEY_ERROR_MSG = "err_msg"
        const val KEY_SSID = "ssid"
        const val KEY_PASSWORD = "password"
        const val KEY_SECURITY_TYPE = "security_type"
        const val KEY_SHOULD_SAVE_PWD = "save_password"
        const val KEY_NEW_PASSWORD = "newpassword"
        const val KEY_REQUEST = "request"
        const val KEY_VERIFICATION_CODE = "verification_code"
        const val KEY_MOBILE_DEVICE_TOKEN = "mobile_device_token"
        const val KEY_PLATFORM = "platform"
        const val KEY_GCM = "GCM"
        const val KEY_MESSAGE_BODY = "message_body"
        const val KEY_ALERT_STRING = "esp.alert.str"
        const val KEY_PARAM = "param"
        const val KEY_PARAM_NAME = "param_name"
        const val KEY_AGGREGATE = "aggregate"
        const val KEY_AGGREGATION_INTERVAL = "aggregation_interval"
        const val KEY_WEEK_START = "week_start"
        const val KEY_START_TIME = "start_time"
        const val KEY_END_TIME = "end_time"
        const val KEY_NUM_INTERVALS = "num_intervals"
        const val KEY_NUM_RECORDS = "num_records"
        const val KEY_TIMEZONE = "timezone"
        const val KEY_OTA_JOB_ID = "ota_job_id"
        const val KEY_OTA_DETAILS = "ota_details"
        const val KEY_OTA_AVAILABLE = "ota_available"
        const val KEY_TBR_ACTIVITY_REASON = "tbr_activity_reason"

        const val KEY_GROUP_NAMES = "group_names"
        const val KEY_GROUP_IDS = "group_ids"

        const val KEY_MUTUALLY_EXCLUSIVE = "mutually_exclusive"
        const val KEY_IS_MATTER = "is_matter"
        const val KEY_ROOT_CA = "root_ca"
        const val KEY_GROUP_CAT_ID_ADMIN = "group_cat_id_admin"
        const val KEY_GROUP_CAT_ID_OPERATE = "group_cat_id_operate"
        const val KEY_MATTER_USER_ID = "matter_user_id"
        const val KEY_USER_CAT_ID = "user_cat_id"
        const val KEY_FABRIC_ID = "fabric_id"
        const val KEY_FABRIC_DETAILS = "fabric_details"
        const val KEY_IPK = "ipk"
        const val KEY_CSR_TYPE = "csr_type"
        const val KEY_CSR_REQUESTS = "csr_requests"
        const val KEY_CSR = "csr"
        const val KEY_USER_NOC = "user_noc"
        const val KEY_NODE_RM_MATTER = "rainmaker_matter"
        const val KEY_CONTROLLER_NODE_ID = "controllerNodeId"
        const val KEY_DEVICETYPE = "deviceType"
        const val KEY_DEVICENAME = "deviceName"
        const val KEY_ENDPOINTS_DATA = "endpointsData"
        const val KEY_SERVERS_DATA = "serversData"
        const val KEY_CLIENTS_DATA = "clientsData"
        const val KEY_PRODUCT_ID = "productId"
        const val KEY_VENDOR_ID = "vendorId"
        const val KEY_IS_RAINMAKER = "isRainmaker"
        const val KEY_MATTER_NODE_ID = "matter_node_id"
        const val KEY_ON_BOARD_PAYLOAD = "on_board_payload"
        const val KEY_CHALLENGE = "challenge"
        const val KEY_RAINMAKER_NODE_ID = "rainmaker_node_id"
        const val KEY_MATTER = "Matter"
        const val KEY_USER_NAME_WIFI = "sec2_username_wifi"
        const val KEY_USER_NAME_THREAD = "sec2_username_thread"
        const val KEY_THREAD_SCAN_AVAILABLE = "thread_scan_available"
        const val KEY_THREAD_DATASET = "thread_dataset"
        const val KEY_DEPENDENCIES = "dependencies"
        const val KEY_IS_RAINMAKER_NODE = "is_rainmaker_node"
        const val KEY_IS_CTRL_SERVICE = "isCtrlService"

        const val CERT_BEGIN = "-----BEGIN CERTIFICATE REQUEST-----"
        const val CERT_END = "-----END CERTIFICATE REQUEST-----"

        // Node types
        const val NODE_TYPE_PURE_MATTER = "pure_matter"
        const val NODE_TYPE_RM_MATTER = "rainmaker_matter"
        const val NODE_TYPE_RM = "rainmaker"

        // Endpoints, Cluster Id, Attribute Ids
        const val ENDPOINT_0 = 0
        const val ENDPOINT_1 = 1

        const val RM_CLUSTER_ID_HEX = 0x131bfc00L
        const val CONTROLLER_CLUSTER_ID_HEX = 0x131BFC01L
        const val THREAD_BR_MANAGEMENT_CLUSTER_ID_HEX = 0x00000452L
        
        const val RM_CLUSTER_ID = 320601088L
        const val CONTROLLER_CLUSTER_ID = 320601089L
        const val THREAD_BR_MANAGEMENT_CLUSTER_ID = 1106L

        const val COMMAND_APPEND_REFRESH_TOKEN = 0L
        const val COMMAND_RESET_REFRESH_TOKEN = 1L
        const val COMMAND_AUTHORIZE_DEVICE = 2L
        const val COMMAND_UPDATE_USER_NOC = 3L
        const val COMMAND_UPDATE_DEVICE_LIST = 4L

        const val CAT_ID_PREFIX = "FFFFFFFD"
        const val CONTROLLER_DATA_VERSION = "1.0.1"

        const val PRIVILEGE_ADMIN = 5
        const val PRIVILEGE_OPERATE = 3

        const val NODE_STATUS_OFFLINE = 1;
        const val NODE_STATUS_ONLINE = 2;
        const val NODE_STATUS_LOCAL = 3;
        const val NODE_STATUS_MATTER_LOCAL = 4;
        const val NODE_STATUS_REMOTELY_CONTROLLABLE = 5;

        const val HEX_PREFIX = "0x"

        const val DOOR_LOCK_PIN = "1234"

        const val DEVICE_NAME_MATTER_CONTROLLER = "Matter Controller"
        const val DEVICE_NAME_THREAD_BR = "Thread-BR"

        enum class SystemMode(val modeValue: Int, val modeName: String) {
            OFF(0, "Off"),
            COOL(3, "Cool"),
            HEAT(4, "Heat")
        }

        enum class LockState(val value: Int, val lockModeText: String) {
            LOCK(0, "Lock"),
            UNLOCK(1, "Unlock")
        }

        enum class UpdateEventType {
            EVENT_DEVICE_ADDED,
            EVENT_DEVICE_REMOVED,
            EVENT_DEVICE_ONLINE,
            EVENT_DEVICE_OFFLINE,
            EVENT_ADD_DEVICE_TIME_OUT,
            EVENT_DEVICE_STATUS_UPDATE,
            EVENT_STATE_CHANGE_UPDATE,
            EVENT_LOCAL_DEVICE_UPDATE,
            EVENT_CTRL_CONFIG_DONE,
            EVENT_MATTER_DEVICE_CONNECTIVITY
        }

        // Command Response constants
        const val KEY_RESPONSE_DATA = "response_data"
        const val KEY_REQUEST_ID = "request_id"
        const val KEY_STATUS_DESCRIPTION = "status_description"
        const val KEY_REQUESTS = "requests"
        const val KEY_CMD = "cmd"
        const val KEY_IS_BASE64 = "is_base64"
        const val KEY_TIMEOUT = "timeout"
        const val KEY_DATA = "data"
        const val URL_USER_NODES_CMD = "/user/nodes/cmd"

        /* Keys used in API calls and responses */
        const val KEY_CHALLENGE_RESP = "challenge_response"
    }
}
