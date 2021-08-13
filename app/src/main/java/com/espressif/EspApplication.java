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

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.db.EspDatabase;
import com.espressif.mdns.mDNSApiManager;
import com.espressif.mdns.mDNSDevice;
import com.espressif.mdns.mDNSManager;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.ui.activities.MainActivity;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Group;
import com.espressif.ui.models.Schedule;
import com.espressif.ui.models.UpdateEvent;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class EspApplication extends Application {

    private static final String TAG = EspApplication.class.getSimpleName();

    private AppState appState = AppState.NO_USER_LOGIN;

    public HashMap<String, EspNode> nodeMap;
    public HashMap<String, Schedule> scheduleMap;
    public HashMap<String, mDNSDevice> mDNSDeviceMap;
    public HashMap<String, Group> groupMap;

    private SharedPreferences appPreferences;
    private ApiManager apiManager;
    private mDNSManager mdnsManager;

    public enum AppState {
        NO_USER_LOGIN,
        GETTING_DATA,
        GET_DATA_SUCCESS,
        GET_DATA_FAILED,
        NO_INTERNET,
        REFRESH_DATA
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ESP Application is created");
        nodeMap = new HashMap<>();
        scheduleMap = new HashMap<>();
        mDNSDeviceMap = new HashMap<>();
        groupMap = new HashMap<>();
        appPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        apiManager = ApiManager.getInstance(this);
        ESPProvisionManager.getInstance(this);
        if (BuildConfig.isLocalControlSupported) {
            mdnsManager = mDNSManager.getInstance(getApplicationContext(), AppConstants.MDNS_SERVICE_TYPE, listener);
        }
    }

    public AppState getAppState() {
        return appState;
    }

    public void changeAppState(AppState newState, Bundle extras) {

        switch (newState) {
            case GETTING_DATA:
                if (BuildConfig.isLocalControlSupported) {
                    mdnsManager.initializeNsd();
                }
            case REFRESH_DATA:
                if (!appState.equals(newState)) {
                    appState = newState;
                    getNodesFromCloud();
                }
                EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_STATE_CHANGE_UPDATE));
                break;

            case GET_DATA_FAILED:
                appState = newState;
                UpdateEvent updateEvent = new UpdateEvent(AppConstants.UpdateEventType.EVENT_STATE_CHANGE_UPDATE);
                if (extras != null) {
                    updateEvent.setData(extras);
                }
                EventBus.getDefault().post(updateEvent);
                startLocalDeviceDiscovery();
                break;

            case NO_USER_LOGIN:
                Intent loginActivity = new Intent(this, MainActivity.class);
                loginActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(loginActivity);
                appState = newState;
                break;

            case GET_DATA_SUCCESS:
            case NO_INTERNET:
                appState = newState;
                EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_STATE_CHANGE_UPDATE));
                startLocalDeviceDiscovery();
                break;
        }
    }

    private void getNodesFromCloud() {

        apiManager.getNodes(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                if (BuildConfig.isNodeGroupingSupported) {

                    apiManager.getUserGroups(null, new ApiResponseListener() {

                        @Override
                        public void onSuccess(Bundle data) {
                            changeAppState(AppState.GET_DATA_SUCCESS, null);
                        }

                        @Override
                        public void onResponseFailure(Exception exception) {
                            Bundle data = new Bundle();
                            data.putString(AppConstants.KEY_ERROR_MSG, exception.getMessage());
                            changeAppState(EspApplication.AppState.GET_DATA_FAILED, data);
                        }

                        @Override
                        public void onNetworkFailure(Exception exception) {
                            changeAppState(AppState.NO_INTERNET, null);
                        }
                    });
                } else {
                    changeAppState(AppState.GET_DATA_SUCCESS, null);
                }
            }

            @Override
            public void onResponseFailure(Exception exception) {
                Bundle data = new Bundle();
                data.putString(AppConstants.KEY_ERROR_MSG, exception.getMessage());
                changeAppState(EspApplication.AppState.GET_DATA_FAILED, data);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                changeAppState(AppState.NO_INTERNET, null);
            }
        });
    }

    public void refreshData() {
        if (!appState.equals(AppState.GETTING_DATA)) {
            changeAppState(AppState.REFRESH_DATA, null);
        }
    }

    public void loginSuccess() {
        EspDatabase.getInstance(this).getNodeDao().deleteAll();
        EspDatabase.getInstance(this).getGroupDao().deleteAll();
        nodeMap.clear();
        scheduleMap.clear();
        mDNSDeviceMap.clear();
        groupMap.clear();
    }

    public void logout() {
        // Do logout and clear all data
        if (!ApiManager.isOAuthLogin) {

            apiManager.logout(new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {
                }

                @Override
                public void onResponseFailure(Exception exception) {
                    // Ignore failure
                }

                @Override
                public void onNetworkFailure(Exception exception) {
                    // Ignore failure
                }
            });
        }

        EspDatabase.getInstance(this).getNodeDao().deleteAll();
        EspDatabase.getInstance(this).getGroupDao().deleteAll();
        nodeMap.clear();
        scheduleMap.clear();
        mDNSDeviceMap.clear();
        groupMap.clear();

        SharedPreferences.Editor editor = appPreferences.edit();
        editor.clear();
        editor.apply();

        SharedPreferences wifiNetworkPref = getSharedPreferences(AppConstants.PREF_FILE_WIFI_NETWORKS, Context.MODE_PRIVATE);
        SharedPreferences.Editor wifiNetworkEditor = wifiNetworkPref.edit();
        wifiNetworkEditor.clear();
        wifiNetworkEditor.apply();
        Log.e(TAG, "Deleted all things from local storage.");
        changeAppState(AppState.NO_USER_LOGIN, null);
    }

    private void startLocalDeviceDiscovery() {
        if (BuildConfig.isLocalControlSupported) {
            if (nodeMap.size() > 0) {
                mdnsManager.discoverServices();
            }
        }
    }

    public void stopLocalDeviceDiscovery() {
        if (BuildConfig.isLocalControlSupported) {
            mdnsManager.stopDiscovery();
        }
    }

    mDNSManager.mDNSEvenListener listener = new mDNSManager.mDNSEvenListener() {

        @Override
        public void deviceFound(final mDNSDevice dnsDevice) {

            Log.e(TAG, "Device Found on Local Network");
            final String url = "http://" + dnsDevice.getIpAddr() + ":" + dnsDevice.getPort();
            final mDNSApiManager dnsMsgHelper = new mDNSApiManager(getApplicationContext());

            dnsMsgHelper.getPropertyCount(url, AppConstants.LOCAL_CONTROL_PATH, new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {

                    if (data != null) {

                        int count = data.getInt(AppConstants.KEY_PROPERTY_COUNT, 0);
                        dnsDevice.setPropertyCount(count);

                        dnsMsgHelper.getPropertyValues(url, AppConstants.LOCAL_CONTROL_PATH, count, new ApiResponseListener() {

                            @Override
                            public void onSuccess(Bundle data) {

                                if (data != null) {

                                    String configData = data.getString(AppConstants.KEY_CONFIG);
                                    String paramsData = data.getString(AppConstants.KEY_PARAMS);

                                    Log.d(TAG, "Config data : " + configData);
                                    Log.d(TAG, "Params data : " + paramsData);

                                    if (!TextUtils.isEmpty(configData)) {

                                        JSONObject configJson = null;
                                        try {
                                            configJson = new JSONObject(configData);
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }

                                        String nodeId = configJson.optString(AppConstants.KEY_NODE_ID);
                                        EspNode node = nodeMap.get(nodeId);
                                        boolean isDeviceFound = false;
                                        if (node != null) {
                                            isDeviceFound = true;
                                        }
                                        EspNode localNode = JsonDataParser.setNodeConfig(node, configJson);

                                        if (node != null) {
                                            Log.e(TAG, "Found node " + localNode.getNodeId() + " on local network.");
                                            isDeviceFound = true;
                                            localNode.setAvailableLocally(true);
                                            localNode.setIpAddress(dnsDevice.getIpAddr());
                                            localNode.setPort(dnsDevice.getPort());
                                            localNode.setOnline(true);
                                            mDNSDeviceMap.put(localNode.getNodeId(), dnsDevice);
                                        }

                                        if (!TextUtils.isEmpty(paramsData) && isDeviceFound) {

                                            JSONObject paramsJson = null;
                                            try {
                                                paramsJson = new JSONObject(paramsData);
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                            JsonDataParser.setAllParams(EspApplication.this, localNode, paramsJson);
                                            nodeMap.put(localNode.getNodeId(), localNode);
                                            EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_LOCAL_DEVICE_UPDATE));
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onResponseFailure(Exception exception) {
                                // Nothing to do
                            }

                            @Override
                            public void onNetworkFailure(Exception exception) {
                                // Nothing to do
                            }
                        });
                    }
                }

                @Override
                public void onResponseFailure(Exception exception) {
                    // Nothing to do
                }

                @Override
                public void onNetworkFailure(Exception exception) {
                    // Nothing to do
                }
            });
        }
    };
}
