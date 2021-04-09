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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.db.EspDatabase;
import com.espressif.JsonDataParser;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.mdns.mDNSApiManager;
import com.espressif.mdns.mDNSDevice;
import com.espressif.mdns.mDNSManager;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.HomeScreenPagerAdapter;
import com.espressif.ui.fragments.DevicesFragment;
import com.espressif.ui.fragments.SchedulesFragment;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Group;
import com.espressif.ui.models.Schedule;
import com.espressif.ui.models.UpdateEvent;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Map;

import static com.espressif.EspApplication.GetDataStatus;

public class EspMainActivity extends AppCompatActivity {

    private static final String TAG = EspMainActivity.class.getSimpleName();

    private static final int REQUEST_LOCATION = 1;

    private BottomNavigationView bottomNavigationView;
    private ViewPager viewPager;
    private TextView tvTitle;
    private ImageView ivAddDevice, ivUserProfile;

    private Fragment deviceFragment;
    private Fragment scheduleFragment;
    private MenuItem prevMenuItem;
    private HomeScreenPagerAdapter pagerAdapter;
    private Snackbar snackbar;

    private ApiManager apiManager;
    private EspApplication espApp;
    private ArrayList<UiUpdateListener> updateListenerArrayList = new ArrayList<>();
    private ArrayList<Device> devices;
    private ArrayList<Schedule> schedules;

