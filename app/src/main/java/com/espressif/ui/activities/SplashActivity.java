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

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoDevice;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ChallengeContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.MultiFactorAuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.AuthenticationHandler;
import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.rainmaker.R;
import com.espressif.ui.user_module.AppHelper;

import java.util.Locale;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = SplashActivity.class.getSimpleName();

    private String email;
    private String accessToken;
    private boolean isOAuthLogin;

    private Handler handler;
    private ApiManager apiManager;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        handler = new Handler();
        apiManager = ApiManager.getInstance(getApplicationContext());
        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        email = sharedPreferences.getString(AppConstants.KEY_EMAIL, "");
        accessToken = sharedPreferences.getString(AppConstants.KEY_ACCESS_TOKEN, "");
        isOAuthLogin = sharedPreferences.getBoolean(AppConstants.KEY_IS_OAUTH_LOGIN, false);
        AppHelper.init(getApplicationContext());

        Log.d(TAG, "Email : " + email);

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(accessToken)) {

            handler.postDelayed(launchLoginScreenTask, 1500);

        } else {

            boolean isTokenExpired = apiManager.isTokenExpired();

            if (isTokenExpired) {

                if (isOAuthLogin) {

                    apiManager.getTokenAndUserId();
                    apiManager.getNewTokenForOAuth(new ApiResponseListener() {

                        @Override
                        public void onSuccess(Bundle data) {
                            handler.postDelayed(launchHomeScreenTask, 1500);
                        }

                        @Override
                        public void onFailure(Exception exception) {
                            handler.postDelayed(launchLoginScreenTask, 500);
                        }
                    });

                } else {

                    AppHelper.setUser(email);
                    AppHelper.getPool().getUser(email).getSessionInBackground(authenticationHandler);
                }

            } else {

                AppHelper.setUser(email);
                apiManager.getTokenAndUserId();
                handler.postDelayed(launchHomeScreenTask, 1500);
            }
        }
    }

    public void launchHomeScreen() {

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

    private Runnable launchLoginScreenTask = new Runnable() {

        @Override
        public void run() {
            launchLoginScreen();
        }
    };

    private Runnable launchHomeScreenTask = new Runnable() {

        @Override
        public void run() {
            launchHomeScreen();
        }
    };

    AuthenticationHandler authenticationHandler = new AuthenticationHandler() {

        @Override
        public void onSuccess(CognitoUserSession cognitoUserSession, CognitoDevice device) {

            Log.d(TAG, " -- Auth Success");
            AppHelper.setCurrSession(cognitoUserSession);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(AppConstants.KEY_EMAIL, email);
            editor.putString(AppConstants.KEY_ID_TOKEN, cognitoUserSession.getIdToken().getJWTToken());
            editor.putString(AppConstants.KEY_ACCESS_TOKEN, cognitoUserSession.getAccessToken().getJWTToken());
            editor.putString(AppConstants.KEY_REFRESH_TOKEN, cognitoUserSession.getRefreshToken().getToken());
            editor.putBoolean(AppConstants.KEY_IS_OAUTH_LOGIN, false);
            editor.apply();

            AppHelper.newDevice(device);
            apiManager.getTokenAndUserId();
            launchHomeScreen();
        }

        @Override
        public void getAuthenticationDetails(AuthenticationContinuation authenticationContinuation, String username) {

            Log.d(TAG, "getAuthenticationDetails");
            Locale.setDefault(Locale.US);
            getUserAuthentication(authenticationContinuation, username);
        }

        @Override
        public void getMFACode(MultiFactorAuthenticationContinuation multiFactorAuthenticationContinuation) {
            // Nothing to do here
            Log.d(TAG, "getMFACode");
        }

        @Override
        public void onFailure(Exception e) {

            Log.e(TAG, "onFailure");
            e.printStackTrace();
            launchLoginScreen();
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
    };

    private void getUserAuthentication(AuthenticationContinuation continuation, String username) {

        Log.d(TAG, "getUserAuthentication");
        if (username != null) {
            email = username;
            AppHelper.setUser(username);
        }

        AuthenticationDetails authenticationDetails = new AuthenticationDetails(email, "", null);
        continuation.setAuthenticationDetails(authenticationDetails);
        continuation.continueTask();
    }
}
