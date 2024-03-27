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

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.NetworkApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.local_control.EspLocalDevice;
import com.espressif.matter.ControllerClusterHelper;
import com.espressif.matter.ControllerLoginActivity;
import com.espressif.matter.DeviceMatterInfo;
import com.espressif.matter.ThreadBRActivity;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.AttrParamAdapter;
import com.espressif.ui.adapters.ParamAdapter;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.UpdateEvent;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

public class EspDeviceActivity extends AppCompatActivity {

    private static final String TAG = EspDeviceActivity.class.getSimpleName();

    private static final int NODE_DETAILS_ACTIVITY_REQUEST = 10;
    private static final int UPDATE_INTERVAL = 5000;
    private static final int UI_UPDATE_INTERVAL = 4500;

    private RelativeLayout rlNodeStatus;
    private RelativeLayout rlControllerLogin, rlMatterController, rlTbr;
    private TextView tvTbrSetup, tvControllerLogin;
    private TextView tvNoParam, tvNodeStatus;
    private ImageView ivSecureLocal;
    private AppCompatButton btnUpdate;
    private RecyclerView paramRecyclerView;
    private RecyclerView attrRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Snackbar snackbar;

    private Device device;
    private EspApplication espApp;
    private NetworkApiManager networkApiManager;
    private ParamAdapter paramAdapter;
    private AttrParamAdapter attrAdapter;
    private ArrayList<Param> paramList;
    private ArrayList<Param> attributeList;
    private Handler handler;
    private ContentLoadingProgressBar progressBar;
    private boolean isNodeOnline;
    private long timeStampOfStatus;
    private boolean isNetworkAvailable = true;
    private boolean shouldGetParams = true;
    private String nodeId;
    private RelativeLayout rlProgress, rlParam;
    private boolean isUpdateView = true;
    private long lastUpdateRequestTime = 0;