    private mDNSManager mdnsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp_main);

        devices = new ArrayList<>();
        schedules = new ArrayList<>();
        espApp = (EspApplication) getApplicationContext();

        apiManager = ApiManager.getInstance(getApplicationContext());

        if (BuildConfig.isLocalControlSupported) {
            mdnsManager = mDNSManager.getInstance(getApplicationContext(), AppConstants.MDNS_SERVICE_TYPE, listener);
            mdnsManager.initializeNsd();
        }
        initViews();

        snackbar = Snackbar.make(findViewById(R.id.frame_container), R.string.msg_fetch_data, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();

        loadDataFromLocalStorage();

        if (BuildConfig.isLocalControlSupported) {
            startLocalDeviceDiscovery();
        }

        getSupportedVersions();
    }

    @Override
    protected void onResume() {
        super.onResume();

        EventBus.getDefault().register(this);

        if (!espApp.getCurrentStatus().equals(GetDataStatus.FETCHING_DATA)) {
            getNodes();
        } else {
            updateUi();
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
        if (BuildConfig.isLocalControlSupported) {
            mdnsManager.stopDiscovery();
        }
        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UpdateEvent event) {

        Log.d(TAG, "Update Event Received : " + event.getEventType());

        switch (event.getEventType()) {

            case EVENT_DEVICE_ADDED:
            case EVENT_DEVICE_REMOVED:
                refreshDeviceList();
                break;

            case EVENT_DEVICE_STATUS_UPDATE:
                if (!espApp.getCurrentStatus().equals(GetDataStatus.FETCHING_DATA)) {
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

    public void refreshDeviceList() {

        espApp.setCurrentStatus(GetDataStatus.DATA_REFRESHING);
        getNodes();
    }

    private void initViews() {

        tvTitle = findViewById(R.id.esp_toolbar_title);
        ivAddDevice = findViewById(R.id.btn_add_device);
        ivUserProfile = findViewById(R.id.btn_user_profile);
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

    private void loadDataFromLocalStorage() {

        EspDatabase espDatabase = EspDatabase.getInstance(getApplicationContext());
        ArrayList<EspNode> nodeList = (ArrayList<EspNode>) espDatabase.getNodeDao().getNodesFromStorage();
        Log.d(TAG, "Node list from Local storage : " + nodeList.size());
        devices.clear();

        for (int nodeIndex = 0; nodeIndex < nodeList.size(); nodeIndex++) {

            EspNode node = nodeList.get(nodeIndex);

            if (node != null) {

                String configData = node.getConfigData();
                String paramData = node.getParamData();

                try {
                    node = JsonDataParser.setNodeConfig(node, new JSONObject(configData));
                    JSONObject paramsJson = new JSONObject(paramData);
                    JsonDataParser.setAllParams(espApp, node, paramsJson);
                    espApp.nodeMap.put(node.getNodeId(), node);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        // Set all devices offline
        for (Map.Entry<String, EspNode> entry : espApp.nodeMap.entrySet()) {

            String key = entry.getKey();
            EspNode node = entry.getValue();

            if (node != null) {
                node.setOnline(false);
                ArrayList<Device> espDevices = node.getDevices();
                devices.addAll(espDevices);
            }
        }

        if (BuildConfig.isNodeGroupingSupported) {
            ArrayList<Group> groupList = (ArrayList<Group>) espDatabase.getGroupDao().getGroupsFromStorage();
            for (int groupIndex = 0; groupIndex < groupList.size(); groupIndex++) {

                Group group = groupList.get(groupIndex);
                if (group != null) {
                    espApp.groupMap.put(group.getGroupId(), group);
                }
            }
        }
        Log.d(TAG, "Device list size from local storage : " + devices.size());
    }

    private void updateUi() {

        switch (espApp.getCurrentStatus()) {

            case FETCHING_DATA:
                if (devices.size() > 0) {
                    ivAddDevice.setVisibility(View.VISIBLE);
                } else {
                    ivAddDevice.setVisibility(View.GONE);
                }
                break;

            case GET_DATA_SUCCESS:
                snackbar.dismiss();
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

                    if (schedules.size() > 0) {

                        ivAddDevice.setVisibility(View.VISIBLE);

                    } else {
                        ivAddDevice.setVisibility(View.GONE);
                    }
                }
                break;

            case GET_DATA_FAILED:

                for (Map.Entry<String, EspNode> entry : espApp.nodeMap.entrySet()) {

                    EspNode node = entry.getValue();

                    if (node != null && !espApp.mDNSDeviceMap.containsKey(node.getNodeId())) {
                        node.setOnline(false);
                    }
                }

                TextView tvSnackbarText = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
                tvSnackbarText.setText(R.string.msg_no_internet);

                if (!snackbar.isShown()) {
                    snackbar = Snackbar.make(findViewById(R.id.frame_container), R.string.msg_no_internet, Snackbar.LENGTH_INDEFINITE);
                    snackbar.show();
                }
                break;
        }

        if (updateListenerArrayList != null) {
            for (UiUpdateListener listener : updateListenerArrayList) {
                listener.updateUi();
            }
        }
    }

    private void startLocalDeviceDiscovery() {
        if (espApp.nodeMap.size() > 0) {
            mdnsManager.discoverServices();
        }
    }

    private void getSupportedVersions() {

        apiManager.getSupportedVersions(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                String updateMsg = data.getString(AppConstants.KEY_ADDITIONAL_INFO);
                ArrayList<String> versions = data.getStringArrayList(AppConstants.KEY_SUPPORTED_VERSIONS);

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

                espApp.setCurrentStatus(GetDataStatus.GET_DATA_FAILED);
                updateUi();
            }
        });
    }

    private int getVersionNumber(String versionString) {

        versionString = versionString.replace("v", "");
        int version = Integer.valueOf(versionString);
        return version;
    }

    private void getNodes() {

        if (BuildConfig.isLocalControlSupported) {
            startLocalDeviceDiscovery();
        }

        if (apiManager.isTokenExpired()) {

            apiManager.getNewToken(new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {

                    getNodesFromCloud();
                }

                @Override
                public void onFailure(Exception exception) {
                    exception.printStackTrace();
                    espApp.setCurrentStatus(GetDataStatus.GET_DATA_FAILED);
                    updateUi();
                }
            });

        } else {
            getNodesFromCloud();
        }
    }

    private void getNodesFromCloud() {

        apiManager.getNodes(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                if (BuildConfig.isNodeGroupingSupported) {
                    apiManager.getUserGroups(null, new ApiResponseListener() {

                        @Override
                        public void onSuccess(Bundle data) {
                            espApp.setCurrentStatus(GetDataStatus.GET_DATA_SUCCESS);
                            updateUi();
                        }

                        @Override
                        public void onFailure(Exception exception) {
                            exception.printStackTrace();
                            espApp.setCurrentStatus(GetDataStatus.GET_DATA_FAILED);
                            updateUi();
                        }
                    });
                } else {
                    espApp.setCurrentStatus(GetDataStatus.GET_DATA_SUCCESS);
                    updateUi();
                }
            }

            @Override
            public void onFailure(Exception exception) {
                exception.printStackTrace();
                espApp.setCurrentStatus(GetDataStatus.GET_DATA_FAILED);
                updateUi();
            }
        });
    }

    private void goToAddDeviceActivity() {

        if (BuildConfig.isQRCodeSupported) {
            Intent intent = new Intent(this, AddDeviceActivity.class);
            startActivity(intent);
        } else {

            boolean isSec1 = true;
            ESPProvisionManager provisionManager = ESPProvisionManager.getInstance(getApplicationContext());

            if (AppConstants.SECURITY_0.equalsIgnoreCase(BuildConfig.SECURITY)) {
                isSec1 = false;
            }

            if (AppConstants.TRANSPORT_SOFTAP.equalsIgnoreCase(BuildConfig.TRANSPORT)) {

                if (isSec1) {
                    provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, ESPConstants.SecurityType.SECURITY_1);
                } else {
                    provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, ESPConstants.SecurityType.SECURITY_0);
                }
                goToWiFiProvisionLanding(isSec1);

            } else if (AppConstants.TRANSPORT_BLE.equalsIgnoreCase(BuildConfig.TRANSPORT)) {

                if (isSec1) {
                    provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_1);
                } else {
                    provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_0);
                }
                goToBLEProvisionLanding(isSec1);

            } else if (AppConstants.TRANSPORT_BOTH.equalsIgnoreCase(BuildConfig.TRANSPORT)) {

                askForDeviceType(isSec1);

            } else {
                Toast.makeText(this, R.string.error_device_type_not_supported, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void goToAddScheduleActivity() {

        Intent intent = new Intent(this, AddScheduleActivity.class);
        startActivity(intent);
    }

    private void askForDeviceType(final boolean isSec1) {

        final String[] deviceTypes = getResources().getStringArray(R.array.prov_transport_types);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle(R.string.dialog_msg_device_selection);
        final ESPProvisionManager provisionManager = ESPProvisionManager.getInstance(getApplicationContext());

        builder.setItems(deviceTypes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int position) {

                switch (position) {
                    case 0:
                        if (isSec1) {
                            provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_1);
                        } else {
                            provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_0);
                        }
                        goToBLEProvisionLanding(isSec1);
                        break;

                    case 1:
                        if (isSec1) {
                            provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, ESPConstants.SecurityType.SECURITY_1);
                        } else {
                            provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, ESPConstants.SecurityType.SECURITY_0);
                        }
                        goToWiFiProvisionLanding(isSec1);
                        break;
                }
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void goToBLEProvisionLanding(boolean isSec1) {

        Intent intent = new Intent(getApplicationContext(), BLEProvisionLanding.class);
        if (isSec1) {
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, AppConstants.SECURITY_1);
        } else {
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, AppConstants.SECURITY_0);
        }
        startActivity(intent);
    }

    private void goToWiFiProvisionLanding(boolean isSec1) {

        Intent intent = new Intent(getApplicationContext(), ProvisionLanding.class);
        if (isSec1) {
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, AppConstants.SECURITY_1);
        } else {
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, AppConstants.SECURITY_0);
        }
        startActivity(intent);
    }

    private void alertForForceUpdate(String message) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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

    public interface UiUpdateListener {

        void updateUi();
    }

    mDNSManager.mDNSEvenListener listener = new mDNSManager.mDNSEvenListener() {

        @Override
        public void deviceFound(final mDNSDevice dnsDevice) {

            Log.e(TAG, "deviceFound on local network");
            final String url = "http://" + dnsDevice.getIpAddr() + ":" + dnsDevice.getPort();
            final mDNSApiManager dnsMsgHelper = new mDNSApiManager(getApplicationContext());

            dnsMsgHelper.getPropertyCount(url, AppConstants.LOCAL_CONTROL_PATH, new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {

                    if (data != null) {

                        int count = data.getInt(AppConstants.KEY_PROPERTY_COUNT, 0);
                        dnsDevice.setPropertyCount(count);

                        dnsMsgHelper.getPropertyValues(url, AppConstants.LOCAL_CONTROL_PATH, count, new ApiResponseListener() {

                            @Override
                            public void onSuccess(Bundle data) {

                                if (data != null) {

                                    String configData = data.getString(AppConstants.KEY_CONFIG);
                                    String paramsData = data.getString(AppConstants.KEY_PARAMS);

                                    Log.d(TAG, "Config data : " + configData);
                                    Log.d(TAG, "Params data : " + paramsData);

                                    if (!TextUtils.isEmpty(configData)) {

                                        JSONObject configJson = null;
                                        try {
                                            configJson = new JSONObject(configData);
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }

                                        boolean isDeviceFound = false;
                                        EspNode localNode = JsonDataParser.setNodeConfig(null, configJson);
                                        EspNode node = espApp.nodeMap.get(localNode.getNodeId());

                                        if (node != null) {
                                            Log.e(TAG, "Found node " + localNode.getNodeId() + " on local network.");
                                            isDeviceFound = true;
                                            localNode.setAvailableLocally(true);
                                            localNode.setIpAddress(dnsDevice.getIpAddr());
                                            localNode.setPort(dnsDevice.getPort());
                                            localNode.setOnline(true);
                                            espApp.mDNSDeviceMap.put(localNode.getNodeId(), dnsDevice);
                                        }

                                        if (!TextUtils.isEmpty(paramsData) && isDeviceFound) {

                                            JSONObject paramsJson = null;
                                            try {
                                                paramsJson = new JSONObject(paramsData);
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                            JsonDataParser.setAllParams(espApp, localNode, paramsJson);
                                            espApp.nodeMap.put(localNode.getNodeId(), localNode);
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    updateUi();
                                                }
                                            });
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onFailure(Exception exception) {
                                // Nothing to do
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Exception exception) {
                    // Nothing to do
                }
            });
        }
    };
}
