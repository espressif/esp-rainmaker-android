// Copyright 2021 Espressif Systems (Shanghai) PTE LTD
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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.espressif.AlexaLinkingWorker;
import com.espressif.AppConstants;
import com.espressif.cloudapi.AlexaApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.rainmaker.databinding.ActivityAlexaAppLinkingBinding;

public class AlexaAppLinkingActivity extends AppCompatActivity {

    private static final String TAG = "AlexaAppLinking";
    private static final long REQUIRED_MINIMUM_VERSION_CODE = 866607211;

    private static String alexaCode = "", alexaAuthCode = "";
    private AlexaApiManager apiManager;
    private boolean isLinked = false;
    private boolean shouldGetAuthCode = false;
    private boolean isReturnedFromBrowser = false;
    private boolean isAppLinkingStartedFromAlexa = false;

    private AppLinkingProgress appLinkingProgress = AppLinkingProgress.NONE;

    enum AppLinkingProgress {
        NONE,
        GET_ALEXA_CODE,
        GET_ALEXA_TOKEN,
        GET_LINKING_STATUS,
        GET_AUTH_CODE,
        ENABLING_SKILL,
        DISABLING_SKILL
    }

    private ActivityAlexaAppLinkingBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAlexaAppLinkingBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        apiManager = AlexaApiManager.getInstance(getApplicationContext());
        SharedPreferences sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);

        if (getIntent().getData() != null) {
            isAppLinkingStartedFromAlexa = true;
        }

        String alexaRefreshToken = sharedPreferences.getString(AppConstants.KEY_ALEXA_REFRESH_TOKEN, "");

        initViews();
        updateUi();

        if (!TextUtils.isEmpty(alexaRefreshToken) && !isAppLinkingStartedFromAlexa) {

            Log.d(TAG, "Get Linking Status....");
            appLinkingProgress = AppLinkingProgress.GET_ALEXA_TOKEN;
            showLoading("Getting linking status...");

            apiManager.getNewToken(new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {
                    try {
                        appLinkingProgress = AppLinkingProgress.GET_LINKING_STATUS;
                        Data inoutData = new Data.Builder()
                                .putString(AppConstants.KEY_EVENT_TYPE, AppConstants.EVENT_GET_STATUS)
                                .build();

                        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(AlexaLinkingWorker.class)
                                .setInputData(inoutData)
                                .build();

                        WorkManager workManager = WorkManager.getInstance(getApplicationContext());
                        workManager.enqueue(workRequest);
                        workManager.getWorkInfoByIdLiveData(workRequest.getId())
                                .observe(AlexaAppLinkingActivity.this, new Observer<WorkInfo>() {

                                    @Override
                                    public void onChanged(@Nullable WorkInfo workInfo) {

                                        if (workInfo != null) {
                                            Log.i("AppLinkingWorkRequest", "Status changed to : " + workInfo.getState());

                                            if (workInfo.getState().equals(WorkInfo.State.SUCCEEDED)) {
                                                hideLoading();
                                                isLinked = true;
                                                updateUi();
                                            }
                                            if (workInfo.getState().equals(WorkInfo.State.FAILED)) {
                                                hideLoading();
                                                isLinked = false;
                                                updateUi();
                                            }
                                        }
                                    }
                                });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onResponseFailure(Exception exception) {
                    updateUi();
                    hideLoading();
                }

                @Override
                public void onNetworkFailure(Exception exception) {
                    updateUi();
                    hideLoading();
                    Toast.makeText(AlexaAppLinkingActivity.this, R.string.msg_no_internet, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!isReturnedFromBrowser && !isAppLinkingStartedFromAlexa) {
            isReturnedFromBrowser = false;
            // User came back without a redirect (e.g. closed the browser login), so allow a retry.
            if (!appLinkingProgress.equals(AppLinkingProgress.GET_ALEXA_TOKEN)) {
                hideLoading();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        isReturnedFromBrowser = true;

        if (intent.getData() != null) {

            String data = intent.getData().toString();
            Log.d(TAG, "Received redirect : " + intent.getData().buildUpon().clearQuery().fragment(null).build());

            if (data.contains(AppConstants.STATE)) {

                data = data.replace(BuildConfig.ALEXA_REDIRECT_URL, "");
                data = data.replace("?code=", "");
                data = data.replace("&state=", "");
                data = data.replace(AppConstants.STATE, "");

                if (data.contains("error")) {
                    Log.e(TAG, "Received error : " + data);
                    hideLoading();
                    return;
                }

                if (appLinkingProgress.equals(AppLinkingProgress.GET_ALEXA_CODE)) {

                    Log.d(TAG, "Received Alexa code");
                    alexaCode = data;

                    appLinkingProgress = AppLinkingProgress.GET_ALEXA_TOKEN;
                    showLoading("Getting alexa token...");

                    apiManager.getAlexaAccessToken(BuildConfig.ALEXA_REDIRECT_URL, alexaCode, new ApiResponseListener() {

                        @Override
                        public void onSuccess(Bundle data) {

                            appLinkingProgress = AppLinkingProgress.GET_LINKING_STATUS;
                            showLoading("Getting linking status...");
                            apiManager.getApiEndpoints(new ApiResponseListener() {

                                @Override
                                public void onSuccess(Bundle data) {
                                    shouldGetAuthCode = true;
                                    getLinkingStatus();
                                }

                                @Override
                                public void onResponseFailure(Exception exception) {
                                    if (exception instanceof CloudException) {
                                        Toast.makeText(AlexaAppLinkingActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(AlexaAppLinkingActivity.this, "Failed to do Alexa app linking", Toast.LENGTH_SHORT).show();
                                    }
                                    updateUi();
                                    hideLoading();
                                }

                                @Override
                                public void onNetworkFailure(Exception exception) {
                                    updateUi();
                                    hideLoading();
                                }
                            });
                        }

                        @Override
                        public void onResponseFailure(Exception exception) {
                            if (exception instanceof CloudException) {
                                Toast.makeText(AlexaAppLinkingActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(AlexaAppLinkingActivity.this, "Failed to do Alexa app linking", Toast.LENGTH_SHORT).show();
                            }
                            updateUi();
                            hideLoading();
                        }

                        @Override
                        public void onNetworkFailure(Exception exception) {
                            updateUi();
                            hideLoading();
                        }
                    });
                } else if (appLinkingProgress.equals(AppLinkingProgress.GET_AUTH_CODE)) {

                    Log.d(TAG, "Received auth code from browser");
                    alexaAuthCode = data;
                    appLinkingProgress = AppLinkingProgress.ENABLING_SKILL;
                    showLoading("Enabling skill...");
                    Data inputData = new Data.Builder()
                            .putString(AppConstants.KEY_EVENT_TYPE, AppConstants.EVENT_ENABLE_SKILL)
                            .putString(AppConstants.KEY_AUTH_CODE, alexaAuthCode)
                            .build();

                    OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(AlexaLinkingWorker.class)
                            .setInputData(inputData)
                            .build();

                    WorkManager workManager = WorkManager.getInstance(getApplicationContext());
                    workManager.enqueue(workRequest);
                    workManager.getWorkInfoByIdLiveData(workRequest.getId())
                            .observe(this, new Observer<WorkInfo>() {

                                @Override
                                public void onChanged(@Nullable WorkInfo workInfo) {
                                    if (workInfo != null) {
                                        Log.d("AppLinkingWorkRequest", "Status changed to : " + workInfo.getState());

                                        if (workInfo.getState().equals(WorkInfo.State.SUCCEEDED)) {
                                            isLinked = true;
                                            updateUi();
                                            hideLoading();
                                        }
                                        if (workInfo.getState().equals(WorkInfo.State.FAILED)) {
                                            isLinked = false;
                                            updateUi();
                                            hideLoading();
                                            Toast.makeText(AlexaAppLinkingActivity.this, "Failed to link with Amazon Alexa", Toast.LENGTH_LONG).show();
                                        }
                                    }
                                }
                            });
                }
            }
        }
    }

    private void getAuthCode() {
        if (!isAlexaRMClientSecretAvailable()) {
            // App built with the older config, keep the browser-based flow.
            getAuthCodeViaBrowser();
            return;
        }

        Log.d(TAG, "Getting auth code via API");
        appLinkingProgress = AppLinkingProgress.GET_AUTH_CODE;
        showLoading("Getting auth code...");

        apiManager.getAlexaAuthCode(BuildConfig.ALEXA_REDIRECT_URL, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (data != null) {
                            alexaAuthCode = data.getString(AppConstants.KEY_AUTH_CODE, "");
                        }
                        Log.d(TAG, "Received auth code from API");

                        appLinkingProgress = AppLinkingProgress.ENABLING_SKILL;
                        showLoading("Enabling skill...");

                        Data inputData = new Data.Builder()
                                .putString(AppConstants.KEY_EVENT_TYPE, AppConstants.EVENT_ENABLE_SKILL)
                                .putString(AppConstants.KEY_AUTH_CODE, alexaAuthCode)
                                .build();

                        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(AlexaLinkingWorker.class)
                                .setInputData(inputData)
                                .build();

                        WorkManager workManager = WorkManager.getInstance(getApplicationContext());
                        workManager.enqueue(workRequest);
                        workManager.getWorkInfoByIdLiveData(workRequest.getId())
                                .observe(AlexaAppLinkingActivity.this, new Observer<WorkInfo>() {

                                    @Override
                                    public void onChanged(@Nullable WorkInfo workInfo) {
                                        if (workInfo != null) {
                                            Log.d("AppLinkingWorkRequest", "Status changed to : " + workInfo.getState());

                                            if (workInfo.getState().equals(WorkInfo.State.SUCCEEDED)) {
                                                isLinked = true;
                                                updateUi();
                                                hideLoading();
                                            }
                                            if (workInfo.getState().equals(WorkInfo.State.FAILED)) {
                                                int statusCode = workInfo.getOutputData().getInt(AppConstants.KEY_STATUS_CODE, -1);
                                                if (statusCode == 400) {
                                                    // Alexa couldn't redeem the auth code from the API, e.g. the skill still uses the
                                                    // older account linking config. Ignore that code and retry once with the browser
                                                    // flow, whose result is final.
                                                    Log.w(TAG, "Enable skill failed with HTTP 400. Retrying with browser flow.");
                                                    alexaAuthCode = "";
                                                    getAuthCodeViaBrowser();
                                                    return;
                                                }
                                                isLinked = false;
                                                updateUi();
                                                hideLoading();
                                                Toast.makeText(AlexaAppLinkingActivity.this, "Failed to link with Amazon Alexa", Toast.LENGTH_LONG).show();
                                            }
                                        }
                                    }
                                });
                    }
                });
            }

            @Override
            public void onResponseFailure(Exception exception) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Backward compatibility: the auth code API didn't return a code, e.g. the backend
                        // or its config doesn't support this flow yet. Older backends answer with different
                        // 4xx or 5xx codes, so fall back to the older browser-based flow on any error response.
                        Log.w(TAG, "Auth code API failed, falling back to browser flow: " + exception.getMessage());
                        getAuthCodeViaBrowser();
                    }
                });
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideLoading();
                        Log.e(TAG, "Network failure getting auth code: " + exception.getMessage());
                        Toast.makeText(AlexaAppLinkingActivity.this, R.string.msg_no_internet, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    // Browser-based flow, used when "alexaRMClientSecret" is not configured, the auth code API
    // fails, or Alexa rejects the code from that API: request the auth code by opening the
    // authorization URL in the browser. The browser redirects back to the app and the response
    // is handled in onNewIntent() under the GET_AUTH_CODE progress state.
    private void getAuthCodeViaBrowser() {
        Log.d(TAG, "Getting auth code via browser (fallback)");
        isReturnedFromBrowser = false;
        appLinkingProgress = AppLinkingProgress.GET_AUTH_CODE;

        String uriStr = BuildConfig.AUTH_URL
                + "/authorize?response_type=code"
                + "&client_id=" + BuildConfig.ALEXA_RM_CLIENT_ID
                + "&redirect_uri=" + BuildConfig.ALEXA_REDIRECT_URL
                + "&state=" + AppConstants.STATE + "&scope=aws.cognito.signin.user.admin";
        Log.d(TAG, "URL : " + uriStr);
        Uri uri = Uri.parse(uriStr);
        Intent openURL = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(openURL);
    }

    private void getLinkingStatus() {
        try {
            Data inoutData = new Data.Builder()
                    .putString(AppConstants.KEY_EVENT_TYPE, AppConstants.EVENT_GET_STATUS)
                    .build();

            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(AlexaLinkingWorker.class)
                    .setInputData(inoutData)
                    .build();

            WorkManager workManager = WorkManager.getInstance(getApplicationContext());
            workManager.enqueue(workRequest);
            workManager.getWorkInfoByIdLiveData(workRequest.getId())
                    .observe(AlexaAppLinkingActivity.this, new Observer<WorkInfo>() {

                        @Override
                        public void onChanged(@Nullable WorkInfo workInfo) {

                            if (workInfo != null) {
                                Log.d("AppLinkingWorkRequest", "Status changed to : " + workInfo.getState());

                                if (workInfo.getState().equals(WorkInfo.State.SUCCEEDED)) {
                                    isLinked = true;
                                    updateUi();
                                    hideLoading();
                                }
                                if (workInfo.getState().equals(WorkInfo.State.FAILED)) {
                                    isLinked = false;
                                    updateUi();

                                    if (shouldGetAuthCode) {
                                        getAuthCode();
                                        shouldGetAuthCode = false;
                                    } else {
                                        hideLoading();
                                    }
                                }
                            }
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateUi() {
        if (!isAppLinkingStartedFromAlexa) {
            if (isLinked) {
                binding.layoutAlexaAppLinking.layoutAlexaLink.setVisibility(View.GONE);
                binding.layoutAlexaAppLinking.layoutAlexaUnlink.setVisibility(View.VISIBLE);
                binding.layoutAlexaAppLinking.btnAlexaAppLink.textBtn.setText(R.string.btn_unlink_alexa);
            } else {
                binding.layoutAlexaAppLinking.layoutAlexaLink.setVisibility(View.VISIBLE);
                binding.layoutAlexaAppLinking.layoutAlexaUnlink.setVisibility(View.GONE);
                binding.layoutAlexaAppLinking.btnAlexaAppLink.textBtn.setText(R.string.btn_link_alexa);
            }
        } else {
            binding.layoutAlexaAppLinking.btnAlexaAppLink.ivArrow.setVisibility(View.GONE);
            binding.layoutAlexaAppLinking.btnAlexaAppLink.textBtn.setText(R.string.btn_allow);
            binding.layoutAlexaAppLinking.btnAppLinkDeny.layoutBtnRemove.setVisibility(View.VISIBLE);
            binding.layoutAlexaAppLinking.btnAppLinkDeny.textBtn.setText(R.string.btn_alexa_deny);
        }
    }

    // The auth code API (no browser login) is used only when "alexaRMClientSecret" is configured.
    // Apps built with the older config don't have it, so they keep using the browser-based flow.
    private boolean isAlexaRMClientSecretAvailable() {
        String clientSecret = BuildConfig.ALEXA_RM_CLIENT_SECRET;
        // A property missing from local.properties ends up in BuildConfig as the string "null".
        return !TextUtils.isEmpty(clientSecret) && !"null".equalsIgnoreCase(clientSecret);
    }

    private boolean doesAlexaAppSupportAppToApp() {
        try {
            PackageManager packageManager = getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(AppConstants.ALEXA_PACKAGE_NAME, 0);

            Log.d(TAG, "Version : " + Build.VERSION.SDK_INT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Log.d(TAG, "getLongVersionCode : " + packageInfo.getLongVersionCode());

                if (packageInfo.getLongVersionCode() > REQUIRED_MINIMUM_VERSION_CODE) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            // The Alexa App is not installed
            return false;
        }
    }

    View.OnClickListener btnAppLinkClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Log.d(TAG, "Amazon Alexa button clicked.");

            if (isLinked) {
                confirmUnlink();
            } else {
                alexaCode = "";
                appLinkingProgress = AppLinkingProgress.GET_ALEXA_CODE;
                showLoading("Getting code...");
                isReturnedFromBrowser = false;

                if (doesAlexaAppSupportAppToApp()) {
                    String url = AppConstants.ALEXA_APP_URL + "&client_id=" + BuildConfig.ALEXA_CLIENT_ID
                            + "&scope=" + AppConstants.LWA_SCOPE + "&skill_stage=" + BuildConfig.SKILL_STAGE
                            + "&response_type=code" + "&redirect_uri=" + BuildConfig.ALEXA_REDIRECT_URL
                            + "&state=" + AppConstants.STATE;
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } else {
                    String fallbackUrl = AppConstants.LWA_URL + "?client_id=" + BuildConfig.ALEXA_CLIENT_ID
                            + "&scope=" + AppConstants.LWA_SCOPE + "&response_type=code"
                            + "&redirect_uri=" + BuildConfig.ALEXA_REDIRECT_URL
                            + "&state=" + AppConstants.STATE;
                    Log.d(TAG, "URL : " + fallbackUrl);
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)));
                }
            }
        }
    };

    View.OnClickListener btnAppLinkAllowClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Log.d(TAG, "App linking allow button clicked.");
            allowAppLinking();
        }
    };

    View.OnClickListener btnAppLinkDenyClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Log.d(TAG, "App linking deny button clicked.");
            finish();
        }
    };

    private void initViews() {

        setSupportActionBar(binding.toolbarLayout.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(R.string.title_activity_amazon_alexa);
        binding.toolbarLayout.toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        binding.toolbarLayout.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        if (!isAppLinkingStartedFromAlexa) {
            // App linking started from RainMaker app.
            binding.layoutAlexaAppLinking.btnAlexaAppLink.ivArrow.setVisibility(View.GONE);
            binding.layoutAlexaAppLinking.btnAlexaAppLink.textBtn.setText(R.string.btn_link_alexa);
            binding.layoutAlexaAppLinking.btnAlexaAppLink.layoutBtn.setOnClickListener(btnAppLinkClickListener);
            binding.layoutAlexaAppLinking.btnAppLinkDeny.layoutBtnRemove.setVisibility(View.GONE);
        } else {
            // App linking started from Alexa app.
            binding.layoutAlexaAppLinking.btnAlexaAppLink.ivArrow.setVisibility(View.GONE);
            binding.layoutAlexaAppLinking.btnAlexaAppLink.textBtn.setText(R.string.btn_allow);
            binding.layoutAlexaAppLinking.btnAlexaAppLink.layoutBtn.setOnClickListener(btnAppLinkAllowClickListener);
            binding.layoutAlexaAppLinking.btnAppLinkDeny.textBtn.setText(R.string.btn_deny);
            binding.layoutAlexaAppLinking.btnAppLinkDeny.layoutBtnRemove.setVisibility(View.VISIBLE);
            binding.layoutAlexaAppLinking.btnAppLinkDeny.layoutBtnRemove.setOnClickListener(btnAppLinkDenyClickListener);
        }
    }

    private void confirmUnlink() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_unlink_skill);
        builder.setMessage(R.string.dialog_msg_unlink_skill);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                unlinkAlexaSkill();
            }
        });

        builder.setNegativeButton(R.string.btn_no, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog userDialog = builder.create();
        userDialog.show();
    }

    private void unlinkAlexaSkill() {

        showLoading("Unlinking skill...");

        Data data = new Data.Builder()
                .putString(AppConstants.KEY_EVENT_TYPE, AppConstants.EVENT_DISABLE_SKILL)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(AlexaLinkingWorker.class)
                .setInputData(data)
                .build();
        WorkManager workManager = WorkManager.getInstance(getApplicationContext());
        workManager.enqueue(workRequest);
        workManager.getWorkInfoByIdLiveData(workRequest.getId())
                .observe(AlexaAppLinkingActivity.this, new Observer<WorkInfo>() {

                    @Override
                    public void onChanged(@Nullable WorkInfo workInfo) {
                        if (workInfo != null) {
                            Log.d("AppLinkingWorkRequest", "Status changed to : " + workInfo.getState());

                            if (workInfo.getState().equals(WorkInfo.State.SUCCEEDED)) {
                                isLinked = false;
                            } else if (workInfo.getState().equals(WorkInfo.State.FAILED)) {
                                isLinked = true;
                                Toast.makeText(AlexaAppLinkingActivity.this, "Failed to unlink from Amazon Alexa", Toast.LENGTH_LONG).show();
                            }
                            updateUi();
                            hideLoading();
                        }
                    }
                });
    }

    private void allowAppLinking() {

        Log.d(TAG, "Getting auth code for app-to-app linking (started from Alexa)");
        Intent intent = getIntent();

        if (intent.getData() != null) {
            // Get values from App Link sent by Alexa
            String clientId = intent.getData().getQueryParameter(AppConstants.KEY_CLIENT_ID);
            String responseType = intent.getData().getQueryParameter(AppConstants.KEY_RESPONSE_TYPE);
            String state = intent.getData().getQueryParameter(AppConstants.KEY_STATE);
            String scope = intent.getData().getQueryParameter(AppConstants.KEY_SCOPE);
            String redirectUri = intent.getData().getQueryParameter(AppConstants.KEY_REDIRECT_URI);

            if (!isAlexaRMClientSecretAvailable()) {
                // App built with the older config, keep the browser-based flow.
                allowAppLinkingViaBrowser(responseType, clientId, redirectUri, state, scope);
                return;
            }

            appLinkingProgress = AppLinkingProgress.GET_AUTH_CODE;
            showLoading("Getting auth code...");

            apiManager.getAlexaAuthCode(redirectUri, new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (data != null) {
                                hideLoading();
                                alexaAuthCode = data.getString(AppConstants.KEY_AUTH_CODE, "");

                                Uri alexaAppUri = Uri.parse(redirectUri).buildUpon()
                                        .appendQueryParameter(AppConstants.KEY_CODE, alexaAuthCode)
                                        .appendQueryParameter(AppConstants.KEY_STATE, state)
                                        .appendQueryParameter("source", "app")
                                        .build();

                                Log.d(TAG, "Redirecting to Alexa app: " + redirectUri);
                                Intent openURL = new Intent(Intent.ACTION_VIEW, alexaAppUri);
                                startActivity(openURL);
                                finish();
                            } else {
                                hideLoading();
                                Toast.makeText(AlexaAppLinkingActivity.this, "Failed to get auth code", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }

                @Override
                public void onResponseFailure(Exception exception) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Backward compatibility: the auth code API didn't return a code, e.g. the backend
                            // or its config doesn't support this flow yet. Older backends answer with different
                            // 4xx or 5xx codes, so fall back to the older browser-based flow on any error response.
                            Log.w(TAG, "Auth code API failed, falling back to browser flow: " + exception.getMessage());
                            allowAppLinkingViaBrowser(responseType, clientId, redirectUri, state, scope);
                        }
                    });
                }

                @Override
                public void onNetworkFailure(Exception exception) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            hideLoading();
                            Log.e(TAG, "Network failure getting auth code: " + exception.getMessage());
                            Toast.makeText(AlexaAppLinkingActivity.this, R.string.msg_no_internet, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        }
    }

    // Browser-based flow, used when "alexaRMClientSecret" is not configured or the auth code API
    // fails: forward the app-to-app authorization request to the browser using the values sent
    // by the Alexa app. The browser completes the flow by redirecting back to the Alexa app.
    private void allowAppLinkingViaBrowser(String responseType, String clientId, String redirectUri,
                                           String state, String scope) {
        Log.d(TAG, "Getting auth code via browser (fallback)");
        hideLoading();

        String uriStr = BuildConfig.AUTH_URL
                + "/authorize?response_type=" + responseType
                + "&client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&state=" + state + "&scope=" + scope;
        Log.d(TAG, "URL : " + uriStr);
        Uri uri = Uri.parse(uriStr);
        Intent openURL = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(openURL);
        finish();
    }

    private void showLoading(String msg) {
        binding.layoutAlexaAppLinking.rlAmazonAlexa.setAlpha(0.3f);
        binding.rlProgressAppLinking.setVisibility(View.VISIBLE);
        binding.tvLoadingAppLinking.setVisibility(View.GONE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private void hideLoading() {
        binding.layoutAlexaAppLinking.rlAmazonAlexa.setAlpha(1);
        binding.rlProgressAppLinking.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }
}