    private boolean isControllerClusterAvailable = false, isTbrClusterAvailable = false;
    private String nodeType, matterNodeId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp_device);

        espApp = (EspApplication) getApplicationContext();
        networkApiManager = new NetworkApiManager(getApplicationContext());
        handler = new Handler();
        device = getIntent().getParcelableExtra(AppConstants.KEY_ESP_DEVICE);

        if (device == null) {
            Log.e(TAG, "DEVICE IS NULL");
            finish();
        } else {
            nodeId = device.getNodeId();
            Log.d(TAG, "NODE ID : " + nodeId);

            isNodeOnline = espApp.nodeMap.get(nodeId).isOnline();
            nodeType = espApp.nodeMap.get(nodeId).getNewNodeType();
            timeStampOfStatus = espApp.nodeMap.get(nodeId).getTimeStampOfStatus();
            snackbar = Snackbar.make(findViewById(R.id.params_parent_layout), R.string.msg_no_internet, Snackbar.LENGTH_INDEFINITE);

            if (TextUtils.isEmpty(nodeType)) {
                nodeType = AppConstants.NODE_TYPE_RM;
            }

            if (nodeType.equals(AppConstants.NODE_TYPE_PURE_MATTER)
                    || nodeType.equals(AppConstants.NODE_TYPE_RM_MATTER)) {

                if (espApp.matterRmNodeIdMap.containsKey(nodeId)) {
                    matterNodeId = espApp.matterRmNodeIdMap.get(nodeId);
                }

                if (!TextUtils.isEmpty(matterNodeId) && espApp.availableMatterDevices.contains(matterNodeId)
                        && espApp.matterDeviceInfoMap.containsKey(matterNodeId)) {

                    List<DeviceMatterInfo> deviceMatterInfo = espApp.matterDeviceInfoMap.get(matterNodeId);

                    if (deviceMatterInfo != null && !deviceMatterInfo.isEmpty()) {
                        for (DeviceMatterInfo info : deviceMatterInfo) {
                            if (info.getEndpoint() == 0 && info.getServerClusters() != null && !info.getServerClusters().isEmpty()) {
                                List<Object> serverClusters = info.getServerClusters();
                                for (Object serverCluster : serverClusters) {
                                    Long id = (Long) serverCluster;
                                    if (id == AppConstants.CONTROLLER_CLUSTER_ID) {
                                        isControllerClusterAvailable = true;
                                    } else if (id == AppConstants.THREAD_BR_CLUSTER_ID) {
                                        isTbrClusterAvailable = true;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Matter device info not available");
                }
            } else {
                Log.d(TAG, "RainMaker device type");
            }
            setParamList(device.getParams());
            initViews();
            updateUi();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getNodeDetails();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopUpdateValueTask();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onDestroy() {
        stopUpdateValueTask();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, 1, Menu.NONE, R.string.btn_info).setIcon(R.drawable.ic_node_info).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case 1:
                goToNodeDetailsActivity();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == NODE_DETAILS_ACTIVITY_REQUEST && resultCode == RESULT_OK) {
            finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UpdateEvent event) {

        Log.d(TAG, "Update Event Received : " + event.getEventType());

        switch (event.getEventType()) {

            case EVENT_DEVICE_ADDED:
            case EVENT_DEVICE_REMOVED:
            case EVENT_STATE_CHANGE_UPDATE:
            case EVENT_LOCAL_DEVICE_UPDATE:
            case EVENT_DEVICE_ONLINE:
            case EVENT_DEVICE_OFFLINE:
                // TODO
                break;

            case EVENT_DEVICE_STATUS_UPDATE:
                long currentTime = System.currentTimeMillis();
                if (BuildConfig.isContinuousUpdateEnable) {
                    if (isUpdateView && currentTime - lastUpdateRequestTime > UI_UPDATE_INTERVAL) {
                        handler.removeCallbacks(updateViewTask);
                        handler.post(updateViewTask);
                    }
                } else {
                    updateUi();
                }
                break;

            case EVENT_MATTER_DEVICE_CONNECTIVITY:
                updateUi();
                break;
        }
    }

    Runnable updateViewTask = this::updateUi;

    public void updateDeviceNameInTitle(String deviceName) {
        getSupportActionBar().setTitle(deviceName);
    }

    public boolean isNodeOnline() {
        return isNodeOnline;
    }

    public void setIsUpdateView(boolean isUpdateView) {
        this.isUpdateView = isUpdateView;
    }

    public void setLastUpdateRequestTime(long lastUpdateRequestTime) {
        this.lastUpdateRequestTime = lastUpdateRequestTime;
    }

    public void startUpdateValueTask() {
        if (!TextUtils.isEmpty(nodeType) && nodeType.equals(AppConstants.NODE_TYPE_PURE_MATTER)) {
            return;
        }
        shouldGetParams = true;
        handler.removeCallbacks(updateValuesTask);
        handler.postDelayed(updateValuesTask, UPDATE_INTERVAL);
    }

    public void stopUpdateValueTask() {
        shouldGetParams = false;
        handler.removeCallbacks(updateValuesTask);
    }

    private void goToNodeDetailsActivity() {

        Intent intent = new Intent(EspDeviceActivity.this, NodeDetailsActivity.class);
        intent.putExtra(AppConstants.KEY_NODE_ID, nodeId);
        startActivityForResult(intent, NODE_DETAILS_ACTIVITY_REQUEST);
    }

    private final Runnable updateValuesTask = new Runnable() {
        @Override
        public void run() {
            if (shouldGetParams) {

                if (BuildConfig.isContinuousUpdateEnable) {
                    long currentTime = System.currentTimeMillis();
                    if (isUpdateView && currentTime - lastUpdateRequestTime >= UI_UPDATE_INTERVAL) {
                        getValues();
                    } else {
                        handler.removeCallbacks(updateValuesTask);
                        handler.postDelayed(updateValuesTask, UI_UPDATE_INTERVAL);
                    }
                } else {
                    getValues();
                }
            }
        }
    };

    private void initViews() {

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        tvNoParam = findViewById(R.id.tv_no_params);
        progressBar = findViewById(R.id.progress_get_params);

        rlParam = findViewById(R.id.params_parent_layout);
        rlProgress = findViewById(R.id.rl_progress);

        rlNodeStatus = findViewById(R.id.rl_node_status);
        rlMatterController = findViewById(R.id.rl_matter_controller);
        rlControllerLogin = findViewById(R.id.rl_controller_login);
        rlTbr = findViewById(R.id.rl_thread_br);
        tvNodeStatus = findViewById(R.id.tv_device_status);
        ivSecureLocal = findViewById(R.id.iv_secure_local);
        btnUpdate = findViewById(R.id.btn_update);
        tvTbrSetup = findViewById(R.id.tv_tbr_setup);
        tvControllerLogin = findViewById(R.id.tv_controller_login);

        getSupportActionBar().setTitle(device.getUserVisibleName());

        paramRecyclerView = findViewById(R.id.rv_dynamic_param_list);
        attrRecyclerView = findViewById(R.id.rv_static_param_list);
        swipeRefreshLayout = findViewById(R.id.swipe_container);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        paramRecyclerView.setLayoutManager(linearLayoutManager);

        LinearLayoutManager linearLayoutManager1 = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager1.setOrientation(RecyclerView.VERTICAL);
        attrRecyclerView.setLayoutManager(linearLayoutManager1);

        paramAdapter = new ParamAdapter(this, device, paramList);
        paramRecyclerView.setAdapter(paramAdapter);

        attrAdapter = new AttrParamAdapter(this, device, attributeList);
        attrRecyclerView.setAdapter(attrAdapter);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {

            @Override
            public void onRefresh() {
                getNodeDetails();
            }
        });

        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BigInteger id = new BigInteger(matterNodeId, 16);
                long deviceId = id.longValue();
                if (espApp.chipClientMap.containsKey(matterNodeId)) {
                    ControllerClusterHelper espClusterHelper = new ControllerClusterHelper(espApp.chipClientMap.get(matterNodeId), espApp);
                    espClusterHelper.sendUpdateDeviceListEventAsync(deviceId, AppConstants.ENDPOINT_0, AppConstants.CONTROLLER_CLUSTER_ID_HEX);
                }
            }
        });

        tvControllerLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(EspDeviceActivity.this, ControllerLoginActivity.class);
                intent.putExtra(AppConstants.KEY_NODE_ID, nodeId);
                startActivity(intent);
            }
        });

        tvTbrSetup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(EspDeviceActivity.this, ThreadBRActivity.class);
                intent.putExtra(AppConstants.KEY_NODE_ID, nodeId);
                startActivity(intent);
            }
        });

        if (isControllerClusterAvailable) {
            rlControllerLogin.setVisibility(View.VISIBLE);
            rlMatterController.setVisibility(View.VISIBLE);
        } else {
            rlControllerLogin.setVisibility(View.GONE);
            rlMatterController.setVisibility(View.GONE);
        }

        if (isTbrClusterAvailable) {
            rlTbr.setVisibility(View.VISIBLE);
        } else {
            rlTbr.setVisibility(View.GONE);
        }
    }

    private void getNodeDetails() {

        stopUpdateValueTask();

        networkApiManager.getNodeDetails(nodeId, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        isNetworkAvailable = true;
                        hideLoading();
                        snackbar.dismiss();
                        swipeRefreshLayout.setRefreshing(false);
                        updateUi();
                        startUpdateValueTask();
                    }
                });
            }

            @Override
            public void onResponseFailure(Exception exception) {

                isNetworkAvailable = true;
                hideLoading();
                snackbar.dismiss();
                swipeRefreshLayout.setRefreshing(false);
                if (exception instanceof CloudException) {
                    Toast.makeText(EspDeviceActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(EspDeviceActivity.this, "Failed to get node details", Toast.LENGTH_SHORT).show();
                }
                updateUi();
            }

            @Override
            public void onNetworkFailure(Exception exception) {

                hideLoading();
                swipeRefreshLayout.setRefreshing(false);
                if (exception instanceof CloudException) {
                    Toast.makeText(EspDeviceActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(EspDeviceActivity.this, "Failed to get node details", Toast.LENGTH_SHORT).show();
                }
                updateUi();
            }
        });
    }

    private void getValues() {

        networkApiManager.getParamsValues(nodeId, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        isNetworkAvailable = true;
                        hideLoading();
                        swipeRefreshLayout.setRefreshing(false);
                        updateUi();
                        handler.removeCallbacks(updateValuesTask);
                        handler.postDelayed(updateValuesTask, UPDATE_INTERVAL);
                    }
                });
            }

            @Override
            public void onResponseFailure(Exception exception) {

                stopUpdateValueTask();
                isNetworkAvailable = true;
                hideLoading();
                swipeRefreshLayout.setRefreshing(false);
                if (exception instanceof CloudException) {
                    Toast.makeText(EspDeviceActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(EspDeviceActivity.this, "Failed to get param values", Toast.LENGTH_SHORT).show();
                }
                updateUi();
            }

            @Override
            public void onNetworkFailure(Exception exception) {

                stopUpdateValueTask();
                hideLoading();
                swipeRefreshLayout.setRefreshing(false);
                if (exception instanceof CloudException) {
                    Toast.makeText(EspDeviceActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(EspDeviceActivity.this, "Failed to get param values", Toast.LENGTH_SHORT).show();
                }
                updateUi();
            }
        });
    }

    private void setParamList(ArrayList<Param> paramArrayList) {

        ArrayList<Param> params = new ArrayList<>();
        ArrayList<Param> attributes = new ArrayList<>();

        if (paramArrayList != null) {
            for (int i = 0; i < paramArrayList.size(); i++) {

                Param param = paramArrayList.get(i);
                if (param.isDynamicParam()) {
                    params.add(new Param(param));
                } else {
                    attributes.add(new Param(param));
                }
            }
            arrangeParamList(params);
        }

        if (paramList == null || attributeList == null) {
            paramList = new ArrayList<>();
            attributeList = new ArrayList<>();
            paramList.addAll(params);
            attributeList.addAll(attributes);
        } else {
            paramAdapter.updateParamList(params);
            attrAdapter.updateAttributeList(attributes);
        }
    }

    private void arrangeParamList(ArrayList<Param> paramList) {

        int firstParamIndex = -1;
        for (int i = 0; i < paramList.size(); i++) {

            Param param = paramList.get(i);
            if (param != null && AppConstants.UI_TYPE_HUE_CIRCLE.equalsIgnoreCase(param.getUiType())) {
                firstParamIndex = i;
                break;
            }
        }

        if (firstParamIndex != -1) {
            Param paramToBeMoved = paramList.remove(firstParamIndex);
            paramList.add(0, paramToBeMoved);
        } else {

            for (int i = 0; i < paramList.size(); i++) {

                Param param = paramList.get(i);
                if (param != null) {
                    String dataType = param.getDataType();
                    if (AppConstants.UI_TYPE_PUSH_BTN_BIG.equalsIgnoreCase(param.getUiType())
                            && (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")))) {
                        firstParamIndex = i;
                        break;
                    }
                }
            }

            if (firstParamIndex != -1) {
                Param paramToBeMoved = paramList.remove(firstParamIndex);
                paramList.add(0, paramToBeMoved);
            }
        }

        int paramNameIndex = -1;
        for (int i = 0; i < paramList.size(); i++) {

            Param param = paramList.get(i);
            if (param != null) {
                if (param.getParamType() != null && param.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                    paramNameIndex = i;
                    break;
                }
            }
        }

        if (paramNameIndex != -1) {
            Param paramToBeMoved = paramList.remove(paramNameIndex);
            if (firstParamIndex != -1) {
                paramList.add(1, paramToBeMoved);
            } else {
                paramList.add(0, paramToBeMoved);
            }
        }

        Iterator<Param> paramIterator = paramList.iterator();
        while (paramIterator.hasNext()) {
            Param p = paramIterator.next();
            if (p.getUiType() != null && p.getUiType().equals(AppConstants.UI_TYPE_HIDDEN)) {
                paramIterator.remove();
            }
        }
    }

    private void updateUi() {

        boolean deviceFound = false;
        Device updatedDevice = null;
        lastUpdateRequestTime = System.currentTimeMillis();

        if (espApp.nodeMap.containsKey(nodeId)) {

            ArrayList<Device> devices = espApp.nodeMap.get(nodeId).getDevices();
            isNodeOnline = espApp.nodeMap.get(nodeId).isOnline();
            timeStampOfStatus = espApp.nodeMap.get(nodeId).getTimeStampOfStatus();

            for (int i = 0; i < devices.size(); i++) {

                if (device.getDeviceName() != null && device.getDeviceName().equals(devices.get(i).getDeviceName())) {
                    updatedDevice = new Device(devices.get(i));
                    deviceFound = true;
                    break;
                }
            }
        } else {
            Log.e(TAG, "Node does not exist in list. It may be deleted.");
            finish();
            return;
        }

        boolean isMatterOnly = false;
        if (!TextUtils.isEmpty(nodeType) && nodeType.equals(AppConstants.NODE_TYPE_PURE_MATTER)) {
            isMatterOnly = true;
        }

        if (!deviceFound && !isMatterOnly) {
            Log.e(TAG, "Device does not exist in node list.");
            finish();
            return;
        }

        if (updatedDevice == null) {
            updatedDevice = device;
        }

        setParamList(updatedDevice.getParams());

        if (!isNodeOnline) {

            if (espApp.getAppState().equals(EspApplication.AppState.GET_DATA_SUCCESS)) {

                rlNodeStatus.setVisibility(View.VISIBLE);

                if (espApp.localDeviceMap.containsKey(nodeId)) {

                    EspLocalDevice localDevice = espApp.localDeviceMap.get(nodeId);
                    if (localDevice.getSecurityType() == 1 || localDevice.getSecurityType() == 2) {
                        ivSecureLocal.setVisibility(View.VISIBLE);
                    } else {
                        ivSecureLocal.setVisibility(View.GONE);
                    }
                    tvNodeStatus.setText(R.string.local_device_text);

                } else if (!TextUtils.isEmpty(matterNodeId) && espApp.availableMatterDevices.contains(matterNodeId)
                        && espApp.matterDeviceInfoMap.containsKey(matterNodeId)) {

                    rlNodeStatus.setVisibility(View.VISIBLE);
                    tvNodeStatus.setText(R.string.status_local);

                } else {
                    ivSecureLocal.setVisibility(View.GONE);
                    String offlineText = getString(R.string.status_offline);
                    tvNodeStatus.setText(offlineText);

                    if (timeStampOfStatus != 0) {

                        Calendar calendar = Calendar.getInstance();
                        int day = calendar.get(Calendar.DATE);

                        calendar.setTimeInMillis(timeStampOfStatus);
                        int offlineDay = calendar.get(Calendar.DATE);

                        if (day == offlineDay) {

                            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");
                            String time = formatter.format(calendar.getTime());
                            offlineText = getString(R.string.offline_at) + " " + time;

                        } else {

                            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yy, HH:mm");
                            String time = formatter.format(calendar.getTime());
                            offlineText = getString(R.string.offline_at) + " " + time;
                        }
                        tvNodeStatus.setText(offlineText);
                    }
                }
            } else {
                rlNodeStatus.setVisibility(View.INVISIBLE);
            }

        } else {

            if (!TextUtils.isEmpty(matterNodeId) && espApp.availableMatterDevices.contains(matterNodeId)
                    && espApp.matterDeviceInfoMap.containsKey(matterNodeId)) {

                rlNodeStatus.setVisibility(View.VISIBLE);
                tvNodeStatus.setText(R.string.status_local);
            } else {
                rlNodeStatus.setVisibility(View.INVISIBLE);
            }

            if (espApp.localDeviceMap.containsKey(nodeId)) {

                rlNodeStatus.setVisibility(View.VISIBLE);
                EspLocalDevice localDevice = espApp.localDeviceMap.get(nodeId);
                if (localDevice.getSecurityType() == 1 || localDevice.getSecurityType() == 2) {
                    ivSecureLocal.setVisibility(View.VISIBLE);
                } else {
                    ivSecureLocal.setVisibility(View.GONE);
                }
                tvNodeStatus.setText(R.string.local_device_text);
            } else {
                rlNodeStatus.setVisibility(View.INVISIBLE);
            }
        }

        paramAdapter.updateParamList(paramList);
        attrAdapter.updateAttributeList(attributeList);

        if (paramList.size() <= 0 && attributeList.size() <= 0) {

            tvNoParam.setVisibility(View.VISIBLE);
            paramRecyclerView.setVisibility(View.GONE);
            attrRecyclerView.setVisibility(View.GONE);

        } else {

            tvNoParam.setVisibility(View.GONE);
            paramRecyclerView.setVisibility(View.VISIBLE);
            attrRecyclerView.setVisibility(View.VISIBLE);
        }

        getSupportActionBar().setTitle(device.getUserVisibleName());

        if (!isNetworkAvailable) {
            if (!snackbar.isShown()) {
                snackbar = Snackbar.make(findViewById(R.id.params_parent_layout), R.string.msg_no_internet, Snackbar.LENGTH_INDEFINITE);
            }
            snackbar.show();
        }
    }

    private void showLoading() {

        progressBar.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setVisibility(View.GONE);
    }

    private void hideLoading() {

        progressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
    }

    public void showParamUpdateLoading(String msg) {
        rlParam.setAlpha(0.3f);
        rlProgress.setVisibility(View.VISIBLE);
        TextView progressText = findViewById(R.id.tv_loading);
        progressText.setText(msg);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    public void hideParamUpdateLoading() {
        rlParam.setAlpha(1);
        rlProgress.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }
}
