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
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.AttrParamAdapter;
import com.espressif.ui.adapters.ParamAdapter;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class EspDeviceActivity extends AppCompatActivity {

    private static final int NODE_DETAILS_ACTIVITY_REQUEST = 10;
    private static final int UPDATE_INTERVAL = 5000;

    private TextView tvTitle, tvBack, tvNoParam, tvNodeOffline;
    private ImageView ivNodeInfo;
    private RecyclerView paramRecyclerView;
    private RecyclerView attrRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;

    private Device device;
    private EspApplication espApp;
    private ApiManager apiManager;
    private ParamAdapter paramAdapter;
    private AttrParamAdapter attrAdapter;
    private ArrayList<Param> paramList;
    private ArrayList<Param> attributeList;
    private Handler handler;
    private ContentLoadingProgressBar progressBar;
    private boolean isNodeOnline;
    private long timeStampOfStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp_device);

        espApp = (EspApplication) getApplicationContext();
        apiManager = ApiManager.getInstance(getApplicationContext());
        device = getIntent().getParcelableExtra(AppConstants.KEY_ESP_DEVICE);
        handler = new Handler();
        isNodeOnline = espApp.nodeMap.get(device.getNodeId()).isOnline();
        timeStampOfStatus = espApp.nodeMap.get(device.getNodeId()).getTimeStampOfStatus();

        ArrayList<Param> espDeviceParams = device.getParams();
        setParamList(espDeviceParams);

        initViews();
        showLoading();
        getNodeDetails();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startUpdateValueTask();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopUpdateValueTask();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(updateValuesTask);
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

        apiManager.getNodeDetails(device.getNodeId(), new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                hideLoading();
                swipeRefreshLayout.setRefreshing(false);

                if (espApp.nodeMap.containsKey(device.getNodeId())) {

                    ArrayList<Device> devices = espApp.nodeMap.get(device.getNodeId()).getDevices();
                    isNodeOnline = espApp.nodeMap.get(device.getNodeId()).isOnline();
                    timeStampOfStatus = espApp.nodeMap.get(device.getNodeId()).getTimeStampOfStatus();

                    for (int i = 0; i < devices.size(); i++) {

                        if (device.getDeviceName().equals(devices.get(i).getDeviceName())) {

                            device = devices.get(i);
                            ArrayList<Param> espDeviceParams = device.getParams();
                            setParamList(espDeviceParams);
                            updateUi();
                            break;
                        }
                    }
                } else {
                    finish();
                }
                startUpdateValueTask();
            }

            @Override
            public void onFailure(Exception exception) {

                exception.printStackTrace();
                hideLoading();
                swipeRefreshLayout.setRefreshing(false);
                startUpdateValueTask();
            }
        });
    }

    private void getValues() {

        apiManager.getParamsValues(device.getNodeId(), new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                hideLoading();
                swipeRefreshLayout.setRefreshing(false);

                if (espApp.nodeMap.containsKey(device.getNodeId())) {

                    ArrayList<Device> devices = espApp.nodeMap.get(device.getNodeId()).getDevices();

                    for (int i = 0; i < devices.size(); i++) {

                        if (device.getDeviceName().equals(devices.get(i).getDeviceName())) {

                            device = devices.get(i);
                            ArrayList<Param> espDeviceParams = device.getParams();
                            setParamList(espDeviceParams);
                            updateUi();
                            break;
                        }
                    }
                } else {
                    finish();
                }
            }

            @Override
            public void onFailure(Exception exception) {

                exception.printStackTrace();
                hideLoading();
                swipeRefreshLayout.setRefreshing(false);
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

        if (!isNodeOnline) {

            tvNodeOffline.setVisibility(View.VISIBLE);

            String offlineText = getString(R.string.offline_at);
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

        } else {

            tvNodeOffline.setVisibility(View.GONE);
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
