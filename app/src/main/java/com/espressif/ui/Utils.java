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

package com.espressif.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;

import com.espressif.AppConstants;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.models.Action;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.Scene;
import com.espressif.ui.models.Schedule;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.TimeZone;

public class Utils {

    public static final String TAG = Utils.class.getSimpleName();

    public static void createESPDevice(Context appContext, ESPConstants.TransportType transportType, int securityType) {
        ESPProvisionManager provisionManager = ESPProvisionManager.getInstance(appContext);
        switch (securityType) {
            case AppConstants.SEC_TYPE_0:
                provisionManager.createESPDevice(transportType, ESPConstants.SecurityType.SECURITY_0);
                break;
            case AppConstants.SEC_TYPE_1:
                provisionManager.createESPDevice(transportType, ESPConstants.SecurityType.SECURITY_1);
                break;
            case AppConstants.SEC_TYPE_2:
            default:
                provisionManager.createESPDevice(transportType, ESPConstants.SecurityType.SECURITY_2);
                break;
        }
    }

    public static void setSecurityTypeFromVersionInfo(Context appContext) {
        ESPProvisionManager provisionManager = ESPProvisionManager.getInstance(appContext);
        String protoVerStr = provisionManager.getEspDevice().getVersionInfo();

        // Prov Json
        try {
            JSONObject jsonObject = new JSONObject(protoVerStr);
            JSONObject provInfo = jsonObject.getJSONObject("prov");

            if (provInfo != null) {
                if (provInfo.has(AppConstants.KEY_SEC_VER)) {

                    int serVer = provInfo.optInt(AppConstants.KEY_SEC_VER);
                    Log.d(TAG, "Security Version : " + serVer);

                    switch (serVer) {
                        case AppConstants.SEC_TYPE_0:
                            provisionManager.getEspDevice().setSecurityType(ESPConstants.SecurityType.SECURITY_0);
                            break;
                        case AppConstants.SEC_TYPE_1:
                            provisionManager.getEspDevice().setSecurityType(ESPConstants.SecurityType.SECURITY_1);
                            break;
                        case AppConstants.SEC_TYPE_2:
                        default:
                            provisionManager.getEspDevice().setSecurityType(ESPConstants.SecurityType.SECURITY_2);
                            String userName = provisionManager.getEspDevice().getUserName();
                            SharedPreferences appPreferences = appContext.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
                            ArrayList<String> deviceCaps = provisionManager.getEspDevice().getDeviceCapabilities();

                            if (TextUtils.isEmpty(userName)) {

                                if (deviceCaps != null && !deviceCaps.isEmpty()) {

                                    if (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_SCAN) || deviceCaps.contains(AppConstants.CAPABILITY_THREAD_PROV)) {
                                        userName = appPreferences.getString(AppConstants.KEY_USER_NAME_THREAD, AppConstants.DEFAULT_SEC2_USER_NAME_THREAD);
                                        if (!TextUtils.isEmpty(BuildConfig.SECURITY_2_USERNAME_THREAD)) {
                                            userName = BuildConfig.SECURITY_2_USERNAME_THREAD;
                                        }
                                        provisionManager.getEspDevice().setUserName(userName);
                                    } else if (deviceCaps.contains(AppConstants.CAPABILITY_WIFI_SCAN) || deviceCaps.contains(AppConstants.CAPABILITY_WIFI_PROV)) {
                                        userName = appPreferences.getString(AppConstants.KEY_USER_NAME_WIFI, AppConstants.DEFAULT_SEC2_USER_NAME_WIFI);
                                        if (!TextUtils.isEmpty(BuildConfig.SECURITY_2_USERNAME_WIFI)) {
                                            userName = BuildConfig.SECURITY_2_USERNAME_WIFI;
                                        }
                                        provisionManager.getEspDevice().setUserName(userName);
                                    } else {
                                        userName = AppConstants.DEFAULT_SEC2_USER_NAME_WIFI;
                                    }
                                }
                            }
                            provisionManager.getEspDevice().setUserName(userName);
                            break;
                    }
                } else {
                    if (provisionManager.getEspDevice().getSecurityType().equals(ESPConstants.SecurityType.SECURITY_2)) {
                        provisionManager.getEspDevice().setSecurityType(ESPConstants.SecurityType.SECURITY_1);
                    }
                }
            } else {
                Log.e(TAG, "proto-ver info is not available.");
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(TAG, "Capabilities JSON not available.");
        }
    }

