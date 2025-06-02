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

package com.espressif.ui.adapters;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.aar.tapholdupbutton.TapHoldUpButton;
import com.espressif.AppConstants;
import com.espressif.ESPControllerAPIKeys;
import com.espressif.EspApplication;
import com.espressif.NetworkApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.local_control.EspLocalDevice;
import com.espressif.matter.ControllerLoginActivity;
import com.espressif.matter.GroupSelectionActivity;
import com.espressif.matter.OnOffClusterHelper;
import com.espressif.matter.RemoteControlApiHelper;
import com.espressif.rainmaker.R;
import com.espressif.ui.Utils;
import com.espressif.ui.activities.EspDeviceActivity;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.Service;
import com.espressif.utils.NodeUtils;
import com.google.gson.JsonObject;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class EspDeviceAdapter extends RecyclerView.Adapter<EspDeviceAdapter.DeviceViewHolder> {

    private Context context;
    private NetworkApiManager networkApiManager;
    private ArrayList<Device> deviceList;

    public EspDeviceAdapter(Context context, ArrayList<Device> deviceList) {
        this.context = context;
        this.deviceList = deviceList;
        networkApiManager = new NetworkApiManager(context.getApplicationContext());
    }

    @Override
    public DeviceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_esp_device, parent, false);
        DeviceViewHolder vh = new DeviceViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull final DeviceViewHolder deviceVh, final int position) {

        final Device device = deviceList.get(position);
        EspApplication espApp = (EspApplication) context.getApplicationContext();
        EspNode node = espApp.nodeMap.get(device.getNodeId());
        String deviceName = device.getUserVisibleName();
        String nodeId = device.getNodeId();
        String matterNodeId = "";
        int nodeStatus = espApp.nodeMap.get(device.getNodeId()).getNodeStatus();
        if (espApp.matterRmNodeIdMap.containsKey(nodeId)) {
            matterNodeId = espApp.matterRmNodeIdMap.get(nodeId);
        }

        if (AppConstants.NODE_TYPE_RM_MATTER.equals(node.getNewNodeType())
                || AppConstants.NODE_TYPE_PURE_MATTER.equals(node.getNewNodeType())) {
            deviceName = (node.getNodeMetadata() != null) ? node.getNodeMetadata().getDeviceName() : "";
        }

        // Set device name according to device type if it is empty.
        if (TextUtils.isEmpty(deviceName)) {
            if (!TextUtils.isEmpty(device.getUserVisibleName())) {
                deviceName = device.getUserVisibleName();
            } else {
                deviceName = setDeviceNameFromType(device.getDeviceType());
            }
        }

        deviceVh.tvDeviceName.setText(deviceName);
        Utils.setDeviceIcon(deviceVh.ivDevice, device.getDeviceType());

        if (!TextUtils.isEmpty(device.getPrimaryParamName())) {

            String paramName = device.getPrimaryParamName();
            boolean isParamFound = false;
            int paramIndex = -1;

            for (int i = 0; i < device.getParams().size(); i++) {

                Param p = device.getParams().get(i);
                if (p != null && paramName.equals(p.getName())) {
                    isParamFound = true;
                    paramIndex = i;
                    break;
                }
            }

            if (isParamFound) {

                final Param param = device.getParams().get(paramIndex);
                String dataType = param.getDataType();

                if (TextUtils.isEmpty(dataType)) {

                    deviceVh.ivDeviceStatus.setVisibility(View.GONE);
                    deviceVh.tvStringValue.setVisibility(View.GONE);
                    deviceVh.btnTrigger.setVisibility(View.GONE);

                } else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(param.getUiType())) {

                    deviceVh.ivDeviceStatus.setVisibility(View.VISIBLE);
                    deviceVh.tvStringValue.setVisibility(View.GONE);
                    deviceVh.btnTrigger.setVisibility(View.GONE);

                    final boolean isOn = param.getSwitchStatus();

                    if (isOn) {
                        deviceVh.ivDeviceStatus.setImageResource(R.drawable.ic_output_on);
                    } else {
                        deviceVh.ivDeviceStatus.setImageResource(R.drawable.ic_output_off);
                    }

                    if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {

                        String finalMatterNodeId = matterNodeId;

                        deviceVh.ivDeviceStatus.setOnClickListener(new View.OnClickListener() {

                            @Override
                            public void onClick(View v) {

                                final boolean status = param.getSwitchStatus();

                                if (status) {
                                    deviceVh.ivDeviceStatus.setImageResource(R.drawable.ic_output_off);
                                } else {
                                    deviceVh.ivDeviceStatus.setImageResource(R.drawable.ic_output_on);
                                }

                                switch (nodeStatus) {
                                    case AppConstants.NODE_STATUS_MATTER_LOCAL:
                                        BigInteger id = new BigInteger(finalMatterNodeId, 16);
                                        long deviceId = id.longValue();
                                        OnOffClusterHelper espClusterHelper = new OnOffClusterHelper(espApp.chipClientMap.get(finalMatterNodeId));
                                        espClusterHelper.setOnOffDeviceStateOnOffClusterAsync(deviceId, !status, AppConstants.ENDPOINT_1);
                                        param.setSwitchStatus(!status);
                                        break;

                                    case AppConstants.NODE_STATUS_REMOTELY_CONTROLLABLE:
                                        RemoteControlApiHelper apiHelper = new RemoteControlApiHelper(espApp);

                                        for (Map.Entry<String, HashMap<String, String>> entry : espApp.controllerDevices.entrySet()) {

                                            HashMap<String, String> controllerDevices = entry.getValue();

                                            if (controllerDevices.containsKey(finalMatterNodeId)) {

                                                EspNode espNode = espApp.nodeMap.get(entry.getKey());
                                                Service controllerService = NodeUtils.Companion.getService(espNode, AppConstants.SERVICE_TYPE_MATTER_CONTROLLER);

                                                if (controllerService != null) {
                                                    for (Param deviceParam : controllerService.getParams()) {
                                                        if (AppConstants.PARAM_TYPE_MATTER_DEVICES.equals(deviceParam.getParamType())) {

                                                            if (status) {
                                                                apiHelper.callOffAPI(entry.getKey(), finalMatterNodeId, deviceParam.getName(), controllerService.getName(), ESPControllerAPIKeys.ENDPOINT_ID_1_HEX, new ApiResponseListener() {
                                                                    @Override
                                                                    public void onSuccess(@Nullable Bundle data) {
                                                                        param.setSwitchStatus(!status);
                                                                    }

                                                                    @Override
                                                                    public void onResponseFailure(@NonNull Exception exception) {
                                                                        Toast.makeText(context, R.string.error_param_update, Toast.LENGTH_SHORT).show();
                                                                    }

                                                                    @Override
                                                                    public void onNetworkFailure(@NonNull Exception exception) {
                                                                        Toast.makeText(context, R.string.error_param_update, Toast.LENGTH_SHORT).show();
                                                                    }
                                                                });
                                                            } else {
                                                                apiHelper.callOnAPI(entry.getKey(), finalMatterNodeId, deviceParam.getName(), controllerService.getName(), ESPControllerAPIKeys.ENDPOINT_ID_1_HEX, new ApiResponseListener() {
                                                                    @Override
                                                                    public void onSuccess(@Nullable Bundle data) {
                                                                        param.setSwitchStatus(!status);
                                                                    }

                                                                    @Override
                                                                    public void onResponseFailure(@NonNull Exception exception) {
                                                                        Toast.makeText(context, R.string.error_param_update, Toast.LENGTH_SHORT).show();
                                                                    }

                                                                    @Override
                                                                    public void onNetworkFailure(@NonNull Exception exception) {
                                                                        Toast.makeText(context, R.string.error_param_update, Toast.LENGTH_SHORT).show();
                                                                    }
                                                                });
                                                            }
                                                            break;
                                                        }
                                                    }
                                                }
                                                break;
                                            }
                                        }
                                        break;

                                    default:
                                        JsonObject jsonParam = new JsonObject();
                                        JsonObject body = new JsonObject();
                                        jsonParam.addProperty(param.getName(), !status);
                                        body.add(device.getDeviceName(), jsonParam);

                                        networkApiManager.updateParamValue(device.getNodeId(), body, new ApiResponseListener() {

                                            @Override
                                            public void onSuccess(Bundle data) {
                                                param.setSwitchStatus(!status);
                                            }

                                            @Override
                                            public void onResponseFailure(Exception exception) {
                                                Toast.makeText(context, R.string.error_param_update, Toast.LENGTH_SHORT).show();
                                            }

                                            @Override
                                            public void onNetworkFailure(Exception exception) {
                                                Toast.makeText(context, R.string.error_param_update, Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                }
                            }
                        });
                    }

                } else if (AppConstants.UI_TYPE_TRIGGER.equalsIgnoreCase(param.getUiType())) {

                    deviceVh.ivDeviceStatus.setVisibility(View.GONE);
                    deviceVh.tvStringValue.setVisibility(View.GONE);
                    deviceVh.btnTrigger.setVisibility(View.VISIBLE);

                    if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {

                        deviceVh.btnTrigger.setAlpha(1f);
                        deviceVh.btnTrigger.setEnabled(true);
                        deviceVh.btnTrigger.setClickable(true);
                        deviceVh.btnTrigger.enableLongHold(true);
                        deviceVh.btnTrigger.setOnButtonClickListener(new TapHoldUpButton.OnButtonClickListener() {

                            @Override
                            public void onLongHoldStart(View v) {
                            }

                            @Override
                            public void onLongHoldEnd(View v) {
                                JsonObject jsonParam = new JsonObject();
                                JsonObject body = new JsonObject();

                                jsonParam.addProperty(param.getName(), true);
                                body.add(device.getDeviceName(), jsonParam);

                                networkApiManager.updateParamValue(device.getNodeId(), body, new ApiResponseListener() {

                                    @Override
                                    public void onSuccess(Bundle data) {
                                    }

                                    @Override
                                    public void onResponseFailure(Exception exception) {
                                        Toast.makeText(context, R.string.error_param_update, Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onNetworkFailure(Exception exception) {
                                        Toast.makeText(context, R.string.error_param_update, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onClick(View v) {
                                JsonObject jsonParam = new JsonObject();
                                JsonObject body = new JsonObject();

                                jsonParam.addProperty(param.getName(), true);
                                body.add(device.getDeviceName(), jsonParam);

                                networkApiManager.updateParamValue(device.getNodeId(), body, new ApiResponseListener() {

                                    @Override
                                    public void onSuccess(Bundle data) {
                                    }

                                    @Override
                                    public void onResponseFailure(Exception exception) {
                                        Toast.makeText(context, R.string.error_param_update, Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onNetworkFailure(Exception exception) {
                                        Toast.makeText(context, R.string.error_param_update, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                    } else {
                        deviceVh.btnTrigger.setEnabled(false);
                        deviceVh.btnTrigger.setOnButtonClickListener(null);
                    }

                } else if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {

                    deviceVh.ivDeviceStatus.setVisibility(View.VISIBLE);
                    deviceVh.tvStringValue.setVisibility(View.GONE);
                    deviceVh.btnTrigger.setVisibility(View.GONE);

                    String value = param.getLabelValue();
                    boolean isOn = false;

                    if (value != null && value.equalsIgnoreCase("true")) {
                        isOn = true;
                    }

                    if (isOn) {
                        deviceVh.ivDeviceStatus.setImageResource(R.drawable.ic_output_on);
                    } else {
                        deviceVh.ivDeviceStatus.setImageResource(R.drawable.ic_output_off);
                    }

                    if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {

                        deviceVh.ivDeviceStatus.setOnClickListener(new View.OnClickListener() {

                            @Override
                            public void onClick(View v) {

                                String value = param.getLabelValue();
                                boolean finalIsOn = false;

                                if (value != null && value.equalsIgnoreCase("true")) {
                                    finalIsOn = true;
                                }

                                JsonObject jsonParam = new JsonObject();
                                JsonObject body = new JsonObject();

                                jsonParam.addProperty(param.getName(), !finalIsOn);
                                body.add(device.getDeviceName(), jsonParam);

                                final boolean finalIsOn1 = finalIsOn;
                                networkApiManager.updateParamValue(device.getNodeId(), body, new ApiResponseListener() {

                                    @Override
                                    public void onSuccess(Bundle data) {

                                        if (finalIsOn1) {
                                            deviceVh.ivDeviceStatus.setImageResource(R.drawable.ic_output_off);
                                            param.setLabelValue("false");
                                        } else {
                                            deviceVh.ivDeviceStatus.setImageResource(R.drawable.ic_output_on);
                                            param.setLabelValue("true");
                                        }
                                    }

                                    @Override
                                    public void onResponseFailure(Exception exception) {
                                        Toast.makeText(context, R.string.error_param_update, Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onNetworkFailure(Exception exception) {
                                        Toast.makeText(context, R.string.error_param_update, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                    }

                } else if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                    deviceVh.ivDeviceStatus.setVisibility(View.GONE);
                    deviceVh.btnTrigger.setVisibility(View.GONE);
                    deviceVh.tvStringValue.setVisibility(View.VISIBLE);
                    deviceVh.tvStringValue.setText(param.getLabelValue());

                } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                    deviceVh.ivDeviceStatus.setVisibility(View.GONE);
                    deviceVh.btnTrigger.setVisibility(View.GONE);
                    deviceVh.tvStringValue.setVisibility(View.VISIBLE);
                    deviceVh.tvStringValue.setText(param.getLabelValue());

                } else if (dataType.equalsIgnoreCase("string")) {

                    deviceVh.ivDeviceStatus.setVisibility(View.GONE);
                    deviceVh.btnTrigger.setVisibility(View.GONE);
                    deviceVh.tvStringValue.setVisibility(View.VISIBLE);
                    deviceVh.tvStringValue.setText(param.getLabelValue());
                }
            } else {
                deviceVh.btnTrigger.setVisibility(View.GONE);
                deviceVh.ivDeviceStatus.setVisibility(View.GONE);
                deviceVh.tvStringValue.setVisibility(View.GONE);
            }
        } else {
            deviceVh.btnTrigger.setVisibility(View.GONE);
            deviceVh.ivDeviceStatus.setVisibility(View.GONE);
            deviceVh.tvStringValue.setVisibility(View.GONE);
        }

        if (node != null && !node.isOnline() && nodeStatus != AppConstants.NODE_STATUS_MATTER_LOCAL
                && nodeStatus != AppConstants.NODE_STATUS_REMOTELY_CONTROLLABLE) {

            deviceVh.itemView.setAlpha(0.8f);
            deviceVh.ivDeviceStatus.setImageResource(R.drawable.ic_output_disable);
            deviceVh.ivDeviceStatus.setOnClickListener(null);
            deviceVh.btnTrigger.setAlpha(0.3f);
            deviceVh.btnTrigger.setEnabled(false);
            deviceVh.btnTrigger.setClickable(false);
            deviceVh.btnTrigger.enableLongHold(false);
            deviceVh.btnTrigger.setOnButtonClickListener(null);

            if (espApp.getAppState().equals(EspApplication.AppState.GET_DATA_SUCCESS)
                    || espApp.getAppState().equals(EspApplication.AppState.REFRESH_DATA)
                    || espApp.getAppState().equals(EspApplication.AppState.GET_DATA_FAILED)) {

                deviceVh.llOffline.setVisibility(View.VISIBLE);
                deviceVh.ivOffline.setVisibility(View.VISIBLE);
                String offlineText = context.getString(R.string.status_offline);
                deviceVh.tvOffline.setText(offlineText);
                deviceVh.tvOffline.setTextColor(context.getColor(R.color.colorAccent));

                if (node.getTimeStampOfStatus() != 0) {

                    Calendar calendar = Calendar.getInstance();
                    int day = calendar.get(Calendar.DATE);

                    calendar.setTimeInMillis(node.getTimeStampOfStatus());
                    int offlineDay = calendar.get(Calendar.DATE);

                    if (day == offlineDay) {

                        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm");
                        String time = formatter.format(calendar.getTime());
                        offlineText = context.getString(R.string.offline_at) + " " + time;

                    } else {

                        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yy, HH:mm");
                        String time = formatter.format(calendar.getTime());
                        offlineText = context.getString(R.string.offline_at) + " " + time;
                    }
                    deviceVh.tvOffline.setText(offlineText);
                }

            } else {
                deviceVh.llOffline.setVisibility(View.INVISIBLE);
            }
        } else {
            deviceVh.itemView.setAlpha(1f);
            deviceVh.btnTrigger.setAlpha(1f);
            deviceVh.btnTrigger.setEnabled(true);
            deviceVh.btnTrigger.setClickable(true);
            deviceVh.btnTrigger.enableLongHold(true);
            deviceVh.llOffline.setVisibility(View.INVISIBLE);
        }

        switch (nodeStatus) {
            case AppConstants.NODE_STATUS_MATTER_LOCAL:
                if (!TextUtils.isEmpty(matterNodeId)) {
                    deviceVh.llOffline.setVisibility(View.VISIBLE);
                    deviceVh.ivOffline.setVisibility(View.GONE);
                    deviceVh.tvOffline.setText(R.string.status_local);
                    deviceVh.tvOffline.setTextColor(context.getColor(R.color.colorPrimaryDark));
                }
                break;

            case AppConstants.NODE_STATUS_LOCAL:
                deviceVh.llOffline.setVisibility(View.VISIBLE);

                EspLocalDevice localDevice = espApp.localDeviceMap.get(nodeId);
                if (localDevice != null) {
                    if (localDevice.getSecurityType() == 1 || localDevice.getSecurityType() == 2) {
                        deviceVh.ivSecureLocal.setVisibility(View.VISIBLE);
                        deviceVh.ivOffline.setVisibility(View.INVISIBLE);
                    } else {
                        deviceVh.ivSecureLocal.setVisibility(View.INVISIBLE);
                        deviceVh.ivOffline.setVisibility(View.GONE);
                    }
                    deviceVh.tvOffline.setText(R.string.local_device_text);
                    deviceVh.tvOffline.setTextColor(context.getColor(R.color.colorPrimaryDark));
                } else {
                    if (espApp.nodeMap.get(nodeId).isOnline()) {
                        node.setNodeStatus(AppConstants.NODE_STATUS_ONLINE);
                    } else {
                        node.setNodeStatus(AppConstants.NODE_STATUS_OFFLINE);
                    }
                }
                break;

            case AppConstants.NODE_STATUS_REMOTELY_CONTROLLABLE:
                deviceVh.llOffline.setVisibility(View.VISIBLE);
                deviceVh.ivOffline.setVisibility(View.GONE);
                deviceVh.tvOffline.setText(R.string.status_remote);
                deviceVh.tvOffline.setTextColor(context.getColor(R.color.colorPrimaryDark));
                break;

            default:
                break;
        }

        // implement setOnClickListener event on item view.
        deviceVh.itemView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                String rmNodeId = device.getNodeId();
                SharedPreferences sharedPreferences = context.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
                boolean isMatterController = sharedPreferences.getBoolean(rmNodeId, false);
                String key = "ctrl_setup_" + rmNodeId;
                boolean isMatterCtrlSetupDone = sharedPreferences.getBoolean(key, false);
                Log.d("TAG", "isMatterController : " + isMatterController);
                Log.d("TAG", "isMatterCtrlSetupDone : " + isMatterCtrlSetupDone);

                Service controllerService = NodeUtils.Companion.getService(espApp.nodeMap.get(device.getNodeId()), AppConstants.SERVICE_TYPE_MATTER_CONTROLLER);
                boolean isCtlServiceAvailable = controllerService != null;
                boolean hasUserToken = false;
                boolean matterNodeIdParamAvailable = false;

                if (isCtlServiceAvailable) {
                    ArrayList<Param> params = controllerService.getParams();

                    if (params != null && !params.isEmpty()) {
                        for (Param param : params) {
                            if (AppConstants.PARAM_TYPE_USER_TOKEN.equals(param.getParamType())) {
                                String userToken = param.getLabelValue();
                                if (userToken != null && !userToken.isEmpty()) {
                                    hasUserToken = true;
                                }
                            }
                            if (AppConstants.PARAM_TYPE_MATTER_NODE_ID.equals(param.getParamType())) {
                                matterNodeIdParamAvailable = true;
                            }
                        }
                    }
                }

                if (isMatterController && !isMatterCtrlSetupDone) {
                    controllerNeedsAccessWarning(rmNodeId, R.string.dialog_msg_matter_controller, false);
                } else if (isCtlServiceAvailable && matterNodeIdParamAvailable && !hasUserToken) {
                    controllerNeedsAccessWarning(rmNodeId, R.string.dialog_msg_controller, true);
                } else {
                    Intent intent = new Intent(context, EspDeviceActivity.class);
                    intent.putExtra(AppConstants.KEY_ESP_DEVICE, device);
                    context.startActivity(intent);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    public void updateList(ArrayList<Device> updatedDeviceList) {
        deviceList = updatedDeviceList;
        notifyDataSetChanged();
    }

    private void controllerNeedsAccessWarning(String rmNodeId, int strResId, boolean isCtrlService) {

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(true);
        builder.setMessage(strResId);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Intent intent = new Intent(context, ControllerLoginActivity.class);
                if (isCtrlService) {
                    intent = new Intent(context, GroupSelectionActivity.class);
                }
                intent.putExtra(AppConstants.KEY_NODE_ID, rmNodeId);
                intent.putExtra(AppConstants.KEY_IS_CTRL_SERVICE, isCtrlService);
                context.startActivity(intent);
            }
        });

        builder.show();
    }

    private String setDeviceNameFromType(String deviceType) {

        String name = "";
        if (!TextUtils.isEmpty(deviceType)) {
            switch (deviceType) {
                case AppConstants.ESP_DEVICE_LIGHT_BULB:
                case AppConstants.ESP_DEVICE_LIGHT:
                case AppConstants.ESP_DEVICE_BULB_CCT:
                case AppConstants.ESP_DEVICE_BULB_RGB:
                    name = "Light";
                    break;
                case AppConstants.ESP_DEVICE_SWITCH:
                    name = "Switch";
                    break;
                case AppConstants.ESP_DEVICE_LOCK:
                    name = "Door Lock";
                    break;
                case AppConstants.ESP_DEVICE_THERMOSTAT:
                    name = "Thermostat";
                    break;
                case AppConstants.ESP_DEVICE_FAN:
                    name = "Fan";
                    break;
                case AppConstants.ESP_DEVICE_TEMP_SENSOR:
                    name = "Temperature";
                    break;
                case AppConstants.ESP_DEVICE_OUTLET:
                    name = "Outlet";
                    break;
                case AppConstants.ESP_DEVICE_PLUG:
                    name = "Plug";
                    break;
                case AppConstants.ESP_DEVICE_SOCKET:
                    name = "Socket";
                    break;
                case AppConstants.ESP_DEVICE_BLINDS_INTERNAL:
                case AppConstants.ESP_DEVICE_BLINDS_EXTERNAL:
                    name = "Blinds";
                    break;
                case AppConstants.ESP_DEVICE_GARAGE_DOOR:
                    name = "Garage Door";
                    break;
                case AppConstants.ESP_DEVICE_SPEAKER:
                    name = "Speaker";
                    break;
                case AppConstants.ESP_DEVICE_AIR_CONDITIONER:
                    name = "AC";
                    break;
                case AppConstants.ESP_DEVICE_TV:
                    name = "TV";
                    break;
                case AppConstants.ESP_DEVICE_WASHER:
                    name = "Washer";
                    break;
                case AppConstants.ESP_DEVICE_CONTACT_SENSOR:
                    name = "Contact Sensor";
                    break;
                case AppConstants.ESP_DEVICE_MOTION_SENSOR:
                    name = "Motion Sensor";
                    break;
                case AppConstants.ESP_DEVICE_DOORBELL:
                    name = "Doorbell";
                    break;
                default:
                    name = name;
                    break;
            }
        }
        return name;
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {

        TextView tvDeviceName, tvStringValue, tvOffline;
        ImageView ivDevice, ivDeviceStatus, ivOffline, ivSecureLocal;
        RelativeLayout llOffline;
        TapHoldUpButton btnTrigger;

        public DeviceViewHolder(View itemView) {
            super(itemView);

            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            ivDevice = itemView.findViewById(R.id.iv_device);
            llOffline = itemView.findViewById(R.id.ll_offline);
            ivOffline = itemView.findViewById(R.id.iv_offline);
            tvOffline = itemView.findViewById(R.id.tv_offline);
            ivSecureLocal = itemView.findViewById(R.id.iv_secure_local);
            ivDeviceStatus = itemView.findViewById(R.id.iv_on_off);
            tvStringValue = itemView.findViewById(R.id.tv_string);
            btnTrigger = itemView.findViewById(R.id.btn_trigger);
        }
    }
}
