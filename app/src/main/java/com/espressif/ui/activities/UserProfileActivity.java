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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.db.EspDatabase;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.UserProfileAdapter;
import com.espressif.ui.models.SharingRequest;
import com.espressif.ui.user_module.AppHelper;

import java.util.ArrayList;

public class UserProfileActivity extends AppCompatActivity {

    private TextView tvTitle, tvBack, tvCancel;

    private RecyclerView rvUserInfo;
    private TextView tvAppVersion;

    private UserProfileAdapter userInfoAdapter;
    private SharedPreferences sharedPreferences;

    private ArrayList<String> userInfoList;
    private ArrayList<SharingRequest> pendingRequests;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);
        overridePendingTransition(R.anim.anim_left_to_right, R.anim.scale_in);
        pendingRequests = new ArrayList<>();
        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        initViews();
    }

    @Override
    public void onResume() {
        super.onResume();
        getSharingRequests();
    }

    private View.OnClickListener backButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            finish();
        }
    };

    private void initViews() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvCancel = findViewById(R.id.btn_cancel);

        tvTitle.setText(R.string.title_activity_user_profile);
        tvBack.setVisibility(View.VISIBLE);
        tvCancel.setVisibility(View.GONE);
        tvBack.setOnClickListener(backButtonClickListener);

        TextView tvEmail = findViewById(R.id.tv_email);
        tvEmail.setText(sharedPreferences.getString(AppConstants.KEY_EMAIL, ""));

        rvUserInfo = findViewById(R.id.rv_user_profile);
        tvAppVersion = findViewById(R.id.tv_app_version);
        RelativeLayout logoutView = findViewById(R.id.layout_logout);

        String version = "";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        String appVersion = getString(R.string.app_version) + " - v" + version;
        tvAppVersion.setText(appVersion);

        logoutView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (!ApiManager.isOAuthLogin) {
                    String username = AppHelper.getCurrUser();
                    CognitoUser user = AppHelper.getPool().getUser(username);
                    user.signOut();
                }

                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.clear();
                editor.apply();
                EspApplication espApp = (EspApplication) getApplicationContext();
                EspDatabase.getInstance(espApp).getNodeDao().deleteAll();
                EspDatabase.getInstance(espApp).getGroupDao().deleteAll();
                espApp.setCurrentStatus(EspApplication.GetDataStatus.FETCHING_DATA);
                espApp.nodeMap.clear();
                espApp.scheduleMap.clear();
                espApp.mDNSDeviceMap.clear();
                espApp.groupMap.clear();

                Intent loginActivity = new Intent(espApp, MainActivity.class);
                loginActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(loginActivity);
                finish();
            }
        });

        LinearLayoutManager llm2 = new LinearLayoutManager(getApplicationContext());
        llm2.setOrientation(RecyclerView.VERTICAL);
        rvUserInfo.setLayoutManager(llm2);
        DividerItemDecoration itemDecor = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        rvUserInfo.addItemDecoration(itemDecor);

        userInfoList = new ArrayList<>();

        if (BuildConfig.isNodeSharingSupported) {
            userInfoList.add(getString(R.string.title_activity_sharing_requests));
        }
        if (!ApiManager.isOAuthLogin) {
            userInfoList.add(getString(R.string.title_activity_change_password));
        }
        userInfoList.add(getString(R.string.documentation));
        userInfoList.add(getString(R.string.privacy_policy));
        userInfoList.add(getString(R.string.terms_of_use));
        userInfoAdapter = new UserProfileAdapter(this, userInfoList, 0);
        rvUserInfo.setAdapter(userInfoAdapter);
    }

    private void getSharingRequests() {

        pendingRequests.clear();
        ApiManager apiManager = ApiManager.getInstance(getApplicationContext());
        apiManager.getSharingRequests(false, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                if (data != null) {
                    ArrayList<SharingRequest> requests = data.getParcelableArrayList(AppConstants.KEY_SHARING_REQUESTS);
                    if (requests != null && requests.size() > 0) {
                        for (int i = 0; i < requests.size(); i++) {
                            SharingRequest req = requests.get(i);
                            if (AppConstants.KEY_REQ_STATUS_PENDING.equals(req.getReqStatus())) {
                                pendingRequests.add(req);
                            }
                        }
                    }
                }
                int count = pendingRequests.size();
                userInfoAdapter.updatePendingRequestCount(count);
            }

            @Override
            public void onFailure(Exception exception) {
            }
        });
    }
}