    public static boolean isValidEmail(CharSequence target) {
        // TODO Add phone number validation
        return (!TextUtils.isEmpty(target) && Patterns.EMAIL_ADDRESS.matcher(target).matches());
    }

    public static String getCurrentTimeZone() {
        return TimeZone.getDefault().getID();
    }

    public static Calendar getCalendarForTimeZone(String timeZoneStr) {
        TimeZone tz = TimeZone.getTimeZone(timeZoneStr);
        return Calendar.getInstance(tz);
    }

    public static void showAlertDialog(final Activity activityContext, String title, String msg, final boolean shouldExit) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(activityContext);

        if (!TextUtils.isEmpty(title)) {
            builder.setTitle(title);
        }
        builder.setMessage(msg);
        builder.setNeutralButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (shouldExit) {
                    activityContext.finish();
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static String processScheduleResponse(Schedule schedule, String jsonResponse, int nodeListSize) {

        // Return empty if schedule has only one node (No need to display device name)
        if (nodeListSize == 1) {
            return "";
        }
        StringBuilder deviceNames = new StringBuilder();

        try {
            JSONArray responseJsonArr = new JSONArray(jsonResponse);
            ArrayList<String> failureNodes = new ArrayList<>();

            if (responseJsonArr != null) {
                for (int i = 0; i < responseJsonArr.length(); i++) {
                    JSONObject nodeResJson = responseJsonArr.optJSONObject(i);
                    String nodeId = nodeResJson.optString(AppConstants.KEY_NODE_ID);
                    String status = nodeResJson.optString(AppConstants.KEY_STATUS);
                    if (AppConstants.KEY_FAILURE_RESPONSE.equals(status)) {
                        failureNodes.add(nodeId);
                    }
                }
            }

            // Return empty if request fails for all nodes (No need to display device name)
            if (nodeListSize == failureNodes.size()) {
                return "";
            }

            if (failureNodes.size() > 0) {
                for (int i = 0; i < failureNodes.size(); i++) {
                    String nodeId = failureNodes.get(i);
                    ArrayList<Action> actions = schedule.getActions();
                    for (int j = 0; j < actions.size(); j++) {
                        if (nodeId.equals(actions.get(j).getNodeId())) {
                            if (deviceNames.length() != 0) {
                                deviceNames.append(", ");
                            }
                            deviceNames.append(actions.get(j).getDevice().getUserVisibleName());
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return deviceNames.toString();
    }

    public static String processSceneResponse(Scene scene, String jsonResponse, int nodeListSize) {

        // Return empty if scene has only one node (No need to display device name)
        if (nodeListSize == 1) {
            return "";
        }
        StringBuilder deviceNames = new StringBuilder();

        try {
            JSONArray responseJsonArr = new JSONArray(jsonResponse);
            ArrayList<String> failureNodes = new ArrayList<>();

            if (responseJsonArr != null) {
                for (int i = 0; i < responseJsonArr.length(); i++) {
                    JSONObject nodeResJson = responseJsonArr.optJSONObject(i);
                    String nodeId = nodeResJson.optString(AppConstants.KEY_NODE_ID);
                    String status = nodeResJson.optString(AppConstants.KEY_STATUS);
                    if (AppConstants.KEY_FAILURE_RESPONSE.equals(status)) {
                        failureNodes.add(nodeId);
                    }
                }
            }

            // Return empty if request fails for all nodes (No need to display device name)
            if (nodeListSize == failureNodes.size()) {
                return "";
            }

            if (failureNodes.size() > 0) {
                for (int i = 0; i < failureNodes.size(); i++) {
                    String nodeId = failureNodes.get(i);
                    ArrayList<Action> actions = scene.getActions();
                    for (int j = 0; j < actions.size(); j++) {
                        if (nodeId.equals(actions.get(j).getNodeId())) {
                            if (deviceNames.length() != 0) {
                                deviceNames.append(", ");
                            }
                            deviceNames.append(actions.get(j).getDevice().getUserVisibleName());
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return deviceNames.toString();
    }

    public static ArrayList<Param> getEventDeviceParams(ArrayList<Param> allParams) {
        Iterator itr = allParams.iterator();
        while (itr.hasNext()) {
            Param p = (Param) itr.next();
            p.setSelected(false);

            if (!p.isDynamicParam()) {
                itr.remove();
            } else if (p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                itr.remove();
            } else if (p.getUiType() != null && p.getUiType().equals(AppConstants.UI_TYPE_HIDDEN)) {
                itr.remove();
            }
        }
        return allParams;
    }

    public static ArrayList<Param> getWritableParams(ArrayList<Param> allParams) {
        if (allParams == null) {
            return new ArrayList<>();
        }
        Iterator itr = allParams.iterator();
        while (itr.hasNext()) {
            Param p = (Param) itr.next();

            if (!p.isDynamicParam()) {
                itr.remove();
            } else if (p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                itr.remove();
            } else if (!p.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {
                itr.remove();
            } else if (p.getUiType() != null && p.getUiType().equals(AppConstants.UI_TYPE_HIDDEN)) {
                itr.remove();
            }
        }
        return allParams;
    }

    public static JsonObject getParamJson(Device device) {

        JsonObject paramJson = new JsonObject();
        ArrayList<Param> params = device.getParams();

        for (int j = 0; j < params.size(); j++) {

            Param param = params.get(j);

            if (param.isSelected()) {

                String dataType = param.getDataType();

                if (AppConstants.UI_TYPE_SLIDER.equalsIgnoreCase(param.getUiType())) {

                    if (dataType.equalsIgnoreCase("int")
                            || dataType.equalsIgnoreCase("integer")) {

                        int max = param.getMaxBounds();
                        int min = param.getMinBounds();

                        if ((min < max)) {
                            int value = (int) param.getValue();
                            paramJson.addProperty(param.getName(), value);
                        } else {

                            int value = Integer.parseInt(param.getLabelValue());
                            paramJson.addProperty(param.getName(), value);
                        }
                    } else if (dataType.equalsIgnoreCase("float")
                            || dataType.equalsIgnoreCase("double")) {

                        int max = param.getMaxBounds();
                        int min = param.getMinBounds();

                        if ((min < max)) {
                            paramJson.addProperty(param.getName(), param.getValue());
                        } else {

                            float value = Float.parseFloat(param.getLabelValue());
                            paramJson.addProperty(param.getName(), value);
                        }
                    }
                } else if (AppConstants.UI_TYPE_TRIGGER.equalsIgnoreCase(param.getUiType())
                        && dataType.equalsIgnoreCase("bool")
                        || dataType.equalsIgnoreCase("boolean")) {

                    paramJson.addProperty(param.getName(), true);

                } else {
                    if (dataType.equalsIgnoreCase("bool")
                            || dataType.equalsIgnoreCase("boolean")) {

                        paramJson.addProperty(param.getName(), param.getSwitchStatus());

                    } else if (dataType.equalsIgnoreCase("int")
                            || dataType.equalsIgnoreCase("integer")) {

                        int value = (int) param.getValue();
                        paramJson.addProperty(param.getName(), value);

                    } else if (dataType.equalsIgnoreCase("float")
                            || dataType.equalsIgnoreCase("double")) {

                        paramJson.addProperty(param.getName(), param.getValue());

                    } else if (dataType.equalsIgnoreCase("string")) {

                        paramJson.addProperty(param.getName(), param.getLabelValue());
                    }
                }
            }
        }

        return paramJson;
    }

    public static String getEventParamString(ArrayList<Param> params, String condition) {

        StringBuilder paramText = new StringBuilder();

        for (int j = 0; j < params.size(); j++) {

            Param param = params.get(j);
            String dataType = param.getDataType();

            if (param.isSelected()) {
                paramText.append(param.getName());

                if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {
                    paramText.append(":");
                } else if (!TextUtils.isEmpty(condition) && condition.equals("==")) {
                    paramText.append(":");
                } else {
                    paramText.append(condition);
                }
                paramText.append(param.getLabelValue());
            }
        }
        return paramText.toString();
    }

    public static String getActionParamsString(ArrayList<Param> params) {

        ArrayList<Param> paramList = new ArrayList<>(params);
        StringBuilder paramText = new StringBuilder();

        Iterator itr = paramList.iterator();
        while (itr.hasNext()) {
            Param p = (Param) itr.next();
            if (!p.isSelected()) {
                itr.remove();
            }
        }

        for (int j = 0; j < paramList.size(); j++) {

            Param param = paramList.get(j);
            paramText.append(param.getName());
            paramText.append(":");
            paramText.append(param.getLabelValue());

            if (j != (paramList.size() - 1)) {
                paramText.append(",");
            }
        }
        return paramText.toString();
    }

    public static long getThrottleDelay() {
        if (BuildConfig.continuosUpdateInterval > AppConstants.MIN_THROTTLE_DELAY
                && BuildConfig.continuosUpdateInterval < AppConstants.MAX_THROTTLE_DELAY) {
            return BuildConfig.continuosUpdateInterval;
        }
        return AppConstants.MID_THROTTLE_DELAY;
    }

    /**
     * Check the device to make sure it has the Google Play Services APK.
     *
     * @return Returns true if Google Api is available.
     */
    public static boolean isPlayServicesAvailable(Context appContext) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(appContext);
        if (resultCode == ConnectionResult.SUCCESS) {
            return true;
        }
        return false;
    }

    public static void showPlayServicesWarning(Activity activityContext) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activityContext);
        builder.setMessage(R.string.dialog_msg_play_services_required);
        builder.setTitle(R.string.dialog_title_play_services_required);
        builder.setCancelable(false);
        builder.setNeutralButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                activityContext.finish();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static void setDeviceIcon(ImageView ivDevice, String deviceType) {

        if (TextUtils.isEmpty(deviceType)) {
            ivDevice.setImageResource(R.drawable.ic_device);
        } else {

            switch (deviceType) {
                case AppConstants.ESP_DEVICE_LIGHT_BULB:
                case AppConstants.ESP_DEVICE_LIGHT:
                    ivDevice.setImageResource(R.drawable.ic_device_light);
                    break;
                case AppConstants.ESP_DEVICE_BULB_CCT:
                    ivDevice.setImageResource(R.drawable.ic_device_bulb_cct);
                    break;
                case AppConstants.ESP_DEVICE_BULB_RGB:
                    ivDevice.setImageResource(R.drawable.ic_device_bulb_rgb);
                    break;
                case AppConstants.ESP_DEVICE_SWITCH:
                    ivDevice.setImageResource(R.drawable.ic_device_switch);
                    break;
                case AppConstants.ESP_DEVICE_LOCK:
                    ivDevice.setImageResource(R.drawable.ic_device_lock);
                    break;
                case AppConstants.ESP_DEVICE_THERMOSTAT:
                    ivDevice.setImageResource(R.drawable.ic_device_thermostat);
                    break;
                case AppConstants.ESP_DEVICE_FAN:
                    ivDevice.setImageResource(R.drawable.ic_device_fan);
                    break;
                case AppConstants.ESP_DEVICE_TEMP_SENSOR:
                    ivDevice.setImageResource(R.drawable.ic_device_temp_sensor);
                    break;
                case AppConstants.ESP_DEVICE_OUTLET:
                    ivDevice.setImageResource(R.drawable.ic_device_outlet);
                    break;
                case AppConstants.ESP_DEVICE_PLUG:
                    ivDevice.setImageResource(R.drawable.ic_device_plug);
                    break;
                case AppConstants.ESP_DEVICE_SOCKET:
                    ivDevice.setImageResource(R.drawable.ic_device_socket);
                    break;
                case AppConstants.ESP_DEVICE_BLINDS_INTERNAL:
                    ivDevice.setImageResource(R.drawable.ic_device_internal_blinds);
                    break;
                case AppConstants.ESP_DEVICE_BLINDS_EXTERNAL:
                    ivDevice.setImageResource(R.drawable.ic_device_external_blinds);
                    break;
                case AppConstants.ESP_DEVICE_GARAGE_DOOR:
                    ivDevice.setImageResource(R.drawable.ic_device_garage_door);
                    break;
                case AppConstants.ESP_DEVICE_SPEAKER:
                    ivDevice.setImageResource(R.drawable.ic_device_speaker);
                    break;
                case AppConstants.ESP_DEVICE_AIR_CONDITIONER:
                    ivDevice.setImageResource(R.drawable.ic_device_air_conditioner);
                    break;
                case AppConstants.ESP_DEVICE_TV:
                    ivDevice.setImageResource(R.drawable.ic_device_tv);
                    break;
                case AppConstants.ESP_DEVICE_WASHER:
                    ivDevice.setImageResource(R.drawable.ic_device_washer);
                    break;
                case AppConstants.ESP_DEVICE_CONTACT_SENSOR:
                    ivDevice.setImageResource(R.drawable.ic_device_contact_sensor);
                    break;
                case AppConstants.ESP_DEVICE_MOTION_SENSOR:
                    ivDevice.setImageResource(R.drawable.ic_device_motion_sensor);
                    break;
                case AppConstants.ESP_DEVICE_DOORBELL:
                    ivDevice.setImageResource(R.drawable.ic_device_door_bell);
                    break;
                case AppConstants.ESP_DEVICE_SECURITY_PANEL:
                    ivDevice.setImageResource(R.drawable.ic_device_security_panel);
                    break;
                case AppConstants.ESP_DEVICE_MATTER_CONTROLLER:
                    ivDevice.setImageResource(R.drawable.ic_device_matter_controller);
                    break;
                case AppConstants.ESP_DEVICE_THREAD_BR:
                    ivDevice.setImageResource(R.drawable.ic_device_thread_br);
                    break;
                case AppConstants.ESP_DEVICE_OTHER:
                case AppConstants.ESP_DEVICE_SENSOR:
                default:
                    ivDevice.setImageResource(R.drawable.ic_device);
                    break;
            }
        }
    }

    public static Long getCatId(String catIdOperate) {
        catIdOperate = AppConstants.CAT_ID_PREFIX + catIdOperate;
        BigDecimal catId = new BigDecimal(new BigInteger(catIdOperate, 16));
        return catId.longValue();
    }

    public static String byteArrayToDs(byte[] byteArray) {
        StringBuilder sb = new StringBuilder();
        for (byte b : byteArray) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String getPrivacyUrl() {
        if (BuildConfig.isChinaRegion) {
            return BuildConfig.CHINA_PRIVACY_URL;
        }
        return BuildConfig.PRIVACY_URL;
    }

    public static String getTermsOfUseUrl() {
        if (BuildConfig.isChinaRegion) {
            return BuildConfig.CHINA_TERMS_URL;
        }
        return BuildConfig.TERMS_URL;
    }

    public static byte[] decodeHex(String hex) {
        int length = hex.length();
        byte[] data = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static int temperatureDeviceToAppConversion(int temp) {
        return temp / 100;
    }

    public static int temperatureAppToDeviceConversion(int temp) {
        return temp * 100;
    }
}
