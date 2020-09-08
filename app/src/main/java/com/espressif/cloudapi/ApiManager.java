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

package com.espressif.cloudapi;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoDevice;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ChallengeContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.MultiFactorAuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.AuthenticationHandler;
import com.auth0.android.jwt.Claim;
import com.auth0.android.jwt.DecodeException;
import com.auth0.android.jwt.JWT;
import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.rainmaker.R;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.UpdateEvent;
import com.espressif.ui.user_module.AppHelper;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiManager {

    private static final String TAG = ApiManager.class.getSimpleName();

    private static final int REQ_STATUS_TIME = 5000;

    public static boolean isOAuthLogin;
    public static String userId = "";
    private static String userName = "";
    private static String idToken = "";
    private static String accessToken = "";
    private static String refreshToken = "";
    private static HashMap<String, String> requestIds = new HashMap<>(); // Map of node id and request id.

    private Context context;
    private EspApplication espApp;
    private Handler handler;
    private ApiInterface apiInterface;
    private SharedPreferences sharedPreferences;
    private static ArrayList<String> nodeIds = new ArrayList<>();

    private static ApiManager apiManager;

    public static ApiManager getInstance(Context context) {

        if (apiManager == null) {
            apiManager = new ApiManager(context);
        }
        return apiManager;
    }

    private ApiManager(Context context) {
        this.context = context;
        handler = new Handler();
        espApp = (EspApplication) context.getApplicationContext();
        apiInterface = ApiClient.getClient(context).create(ApiInterface.class);
        sharedPreferences = context.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        getTokenAndUserId();
    }

    public void getOAuthToken(String code, final ApiResponseListener listener) {

        Log.d(TAG, "Get OAuth Token");
        try {
            apiInterface.loginWithGithub("application/x-www-form-urlencoded",
                    "authorization_code", context.getString(R.string.client_id), code,
                    AppConstants.REDIRECT_URI).enqueue(new Callback<ResponseBody>() {

                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                    Log.d(TAG, "Get OAuth Token, Response code  : " + response.code());
                    try {
                        if (response.isSuccessful()) {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            idToken = jsonObject.getString("id_token");
                            accessToken = jsonObject.getString("access_token");
                            refreshToken = jsonObject.getString("refresh_token");
                            isOAuthLogin = true;

                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString(AppConstants.KEY_ID_TOKEN, idToken);
                            editor.putString(AppConstants.KEY_ACCESS_TOKEN, accessToken);
                            editor.putString(AppConstants.KEY_REFRESH_TOKEN, refreshToken);
                            editor.putBoolean(AppConstants.KEY_IS_OAUTH_LOGIN, true);
                            editor.apply();

                            getTokenAndUserId();
                            listener.onSuccess(null);

                        } else {
                            listener.onFailure(new RuntimeException("Failed to login"));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        listener.onFailure(e);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        listener.onFailure(e);
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    t.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            listener.onFailure(e);
        }
    }

    public void getTokenAndUserId() {

        userName = sharedPreferences.getString(AppConstants.KEY_EMAIL, "");
        idToken = sharedPreferences.getString(AppConstants.KEY_ID_TOKEN, "");
        accessToken = sharedPreferences.getString(AppConstants.KEY_ACCESS_TOKEN, "");
        refreshToken = sharedPreferences.getString(AppConstants.KEY_REFRESH_TOKEN, "");
        isOAuthLogin = sharedPreferences.getBoolean(AppConstants.KEY_IS_OAUTH_LOGIN, false);

        if (!TextUtils.isEmpty(idToken)) {

            JWT jwt = null;
            try {
                jwt = new JWT(idToken);
            } catch (DecodeException e) {
                e.printStackTrace();
            }

            Claim claimUserId = jwt.getClaim("custom:user_id");
            userId = claimUserId.asString();

            if (isOAuthLogin) {

                Claim claimEmail = jwt.getClaim("email");
                String email = claimEmail.asString();
                userName = email;

                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(AppConstants.KEY_EMAIL, email);
                editor.apply();
            }
            Log.d(TAG, "==============>>>>>>>>>>> USER ID : " + userId);
        }
    }

    /**
     * This method is used to get user id from user name.
     *
     * @param listener Listener to send success or failure.
     */
    public void getSupportedVersions(final ApiResponseListener listener) {

        Log.d(TAG, "Get Supported Versions");

        apiInterface.getSupportedVersions()

                .enqueue(new Callback<ResponseBody>() {

                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                        Log.d(TAG, "Get Supported Versions, Response code  : " + response.code());

                        if (response.isSuccessful()) {

                            if (response.body() != null) {

                                try {
                                    String jsonResponse = response.body().string();
                                    Log.e(TAG, "onResponse Success : " + jsonResponse);
                                    JSONObject jsonObject = new JSONObject(jsonResponse);
                                    JSONArray jsonArray = jsonObject.optJSONArray("supported_versions");
                                    ArrayList<String> supportedVersions = new ArrayList<>();

                                    for (int i = 0; i < jsonArray.length(); i++) {

                                        String version = jsonArray.optString(i);
                                        Log.d(TAG, "Supported Version : " + version);
                                        supportedVersions.add(version);
                                    }

                                    String additionalInfoMsg = jsonObject.optString("additional_info");
                                    Bundle bundle = new Bundle();
                                    bundle.putString("additional_info", additionalInfoMsg);
                                    bundle.putStringArrayList("supported_versions", supportedVersions);
                                    listener.onSuccess(bundle);

                                } catch (IOException e) {
                                    e.printStackTrace();
                                    listener.onFailure(e);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                    listener.onFailure(e);
                                }
                            } else {
                                Log.e(TAG, "Response received : null");
                                listener.onFailure(new RuntimeException("Failed to get Supported Versions"));
                            }

                        } else {
                            listener.onFailure(new RuntimeException("Failed to get Supported Versions"));
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Log.e(TAG, "Error in receiving Supported Versions");
                        t.printStackTrace();
                        listener.onFailure(new Exception(t));
                    }
                });
    }

    /**
     * This method is used to get all nodes for the user.
     *
     * @param listener Listener to send success or failure.
     */
    public void getNodes(final ApiResponseListener listener) {

        Log.d(TAG, "Get Nodes");

        apiInterface.getNodes(accessToken).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Get Nodes, Response code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            if (espApp.nodeMap == null) {
                                espApp.nodeMap = new HashMap<>();
                            } else {
//                                espApp.nodeMap.clear();
                            }

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            JSONArray nodeJsonArray = jsonObject.optJSONArray("node_details");
                            nodeIds.clear();

                            if (nodeJsonArray != null) {

                                for (int nodeIndex = 0; nodeIndex < nodeJsonArray.length(); nodeIndex++) {

                                    JSONObject nodeJson = nodeJsonArray.optJSONObject(nodeIndex);

                                    if (nodeJson != null) {

                                        // Node ID
                                        String nodeId = nodeJson.optString("id");
                                        Log.d(TAG, "Node id : " + nodeId);
                                        nodeIds.add(nodeId);
                                        EspNode espNode;

                                        if (espApp.nodeMap.get(nodeId) != null) {
                                            espNode = espApp.nodeMap.get(nodeId);
                                        } else {
                                            espNode = new EspNode(nodeId);
                                        }

                                        // Node Config
                                        JSONObject configJson = nodeJson.optJSONObject("config");
                                        if (configJson != null) {

                                            espNode.setConfigVersion(configJson.optString("config_version"));

                                            JSONObject infoObj = configJson.optJSONObject("info");

                                            if (infoObj != null) {
                                                espNode.setNodeName(infoObj.optString("name"));
                                                espNode.setFwVersion(infoObj.optString("fw_version"));
                                                espNode.setNodeType(infoObj.optString("type"));
                                            } else {
                                                Log.d(TAG, "Info object is null");
                                            }
                                            espNode.setOnline(true);

                                            JSONArray devicesJsonArray = configJson.optJSONArray("devices");
                                            ArrayList<Device> devices = new ArrayList<>();

                                            if (devicesJsonArray != null) {

                                                for (int i = 0; i < devicesJsonArray.length(); i++) {

                                                    JSONObject deviceObj = devicesJsonArray.optJSONObject(i);
                                                    Device device = new Device(nodeId);
                                                    device.setDeviceName(deviceObj.optString("name"));
                                                    device.setDeviceType(deviceObj.optString("type"));
                                                    device.setPrimaryParamName(deviceObj.optString("primary"));

                                                    JSONArray paramsJson = deviceObj.optJSONArray("params");
                                                    ArrayList<Param> params = new ArrayList<>();

                                                    if (paramsJson != null) {

                                                        for (int j = 0; j < paramsJson.length(); j++) {

                                                            JSONObject paraObj = paramsJson.optJSONObject(j);
                                                            Param param = new Param();
                                                            param.setName(paraObj.optString("name"));
                                                            param.setParamType(paraObj.optString("type"));
                                                            param.setDataType(paraObj.optString("data_type"));
                                                            param.setUiType(paraObj.optString("ui_type"));
                                                            param.setDynamicParam(true);
                                                            params.add(param);

                                                            JSONArray propertiesJson = paraObj.optJSONArray("properties");
                                                            ArrayList<String> properties = new ArrayList<>();

                                                            if (propertiesJson != null) {
                                                                for (int k = 0; k < propertiesJson.length(); k++) {

                                                                    properties.add(propertiesJson.optString(k));
                                                                }
                                                            }
                                                            param.setProperties(properties);

                                                            JSONObject boundsJson = paraObj.optJSONObject("bounds");

                                                            if (boundsJson != null) {
                                                                param.setMaxBounds(boundsJson.optInt("max"));
                                                                param.setMinBounds(boundsJson.optInt("min"));
                                                            }
                                                        }
                                                    }

                                                    JSONArray attributesJson = deviceObj.optJSONArray("attributes");

                                                    if (attributesJson != null) {

                                                        for (int j = 0; j < attributesJson.length(); j++) {

                                                            JSONObject attrObj = attributesJson.optJSONObject(j);
                                                            Param param = new Param();
                                                            param.setName(attrObj.optString("name"));
                                                            param.setDataType(attrObj.optString("data_type"));
                                                            param.setLabelValue(attrObj.optString("value"));
                                                            params.add(param);
                                                        }
                                                    }

                                                    device.setParams(params);
                                                    devices.add(device);
                                                }
                                            }

                                            espNode.setDevices(devices);

                                            JSONArray nodeAttributesJson = infoObj.optJSONArray("attributes");
                                            ArrayList<Param> nodeAttributes = new ArrayList<>();

                                            if (nodeAttributesJson != null) {

                                                for (int j = 0; j < nodeAttributesJson.length(); j++) {

                                                    JSONObject attrObj = nodeAttributesJson.optJSONObject(j);
                                                    Param param = new Param();
                                                    param.setName(attrObj.optString("name"));
                                                    param.setLabelValue(attrObj.optString("value"));
                                                    nodeAttributes.add(param);
                                                }
                                            }

                                            espNode.setAttributes(nodeAttributes);

                                            espApp.nodeMap.put(nodeId, espNode);
                                        }

                                        // Node Params
                                        JSONObject paramsJson = nodeJson.optJSONObject("params");
                                        if (paramsJson != null) {

                                            ArrayList<Device> devices = espNode.getDevices();

                                            for (int i = 0; i < devices.size(); i++) {

                                                ArrayList<Param> params = devices.get(i).getParams();
                                                String deviceName = devices.get(i).getDeviceName();
                                                JSONObject deviceJson = paramsJson.optJSONObject(deviceName);

                                                if (deviceJson != null) {

                                                    for (int j = 0; j < params.size(); j++) {

                                                        Param param = params.get(j);
                                                        String key = param.getName();

                                                        if (!param.isDynamicParam()) {
                                                            continue;
                                                        }

                                                        if (jsonResponse.contains(key)) {

                                                            String dataType = param.getDataType();

                                                            if (AppConstants.UI_TYPE_SLIDER.equalsIgnoreCase(param.getUiType())) {

                                                                String labelValue = "";

                                                                if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                                                                    int value = deviceJson.optInt(key);
                                                                    labelValue = String.valueOf(value);
                                                                    param.setLabelValue(labelValue);
                                                                    param.setSliderValue(value);

                                                                } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                                                                    double value = deviceJson.optDouble(key);
                                                                    labelValue = String.valueOf(value);
                                                                    param.setLabelValue(labelValue);
                                                                    param.setSliderValue(value);

                                                                } else {

                                                                    labelValue = deviceJson.optString(key);
                                                                    param.setLabelValue(labelValue);
                                                                }

                                                            } else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(param.getUiType())) {

                                                                boolean value = deviceJson.optBoolean(key);
                                                                param.setSwitchStatus(value);

                                                            } else {

                                                                String labelValue = "";

                                                                if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {

                                                                    boolean value = deviceJson.optBoolean(key);
                                                                    if (value) {
                                                                        param.setLabelValue("true");
                                                                    } else {
                                                                        param.setLabelValue("false");
                                                                    }

                                                                } else if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                                                                    int value = deviceJson.optInt(key);
                                                                    labelValue = String.valueOf(value);
                                                                    param.setLabelValue(labelValue);

                                                                } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                                                                    double value = deviceJson.optDouble(key);
                                                                    labelValue = String.valueOf(value);
                                                                    param.setLabelValue(labelValue);

                                                                } else {

                                                                    labelValue = deviceJson.optString(key);
                                                                    param.setLabelValue(labelValue);
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Log.e(TAG, "Device JSON is null");
                                                }
                                            }
                                        }

                                        // Node Status
                                        JSONObject statusJson = nodeJson.optJSONObject("status");

                                        if (statusJson != null) {

                                            JSONObject connectivityObject = statusJson.optJSONObject("connectivity");

                                            if (connectivityObject != null) {

                                                boolean nodeStatus = connectivityObject.optBoolean("connected");
                                                long timestamp = connectivityObject.optLong("timestamp");
                                                espNode.setTimeStampOfStatus(timestamp);

                                                if (espNode.isOnline() != nodeStatus) {
                                                    espNode.setOnline(nodeStatus);
                                                    EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
                                                }
                                            } else {
                                                Log.e(TAG, "Connectivity object is null");
                                            }
                                        }
                                    }
                                }
                            }

                            Iterator<Map.Entry<String, EspNode>> itr = espApp.nodeMap.entrySet().iterator();

                            // iterate and remove items simultaneously
                            while (itr.hasNext()) {

                                Map.Entry<String, EspNode> entry = itr.next();
                                String key = entry.getKey();

                                if (!nodeIds.contains(key)) {
                                    itr.remove();
                                }
                            }

                            listener.onSuccess(null);

                        } catch (IOException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        }
                    } else {
                        Log.e(TAG, "Response received : null");
                        listener.onFailure(new RuntimeException("Failed to get User device mapping"));
                    }
                } else {

                    if (response.body() != null) {

                        try {
                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Failure : " + jsonResponse);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    listener.onFailure(new RuntimeException("Failed to get User device mapping"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onFailure(new Exception(t));
            }
        });
    }

    public void getNodeDetails(String nodeId, final ApiResponseListener listener) {

        Log.d(TAG, "Get Node Details");

        apiInterface.getNode(accessToken, nodeId).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Get Node Details, Response code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            if (espApp.nodeMap == null) {
                                espApp.nodeMap = new HashMap<>();
                            } else {
//                                espApp.nodeMap.clear();
                            }

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            JSONArray nodeJsonArray = jsonObject.optJSONArray("node_details");
                            nodeIds.clear();

                            if (nodeJsonArray != null) {

                                for (int nodeIndex = 0; nodeIndex < nodeJsonArray.length(); nodeIndex++) {

                                    JSONObject nodeJson = nodeJsonArray.optJSONObject(nodeIndex);

                                    if (nodeJson != null) {

                                        // Node ID
                                        String nodeId = nodeJson.optString("id");
                                        Log.d(TAG, "Node id : " + nodeId);
                                        nodeIds.add(nodeId);
                                        EspNode espNode;

                                        if (espApp.nodeMap.get(nodeId) != null) {
                                            espNode = espApp.nodeMap.get(nodeId);
                                        } else {
                                            espNode = new EspNode(nodeId);
                                        }

                                        // Node Config
                                        JSONObject configJson = nodeJson.optJSONObject("config");
                                        if (configJson != null) {

                                            espNode.setConfigVersion(configJson.optString("config_version"));

                                            JSONObject infoObj = configJson.optJSONObject("info");

                                            if (infoObj != null) {
                                                espNode.setNodeName(infoObj.optString("name"));
                                                espNode.setFwVersion(infoObj.optString("fw_version"));
                                                espNode.setNodeType(infoObj.optString("type"));
                                            } else {
                                                Log.d(TAG, "Info object is null");
                                            }
                                            espNode.setOnline(true);

                                            JSONArray devicesJsonArray = configJson.optJSONArray("devices");
                                            ArrayList<Device> devices = new ArrayList<>();

                                            if (devicesJsonArray != null) {

                                                for (int i = 0; i < devicesJsonArray.length(); i++) {

                                                    JSONObject deviceObj = devicesJsonArray.optJSONObject(i);
                                                    Device device = new Device(nodeId);
                                                    device.setDeviceName(deviceObj.optString("name"));
                                                    device.setDeviceType(deviceObj.optString("type"));
                                                    device.setPrimaryParamName(deviceObj.optString("primary"));

                                                    JSONArray paramsJson = deviceObj.optJSONArray("params");
                                                    ArrayList<Param> params = new ArrayList<>();

                                                    if (paramsJson != null) {

                                                        for (int j = 0; j < paramsJson.length(); j++) {

                                                            JSONObject paraObj = paramsJson.optJSONObject(j);
                                                            Param param = new Param();
                                                            param.setName(paraObj.optString("name"));
                                                            param.setParamType(paraObj.optString("type"));
                                                            param.setDataType(paraObj.optString("data_type"));
                                                            param.setUiType(paraObj.optString("ui_type"));
                                                            param.setDynamicParam(true);
                                                            params.add(param);

                                                            JSONArray propertiesJson = paraObj.optJSONArray("properties");
                                                            ArrayList<String> properties = new ArrayList<>();

                                                            if (propertiesJson != null) {
                                                                for (int k = 0; k < propertiesJson.length(); k++) {

                                                                    properties.add(propertiesJson.optString(k));
                                                                }
                                                            }
                                                            param.setProperties(properties);

                                                            JSONObject boundsJson = paraObj.optJSONObject("bounds");

                                                            if (boundsJson != null) {
                                                                param.setMaxBounds(boundsJson.optInt("max"));
                                                                param.setMinBounds(boundsJson.optInt("min"));
                                                            }
                                                        }
                                                    }

                                                    JSONArray attributesJson = deviceObj.optJSONArray("attributes");

                                                    if (attributesJson != null) {

                                                        for (int j = 0; j < attributesJson.length(); j++) {

                                                            JSONObject attrObj = attributesJson.optJSONObject(j);
                                                            Param param = new Param();
                                                            param.setName(attrObj.optString("name"));
                                                            param.setDataType(attrObj.optString("data_type"));
                                                            param.setLabelValue(attrObj.optString("value"));
                                                            params.add(param);
                                                        }
                                                    }

                                                    device.setParams(params);
                                                    devices.add(device);
                                                }
                                            }

                                            espNode.setDevices(devices);

                                            JSONArray nodeAttributesJson = infoObj.optJSONArray("attributes");
                                            ArrayList<Param> nodeAttributes = new ArrayList<>();

                                            if (nodeAttributesJson != null) {

                                                for (int j = 0; j < nodeAttributesJson.length(); j++) {

                                                    JSONObject attrObj = nodeAttributesJson.optJSONObject(j);
                                                    Param param = new Param();
                                                    param.setName(attrObj.optString("name"));
                                                    param.setLabelValue(attrObj.optString("value"));
                                                    nodeAttributes.add(param);
                                                }
                                            }

                                            espNode.setAttributes(nodeAttributes);

                                            espApp.nodeMap.put(nodeId, espNode);
                                        }

                                        // Node Params
                                        JSONObject paramsJson = nodeJson.optJSONObject("params");
                                        if (paramsJson != null) {

                                            ArrayList<Device> devices = espNode.getDevices();

                                            for (int i = 0; i < devices.size(); i++) {

                                                ArrayList<Param> params = devices.get(i).getParams();
                                                String deviceName = devices.get(i).getDeviceName();
                                                JSONObject deviceJson = paramsJson.optJSONObject(deviceName);

                                                if (deviceJson != null) {

                                                    for (int j = 0; j < params.size(); j++) {

                                                        Param param = params.get(j);
                                                        String key = param.getName();

                                                        if (!param.isDynamicParam()) {
                                                            continue;
                                                        }

                                                        if (jsonResponse.contains(key)) {

                                                            String dataType = param.getDataType();

                                                            if (AppConstants.UI_TYPE_SLIDER.equalsIgnoreCase(param.getUiType())) {

                                                                String labelValue = "";

                                                                if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                                                                    int value = deviceJson.optInt(key);
                                                                    labelValue = String.valueOf(value);
                                                                    param.setLabelValue(labelValue);
                                                                    param.setSliderValue(value);

                                                                } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                                                                    double value = deviceJson.optDouble(key);
                                                                    labelValue = String.valueOf(value);
                                                                    param.setLabelValue(labelValue);
                                                                    param.setSliderValue(value);

                                                                } else {

                                                                    labelValue = deviceJson.optString(key);
                                                                    param.setLabelValue(labelValue);
                                                                }

                                                            } else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(param.getUiType())) {

                                                                boolean value = deviceJson.optBoolean(key);
                                                                param.setSwitchStatus(value);

                                                            } else {

                                                                String labelValue = "";

                                                                if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {

                                                                    boolean value = deviceJson.optBoolean(key);
                                                                    if (value) {
                                                                        param.setLabelValue("true");
                                                                    } else {
                                                                        param.setLabelValue("false");
                                                                    }

                                                                } else if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                                                                    int value = deviceJson.optInt(key);
                                                                    labelValue = String.valueOf(value);
                                                                    param.setLabelValue(labelValue);

                                                                } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                                                                    double value = deviceJson.optDouble(key);
                                                                    labelValue = String.valueOf(value);
                                                                    param.setLabelValue(labelValue);

                                                                } else {

                                                                    labelValue = deviceJson.optString(key);
                                                                    param.setLabelValue(labelValue);
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Log.e(TAG, "Device JSON is null");
                                                }
                                            }
                                        }

                                        // Node Status
                                        JSONObject statusJson = nodeJson.optJSONObject("status");

                                        if (statusJson != null) {

                                            JSONObject connectivityObject = statusJson.optJSONObject("connectivity");

                                            if (connectivityObject != null) {

                                                boolean nodeStatus = connectivityObject.optBoolean("connected");
                                                long timestamp = connectivityObject.optLong("timestamp");
                                                espNode.setTimeStampOfStatus(timestamp);

                                                if (espNode.isOnline() != nodeStatus) {
                                                    espNode.setOnline(nodeStatus);
                                                    EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
                                                }
                                            } else {
                                                Log.e(TAG, "Connectivity object is null");
                                            }
                                        }
                                    }
                                }
                            }

                            listener.onSuccess(null);

                        } catch (IOException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        }
                    } else {
                        Log.e(TAG, "Response received : null");
                        listener.onFailure(new RuntimeException("Failed to get User device mapping"));
                    }
                } else {

                    if (response.body() != null) {

                        try {
                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Failure : " + jsonResponse);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    listener.onFailure(new RuntimeException("Failed to get User device mapping"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onFailure(new Exception(t));
            }
        });
    }

    /**
     * This method is used to send request for add device (Associate device with user).
     *
     * @param nodeId    Device Id.
     * @param secretKey Generated Secret Key.
     * @param listener  Listener to send success or failure.
     */
    public void addNode(final String nodeId, String secretKey, final ApiResponseListener listener) {

        Log.d(TAG, "Add Node, nodeId : " + nodeId);

        DeviceOperationRequest req = new DeviceOperationRequest();
        req.setNodeId(nodeId);
        req.setSecretKey(secretKey);
        req.setOperation("add");

        apiInterface.addDevice(accessToken, req).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Add Node, Response code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            String reqId = jsonObject.optString("request_id");
                            requestIds.put(nodeId, reqId);
                            handler.post(getRequestStatusTask);
                            Bundle data = new Bundle();
                            data.putString("request_id", reqId);
                            listener.onSuccess(data);

                        } catch (IOException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        }
                    } else {
                        listener.onFailure(new RuntimeException("Failed to add device"));
                    }

                } else {
                    listener.onFailure(new RuntimeException("Failed to add device"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onFailure(new Exception(t));
            }
        });
    }

    /**
     * This method is used to send request for remove device.
     *
     * @param nodeId   Device Id.
     * @param listener Listener to send success or failure.
     */
    public void removeNode(final String nodeId, final ApiResponseListener listener) {

        Log.d(TAG, "Remove Node, nodeId : " + nodeId);

        DeviceOperationRequest req = new DeviceOperationRequest();
        req.setNodeId(nodeId);
        req.setOperation("remove");

        apiInterface.removeDevice(accessToken, req).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Remove Node, response code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {
                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            listener.onSuccess(null);

                        } catch (IOException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        }
                    } else {
                        listener.onFailure(new RuntimeException("Failed to remove device"));
                    }

                } else {
                    listener.onFailure(new RuntimeException("Failed to remove device"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onFailure(new Exception(t));
            }
        });
    }

    public void getParamsValues(final String nodeId, final ApiResponseListener listener) {

        Log.d(TAG, "Get Param values");

        apiInterface.getParamValue(accessToken, nodeId).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Get Params Values, Response code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);

                            EspNode node = espApp.nodeMap.get(nodeId);

                            if (node != null) {

                                ArrayList<Device> devices = node.getDevices();

                                for (int i = 0; i < devices.size(); i++) {

                                    ArrayList<Param> params = devices.get(i).getParams();
                                    String deviceName = devices.get(i).getDeviceName();
                                    JSONObject deviceJson = jsonObject.optJSONObject(deviceName);

                                    if (deviceJson != null) {

                                        for (int j = 0; j < params.size(); j++) {

                                            Param param = params.get(j);
                                            String key = param.getName();

                                            if (jsonResponse.contains(key)) {

                                                String dataType = param.getDataType();

                                                if (AppConstants.UI_TYPE_SLIDER.equalsIgnoreCase(param.getUiType())) {

                                                    String labelValue = "";

                                                    if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                                                        int value = deviceJson.optInt(key);
                                                        labelValue = String.valueOf(value);
                                                        param.setLabelValue(labelValue);
                                                        param.setSliderValue(value);

                                                    } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                                                        double value = deviceJson.optDouble(key);
                                                        labelValue = String.valueOf(value);
                                                        param.setLabelValue(labelValue);
                                                        param.setSliderValue(value);

                                                    } else {

                                                        labelValue = deviceJson.optString(key);
                                                        param.setLabelValue(labelValue);
                                                    }

                                                } else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(param.getUiType())) {

                                                    boolean value = deviceJson.optBoolean(key);
                                                    param.setSwitchStatus(value);

                                                } else {

                                                    String labelValue = "";

                                                    if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {

                                                        boolean value = deviceJson.optBoolean(key);
                                                        if (value) {
                                                            param.setLabelValue("true");
                                                        } else {
                                                            param.setLabelValue("false");
                                                        }

                                                    } else if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                                                        int value = deviceJson.optInt(key);
                                                        labelValue = String.valueOf(value);
                                                        param.setLabelValue(labelValue);

                                                    } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                                                        double value = deviceJson.optDouble(key);
                                                        labelValue = String.valueOf(value);
                                                        param.setLabelValue(labelValue);

                                                    } else {

                                                        labelValue = deviceJson.optString(key);
                                                        param.setLabelValue(labelValue);
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Log.e(TAG, "Device JSON is null");
                                    }
                                }
                            }
                            listener.onSuccess(null);

                        } catch (IOException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        }
                    } else {
                        listener.onFailure(new RuntimeException("Failed to Get Dynamic Params"));
                    }

                } else {
                    listener.onFailure(new RuntimeException("Failed to Get Dynamic Params"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onFailure(new Exception(t));
            }
        });
    }

    public void updateParamValue(final String nodeId, JsonObject body, final ApiResponseListener listener) {

        Log.d(TAG, "Updating param value");

        try {
            apiInterface.updateParamValue(accessToken, nodeId, body).enqueue(new Callback<ResponseBody>() {

                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                    Log.e(TAG, "Update Params Value, Response code : " + response.code());

                    if (response.isSuccessful()) {

                        if (response.body() != null) {

                            try {
                                String jsonResponse = response.body().string();
                                Log.e(TAG, "onResponse Success : " + jsonResponse);
                                JSONObject jsonObject = new JSONObject(jsonResponse);
                                listener.onSuccess(null);

                            } catch (IOException e) {
                                e.printStackTrace();
                                listener.onFailure(e);
                            } catch (JSONException e) {
                                e.printStackTrace();
                                listener.onFailure(e);
                            }
                        } else {
                            listener.onFailure(new RuntimeException("Failed to update dynamic param"));
                        }

                    } else {
                        listener.onFailure(new RuntimeException("Failed to update dynamic param"));
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    t.printStackTrace();
                    listener.onFailure(new Exception(t));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getAddNodeRequestStatus(final String nodeId, String requestId) {

        Log.d(TAG, "Get Node mapping status");

        apiInterface.getAddNodeRequestStatus(accessToken, requestId, true).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Get Node mapping status, Response code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {
                        try {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            String reqStatus = jsonObject.optString("request_status");

                            if (!TextUtils.isEmpty(reqStatus) && reqStatus.equals("confirmed")) {

                                requestIds.remove(nodeId);

                                // Send event for update UI
                                if (requestIds.size() == 0) {
                                    EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_DEVICE_ADDED));
                                }
                            } else if (!TextUtils.isEmpty(reqStatus) && reqStatus.equals("timedout")) {

                                requestIds.remove(nodeId);

                                // Send event for update UI
                                if (requestIds.size() == 0) {
                                    EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_ADD_DEVICE_TIME_OUT));
                                }
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.e(TAG, "Get node mapping status failed");
                    }

                } else {
                    Log.e(TAG, "Get node mapping status failed");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }

    private void getNodeReqStatus() {

        handler.postDelayed(getRequestStatusTask, REQ_STATUS_TIME);
    }

    private Runnable getRequestStatusTask = new Runnable() {

        @Override
        public void run() {

            if (requestIds.size() > 0) {

                for (String key : requestIds.keySet()) {

                    String nodeId = key;
                    String requestId = requestIds.get(nodeId);
                    getAddNodeRequestStatus(nodeId, requestId);
                }
                getNodeReqStatus();
            } else {
                Log.i(TAG, "No request id is available to check status");
                handler.removeCallbacks(getRequestStatusTask);
            }
        }
    };

    public boolean isTokenExpired() {

        Log.d(TAG, "Check isTokenExpired");

        idToken = sharedPreferences.getString(AppConstants.KEY_ID_TOKEN, "");
        accessToken = sharedPreferences.getString(AppConstants.KEY_ACCESS_TOKEN, "");
        refreshToken = sharedPreferences.getString(AppConstants.KEY_REFRESH_TOKEN, "");

        JWT jwt = null;
        try {
            jwt = new JWT(accessToken);
        } catch (DecodeException e) {
            e.printStackTrace();
        }

        Date expiresAt = jwt.getExpiresAt();
        Calendar calendar = Calendar.getInstance();
        Date currentTIme = calendar.getTime();

        if (currentTIme.after(expiresAt)) {
            Log.e(TAG, "Token has expired");
            return true;
        } else {
            Log.d(TAG, "Token has not expired");
            return false;
        }
    }

    public void getNewToken() {

        if (isOAuthLogin) {
            getNewTokenForOAuth(new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {
                }

                @Override
                public void onFailure(Exception exception) {
                }
            });

        } else {
            AppHelper.getPool().getUser(userName).getSessionInBackground(authenticationHandler);
        }
    }

    public void getNewTokenForOAuth(final ApiResponseListener listener) {

        Log.d(TAG, "Get New Token For OAuth");
        HashMap<String, String> body = new HashMap<>();
        body.put("user_name", userId);
        body.put("refreshtoken", refreshToken);

        apiInterface.getOAuthLoginToken(body).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "onResponse code  : " + response.code());
                try {
                    if (response.isSuccessful()) {

                        String jsonResponse = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonResponse);
                        idToken = jsonObject.getString("idtoken");
                        accessToken = jsonObject.getString("accesstoken");
                        isOAuthLogin = true;

                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(AppConstants.KEY_ID_TOKEN, idToken);
                        editor.putString(AppConstants.KEY_ACCESS_TOKEN, accessToken);
                        editor.putBoolean(AppConstants.KEY_IS_OAUTH_LOGIN, true);
                        editor.apply();

                        getTokenAndUserId();
                        listener.onSuccess(null);

                    } else {
                        // TODO Handle 400 error case
                        listener.onFailure(new RuntimeException("Failed to get new token"));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    listener.onFailure(e);
                } catch (JSONException e) {
                    e.printStackTrace();
                    listener.onFailure(e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onFailure(new Exception(t));
            }
        });
    }

    public void initiateClaim(JsonObject body, final ApiResponseListener listener) {

        Log.d(TAG, "Initiate Claiming...");

        apiInterface.initiateClaiming(accessToken, body).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "onResponse code  : " + response.code());

                try {
                    if (response.isSuccessful()) {

                        String jsonResponse = response.body().string();
                        Bundle data = new Bundle();
                        data.putString("claim_initiate_response", jsonResponse);
                        listener.onSuccess(data);

                    } else {

                        String jsonErrResponse = response.errorBody().string();
                        Log.e(TAG, "Error Response : " + jsonErrResponse);

                        if (jsonErrResponse.contains("failure")) {

                            JSONObject jsonObject = new JSONObject(jsonErrResponse);
                            String err = jsonObject.optString("description");

                            if (!TextUtils.isEmpty(err)) {
                                listener.onFailure(new RuntimeException(err));
                            } else {
                                listener.onFailure(new RuntimeException("Claim init failed"));
                            }

                        } else {
                            listener.onFailure(new RuntimeException("Claim init failed"));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    listener.onFailure(new RuntimeException("Claim init failed"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onFailure(new RuntimeException("Claim init failed"));
            }
        });
    }

    public void verifyClaiming(JsonObject body, final ApiResponseListener listener) {

        Log.d(TAG, "Verifying Claiming...");

        apiInterface.verifyClaiming(accessToken, body).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Response code  : " + response.code());

                try {
                    if (response.isSuccessful()) {

                        String jsonResponse = response.body().string();
                        Bundle data = new Bundle();
                        data.putString("claim_verify_response", jsonResponse);
                        listener.onSuccess(data);

                    } else {

                        String jsonErrResponse = response.errorBody().string();
                        Log.e(TAG, "Error Response : " + jsonErrResponse);

                        if (jsonErrResponse.contains("failure")) {

                            JSONObject jsonObject = new JSONObject(jsonErrResponse);
                            String err = jsonObject.optString("description");

                            if (!TextUtils.isEmpty(err)) {
                                listener.onFailure(new RuntimeException(err));
                            } else {
                                listener.onFailure(new RuntimeException("Claim verify failed"));
                            }

                        } else {
                            listener.onFailure(new RuntimeException("Claim verify failed"));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    listener.onFailure(new RuntimeException("Claim verify failed"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onFailure(new RuntimeException("Claim verify failed"));
            }
        });
    }

    private Runnable stopRequestStatusPollingTask = new Runnable() {

        @Override
        public void run() {
            requestIds.clear();
            Log.d(TAG, "Stopped Polling Task");
            handler.removeCallbacks(getRequestStatusTask);
            EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_ADD_DEVICE_TIME_OUT));
        }
    };

    public void cancelRequestStatusPollingTask() {

        handler.removeCallbacks(stopRequestStatusPollingTask);
    }

    AuthenticationHandler authenticationHandler = new AuthenticationHandler() {

        @Override
        public void onSuccess(CognitoUserSession cognitoUserSession, CognitoDevice newDevice) {

            Log.d(TAG, " -- Auth Success");
            AppHelper.setCurrSession(cognitoUserSession);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(AppConstants.KEY_ID_TOKEN, cognitoUserSession.getIdToken().getJWTToken());
            editor.putString(AppConstants.KEY_ACCESS_TOKEN, cognitoUserSession.getAccessToken().getJWTToken());
            editor.putString(AppConstants.KEY_REFRESH_TOKEN, cognitoUserSession.getRefreshToken().getToken());
            editor.putBoolean(AppConstants.KEY_IS_OAUTH_LOGIN, false);
            editor.apply();

            AppHelper.newDevice(newDevice);
            getTokenAndUserId();
        }

        @Override
        public void getAuthenticationDetails(AuthenticationContinuation authenticationContinuation, String userId) {
            Log.d(TAG, "getAuthenticationDetails ");
        }

        @Override
        public void getMFACode(MultiFactorAuthenticationContinuation continuation) {
            Log.d(TAG, "getMFACode ");
        }

        @Override
        public void authenticationChallenge(ChallengeContinuation continuation) {
            Log.d(TAG, "authenticationChallenge ");
        }

        @Override
        public void onFailure(Exception exception) {
            Log.e(TAG, "onFailure ");
            exception.printStackTrace();
        }
    };
}
