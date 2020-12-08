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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.espressif.AppConstants;
import com.espressif.ui.user_module.AppHelper;
import com.espressif.ui.theme_manager.WindowThemeManager;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = SplashActivity.class.getSimpleName();

    private String email;
    private String accessToken;

    private Handler handler;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowThemeManager WindowTheme = new WindowThemeManager(this, true);
        WindowTheme.applyWindowTheme(getWindow());
        super.onCreate(savedInstanceState);


        handler = new Handler();
        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        email = sharedPreferences.getString(AppConstants.KEY_EMAIL, "");
        accessToken = sharedPreferences.getString(AppConstants.KEY_ACCESS_TOKEN, "");

        Log.d(TAG, "Email : " + email);

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(accessToken)) {

            launchLoginScreen();

        } else {

            AppHelper.setUser(email);
            launchHomeScreen();
        }
    }

    public void launchHomeScreen() {

        Intent espMainActivity = new Intent(getApplicationContext(), EspMainActivity.class);
        startActivity(espMainActivity);
        finish();
    }

    public void launchLoginScreen() {

        Intent espMainActivity = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(espMainActivity);
        finish();
    }
}
