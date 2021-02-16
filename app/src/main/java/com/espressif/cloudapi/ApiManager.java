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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoDevice;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ChallengeContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.MultiFactorAuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.AuthenticationHandler;
import com.auth0.android.jwt.Claim;
import com.auth0.android.jwt.DecodeException;
import com.auth0.android.jwt.JWT;
import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.EspDatabase;
import com.espressif.JsonDataParser;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.ui.models.Action;
import com.espressif.ui.models.ApiResponse;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.Schedule;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
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
    private EspDatabase espDatabase;
    private SharedPreferences sharedPreferences;
    private static ArrayList<String> nodeIds = new ArrayList<>();
    private static ArrayList<String> scheduleIds = new ArrayList<>();

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
        espDatabase = EspDatabase.getInstance(context);
        apiInterface = ApiClient.getClient(context).create(ApiInterface.class);
        sharedPreferences = context.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        getTokenAndUserId();
    }

    public void getOAuthToken(String code, final ApiResponseListener listener) {

        Log.d(TAG, "Get OAuth Token");
        String url = BuildConfig.TOKEN_URL;

        try {
            apiInterface.loginWithGithub(url, "application/x-www-form-urlencoded",
                    "authorization_code", BuildConfig.CLIENT_ID, code,
                    BuildConfig.REDIRECT_URI).enqueue(new Callback<ResponseBody>() {

                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                    Log.d(TAG, "Get OAuth Token, Response code  : " + response.code());
                    try {
                        if (response.isSuccessful()) {

                            String jsonResponse = response.body().string();
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            idToken = jsonObject.getString(AppConstants.KEY_ID_TOKEN);
                            accessToken = jsonObject.getString(AppConstants.KEY_ACCESS_TOKEN);
                            refreshToken = jsonObject.getString(AppConstants.KEY_REFRESH_TOKEN);
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

                            String jsonErrResponse = response.errorBody().string();
                            Log.e(TAG, "Error Response : " + jsonErrResponse);

                            if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {

                                JSONObject jsonObject = new JSONObject(jsonErrResponse);
                                String err = jsonObject.optString(AppConstants.KEY_DESCRIPTION);

                                if (!TextUtils.isEmpty(err)) {
                                    listener.onFailure(new CloudException(err));
                                } else {
                                    listener.onFailure(new RuntimeException("Failed to login"));
                                }

                            } else {
                                listener.onFailure(new RuntimeException("Failed to login"));
                            }
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

        apiInterface.getSupportedVersions(AppConstants.URL_SUPPORTED_VERSIONS)

                .enqueue(new Callback<ResponseBody>() {

                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                        Log.d(TAG, "Get Supported Versions, Response code  : " + response.code());

                        try {

                            if (response.isSuccessful()) {

                                if (response.body() != null) {

                                    String jsonResponse = response.body().string();
                                    Log.e(TAG, "onResponse Success : " + jsonResponse);
                                    JSONObject jsonObject = new JSONObject(jsonResponse);
                                    JSONArray jsonArray = jsonObject.optJSONArray(AppConstants.KEY_SUPPORTED_VERSIONS);
                                    ArrayList<String> supportedVersions = new ArrayList<>();

                                    for (int i = 0; i < jsonArray.length(); i++) {

                                        String version = jsonArray.optString(i);
                                        Log.d(TAG, "Supported Version : " + version);
                                        supportedVersions.add(version);
                                    }

                                    String additionalInfoMsg = jsonObject.optString(AppConstants.KEY_ADDITIONAL_INFO);
                                    Bundle bundle = new Bundle();
                                    bundle.putString(AppConstants.KEY_ADDITIONAL_INFO, additionalInfoMsg);
                                    bundle.putStringArrayList(AppConstants.KEY_SUPPORTED_VERSIONS, supportedVersions);
                                    listener.onSuccess(bundle);

                                } else {
                                    Log.e(TAG, "Response received : null");
                                    listener.onFailure(new RuntimeException("Failed to get Supported Versions"));
                                }

                            } else {

                                String jsonErrResponse = response.errorBody().string();
                                Log.e(TAG, "Error Response : " + jsonErrResponse);

                                if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {

                                    JSONObject jsonObject = new JSONObject(jsonErrResponse);
                                    String err = jsonObject.optString(AppConstants.KEY_DESCRIPTION);

                                    if (!TextUtils.isEmpty(err)) {
                                        listener.onFailure(new CloudException(err));
                                    } else {
                                        listener.onFailure(new RuntimeException("Failed to get Supported Versions"));
                                    }

                                } else {
                                    listener.onFailure(new RuntimeException("Failed to get Supported Versions"));
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        } catch (IOException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
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
        apiInterface.getNodes(AppConstants.URL_USER_NODES_DETAILS, accessToken).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Get Nodes, Response code : " + response.code());

                try {

                    if (response.isSuccessful()) {

                        if (response.body() != null) {

                            if (espApp.nodeMap == null) {
                                espApp.nodeMap = new HashMap<>();
                            }

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            JSONArray nodeJsonArray = jsonObject.optJSONArray(AppConstants.KEY_NODE_DETAILS);
                            nodeIds.clear();
                            scheduleIds.clear();
                            espDatabase.getNodeDao().deleteAll();
                            Log.d(TAG, "Delete all nodes from local storage.");
                            HashMap<String, Schedule> scheduleMap = new HashMap<>();

                            if (nodeJsonArray != null) {

                                for (int nodeIndex = 0; nodeIndex < nodeJsonArray.length(); nodeIndex++) {

                                    JSONObject nodeJson = nodeJsonArray.optJSONObject(nodeIndex);

                                    if (nodeJson != null) {

                                        // Node ID
                                        String nodeId = nodeJson.optString(AppConstants.KEY_ID);
                                        Log.d(TAG, "Node id : " + nodeId);
                                        nodeIds.add(nodeId);
                                        EspNode espNode;

                                        if (espApp.nodeMap.get(nodeId) != null) {
                                            espNode = espApp.nodeMap.get(nodeId);
                                        } else {
                                            espNode = new EspNode(nodeId);
                                        }

                                        // Node Config
                                        JSONObject configJson = nodeJson.optJSONObject(AppConstants.KEY_CONFIG);
                                        if (configJson != null) {

                                            // If node is available on local network then ignore configuration received from cloud.
                                            if (!espApp.mDNSDeviceMap.containsKey(nodeId)) {
                                                espNode = JsonDataParser.setNodeConfig(espNode, configJson);
                                            } else {
                                                Log.d(TAG, "Ignore config values for local node :" + nodeId);
                                            }

                                            espNode.setOnline(true);
                                            espNode.setConfigData(configJson.toString());
                                            espApp.nodeMap.put(nodeId, espNode);
                                        }

                                        // Node Params values
                                        JSONObject paramsJson = nodeJson.optJSONObject(AppConstants.KEY_PARAMS);
                                        if (paramsJson != null) {

                                            espNode.setParamData(paramsJson.toString());
                                            espDatabase.getNodeDao().insert(espNode);

                                            ArrayList<Device> devices = espNode.getDevices();
                                            JSONObject scheduleJson = paramsJson.optJSONObject(AppConstants.KEY_SCHEDULE);

                                            // If node is available on local network then ignore param values received from cloud.
                                            if (!espApp.mDNSDeviceMap.containsKey(nodeId)) {

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

                                                            if (deviceJson.has(key)) {
                                                                JsonDataParser.setDeviceParamValue(deviceJson, devices.get(i), param);
                                                            }
                                                        }
                                                    } else {
                                                        Log.e(TAG, "Device JSON is null");
                                                    }
                                                }
                                            } else {
                                                Log.d(TAG, "Ignore param values for local node :" + nodeId);
                                            }

                                            // Schedules
                                            if (scheduleJson != null) {

                                                JSONArray scheduleArrayJson = scheduleJson.optJSONArray(AppConstants.KEY_SCHEDULES);

                                                if (scheduleArrayJson != null) {

                                                    for (int index = 0; index < scheduleArrayJson.length(); index++) {

                                                        JSONObject schJson = scheduleArrayJson.getJSONObject(index);
                                                        String scheduleId = schJson.optString(AppConstants.KEY_ID);
                                                        String key = scheduleId;

                                                        if (!TextUtils.isEmpty(scheduleId)) {

                                                            String name = schJson.optString(AppConstants.KEY_NAME);
                                                            key = key + "_" + name + "_" + schJson.optBoolean(AppConstants.KEY_ENABLED);

                                                            HashMap<String, Integer> triggers = new HashMap<>();
                                                            JSONArray triggerArray = schJson.optJSONArray(AppConstants.KEY_TRIGGERS);
                                                            for (int t = 0; t < triggerArray.length(); t++) {
                                                                JSONObject triggerJson = triggerArray.optJSONObject(t);
                                                                int days = triggerJson.optInt(AppConstants.KEY_DAYS);
                                                                int mins = triggerJson.optInt(AppConstants.KEY_MINUTES);
                                                                triggers.put(AppConstants.KEY_DAYS, days);
                                                                triggers.put(AppConstants.KEY_MINUTES, mins);
                                                                key = key + "_" + days + "_" + mins;
                                                            }

                                                            Schedule schedule = scheduleMap.get(key);
                                                            if (schedule == null) {
                                                                schedule = new Schedule();
                                                            }

                                                            schedule.setId(scheduleId);
                                                            schedule.setName(schJson.optString(AppConstants.KEY_NAME));
                                                            schedule.setEnabled(schJson.optBoolean(AppConstants.KEY_ENABLED));

                                                            scheduleIds.add(key);
                                                            schedule.setTriggers(triggers);
                                                            Log.d(TAG, "=============== Schedule : " + schedule.getName() + " ===============");

                                                            // Actions
                                                            JSONObject actionsSchJson = schJson.optJSONObject(AppConstants.KEY_ACTION);

                                                            if (actionsSchJson != null) {

                                                                ArrayList<Action> actions = schedule.getActions();
                                                                if (actions == null) {
                                                                    actions = new ArrayList<>();
                                                                    schedule.setActions(actions);
                                                                }

                                                                for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {

                                                                    Device d = new Device(devices.get(deviceIndex));
                                                                    ArrayList<Param> params = d.getParams();
                                                                    String deviceName = d.getDeviceName();
                                                                    JSONObject deviceAction = actionsSchJson.optJSONObject(deviceName);

                                                                    if (deviceAction != null) {

                                                                        Action action = null;
                                                                        Device actionDevice = null;
                                                                        int actionIndex = -1;

                                                                        for (int aIndex = 0; aIndex < actions.size(); aIndex++) {

                                                                            Action a = actions.get(aIndex);
                                                                            if (a.getDevice().getNodeId().equals(nodeId) && deviceName.equals(a.getDevice().getDeviceName())) {
                                                                                action = actions.get(aIndex);
                                                                                actionIndex = aIndex;
                                                                            }
                                                                        }

                                                                        if (action == null) {
                                                                            action = new Action();
                                                                            action.setNodeId(nodeId);

                                                                            for (int k = 0; k < devices.size(); k++) {

                                                                                if (devices.get(k).getNodeId().equals(nodeId) && devices.get(k).getDeviceName().equals(deviceName)) {
                                                                                    actionDevice = new Device(devices.get(k));
                                                                                    actionDevice.setSelectedState(1);
                                                                                    break;
                                                                                }
                                                                            }

                                                                            if (actionDevice == null) {
                                                                                actionDevice = new Device(nodeId);
                                                                            }
                                                                            action.setDevice(actionDevice);
                                                                        } else {
                                                                            actionDevice = action.getDevice();
                                                                        }

                                                                        ArrayList<Param> actionParams = new ArrayList<>();
                                                                        if (params != null) {

                                                                            Iterator<Param> iterator = params.iterator();
                                                                            while (iterator.hasNext()) {
                                                                                Param p = iterator.next();
                                                                                actionParams.add(new Param(p));
                                                                            }

                                                                            Iterator itr = actionParams.iterator();

                                                                            while (itr.hasNext()) {

                                                                                Param p = (Param) itr.next();

                                                                                if (!p.isDynamicParam()) {
                                                                                    itr.remove();
                                                                                } else if (p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                                                                                    itr.remove();
                                                                                } else if (!p.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {
                                                                                    itr.remove();
                                                                                }
                                                                            }
                                                                        }
                                                                        actionDevice.setParams(actionParams);

                                                                        for (int paramIndex = 0; paramIndex < actionParams.size(); paramIndex++) {

                                                                            Param p = actionParams.get(paramIndex);
                                                                            String paramName = p.getName();

                                                                            if (deviceAction.has(paramName)) {

                                                                                p.setSelected(true);
                                                                                JsonDataParser.setDeviceParamValue(deviceAction, devices.get(deviceIndex), p);
                                                                            }
                                                                        }

                                                                        for (int paramIndex = 0; paramIndex < actionParams.size(); paramIndex++) {

                                                                            if (!actionParams.get(paramIndex).isSelected()) {
                                                                                actionDevice.setSelectedState(2); // Partially selected
                                                                            }
                                                                        }

                                                                        if (actionIndex == -1) {
                                                                            actions.add(action);
                                                                        } else {
                                                                            actions.set(actionIndex, action);
                                                                        }
                                                                        schedule.setActions(actions);

                                                                    }
                                                                }
                                                            }
                                                            scheduleMap.put(key, schedule);
                                                        }
                                                    }
                                                }
                                            } else {
                                                Log.e(TAG, "Schedule JSON is null");
                                            }
                                        }

                                        // Node Status
                                        JSONObject statusJson = nodeJson.optJSONObject(AppConstants.KEY_STATUS);

                                        if (statusJson != null && !espApp.mDNSDeviceMap.containsKey(nodeId)) {

                                            JSONObject connectivityObject = statusJson.optJSONObject(AppConstants.KEY_CONNECTIVITY);

                                            if (connectivityObject != null) {

                                                boolean nodeStatus = connectivityObject.optBoolean(AppConstants.KEY_CONNECTED);
                                                long timestamp = connectivityObject.optLong(AppConstants.KEY_TIMESTAMP);
                                                espNode.setTimeStampOfStatus(timestamp);

                                                if (espNode.isOnline() != nodeStatus) {
                                                    espNode.setOnline(nodeStatus);
//                                                    EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
                                                }
                                            } else {
                                                Log.e(TAG, "Connectivity object is null");
                                            }
                                        }
                                    }
                                }
                            }

                            espApp.scheduleMap = scheduleMap;
                            Iterator<Map.Entry<String, EspNode>> itr = espApp.nodeMap.entrySet().iterator();

                            // iterate and remove items simultaneously
                            while (itr.hasNext()) {

                                Map.Entry<String, EspNode> entry = itr.next();
                                String key = entry.getKey();

                                if (!nodeIds.contains(key)) {
                                    itr.remove();
                                }
                            }

                            Iterator<Map.Entry<String, Schedule>> schItr = espApp.scheduleMap.entrySet().iterator();

                            // iterate and remove items simultaneously
                            while (schItr.hasNext()) {

                                Map.Entry<String, Schedule> entry = schItr.next();
                                String key = entry.getKey();

                                if (!scheduleIds.contains(key)) {
                                    schItr.remove();
                                    Log.e(TAG, "Remove schedule for key : " + key + " and Size : " + espApp.scheduleMap.size());
                                }
                            }

                            listener.onSuccess(null);

                        } else {
                            Log.e(TAG, "Response received : null");
                            listener.onFailure(new RuntimeException("Failed to get User device mapping"));
                        }

                    } else {

                        nodeIds.clear();
                        scheduleIds.clear();

                        String jsonErrResponse = response.errorBody().string();
                        Log.e(TAG, "Error Response : " + jsonErrResponse);

                        if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {

                            JSONObject jsonObject = new JSONObject(jsonErrResponse);
                            String err = jsonObject.optString(AppConstants.KEY_DESCRIPTION);

                            if (!TextUtils.isEmpty(err)) {
                                listener.onFailure(new CloudException(err));
                            } else {
                                listener.onFailure(new RuntimeException("Failed to get User device mapping"));
                            }

                        } else {
                            listener.onFailure(new RuntimeException("Failed to get User device mapping"));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    listener.onFailure(e);
                } catch (IOException e) {
                    e.printStackTrace();
                    listener.onFailure(e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {

                nodeIds.clear();
                scheduleIds.clear();
                t.printStackTrace();
                listener.onFailure(new Exception(t));
            }
        });
    }

    public void getNodeDetails(String nodeId, final ApiResponseListener listener) {

        Log.d(TAG, "Get Node Details for id : " + nodeId);

        apiInterface.getNode(AppConstants.URL_USER_NODES, accessToken, nodeId).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Get Node Details, Response code : " + response.code());

                try {
                    if (response.isSuccessful()) {

                        if (response.body() != null) {

                            if (espApp.nodeMap == null) {
                                espApp.nodeMap = new HashMap<>();
                            }

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            JSONArray nodeJsonArray = jsonObject.optJSONArray(AppConstants.KEY_NODE_DETAILS);
                            nodeIds.clear();

                            if (nodeJsonArray != null) {

                                for (int nodeIndex = 0; nodeIndex < nodeJsonArray.length(); nodeIndex++) {

                                    JSONObject nodeJson = nodeJsonArray.optJSONObject(nodeIndex);

                                    if (nodeJson != null) {

                                        // Node ID
                                        String nodeId = nodeJson.optString(AppConstants.KEY_ID);
                                        Log.d(TAG, "Node id : " + nodeId);
                                        nodeIds.add(nodeId);
                                        EspNode espNode;

                                        if (espApp.nodeMap.get(nodeId) != null) {
                                            espNode = espApp.nodeMap.get(nodeId);
                                        } else {
                                            espNode = new EspNode(nodeId);
                                        }

                                        // Node Config
                                        JSONObject configJson = nodeJson.optJSONObject(AppConstants.KEY_CONFIG);
                                        if (configJson != null) {

                                            espNode = JsonDataParser.setNodeConfig(espNode, configJson);
                                            espNode.setConfigData(configJson.toString());
                                            espApp.nodeMap.put(nodeId, espNode);
                                        }

                                        // Node Params
                                        JSONObject paramsJson = nodeJson.optJSONObject(AppConstants.KEY_PARAMS);
                                        if (paramsJson != null) {
                                            JsonDataParser.setAllParams(espApp, espNode, paramsJson);
                                            espNode.setParamData(paramsJson.toString());
                                            espDatabase.getNodeDao().update(espNode);
                                        }

                                        // Node Status
                                        JSONObject statusJson = nodeJson.optJSONObject(AppConstants.KEY_STATUS);

                                        if (statusJson != null) {

                                            JSONObject connectivityObject = statusJson.optJSONObject(AppConstants.KEY_CONNECTIVITY);

                                            if (connectivityObject != null) {

                                                boolean nodeStatus = connectivityObject.optBoolean(AppConstants.KEY_CONNECTED);
                                                long timestamp = connectivityObject.optLong(AppConstants.KEY_TIMESTAMP);
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

                        } else {
                            Log.e(TAG, "Response received : null");
                            listener.onFailure(new RuntimeException("Failed to get Node Details"));
                        }
                    } else {

                        String jsonErrResponse = response.errorBody().string();
                        Log.e(TAG, "Error Response : " + jsonErrResponse);

                        if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {

                            JSONObject jsonObject = new JSONObject(jsonErrResponse);
                            String err = jsonObject.optString(AppConstants.KEY_DESCRIPTION);

                            if (!TextUtils.isEmpty(err)) {
                                listener.onFailure(new CloudException(err));
                            } else {
                                listener.onFailure(new RuntimeException("Failed to get Node Details"));
                            }

                        } else {
                            listener.onFailure(new RuntimeException("Failed to get Node Details"));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    listener.onFailure(e);
                } catch (IOException e) {
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

    public void getNodeStatus(final String nodeId, final ApiResponseListener listener) {

        Log.d(TAG, "Get Node connectivity status for id : " + nodeId);

        apiInterface.getNodeStatus(AppConstants.URL_USER_NODE_STATUS, accessToken, nodeId).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Get Node status, Response code : " + response.code());

                try {
                    if (response.isSuccessful()) {

                        if (response.body() != null) {

                            String jsonResponse = response.body().string();
                            Log.d(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject nodeStatusJson = new JSONObject(jsonResponse);
                            EspNode espNode = espApp.nodeMap.get(nodeId);

                            if (espNode != null) {

                                // Node Status
                                JSONObject connectivityObject = nodeStatusJson.optJSONObject(AppConstants.KEY_CONNECTIVITY);

                                if (connectivityObject != null) {

                                    boolean nodeStatus = connectivityObject.optBoolean(AppConstants.KEY_CONNECTED);
                                    long timestamp = connectivityObject.optLong(AppConstants.KEY_TIMESTAMP);
                                    espNode.setTimeStampOfStatus(timestamp);

                                    if (espNode.isOnline() != nodeStatus) {
                                        espNode.setOnline(nodeStatus);
                                        EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
                                    }
                                } else {
                                    Log.e(TAG, "Connectivity object is null");
                                }
                            }

                            listener.onSuccess(null);

                        } else {
                            Log.e(TAG, "Response received : null");
                            listener.onFailure(new RuntimeException("Failed to get Node status"));
                        }
                    } else {

                        String jsonErrResponse = response.errorBody().string();
                        Log.e(TAG, "Error Response : " + jsonErrResponse);

                        if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {

                            JSONObject jsonObject = new JSONObject(jsonErrResponse);
                            String err = jsonObject.optString(AppConstants.KEY_DESCRIPTION);

                            if (!TextUtils.isEmpty(err)) {
                                listener.onFailure(new CloudException(err));
                            } else {
                                listener.onFailure(new RuntimeException("Failed to get Node status"));
                            }

                        } else {
                            listener.onFailure(new RuntimeException("Failed to get Node status"));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    listener.onFailure(e);
                } catch (IOException e) {
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

    /**
     * This method is used to send request for add device (Associate device with user).
     *
     * @param nodeId    Device Id.
     * @param secretKey Generated Secret Key.
     * @param listener  Listener to send success or failure.
     */
    public void addNode(final String nodeId, String secretKey,
                        final ApiResponseListener listener) {

        Log.d(TAG, "Add Node, nodeId : " + nodeId);

        DeviceOperationRequest req = new DeviceOperationRequest();
        req.setNodeId(nodeId);
        req.setSecretKey(secretKey);
        req.setOperation(AppConstants.KEY_OPERATION_ADD);

        apiInterface.addNode(AppConstants.URL_USER_NODE_MAPPING, accessToken, req).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Add Node, Response code : " + response.code());

                try {
                    if (response.isSuccessful()) {

                        if (response.body() != null) {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            String reqId = jsonObject.optString(AppConstants.KEY_REQ_ID);
                            requestIds.put(nodeId, reqId);
                            handler.post(getUserNodeMappingStatusTask);
                            Bundle data = new Bundle();
                            data.putString(AppConstants.KEY_REQ_ID, reqId);
                            listener.onSuccess(data);

                        } else {
                            listener.onFailure(new RuntimeException("Failed to add device"));
                        }

                    } else {

                        String jsonErrResponse = response.errorBody().string();
                        Log.e(TAG, "Error Response : " + jsonErrResponse);

                        if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {

                            JSONObject jsonObject = new JSONObject(jsonErrResponse);
                            String err = jsonObject.optString(AppConstants.KEY_DESCRIPTION);

                            if (!TextUtils.isEmpty(err)) {
                                listener.onFailure(new CloudException(err));
                            } else {
                                listener.onFailure(new RuntimeException("Failed to add device"));
                            }

                        } else {
                            listener.onFailure(new RuntimeException("Failed to add device"));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    listener.onFailure(e);
                } catch (IOException e) {
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
        req.setOperation(AppConstants.KEY_OPERATION_REMOVE);

        apiInterface.removeNode(AppConstants.URL_USER_NODE_MAPPING, accessToken, req).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Remove Node, response code : " + response.code());

                try {
                    if (response.isSuccessful()) {

                        if (response.body() != null) {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            listener.onSuccess(null);

                        } else {
                            listener.onFailure(new RuntimeException("Failed to delete this node."));
                        }

                    } else {

                        String jsonErrResponse = response.errorBody().string();
                        Log.e(TAG, "Error Response : " + jsonErrResponse);

                        if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {

                            JSONObject jsonObject = new JSONObject(jsonErrResponse);
                            String err = jsonObject.optString(AppConstants.KEY_DESCRIPTION);

                            if (!TextUtils.isEmpty(err)) {
                                listener.onFailure(new CloudException(err));
                            } else {
                                listener.onFailure(new RuntimeException("Failed to delete this node."));
                            }

                        } else {
                            listener.onFailure(new RuntimeException("Failed to delete this node."));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    listener.onFailure(e);
                } catch (IOException e) {
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

    public void getParamsValues(final String nodeId, final ApiResponseListener listener) {

        Log.d(TAG, "Get Param values for node : " + nodeId);

        apiInterface.getParamValue(AppConstants.URL_USER_NODES_PARAMS, accessToken, nodeId).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Get Params Values, Response code : " + response.code());

                try {

                    if (response.isSuccessful()) {

                        if (response.body() != null) {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            JSONObject scheduleJson = jsonObject.optJSONObject(AppConstants.KEY_SCHEDULE);

                            EspNode node = espApp.nodeMap.get(nodeId);

                            if (node != null) {

                                ArrayList<Device> devices = node.getDevices();

                                // Node Params
                                for (int i = 0; i < devices.size(); i++) {

                                    ArrayList<Param> params = devices.get(i).getParams();
                                    String deviceName = devices.get(i).getDeviceName();
                                    JSONObject deviceJson = jsonObject.optJSONObject(deviceName);

                                    if (deviceJson != null) {

                                        for (int j = 0; j < params.size(); j++) {

                                            Param param = params.get(j);
                                            String key = param.getName();

                                            if (deviceJson.has(key)) {
                                                JsonDataParser.setDeviceParamValue(deviceJson, devices.get(i), param);
                                            }
                                        }
                                    } else {
                                        Log.e(TAG, "Device JSON is null");
                                    }
                                }

                                // Schedules
                                if (scheduleJson != null) {

                                    JSONArray scheduleArrayJson = scheduleJson.optJSONArray(AppConstants.KEY_SCHEDULES);

                                    if (scheduleArrayJson != null) {

                                        if (espApp.scheduleMap == null) {
                                            espApp.scheduleMap = new HashMap<>();
                                        }

                                        for (int index = 0; index < scheduleArrayJson.length(); index++) {

                                            JSONObject schJson = scheduleArrayJson.getJSONObject(index);
                                            String scheduleId = schJson.optString(AppConstants.KEY_ID);
                                            String key = scheduleId;

                                            if (!TextUtils.isEmpty(scheduleId)) {

                                                String name = schJson.optString(AppConstants.KEY_NAME);
                                                key = key + "_" + name + "_" + schJson.optBoolean(AppConstants.KEY_ENABLED);

                                                HashMap<String, Integer> triggers = new HashMap<>();
                                                JSONArray triggerArray = schJson.optJSONArray(AppConstants.KEY_TRIGGERS);
                                                for (int t = 0; t < triggerArray.length(); t++) {
                                                    JSONObject triggerJson = triggerArray.optJSONObject(t);
                                                    int days = triggerJson.optInt(AppConstants.KEY_DAYS);
                                                    int mins = triggerJson.optInt(AppConstants.KEY_MINUTES);
                                                    triggers.put(AppConstants.KEY_DAYS, days);
                                                    triggers.put(AppConstants.KEY_MINUTES, mins);
                                                    key = key + "_" + days + "_" + mins;
                                                }

                                                Schedule schedule = espApp.scheduleMap.get(key);
                                                if (schedule == null) {
                                                    schedule = new Schedule();
                                                }

                                                schedule.setId(scheduleId);
                                                schedule.setName(schJson.optString(AppConstants.KEY_NAME));
                                                schedule.setEnabled(schJson.optBoolean(AppConstants.KEY_ENABLED));
                                                schedule.setTriggers(triggers);

                                                // Actions
                                                JSONObject actionsSchJson = schJson.optJSONObject(AppConstants.KEY_ACTION);

                                                if (actionsSchJson != null) {

                                                    ArrayList<Action> actions = schedule.getActions();
                                                    if (actions == null) {
                                                        actions = new ArrayList<>();
                                                        schedule.setActions(actions);
                                                    }

                                                    for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {

                                                        Device d = new Device(devices.get(deviceIndex));
                                                        ArrayList<Param> params = d.getParams();
                                                        String deviceName = d.getDeviceName();
                                                        JSONObject deviceAction = actionsSchJson.optJSONObject(deviceName);

                                                        if (deviceAction != null) {

                                                            Action action = null;
                                                            Device actionDevice = null;
                                                            int actionIndex = -1;

                                                            for (int aIndex = 0; aIndex < actions.size(); aIndex++) {

                                                                Action a = actions.get(aIndex);
                                                                if (a.getDevice().getNodeId().equals(nodeId) && deviceName.equals(a.getDevice().getDeviceName())) {
                                                                    action = actions.get(aIndex);
                                                                    actionIndex = aIndex;
                                                                }
                                                            }

                                                            if (action == null) {
                                                                action = new Action();
                                                                action.setNodeId(nodeId);

                                                                for (int k = 0; k < devices.size(); k++) {

                                                                    if (devices.get(k).getNodeId().equals(nodeId) && devices.get(k).getDeviceName().equals(deviceName)) {
                                                                        actionDevice = new Device(devices.get(k));
                                                                        actionDevice.setSelectedState(1);
                                                                        break;
                                                                    }
                                                                }

                                                                if (actionDevice == null) {
                                                                    actionDevice = new Device(nodeId);
                                                                }
                                                                action.setDevice(actionDevice);
                                                            } else {
                                                                actionDevice = action.getDevice();
                                                            }

                                                            ArrayList<Param> actionParams = new ArrayList<>();
                                                            if (params != null) {

                                                                Iterator<Param> iterator = params.iterator();
                                                                while (iterator.hasNext()) {
                                                                    Param p = iterator.next();
                                                                    actionParams.add(new Param(p));
                                                                }

                                                                Iterator itr = actionParams.iterator();

                                                                while (itr.hasNext()) {

                                                                    Param p = (Param) itr.next();

                                                                    if (!p.isDynamicParam()) {
                                                                        itr.remove();
                                                                    } else if (p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                                                                        itr.remove();
                                                                    } else if (!p.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {
                                                                        itr.remove();
                                                                    }
                                                                }
                                                            }
                                                            actionDevice.setParams(actionParams);

                                                            for (int paramIndex = 0; paramIndex < actionParams.size(); paramIndex++) {

                                                                Param p = actionParams.get(paramIndex);
                                                                String paramName = p.getName();

                                                                if (deviceAction.has(paramName)) {

                                                                    p.setSelected(true);
                                                                    JsonDataParser.setDeviceParamValue(deviceAction, devices.get(deviceIndex), p);
                                                                }
                                                            }

                                                            for (int paramIndex = 0; paramIndex < actionParams.size(); paramIndex++) {

                                                                if (!actionParams.get(paramIndex).isSelected()) {
                                                                    actionDevice.setSelectedState(2); // Partially selected
                                                                }
                                                            }

                                                            if (actionIndex == -1) {
                                                                actions.add(action);
                                                            } else {
                                                                actions.set(actionIndex, action);
                                                            }
                                                            schedule.setActions(actions);

                                                        }
                                                    }
                                                }
                                                espApp.scheduleMap.put(key, schedule);
                                            }
                                        }
                                    }
                                } else {
                                    Log.d(TAG, "Schedule JSON is null");
                                }
                            }
                            listener.onSuccess(null);

                        } else {
                            listener.onFailure(new RuntimeException("Failed to get param values"));
                        }

                    } else {

                        String jsonErrResponse = response.errorBody().string();
                        Log.e(TAG, "Error Response : " + jsonErrResponse);

                        if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {

                            JSONObject jsonObject = new JSONObject(jsonErrResponse);
                            String err = jsonObject.optString(AppConstants.KEY_DESCRIPTION);

                            if (!TextUtils.isEmpty(err)) {
                                listener.onFailure(new CloudException(err));
                            } else {
                                listener.onFailure(new RuntimeException("Failed to get param values"));
                            }

                        } else {
                            listener.onFailure(new RuntimeException("Failed to get param values"));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    listener.onFailure(e);
                } catch (IOException e) {
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

    public void updateParamValue(final String nodeId, JsonObject body, final ApiResponseListener listener) {

        Log.d(TAG, "Updating param value");

        apiInterface.updateParamValue(AppConstants.URL_USER_NODES_PARAMS, accessToken, nodeId, body).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Update Params Value, Response code : " + response.code());

                try {

                    if (response.isSuccessful()) {

                        if (response.body() != null) {

                            String jsonResponse = response.body().string();
                            Log.d(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            listener.onSuccess(null);

                        } else {
                            listener.onFailure(new RuntimeException("Failed to update param value"));
                        }

                    } else {

                        String jsonErrResponse = response.errorBody().string();
                        Log.e(TAG, "Error Response : " + jsonErrResponse);

                        if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {

                            JSONObject jsonObject = new JSONObject(jsonErrResponse);
                            String err = jsonObject.optString(AppConstants.KEY_DESCRIPTION);

                            if (!TextUtils.isEmpty(err)) {
                                listener.onFailure(new CloudException(err));
                            } else {
                                listener.onFailure(new RuntimeException("Failed to update param value"));
                            }

                        } else {
                            listener.onFailure(new RuntimeException("Failed to update param value"));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    listener.onFailure(e);
                } catch (IOException e) {
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

    /**
     * This method is used to add , update or remove schedule.
     *
     * @param map      Map of node id and its schedule JSON data.
     * @param listener Listener to send success or failure.
     */
    @SuppressLint("CheckResult")
    public void updateSchedules(final HashMap<String, JsonObject> map,
                                final ApiResponseListener listener) {

        Log.d(TAG, "Updating Schedule");
        List<Observable<ApiResponse>> requests = new ArrayList<>();
        final ArrayList<ApiResponse> responses = new ArrayList<>();

        for (Map.Entry<String, JsonObject> entry : map.entrySet()) {

            final String nodeId = entry.getKey();
            JsonObject jsonBody = entry.getValue();

            requests.add(
                    apiInterface.updateSchedules(AppConstants.URL_USER_NODES_PARAMS, accessToken, nodeId, jsonBody)

                            .map(new Function<ResponseBody, ApiResponse>() {

                                @Override
                                public ApiResponse apply(ResponseBody responseBody) throws Exception {

                                    ApiResponse apiResponse = new ApiResponse();
                                    apiResponse.responseBody = responseBody;
                                    apiResponse.isSuccessful = true;
                                    apiResponse.nodeId = nodeId;
                                    return apiResponse;
                                }
                            })
                            .onErrorReturn(new Function<Throwable, ApiResponse>() {

                                @Override
                                public ApiResponse apply(Throwable throwable) throws Exception {

                                    ApiResponse apiResponse = new ApiResponse();
                                    apiResponse.isSuccessful = false;
                                    apiResponse.throwable = throwable;
                                    apiResponse.nodeId = nodeId;
                                    return apiResponse;
                                }
                            }));
        }

        Observable.merge(requests)
                .take(requests.size())
                .doFinally(new io.reactivex.functions.Action() {

                    @Override
                    public void run() throws Exception {

                        Log.d(TAG, "Update schedule requests completed.");
                        boolean isAllReqSuccessful = true;

                        for (int i = 0; i < responses.size(); i++) {

                            if (!responses.get(i).isSuccessful) {
                                isAllReqSuccessful = false;
                                break;
                            }
                        }

                        if (isAllReqSuccessful) {
                            listener.onSuccess(null);
                        } else {
                            listener.onFailure(new RuntimeException("Failed to update schedule for few devices"));
                        }
                    }
                })
                .subscribeOn(Schedulers.io())
                .subscribe(new Consumer<ApiResponse>() {

                    @Override
                    public void accept(ApiResponse apiResponse) throws Exception {

                        Log.d(TAG, "Response : " + apiResponse.nodeId);
                        Log.d(TAG, "Response : " + apiResponse.isSuccessful);
                        responses.add(apiResponse);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.e("onSubscribe", "Throwable: " + throwable);
                    }
                });
    }

    private void getAddNodeRequestStatus(final String nodeId, String requestId) {

        Log.d(TAG, "Get Node mapping status");

        apiInterface.getAddNodeRequestStatus(AppConstants.URL_USER_NODE_MAPPING, accessToken, requestId, true).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Get Node mapping status, Response code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {
                        try {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            String reqStatus = jsonObject.optString(AppConstants.KEY_REQ_STATUS);

                            if (!TextUtils.isEmpty(reqStatus) && reqStatus.equals(AppConstants.KEY_REQ_CONFIRMED)) {

                                requestIds.remove(nodeId);

                                // Send event for update UI
                                if (requestIds.size() == 0) {
                                    EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_DEVICE_ADDED));
                                }
                            } else if (!TextUtils.isEmpty(reqStatus) && reqStatus.equals(AppConstants.KEY_REQ_TIMEDOUT)) {

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

    private void getUserNodeMappingStatus() {

        handler.postDelayed(getUserNodeMappingStatusTask, REQ_STATUS_TIME);
    }

    private Runnable getUserNodeMappingStatusTask = new Runnable() {

        @Override
        public void run() {

            if (requestIds.size() > 0) {

                for (String key : requestIds.keySet()) {

                    String nodeId = key;
                    String requestId = requestIds.get(nodeId);
                    getAddNodeRequestStatus(nodeId, requestId);
                }
                getUserNodeMappingStatus();
            } else {
                Log.i(TAG, "No request id is available to check status");
                handler.removeCallbacks(getUserNodeMappingStatusTask);
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
        Log.e(TAG, "Token expires At : " + expiresAt);

        if (currentTIme.after(expiresAt)) {
            Log.e(TAG, "Token has expired");
            return true;
        } else {
            Log.d(TAG, "Token has not expired");
            return false;
        }
    }

    public void getNewToken(final ApiResponseListener listener) {

        if (isOAuthLogin) {

            getNewTokenForOAuth(listener);

        } else {

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
                    listener.onSuccess(null);
                }

                @Override
                public void getAuthenticationDetails(AuthenticationContinuation authenticationContinuation, String userId) {
                    Log.d(TAG, "getAuthenticationDetails " + userId);
                    Locale.setDefault(Locale.US);
                    getUserAuthentication(authenticationContinuation, userName);
                }

                @Override
                public void getMFACode(MultiFactorAuthenticationContinuation continuation) {
                    Log.d(TAG, "getMFACode ");
                }

                @Override
                public void authenticationChallenge(ChallengeContinuation continuation) {
                    // Nothing to do for this app.
                    /*
                     * For Custom authentication challenge, implement your logic to present challenge to the
                     * user and pass the user's responses to the continuation.
                     */
                    Log.d(TAG, "authenticationChallenge : " + continuation.getChallengeName());
                }

                @Override
                public void onFailure(Exception exception) {
                    Log.e(TAG, "onFailure ");
                    exception.printStackTrace();
                    listener.onFailure(exception);
                }
            };

            AppHelper.getPool().getUser(userName).getSessionInBackground(authenticationHandler);
        }
    }

    private void getUserAuthentication(AuthenticationContinuation continuation, String username) {

        Log.d(TAG, "getUserAuthentication");
        if (username != null) {
            userName = username;
            AppHelper.setUser(username);
        }

        AuthenticationDetails authenticationDetails = new AuthenticationDetails(userName, "", null);
        continuation.setAuthenticationDetails(authenticationDetails);
        continuation.continueTask();
    }

    public void getNewTokenForOAuth(final ApiResponseListener listener) {

        Log.d(TAG, "Get New Token For OAuth");
        HashMap<String, String> body = new HashMap<>();
        body.put("user_name", userId);
        body.put("refreshtoken", refreshToken);

        apiInterface.getOAuthLoginToken(AppConstants.URL_OAUTH_LOGIN, body).enqueue(new Callback<ResponseBody>() {

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

                        String jsonErrResponse = response.errorBody().string();
                        Log.e(TAG, "Error Response : " + jsonErrResponse);

                        if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {

                            JSONObject jsonObject = new JSONObject(jsonErrResponse);
                            String err = jsonObject.optString(AppConstants.KEY_DESCRIPTION);

                            if (!TextUtils.isEmpty(err)) {
                                listener.onFailure(new CloudException(err));
                            } else {
                                listener.onFailure(new RuntimeException("Failed to get new token"));
                            }

                        } else {
                            listener.onFailure(new RuntimeException("Failed to get new token"));
                        }
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

        apiInterface.initiateClaiming(AppConstants.URL_CLAIM_INITIATE, accessToken, body).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "onResponse code  : " + response.code());

                try {
                    if (response.isSuccessful()) {

                        String jsonResponse = response.body().string();
                        Bundle data = new Bundle();
                        data.putString(AppConstants.KEY_CLAIM_INIT_RESPONSE, jsonResponse);
                        listener.onSuccess(data);

                    } else {

                        String jsonErrResponse = response.errorBody().string();
                        Log.e(TAG, "Error Response : " + jsonErrResponse);

                        if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {

                            JSONObject jsonObject = new JSONObject(jsonErrResponse);
                            String err = jsonObject.optString(AppConstants.KEY_DESCRIPTION);

                            if (!TextUtils.isEmpty(err)) {
                                listener.onFailure(new CloudException(err));
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

        apiInterface.verifyClaiming(AppConstants.URL_CLAIM_VERIFY, accessToken, body).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Response code  : " + response.code());

                try {
                    if (response.isSuccessful()) {

                        String jsonResponse = response.body().string();
                        Bundle data = new Bundle();
                        data.putString(AppConstants.KEY_CLAIM_VERIFY_RESPONSE, jsonResponse);
                        listener.onSuccess(data);

                    } else {

                        String jsonErrResponse = response.errorBody().string();
                        Log.e(TAG, "Error Response : " + jsonErrResponse);

                        if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {

                            JSONObject jsonObject = new JSONObject(jsonErrResponse);
                            String err = jsonObject.optString(AppConstants.KEY_DESCRIPTION);

                            if (!TextUtils.isEmpty(err)) {
                                listener.onFailure(new CloudException(err));
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
            handler.removeCallbacks(getUserNodeMappingStatusTask);
            EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_ADD_DEVICE_TIME_OUT));
        }
    };

    public void cancelRequestStatusPollingTask() {

        handler.removeCallbacks(stopRequestStatusPollingTask);
    }
}
