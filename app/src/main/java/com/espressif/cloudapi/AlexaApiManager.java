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

package com.espressif.cloudapi;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.rainmaker.BuildConfig;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlexaApiManager {

    public static final String TAG = AlexaApiManager.class.getSimpleName();

    private static AlexaApiManager apiManager;

    private static String accessToken, refreshToken;

    private AlexaApiInterface apiInterface;
    private SharedPreferences sharedPreferences;
    private ArrayList<String> endpoints = new ArrayList<>();

    public static AlexaApiManager getInstance(Context context) {

        if (apiManager == null) {
            apiManager = new AlexaApiManager(context);
        }
        return apiManager;
    }

    private AlexaApiManager(Context context) {
        apiInterface = AlexaApiClient.getAlexaApiClient(context).create(AlexaApiInterface.class);
        sharedPreferences = context.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        accessToken = sharedPreferences.getString(AppConstants.KEY_ALEXA_ACCESS_TOKEN, "");
        refreshToken = sharedPreferences.getString(AppConstants.KEY_ALEXA_REFRESH_TOKEN, "");
    }

    public void getAlexaAuthCode(String redirectUri, ApiResponseListener listener) {

        String authorizeUrl = EspApplication.BASE_URL + AppConstants.URL_APP_LINK;
        String rmAccessToken = sharedPreferences.getString(AppConstants.KEY_ACCESS_TOKEN, "");

        HttpUrl baseUrl = HttpUrl.parse(authorizeUrl);
        if (baseUrl == null) {
            listener.onResponseFailure(new RuntimeException("Invalid authorize URL"));
            return;
        }

        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter(AppConstants.KEY_CLIENT_ID, BuildConfig.ALEXA_RM_CLIENT_ID)
                .addQueryParameter(AppConstants.KEY_REDIRECT_URI, redirectUri)
                .addQueryParameter(AppConstants.KEY_RESPONSE_TYPE, "code")
                .addQueryParameter(AppConstants.KEY_STATE, AppConstants.STATE)
                .addQueryParameter(AppConstants.KEY_CLIENT_SECRET, BuildConfig.ALEXA_RM_CLIENT_SECRET)
                .build();

        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", rmAccessToken)
                .get()
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {

            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "getAlexaAuthCode network failure", e);
                listener.onNetworkFailure(new RuntimeException(e));
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                try {
                    Log.d(TAG, "getAlexaAuthCode, Response code: " + response.code());

                    if (response.code() == 302) {
                        String location = response.header("location");

                        if (location != null) {
                            Uri locationUri = Uri.parse(location);
                            String code = locationUri.getQueryParameter("code");

                            if (code != null && !code.isEmpty()) {
                                Bundle data = new Bundle();
                                data.putString(AppConstants.KEY_AUTH_CODE, code);
                                listener.onSuccess(data);
                            } else {
                                listener.onResponseFailure(new RuntimeException("Auth code not found in redirect URL"));
                            }
                        } else {
                            listener.onResponseFailure(new RuntimeException("No location header in 302 response"));
                        }
                    } else {
                        String errBody = response.body() != null ? response.body().string() : "";
                        Log.e(TAG, "getAlexaAuthCode failed. Code: " + response.code() + ", Body: " + errBody);
                        listener.onResponseFailure(new AlexaAuthCodeException(response.code(), "Expected 302 but got " + response.code()));
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    public void getAlexaAccessToken(String redirectUri, String code, ApiResponseListener listener) {

        String url = BuildConfig.ALEXA_ACCESS_TOKEN_URL;
        JsonObject body = new JsonObject();
        body.addProperty(AppConstants.KEY_REDIRECT_URI, redirectUri);
        body.addProperty(AppConstants.KEY_CODE, code);

        apiInterface.getAlexaTokens(url, body).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Log.d(TAG, "getAccessToken, Response code  : " + response.code());

                try {
                    if (response.isSuccessful()) {

                        String jsonResponse = response.body().string();
                        Log.d(TAG, "getAccessToken, Response : " + jsonResponse);
                        JSONObject jsonObject = new JSONObject(jsonResponse);
                        accessToken = jsonObject.getString(AppConstants.KEY_ACCESS_TOKEN);
                        refreshToken = jsonObject.getString(AppConstants.KEY_REFRESH_TOKEN);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(AppConstants.KEY_ALEXA_ACCESS_TOKEN, accessToken);
                        editor.putString(AppConstants.KEY_ALEXA_REFRESH_TOKEN, refreshToken);
                        editor.apply();
                        listener.onSuccess(null);
                    } else {
                        Log.e(TAG, "Get alexa access token failed");
                        listener.onResponseFailure(new RuntimeException());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    listener.onResponseFailure(e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onNetworkFailure(new RuntimeException(t));
            }
        });
    }

    public void getApiEndpoints(ApiResponseListener listener) {

        String header = "Bearer " + accessToken;

        apiInterface.getApiEndpoints(AppConstants.ALEXA_API_ENDPOINTS_URL, header).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Log.d(TAG, "getApiEndpoints, Response code  : " + response.code());

                try {
                    if (response.isSuccessful()) {
                        endpoints.clear();
                        String jsonResponse = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonResponse);
                        JSONArray endpointsArray = jsonObject.optJSONArray(AppConstants.KEY_ENDPOINTS);
                        for (int i = 0; i < endpointsArray.length(); i++) {
                            endpoints.add(endpointsArray.getString(i));
                        }
                        Log.d(TAG, "getApiEndpoints, Response : " + jsonResponse);
                        listener.onSuccess(null);
                    } else {
                        String jsonErrResponse = response.errorBody().string();
                        Log.e(TAG, "getApiEndpoints, Response : " + jsonErrResponse);
                        listener.onResponseFailure(new RuntimeException("Failed to get endpoints."));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                listener.onNetworkFailure(new RuntimeException(t));
            }
        });
    }

    public boolean getStatus() {

        String header = "Bearer " + accessToken;
        boolean isLinked = false, areEndpointsReceived = false;
        Log.e(TAG, "Access token : " + accessToken);

        if (endpoints.isEmpty()) {

            Log.e(TAG, "No endpoints available. Get endpoints...");

            try {
                Response<ResponseBody> response = apiInterface.getApiEndpoints(AppConstants.ALEXA_API_ENDPOINTS_URL, header).execute();
                Log.d(TAG, "getApiEndpoints, Response code  : " + response.code());

                if (response.isSuccessful()) {

                    String jsonResponse = response.body().string();
                    Log.e(TAG, "getApiEndpoints, Response : " + jsonResponse);
                    endpoints.clear();
                    JSONObject jsonObject = new JSONObject(jsonResponse);
                    JSONArray endpointsArray = jsonObject.optJSONArray(AppConstants.KEY_ENDPOINTS);
                    for (int i = 0; i < endpointsArray.length(); i++) {
                        endpoints.add(endpointsArray.getString(i));
                    }
                    areEndpointsReceived = true;
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        } else {
            areEndpointsReceived = true;
        }

        if (!areEndpointsReceived) {
            return false;
        }

        for (int index = 0; index < endpoints.size(); index++) {

            String getStatusUrl = "https://" + endpoints.get(index) + "/v1/users/~current/skills/"
                    + BuildConfig.SKILL_ID + "/enablement";
            Log.e(TAG, "getStatus, url : " + getStatusUrl);

            try {
                Response<ResponseBody> response = apiInterface.getStatus(getStatusUrl, header).execute();

                if (response.isSuccessful()) {

                    String jsonResponse = response.body().string();
                    Log.d(TAG, "getStatus, Response : " + jsonResponse);
                    JSONObject jsonObject = new JSONObject(jsonResponse);
                    String status = jsonObject.optString(AppConstants.KEY_STATUS);

                    JSONObject skillJson = jsonObject.optJSONObject(AppConstants.KEY_SKILL);
                    String skillId = skillJson.optString(AppConstants.KEY_ID);

                    JSONObject accountLinkJson = jsonObject.optJSONObject(AppConstants.KEY_ACCOUNTLINK);
                    String accountLinkStatus = accountLinkJson.optString(AppConstants.KEY_STATUS);

                    Log.e(TAG, "Status : " + status);
                    Log.e(TAG, "Skill ID : " + skillId);
                    Log.e(TAG, "Account Link Status : " + accountLinkStatus);

                    if (!TextUtils.isEmpty(accountLinkStatus)
                            && accountLinkStatus.equalsIgnoreCase(AppConstants.KEY_STATUS_LINKED)) {
                        isLinked = true;
                    }
                    break;
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            } finally {
                if (!isLinked) {
                    continue;
                }
            }
        }
        return isLinked;
    }

    /**
     * Enables the skill and links the accounts using the given auth code.
     *
     * @return HTTP status code of the last enablement response, or -1 if no response was received.
     */
    public int enableAlexaSkill(String authCode) {

        String header = "Bearer " + accessToken;
        int statusCode = -1;

        JsonObject body = new JsonObject();
        body.addProperty(AppConstants.KEY_STAGE, BuildConfig.SKILL_STAGE);
        JsonObject accountLinkReqJson = new JsonObject();
        accountLinkReqJson.addProperty("redirectUri", BuildConfig.ALEXA_REDIRECT_URL);
        accountLinkReqJson.addProperty(AppConstants.KEY_AUTH_CODE, authCode);
        accountLinkReqJson.addProperty(AppConstants.KEY_TYPE, "AUTH_CODE");
        body.add(AppConstants.KEY_ACCOUNT_LINK_REQUEST, accountLinkReqJson);

        for (int index = 0; index < endpoints.size(); index++) {

            String url = "https://" + endpoints.get(index) + "/v1/users/~current/skills/"
                    + BuildConfig.SKILL_ID + "/enablement";
            Log.e(TAG, "enableAlexaSkill, url : " + url);

            try {
                Response<ResponseBody> response = apiInterface.enableAlexaSkill(url, header, body).execute();
                statusCode = response.code();
                Log.d(TAG, "enableAlexaSkill, response code : " + statusCode);

                if (response.isSuccessful()) {

                    String jsonResponse = response.body().string();
                    Log.d(TAG, "enableAlexaSkill, Response : " + jsonResponse);
                    break;
                } else {
                    String jsonErrResponse = response.errorBody().string();
                    Log.e(TAG, "Error response : " + jsonErrResponse);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return statusCode;
    }

    public boolean disableAlexaSkill() {

        String header = "Bearer " + accessToken;
        boolean success = false;

        for (int index = 0; index < endpoints.size(); index++) {

            String url = "https://" + endpoints.get(index) + "/v1/users/~current/skills/"
                    + BuildConfig.SKILL_ID + "/enablement";
            Log.e(TAG, "disableAlexaSkill, url : " + url);

            try {
                Response<ResponseBody> response = apiInterface.disableAlexaSkill(url, header).execute();
                Log.d(TAG, "disableAlexaSkill, Response code : " + response.code());

                if (response.isSuccessful()) {
                    success = true;
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return success;
    }

    public String getNewToken() {

        String newAccessToken = "";
        Log.e(TAG, "Getting new access token for alexa app linking");

        try {
            Response<ResponseBody> response = apiInterface.getNewToken(AppConstants.ALEXA_REFRESH_TOKEN_URL,
                    "application/x-www-form-urlencoded", AppConstants.KEY_REFRESH_TOKEN,
                    BuildConfig.ALEXA_CLIENT_ID, refreshToken,
                    BuildConfig.ALEXA_CLIENT_SECRET).execute();

            Log.e(TAG, "Get New Token, Response code : " + response.code());
            if (response.isSuccessful()) {
                String jsonResponse = response.body().string();
                Log.e(TAG, "Get New Token, Response : " + jsonResponse);
                JSONObject jsonObject = new JSONObject(jsonResponse);
                accessToken = jsonObject.getString(AppConstants.KEY_ACCESS_TOKEN);
                refreshToken = jsonObject.getString(AppConstants.KEY_REFRESH_TOKEN);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(AppConstants.KEY_ALEXA_ACCESS_TOKEN, accessToken);
                editor.putString(AppConstants.KEY_ALEXA_REFRESH_TOKEN, refreshToken);
                editor.apply();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return newAccessToken;
    }

    public void getNewToken(ApiResponseListener listener) {

        Log.e(TAG, "Getting new access token for alexa app linking");

        try {
            apiInterface.getNewToken(AppConstants.ALEXA_REFRESH_TOKEN_URL, "application/x-www-form-urlencoded",
                    AppConstants.KEY_REFRESH_TOKEN, BuildConfig.ALEXA_CLIENT_ID, refreshToken,
                    BuildConfig.ALEXA_CLIENT_SECRET).enqueue(new Callback<ResponseBody>() {

                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    Log.e(TAG, "Get new token, Response code  : " + response.code());

                    try {
                        if (response.isSuccessful()) {

                            String jsonResponse = response.body().string();
                            Log.d(TAG, "get new AccessToken, Response : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            accessToken = jsonObject.getString(AppConstants.KEY_ACCESS_TOKEN);
                            refreshToken = jsonObject.getString(AppConstants.KEY_REFRESH_TOKEN);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString(AppConstants.KEY_ALEXA_ACCESS_TOKEN, accessToken);
                            editor.putString(AppConstants.KEY_ALEXA_REFRESH_TOKEN, refreshToken);
                            editor.apply();
                            listener.onSuccess(null);
                        } else {
                            String jsonErrResponse = response.errorBody().string();
                            Log.e(TAG, "get new AccessToken, Response : " + jsonErrResponse);
                            listener.onResponseFailure(new RuntimeException("Failed to get token"));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        listener.onResponseFailure(new RuntimeException("Failed to get token"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        listener.onResponseFailure(new RuntimeException("Failed to get token"));
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    t.printStackTrace();
                    listener.onNetworkFailure(new RuntimeException(t));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
