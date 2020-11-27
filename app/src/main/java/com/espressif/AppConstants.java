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

public class AppConstants {

    public static final int SECURITY_TYPE_0 = 0;
    public static final int SECURITY_TYPE_1 = 1;

    // End point names
    public static final String HANDLER_RM_USER_MAPPING = "cloud_user_assoc";
    public static final String HANDLER_RM_CLAIM = "rmaker_claim";

    // Keys used to pass data between activities and to store data in SharedPreference.
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

    public static final String ESP_PREFERENCES = "Esp_Preferences";

    // UI Types of Device
    public static final String UI_TYPE_TOGGLE = "esp.ui.toggle";
    public static final String UI_TYPE_SLIDER = "esp.ui.slider";
    public static final String UI_TYPE_HUE_SLIDER = "esp.ui.hue-slider";

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

    // Service Types
    public static final String SERVICE_TYPE_SCHEDULE = "esp.service.schedule";

    // Param Types
    public static final String PARAM_TYPE_NAME = "esp.param.name";
    public static final String PARAM_TYPE_OUTPUT = "esp.param.output";
    public static final String PARAM_TYPE_BRIGHTNESS = "esp.param.brightness";
    public static final String PARAM_TYPE_TEMPERATURE = "esp.param.temperature";

    public static final String ESP_DATABASE_NAME = "esp_db";
    public static final String NODE_TABLE = "node_table";
    public static final String MDNS_SERVICE_TYPE = "_esp_local_ctrl._tcp.";

    public enum UpdateEventType {

        EVENT_DEVICE_ADDED,
        EVENT_DEVICE_REMOVED,
        EVENT_ADD_DEVICE_TIME_OUT,
        EVENT_DEVICE_STATUS_UPDATE
    }

    public static final String CURRENT_VERSION = "v1";
    public static final String PATH_SEPARATOR = "/";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    public static final String BASE_URL = "https://api.rainmaker.espressif.com";
    public static final String AUTH_URL = "https://auth.rainmaker.espressif.com/oauth2";
    public static final String TOKEN_URL = "https://auth.rainmaker.espressif.com/oauth2/token";
    public static final String GITHUB_URL = AUTH_URL + PATH_SEPARATOR + "authorize?identity_provider=GitHub&redirect_uri=rainmaker://com.espressif.rainmaker/success&response_type=CODE&client_id=";
    public static final String GOOGLE_URL = AUTH_URL + PATH_SEPARATOR + "authorize?identity_provider=Google&redirect_uri=rainmaker://com.espressif.rainmaker/success&response_type=CODE&client_id=";
    public static final String CLAIM_BASE_URL = "https://esp-claiming.rainmaker.espressif.com";

    public static final String REDIRECT_URI = "rainmaker://com.espressif.rainmaker/success";

    public static final String DOCUMENTATION_URL = "https://rainmaker.espressif.com/";
    public static final String PRIVACY_URL = "https://rainmaker.espressif.com/docs/privacy-policy.html";
    public static final String TERMS_URL = "https://rainmaker.espressif.com/docs/terms-of-use.html";
}
