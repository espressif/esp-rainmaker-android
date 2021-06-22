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

package com.espressif.ui.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.rainmaker.R;

public class SplashActivity extends Activity {

    private static final String TAG = SplashActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        SharedPreferences sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        String email = sharedPreferences.getString(AppConstants.KEY_EMAIL, "");
        String accessToken = sharedPreferences.getString(AppConstants.KEY_ACCESS_TOKEN, "");

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(accessToken)) {
            Log.d(TAG, "Email : " + email);
            launchLoginScreen();
        } else {
            launchHomeScreen();
        }
    }

    public void launchHomeScreen() {
        ((EspApplication) getApplicationContext()).changeAppState(EspApplication.AppState.GETTING_DATA, null);
        Intent espMainActivity = new Intent(getApplicationContext(), EspMainActivity.class);
        espMainActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(espMainActivity);
        finish();
    }

    public void launchLoginScreen() {
        Intent espMainActivity = new Intent(getApplicationContext(), MainActivity.class);
        espMainActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(espMainActivity);
        finish();
    }
}