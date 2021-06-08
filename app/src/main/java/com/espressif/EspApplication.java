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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.db.EspDatabase;
import com.espressif.mdns.mDNSDevice;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Group;
import com.espressif.ui.models.Schedule;
import com.espressif.ui.models.UpdateEvent;
import com.espressif.ui.user_module.AppHelper;

import org.greenrobot.eventbus.EventBus;

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
        AppHelper.init(this);
        apiManager = ApiManager.getInstance(this);
        ESPProvisionManager.getInstance(this);
    }

    public AppState getAppState() {
        return appState;
    }

    public void changeAppState(AppState newState, Bundle extras) {

        switch (newState) {
            case GETTING_DATA:
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
                break;

            case NO_USER_LOGIN:
            case GET_DATA_SUCCESS:
            case NO_INTERNET:
                appState = newState;
                EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_STATE_CHANGE_UPDATE));
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

    public void logout() {
        // Do logout and clear all data
        if (!ApiManager.isOAuthLogin) {
            String username = AppHelper.getCurrUser();
            CognitoUser user = AppHelper.getPool().getUser(username);
            user.signOut();
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
}
