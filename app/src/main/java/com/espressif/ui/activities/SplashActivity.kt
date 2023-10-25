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

package com.espressif.ui.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log

import com.espressif.AppConstants
import com.espressif.EspApplication
import com.espressif.rainmaker.databinding.ActivitySplashBinding

class SplashActivity : Activity() {

    companion object {
        private const val TAG = "SplashActivity"
    }

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, MODE_PRIVATE)
        val email = sharedPreferences.getString(AppConstants.KEY_EMAIL, "")
        val accessToken = sharedPreferences.getString(AppConstants.KEY_ACCESS_TOKEN, "")
        Log.d(TAG, "Username : $email")

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(accessToken)) {
            // Launch startup screen
            launchStartupScreen()
        } else {
            // Launch home screen
            launchHomeScreen()
        }
    }

    private fun launchStartupScreen() {
        Intent(applicationContext, ConsentActivity::class.java).also {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(it)
        }
        finish()
    }

    private fun launchHomeScreen() {
        (applicationContext as EspApplication).changeAppState(
            EspApplication.AppState.GETTING_DATA,
            null
        )
        val espMainActivity = Intent(applicationContext, EspMainActivity::class.java)
        val reqId = intent.getStringExtra(AppConstants.KEY_REQ_ID)
        if (!TextUtils.isEmpty(reqId)) {
            Log.d(TAG, "Request id is available")
            espMainActivity.putExtra(AppConstants.KEY_REQ_ID, reqId)
            espMainActivity.putExtra(
                AppConstants.KEY_ID,
                intent.getIntExtra(AppConstants.KEY_ID, -1)
            )
            Log.d(TAG, "Request id : $reqId")
        }
        espMainActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(espMainActivity)
        finish()
    }
}