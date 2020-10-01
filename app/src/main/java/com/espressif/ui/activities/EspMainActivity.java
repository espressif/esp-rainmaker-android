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
import android.content.DialogInterface;
import android.content.Intent;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.HomeScreenPagerAdapter;
import com.espressif.ui.fragments.DevicesFragment;
import com.espressif.ui.fragments.SchedulesFragment;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Schedule;
import com.espressif.ui.models.UpdateEvent;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Map;

public class EspMainActivity extends AppCompatActivity {

    private static final String TAG = EspMainActivity.class.getSimpleName();

    private static final int REQUEST_LOCATION = 1;

    private GetDataStatus currentStatus = GetDataStatus.NOT_RECEIVED;

    private ContentLoadingProgressBar progressBar;
    private BottomNavigationView bottomNavigationView;
    private ViewPager viewPager;
    private TextView tvTitle;
    private ImageView ivAddDevice, ivUserProfile;

    private Fragment deviceFragment;
    private Fragment scheduleFragment;
    private MenuItem prevMenuItem;
    private HomeScreenPagerAdapter pagerAdapter;

    private ApiManager apiManager;
    private EspApplication espApp;
    private ArrayList<UiUpdateListener> updateListenerArrayList = new ArrayList<>();
    private ArrayList<Device> devices;
    private ArrayList<Schedule> schedules;

    public enum GetDataStatus {
        NOT_RECEIVED,
        GET_DATA_SUCCESS,
        GET_DATA_FAILED,
        DATA_REFRESHING
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp_main);

        devices = new ArrayList<>();
        schedules = new ArrayList<>();
        espApp = (EspApplication) getApplicationContext();
        initViews();

