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

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.espressif.EspApplication;
import com.espressif.rainmaker.R;
import com.espressif.ui.activities.AddDeviceActivity;
import com.espressif.ui.activities.EspMainActivity;
import com.espressif.ui.adapters.EspDeviceAdapter;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;

import java.util.ArrayList;
import java.util.Map;

public class DevicesFragment extends Fragment {

    private static final String TAG = DevicesFragment.class.getSimpleName();

    private CardView btnAddDevice;
    private TextView txtAddDeviceBtn;
    private ImageView arrowImage;

    private RecyclerView recyclerView;
    private TextView tvNoDevice, tvAddDevice;
    private RelativeLayout rlNoDevices;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageView ivNoDevice;

    private EspApplication espApp;
    private EspDeviceAdapter deviceAdapter;
    private ArrayList<Device> devices;

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
        espApp = (EspApplication) getActivity().getApplicationContext();
        init(root);
        tvNoDevice.setVisibility(View.GONE);
        rlNoDevices.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
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
        recyclerView = view.findViewById(R.id.rv_device_list);
        swipeRefreshLayout = view.findViewById(R.id.swipe_container);

        btnAddDevice = view.findViewById(R.id.btn_add_device_1);
        txtAddDeviceBtn = view.findViewById(R.id.text_btn);
        arrowImage = view.findViewById(R.id.iv_arrow);
        txtAddDeviceBtn.setText(R.string.btn_add_device);
        btnAddDevice.setVisibility(View.GONE);
        arrowImage.setVisibility(View.GONE);

        btnAddDevice.setOnClickListener(addDeviceBtnClickListener);

        // set a LinearLayoutManager with default orientation
        GridLayoutManager linearLayoutManager = new GridLayoutManager(getActivity().getApplicationContext(), 2);
        recyclerView.setLayoutManager(linearLayoutManager); // set LayoutManager to RecyclerView

        deviceAdapter = new EspDeviceAdapter(getActivity(), devices);
        recyclerView.setAdapter(deviceAdapter);

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

            rlNoDevices.setVisibility(View.GONE);
            btnAddDevice.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

        } else {
            tvNoDevice.setText(R.string.no_devices);
            rlNoDevices.setVisibility(View.VISIBLE);
            tvNoDevice.setVisibility(View.VISIBLE);
            tvAddDevice.setVisibility(View.GONE);
            ivNoDevice.setVisibility(View.VISIBLE);
            btnAddDevice.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }

        deviceAdapter.updateList(devices);
        swipeRefreshLayout.setRefreshing(isRefreshing);
    }

    private void updateUiOnFailure() {

        swipeRefreshLayout.setRefreshing(false);
        tvNoDevice.setText(R.string.error_device_list_not_received);
        rlNoDevices.setVisibility(View.VISIBLE);
        tvNoDevice.setVisibility(View.VISIBLE);
        tvAddDevice.setVisibility(View.GONE);
        ivNoDevice.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }

    private void goToAddDeviceActivity() {

        Intent intent = new Intent(getActivity(), AddDeviceActivity.class);
        startActivity(intent);
    }

    private void askForLocation() {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme);
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
