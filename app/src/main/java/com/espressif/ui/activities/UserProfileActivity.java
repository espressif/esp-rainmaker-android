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
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
import com.espressif.AppConstants;
import com.espressif.EspDatabase;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.UserProfileAdapter;
import com.espressif.ui.user_module.AppHelper;
import com.espressif.ui.user_module.ChangePasswordActivity;
import com.espressif.ui.theme_manager.WindowThemeManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

public class UserProfileActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private UserProfileAdapter termsInfoAdapter;
    private ArrayList<String> termsInfoList;

    private TextView tvAppVersion;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);
        WindowThemeManager WindowTheme = new WindowThemeManager(this, false);
        WindowTheme.applyWindowTheme(getWindow());

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.title_activity_user_profile);
        toolbar.setNavigationIcon(R.drawable.ic_fluent_arrow_left);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        initViews();
    }

    private void initViews() {

        RecyclerView userInfoView = findViewById(R.id.rv_user_info);
        RecyclerView termsInfoView = findViewById(R.id.rv_terms);
        MaterialButton btnLogout = findViewById(R.id.btn_logout);
        tvAppVersion = findViewById(R.id.tv_app_version);

        String version = "";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        String appVersion = getString(R.string.app_version) + " - v" + version;
        tvAppVersion.setText(appVersion);

        btnLogout.setOnClickListener(new View.OnClickListener() {

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
                EspDatabase.getInstance(getApplicationContext()).getNodeDao().deleteAll();
                if (((EspApplication) getApplicationContext()).nodeMap != null) {
                    ((EspApplication) getApplicationContext()).nodeMap.clear();
                }

                Intent loginActivity = new Intent(getApplicationContext(), MainActivity.class);
                loginActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(loginActivity);
                finish();
            }
        });

        LinearLayoutManager llm1 = new LinearLayoutManager(getApplicationContext());
        llm1.setOrientation(RecyclerView.VERTICAL);
        userInfoView.setLayoutManager(llm1); // set LayoutManager to RecyclerView

        LinearLayoutManager llm2 = new LinearLayoutManager(getApplicationContext());
        llm2.setOrientation(RecyclerView.VERTICAL);
        termsInfoView.setLayoutManager(llm2); // set LayoutManager to RecyclerView

        ArrayList<String> userInfoList = new ArrayList<>();
        userInfoList.add(getString(R.string.hint_email));
        ArrayList<String> userInfoValues = new ArrayList<>();
        userInfoValues.add(sharedPreferences.getString(AppConstants.KEY_EMAIL, ""));
        UserProfileAdapter userInfoAdapter = new UserProfileAdapter(this, userInfoList, userInfoValues, true);
        userInfoView.setAdapter(userInfoAdapter);

        termsInfoList = new ArrayList<>();

        if (!ApiManager.isOAuthLogin) {
            termsInfoList.add(getString(R.string.title_activity_change_password));
        }
        termsInfoList.add(getString(R.string.documentation));
        termsInfoList.add(getString(R.string.privacy_policy));
        termsInfoList.add(getString(R.string.terms_of_use));
        termsInfoAdapter = new UserProfileAdapter(this, termsInfoList, null, false);
        termsInfoView.setAdapter(termsInfoAdapter);
        termsInfoAdapter.setOnItemClickListener(onItemClickListener);
    }

    private View.OnClickListener onItemClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {

            RecyclerView.ViewHolder viewHolder = (RecyclerView.ViewHolder) view.getTag();
            int position = viewHolder.getAdapterPosition();
            String str = termsInfoList.get(position);

            if (str.equals(getString(R.string.title_activity_change_password))) {

                startActivity(new Intent(UserProfileActivity.this, ChangePasswordActivity.class));

            } else if (str.equals(getString(R.string.documentation))) {

                Intent openURL = new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.DOCUMENTATION_URL));
                startActivity(openURL);

            } else if (str.equals(getString(R.string.privacy_policy))) {

                Intent openURL = new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_URL));
                startActivity(openURL);

            } else if (str.equals(getString(R.string.terms_of_use))) {

                Intent openURL = new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.TERMS_URL));
                startActivity(openURL);
            }
        }
    };
}