        showLoading();
        apiManager = ApiManager.getInstance(getApplicationContext());
        getSupportedVersions();
    }

    @Override
    protected void onResume() {
        super.onResume();

        EventBus.getDefault().register(this);

        if (apiManager.isTokenExpired()) {

            apiManager.getNewToken(new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {
                    getNodes();
                }

                @Override
                public void onFailure(Exception exception) {
                    exception.printStackTrace();
                    currentStatus = GetDataStatus.GET_DATA_FAILED;
                    hideLoading();
                    updateUi();
                }
            });

        } else if (!currentStatus.equals(GetDataStatus.NOT_RECEIVED)) {
            getNodes();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onDestroy() {

        if (updateListenerArrayList != null) {
            updateListenerArrayList.clear();
        }

        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UpdateEvent event) {

        Log.d(TAG, "Update Event Received : " + event.getEventType());

        switch (event.getEventType()) {

            case EVENT_DEVICE_ADDED:
            case EVENT_DEVICE_REMOVED:
                showLoading();
                getNodes();
                break;

            case EVENT_DEVICE_STATUS_UPDATE:
                if (!currentStatus.equals(GetDataStatus.NOT_RECEIVED)) {
                    updateUi();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {

            case REQUEST_LOCATION:

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                    if (isLocationEnabled()) {
                        goToAddDeviceActivity();
                    }
                }
                break;
        }
    }

    View.OnClickListener addDeviceBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vib.vibrate(HapticFeedbackConstants.VIRTUAL_KEY);

            if (tvTitle.getText().toString().equals(getString(R.string.title_activity_devices))) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                    if (!isLocationEnabled()) {
                        askForLocation();
                        return;
                    }
                }
                goToAddDeviceActivity();
            } else {
                goToAddScheduleActivity();
            }
        }
    };

    BottomNavigationView.OnNavigationItemSelectedListener navigationItemSelectedListener = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {

            switch (menuItem.getItemId()) {

                case R.id.action_devices:
                    tvTitle.setText(pagerAdapter.getPageTitle(0));
                    viewPager.setCurrentItem(0);
                    updateUi();
                    return true;

                case R.id.action_schedules:
                    tvTitle.setText(pagerAdapter.getPageTitle(1));
                    viewPager.setCurrentItem(1);
                    updateUi();
                    return true;
            }
            return false;
        }
    };

    public void setUpdateListener(UiUpdateListener updateListener) {
        updateListenerArrayList.add(updateListener);
    }

    public void removeUpdateListener(UiUpdateListener updateListener) {
        updateListenerArrayList.remove(updateListener);
    }

    private void initViews() {

        tvTitle = findViewById(R.id.esp_toolbar_title);
        ivAddDevice = findViewById(R.id.btn_add_device);
        ivUserProfile = findViewById(R.id.btn_user_profile);
        progressBar = findViewById(R.id.progress_get_nodes);
        bottomNavigationView = findViewById(R.id.bottom_navigation_view);
        viewPager = findViewById(R.id.view_pager);

        tvTitle.setText(R.string.title_activity_devices);
        ivAddDevice.setOnClickListener(addDeviceBtnClickListener);

        ivUserProfile.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(new Intent(EspMainActivity.this, UserProfileActivity.class));
            }
        });

        if (BuildConfig.isScheduleSupported) {

            bottomNavigationView.setOnNavigationItemSelectedListener(navigationItemSelectedListener);
        } else {
            bottomNavigationView.setVisibility(View.GONE);
        }
        setupViewPager();
    }

    private void setupViewPager() {

        pagerAdapter = new HomeScreenPagerAdapter(getSupportFragmentManager(), this);
        deviceFragment = new DevicesFragment();
        pagerAdapter.addFragment(deviceFragment);

        if (BuildConfig.isScheduleSupported) {

            scheduleFragment = new SchedulesFragment();
            pagerAdapter.addFragment(scheduleFragment);

            viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                }

                @Override
                public void onPageSelected(int position) {

                    if (prevMenuItem != null) {
                        prevMenuItem.setChecked(false);
                    } else {
                        bottomNavigationView.getMenu().getItem(0).setChecked(false);
                    }
                    bottomNavigationView.getMenu().getItem(position).setChecked(true);
                    prevMenuItem = bottomNavigationView.getMenu().getItem(position);
                    tvTitle.setText(pagerAdapter.getPageTitle(position));
                    updateUi();
                }

                @Override
                public void onPageScrollStateChanged(int state) {
                }
            });
        }
        viewPager.setAdapter(pagerAdapter);
    }

    private void getSupportedVersions() {

        apiManager.getSupportedVersions(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                String updateMsg = data.getString("additional_info");
                ArrayList<String> versions = data.getStringArrayList("supported_versions");

                if (!versions.contains(AppConstants.CURRENT_VERSION)) {
                    alertForForceUpdate(updateMsg);
                } else {

                    int currentVersion = getVersionNumber(AppConstants.CURRENT_VERSION);

                    for (int i = 0; i < versions.size(); i++) {

                        int version = getVersionNumber(versions.get(i));

                        if (version > currentVersion) {

                            // TODO Make flag true once alert is shown so that update popup will not come every time.
                            if (!TextUtils.isEmpty(updateMsg)) {
                                alertForNewVersion(updateMsg);
                            } else {
                                alertForNewVersion("");
                            }
                            break;
                        }
                    }
                }

                getNodes();
            }

            @Override
            public void onFailure(Exception exception) {
                hideLoading();
                currentStatus = GetDataStatus.GET_DATA_FAILED;
                updateUi();
            }
        });
    }

    private int getVersionNumber(String versionString) {

        versionString = versionString.replace("v", "");
        int version = Integer.valueOf(versionString);
        return version;
    }

    public void refreshDeviceList() {

        currentStatus = GetDataStatus.DATA_REFRESHING;

        if (apiManager.isTokenExpired()) {

            apiManager.getNewToken(new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {

                    getNodes();
                }

                @Override
                public void onFailure(Exception exception) {
                    exception.printStackTrace();
                    currentStatus = GetDataStatus.GET_DATA_FAILED;
                    hideLoading();
                    updateUi();
                }
            });

        } else {
            getNodes();
        }
    }

    public GetDataStatus getCurrentStatus() {
        return currentStatus;
    }

    private void getNodes() {

        apiManager.getNodes(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                currentStatus = GetDataStatus.GET_DATA_SUCCESS;
                hideLoading();
                updateUi();
            }

            @Override
            public void onFailure(Exception exception) {

                exception.printStackTrace();
                currentStatus = GetDataStatus.GET_DATA_FAILED;
                hideLoading();
                updateUi();
            }
        });
    }

    private void updateUi() {

        switch (currentStatus) {

            case GET_DATA_SUCCESS:
                if (viewPager.getCurrentItem() == 0) {

                    devices.clear();

                    for (Map.Entry<String, EspNode> entry : espApp.nodeMap.entrySet()) {

                        String key = entry.getKey();
                        EspNode node = entry.getValue();

                        if (node != null) {
                            ArrayList<Device> espDevices = node.getDevices();
                            devices.addAll(espDevices);
                        }
                    }
                    Log.d(TAG, "Device list size : " + devices.size());

                    if (devices.size() > 0) {

                        ivAddDevice.setVisibility(View.VISIBLE);

                    } else {
                        ivAddDevice.setVisibility(View.GONE);
                    }

                } else if (viewPager.getCurrentItem() == 1) {

                    schedules.clear();

                    for (Map.Entry<String, Schedule> entry : espApp.scheduleMap.entrySet()) {

                        String key = entry.getKey();
                        Schedule schedule = entry.getValue();

                        if (schedule != null) {
                            schedules.add(schedule);
                        }
                    }
                    Log.d(TAG, "Schedules size : " + schedules.size());

                    if (schedules.size() > 0) {

                        ivAddDevice.setVisibility(View.VISIBLE);

                    } else {
                        ivAddDevice.setVisibility(View.GONE);
                    }
                }
                break;

            case GET_DATA_FAILED:
                break;
        }

        if (updateListenerArrayList != null) {
            for (UiUpdateListener listener : updateListenerArrayList) {
                listener.updateUi();
            }
        }
    }

    private void goToAddDeviceActivity() {

        Intent intent = new Intent(this, AddDeviceActivity.class);
        startActivity(intent);
    }

    private void goToAddScheduleActivity() {

        Intent intent = new Intent(this, AddScheduleActivity.class);
        startActivity(intent);
    }

    private void alertForForceUpdate(String message) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setCancelable(false);

        builder.setTitle(R.string.dialog_title_new_version_available);
        builder.setMessage(message);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_update, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                } catch (android.content.ActivityNotFoundException anfe) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                }
            }
        });

        builder.show();
    }

    private void alertForNewVersion(String message) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setCancelable(true);

        builder.setTitle(R.string.dialog_title_new_version_available);
        builder.setMessage(message);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_update, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                } catch (android.content.ActivityNotFoundException anfe) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                }
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        builder.show();
    }

    private void askForLocation() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setCancelable(true);
        builder.setMessage(R.string.dialog_msg_for_gps);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_LOCATION);
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private boolean isLocationEnabled() {

        boolean gps_enabled = false;
        boolean network_enabled = false;
        LocationManager lm = (LocationManager) getApplicationContext().getSystemService(Activity.LOCATION_SERVICE);

        try {
            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
        }

        try {
            network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
        }

        Log.d(TAG, "GPS Enabled : " + gps_enabled + " , Network Enabled : " + network_enabled);

        boolean result = gps_enabled || network_enabled;
        return result;
    }

    private void showLoading() {

        progressBar.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
    }

    public interface UiUpdateListener {

        void updateUi();
    }
}
