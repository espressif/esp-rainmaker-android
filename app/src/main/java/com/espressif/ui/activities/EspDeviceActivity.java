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
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.NetworkApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.AttrParamAdapter;
import com.espressif.ui.adapters.ParamAdapter;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import static com.espressif.EspApplication.GetDataStatus;

public class EspDeviceActivity extends AppCompatActivity {

    private static final String TAG = EspDeviceActivity.class.getSimpleName();

    private static final int NODE_DETAILS_ACTIVITY_REQUEST = 10;
    private static final int UPDATE_INTERVAL = 5000;

    private TextView tvTitle, ivNodeInfo, tvBack, tvNoParam, tvNodeOffline;
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
    private boolean isNetworkAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp_device);

        Window window = EspDeviceActivity.this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(ContextCompat.getColor(EspDeviceActivity.this,R.color.color_actionbar_bg));
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (currentNightMode){
            case Configuration.UI_MODE_NIGHT_NO:
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                break;
            case Configuration.UI_MODE_NIGHT_YES:
                window.getDecorView().setSystemUiVisibility(window.getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                break;
        }

        espApp = (EspApplication) getApplicationContext();
        networkApiManager = new NetworkApiManager(getApplicationContext());
        device = getIntent().getParcelableExtra(AppConstants.KEY_ESP_DEVICE);
        handler = new Handler();
        isNodeOnline = espApp.nodeMap.get(device.getNodeId()).isOnline();
        timeStampOfStatus = espApp.nodeMap.get(device.getNodeId()).getTimeStampOfStatus();
        snackbar = Snackbar.make(findViewById(R.id.params_parent_layout), R.string.msg_no_internet, Snackbar.LENGTH_INDEFINITE);
        ArrayList<Param> espDeviceParams = device.getParams();
        setParamList(espDeviceParams);

        initViews();
        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getNodeDetails();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopUpdateValueTask();
    }

    @Override
    protected void onDestroy() {
        stopUpdateValueTask();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == NODE_DETAILS_ACTIVITY_REQUEST && resultCode == RESULT_OK) {
            finish();
        }
    }

    public void setDeviceName(String deviceName) {
        tvTitle.setText(deviceName);
    }

    public boolean isNodeOnline() {
        return isNodeOnline;
    }

    public void startUpdateValueTask() {
        handler.removeCallbacks(updateValuesTask);
        handler.postDelayed(updateValuesTask, UPDATE_INTERVAL);
    }

    public void stopUpdateValueTask() {
        handler.removeCallbacks(updateValuesTask);
    }

    private View.OnClickListener backButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            finish();
        }
    };

    private View.OnClickListener infoBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Intent intent = new Intent(EspDeviceActivity.this, NodeDetailsActivity.class);
            intent.putExtra(AppConstants.KEY_NODE_ID, device.getNodeId());
            startActivityForResult(intent, NODE_DETAILS_ACTIVITY_REQUEST);
        }
    };

    private Runnable updateValuesTask = new Runnable() {

        @Override
        public void run() {
            getValues();
            handler.postDelayed(updateValuesTask, UPDATE_INTERVAL);
        }
    };

    private void initViews() {

        tvTitle = findViewById(R.id.esp_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvNoParam = findViewById(R.id.tv_no_params);
        ivNodeInfo = findViewById(R.id.btn_info);
        progressBar = findViewById(R.id.progress_get_params);
        tvNodeOffline = findViewById(R.id.tv_device_offline);

        boolean isParamTypeNameAvailable = false;

        for (int i = 0; i < device.getParams().size(); i++) {

            Param p = device.getParams().get(i);
            if (p != null && p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                isParamTypeNameAvailable = true;
                tvTitle.setText(p.getLabelValue());
                break;
            }
        }

        if (!isParamTypeNameAvailable) {
            tvTitle.setText(device.getDeviceName());
        }

        tvBack.setVisibility(View.VISIBLE);

        paramRecyclerView = findViewById(R.id.rv_dynamic_param_list);
        attrRecyclerView = findViewById(R.id.rv_static_param_list);
        swipeRefreshLayout = findViewById(R.id.swipe_container);

        tvBack.setOnClickListener(backButtonClickListener);
        ivNodeInfo.setOnClickListener(infoBtnClickListener);

        // set a LinearLayoutManager with default orientation
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        paramRecyclerView.setLayoutManager(linearLayoutManager); // set LayoutManager to RecyclerView

        LinearLayoutManager linearLayoutManager1 = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager1.setOrientation(RecyclerView.VERTICAL);
        attrRecyclerView.setLayoutManager(linearLayoutManager1); // set LayoutManager to RecyclerView

        paramAdapter = new ParamAdapter(this, device.getNodeId(), device.getDeviceName(), paramList);
        paramRecyclerView.setAdapter(paramAdapter);

        attrAdapter = new AttrParamAdapter(this, device.getNodeId(), device.getDeviceName(), attributeList);
        attrRecyclerView.setAdapter(attrAdapter);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {

            @Override
            public void onRefresh() {
                getNodeDetails();
            }
        });
    }

    private void getNodeDetails() {

        stopUpdateValueTask();

        networkApiManager.getNodeDetails(device.getNodeId(), new ApiResponseListener() {

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
            public void onFailure(Exception exception) {

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

        networkApiManager.getParamsValues(device.getNodeId(), new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        isNetworkAvailable = true;
                        hideLoading();
                        swipeRefreshLayout.setRefreshing(false);
                        updateUi();
                    }
                });
            }

            @Override
            public void onFailure(Exception exception) {

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

        if (paramList == null || attributeList == null) {

            paramList = new ArrayList<>();
            attributeList = new ArrayList<>();
        }

        for (int i = 0; i < paramArrayList.size(); i++) {

            Param updatedParam = paramArrayList.get(i);
            boolean isFound = false;

            if (updatedParam.isDynamicParam()) {

                for (int j = 0; j < paramList.size(); j++) {

                    if (paramList.get(j).getName() != null && paramList.get(j).getName().equals(updatedParam.getName())) {

                        isFound = true;
                        paramList.set(j, updatedParam);
                        break;
                    }
                }

                if (!isFound) {
                    paramList.add(updatedParam);
                }

            } else {

                for (int j = 0; j < attributeList.size(); j++) {

                    if (attributeList.get(j).getName() != null && attributeList.get(j).getName().equals(updatedParam.getName())) {

                        isFound = true;
                        attributeList.set(j, updatedParam);
                        break;
                    }
                }

                if (!isFound) {
                    attributeList.add(updatedParam);
                }
            }
        }
    }

    private void updateUi() {

        boolean deviceFound = false;
        if (espApp.nodeMap.containsKey(device.getNodeId())) {

            ArrayList<Device> devices = espApp.nodeMap.get(device.getNodeId()).getDevices();
            isNodeOnline = espApp.nodeMap.get(device.getNodeId()).isOnline();
            timeStampOfStatus = espApp.nodeMap.get(device.getNodeId()).getTimeStampOfStatus();

            for (int i = 0; i < devices.size(); i++) {

                if (device.getDeviceName().equals(devices.get(i).getDeviceName())) {

                    device = devices.get(i);
                    deviceFound = true;
                    break;
                }
            }

        } else {
            Log.e(TAG, "Node does not exist in list. It may be deleted.");
            finish();
            return;
        }

        if (!deviceFound) {
            Log.e(TAG, "Device does not exist in node.");
            finish();
            return;
        }

        ArrayList<Param> espDeviceParams = device.getParams();
        setParamList(espDeviceParams);

        if (!isNodeOnline) {

            if (espApp.getCurrentStatus().equals(GetDataStatus.GET_DATA_SUCCESS)) {

                tvNodeOffline.setVisibility(View.VISIBLE);

                if (espApp.mDNSDeviceMap.containsKey(device.getNodeId())) {

                    tvNodeOffline.setText(R.string.local_device_text);

                } else {

                    String offlineText = getString(R.string.status_offline);
                    tvNodeOffline.setText(offlineText);

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
                        tvNodeOffline.setText(offlineText);
                    }
                }

            } else {
                tvNodeOffline.setVisibility(View.INVISIBLE);
            }

        } else {

            if (espApp.mDNSDeviceMap.containsKey(device.getNodeId())) {
                tvNodeOffline.setVisibility(View.VISIBLE);
                tvNodeOffline.setText(R.string.local_device_text);
            } else {
                tvNodeOffline.setVisibility(View.INVISIBLE);
            }
        }

        paramAdapter.updateList(paramList);
        paramAdapter.notifyDataSetChanged();

        attrAdapter.updateList(attributeList);
        attrAdapter.notifyDataSetChanged();

        if (paramList.size() <= 0 && attributeList.size() <= 0) {

            tvNoParam.setVisibility(View.VISIBLE);
            paramRecyclerView.setVisibility(View.GONE);
            attrRecyclerView.setVisibility(View.GONE);

        } else {

            tvNoParam.setVisibility(View.GONE);
            paramRecyclerView.setVisibility(View.VISIBLE);
            attrRecyclerView.setVisibility(View.VISIBLE);
        }

        boolean isParamTypeNameAvailable = false;

        for (int i = 0; i < device.getParams().size(); i++) {

            Param p = device.getParams().get(i);
            if (p != null && p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                isParamTypeNameAvailable = true;
                tvTitle.setText(p.getLabelValue());
                break;
            }
        }

        if (!isParamTypeNameAvailable) {
            tvTitle.setText(device.getDeviceName());
        }

        if (espApp.getCurrentStatus().equals(GetDataStatus.GET_DATA_FAILED) && !isNetworkAvailable) {
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
}
