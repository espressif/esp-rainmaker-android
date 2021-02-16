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

package com.espressif.ui.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.activities.AddDeviceActivity;
import com.espressif.ui.activities.BLEProvisionLanding;
import com.espressif.ui.activities.EspMainActivity;
import com.espressif.ui.activities.ProvisionLanding;
import com.espressif.ui.adapters.EspDeviceAdapter;
import com.espressif.ui.adapters.NodeAdapter;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Map;

public class DevicesFragment extends Fragment {

    private static final String TAG = DevicesFragment.class.getSimpleName();

    private MaterialCardView btnAddDevice;
    private TextView txtAddDeviceBtn;
    private ImageView arrowImage;

    private RecyclerView rvDevices, rvNodes;
    private TextView tvNoDevice, tvAddDevice;
    private RelativeLayout rlNoDevices;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageView ivNoDevice;

    private EspApplication espApp;
    private EspDeviceAdapter deviceAdapter;
    private NodeAdapter nodeAdapter;
    private ArrayList<Device> devices;
    private ArrayList<EspNode> nodes;

    public DevicesFragment() {
        // Required empty public constructor
    }

    public static DevicesFragment newInstance() {
        return new DevicesFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_devices, container, false);
        devices = new ArrayList<>();
        nodes = new ArrayList<>();
        espApp = (EspApplication) getActivity().getApplicationContext();
        init(root);
        tvNoDevice.setVisibility(View.GONE);
        rlNoDevices.setVisibility(View.GONE);
        rvDevices.setVisibility(View.GONE);
        rvNodes.setVisibility(View.GONE);
        updateDeviceUi();
        ((EspMainActivity) getActivity()).setUpdateListener(updateListener);
        return root;
    }

    @Override
    public void onDestroy() {
        ((EspMainActivity) getActivity()).removeUpdateListener(updateListener);
        super.onDestroy();
    }

    EspMainActivity.UiUpdateListener updateListener = new EspMainActivity.UiUpdateListener() {

        @Override
        public void updateUi() {
            updateDeviceUi();
        }
    };

    View.OnClickListener addDeviceBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Vibrator vib = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
            vib.vibrate(HapticFeedbackConstants.VIRTUAL_KEY);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                if (!isLocationEnabled()) {
                    askForLocation();
                    return;
                }
            }
            goToAddDeviceActivity();
        }
    };

    private void init(View view) {

        rlNoDevices = view.findViewById(R.id.rl_no_device);
        tvNoDevice = view.findViewById(R.id.tv_no_device);
        tvAddDevice = view.findViewById(R.id.tv_add_device);
        ivNoDevice = view.findViewById(R.id.iv_no_device);
        rvDevices = view.findViewById(R.id.rv_device_list);
        rvNodes = view.findViewById(R.id.rv_node_list);
        swipeRefreshLayout = view.findViewById(R.id.swipe_container);

        btnAddDevice = view.findViewById(R.id.btn_add_device_1);
        txtAddDeviceBtn = view.findViewById(R.id.text_btn);
        arrowImage = view.findViewById(R.id.iv_arrow);
        txtAddDeviceBtn.setText(R.string.btn_add_device);
        btnAddDevice.setVisibility(View.GONE);
        arrowImage.setVisibility(View.GONE);

        btnAddDevice.setOnClickListener(addDeviceBtnClickListener);

        // set a LayoutManager with default orientation
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 2);
        rvDevices.setLayoutManager(gridLayoutManager);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        rvNodes.setLayoutManager(linearLayoutManager);

        deviceAdapter = new EspDeviceAdapter(getActivity(), devices);
        rvDevices.setAdapter(deviceAdapter);

        nodeAdapter = new NodeAdapter(getActivity(), nodes);
        rvNodes.setAdapter(nodeAdapter);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {

            @Override
            public void onRefresh() {
                ((EspMainActivity) getActivity()).refreshDeviceList();
            }
        });
    }

    private void updateDeviceUi() {

        switch (espApp.getCurrentStatus()) {

            case FETCHING_DATA:
            case GET_DATA_SUCCESS:
            case GET_DATA_FAILED:
                updateUiOnSuccess(false);
                break;

            case DATA_REFRESHING:
                updateUiOnSuccess(true);
                break;
        }
    }

    private void updateUiOnSuccess(boolean isRefreshing) {

        devices.clear();
        nodes.clear();

        for (Map.Entry<String, EspNode> entry : espApp.nodeMap.entrySet()) {

            String key = entry.getKey();
            EspNode node = entry.getValue();

            if (node.getDevices().size() == 1) {
                devices.addAll(node.getDevices());
            } else if (node.getDevices().size() > 1) {
                nodes.add(node);
            }
        }

        Log.d(TAG, "Device list size : " + devices.size());
        Log.d(TAG, "Node list size : " + nodes.size());

        if (devices.size() <= 0 && nodes.size() <= 0) {

            tvNoDevice.setText(R.string.no_devices);
            rlNoDevices.setVisibility(View.VISIBLE);
            tvNoDevice.setVisibility(View.VISIBLE);
            tvAddDevice.setVisibility(View.GONE);
            ivNoDevice.setVisibility(View.VISIBLE);
            btnAddDevice.setVisibility(View.VISIBLE);
            rvDevices.setVisibility(View.GONE);
            rvNodes.setVisibility(View.GONE);

        } else {

            rlNoDevices.setVisibility(View.GONE);
            btnAddDevice.setVisibility(View.GONE);

            if (devices.size() > 0) {
                rvDevices.setVisibility(View.VISIBLE);
                deviceAdapter.updateList(devices);
            } else {
                rvDevices.setVisibility(View.GONE);
            }

            if (nodes.size() > 0) {
                rvNodes.setVisibility(View.VISIBLE);
                nodeAdapter.updateList(nodes);
            } else {
                rvNodes.setVisibility(View.GONE);
            }
        }

        swipeRefreshLayout.setRefreshing(isRefreshing);
    }

    private void updateUiOnFailure() {

        swipeRefreshLayout.setRefreshing(false);
        tvNoDevice.setText(R.string.error_device_list_not_received);
        rlNoDevices.setVisibility(View.VISIBLE);
        tvNoDevice.setVisibility(View.VISIBLE);
        tvAddDevice.setVisibility(View.GONE);
        ivNoDevice.setVisibility(View.VISIBLE);
        rvDevices.setVisibility(View.GONE);
        rvNodes.setVisibility(View.GONE);
    }

    private void goToAddDeviceActivity() {

        if (BuildConfig.isQRCodeSupported) {
            Intent intent = new Intent(getActivity(), AddDeviceActivity.class);
            getActivity().startActivity(intent);
        } else {

            boolean isSec1 = true;
            ESPProvisionManager provisionManager = ESPProvisionManager.getInstance(getActivity().getApplicationContext());

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
                Toast.makeText(getActivity(), R.string.error_device_type_not_supported, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void askForDeviceType(final boolean isSec1) {

        final String[] deviceTypes = getResources().getStringArray(R.array.prov_transport_types);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setCancelable(true);
        builder.setTitle(R.string.dialog_msg_device_selection);
        final ESPProvisionManager provisionManager = ESPProvisionManager.getInstance(getActivity().getApplicationContext());

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

        Intent intent = new Intent(getActivity(), BLEProvisionLanding.class);
        if (isSec1) {
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, AppConstants.SECURITY_1);
        } else {
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, AppConstants.SECURITY_0);
        }
        getActivity().startActivity(intent);
    }

    private void goToWiFiProvisionLanding(boolean isSec1) {

        Intent intent = new Intent(getActivity(), ProvisionLanding.class);
        if (isSec1) {
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, AppConstants.SECURITY_1);
        } else {
            intent.putExtra(AppConstants.KEY_SECURITY_TYPE, AppConstants.SECURITY_0);
        }
        getActivity().startActivity(intent);
    }

    private void askForLocation() {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setCancelable(true);
        builder.setMessage(R.string.dialog_msg_for_gps);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                // TODO Receive result in activity.
//                startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_LOCATION);
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
        LocationManager lm = (LocationManager) getActivity().getApplicationContext().getSystemService(Activity.LOCATION_SERVICE);

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
}
