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
import android.util.Log;

import com.espressif.cloudapi.ApiManager;
import com.espressif.mdns.mDNSDevice;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Schedule;
import com.espressif.ui.theme_manager.AppThemeManager;
import com.espressif.ui.user_module.AppHelper;

import java.util.HashMap;

public class EspApplication extends Application {

    private static final String TAG = EspApplication.class.getSimpleName();

    private GetDataStatus currentStatus = GetDataStatus.FETCHING_DATA;

    public HashMap<String, EspNode> nodeMap;
    public HashMap<String, Schedule> scheduleMap;
    public HashMap<String, mDNSDevice> mDNSDeviceMap;

    public enum GetDataStatus {
        FETCHING_DATA,
        GET_DATA_SUCCESS,
        GET_DATA_FAILED,
        DATA_REFRESHING
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppThemeManager AppTheme = new AppThemeManager(this);
        AppTheme.getAndApplyTheme();
        Log.d(TAG, "ESP Application is created");
        AppHelper.init(this);
        ApiManager.getInstance(this);
        ESPProvisionManager.getInstance(this);
        nodeMap = new HashMap<>();
        scheduleMap = new HashMap<>();
        mDNSDeviceMap = new HashMap<>();
    }

    public GetDataStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(GetDataStatus currentStatus) {
        this.currentStatus = currentStatus;
    }
}
