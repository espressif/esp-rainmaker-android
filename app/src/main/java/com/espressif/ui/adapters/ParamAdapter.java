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

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.aar.tapholdupbutton.TapHoldUpButton;
import com.espressif.AppConstants;
import com.espressif.AppConstants.Companion.LockState;
import com.espressif.AppConstants.Companion.SystemMode;
import com.espressif.ESPControllerAPIKeys;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.matter.ColorControlClusterHelper;
import com.espressif.matter.DoorLockClusterHelper;
import com.espressif.matter.LevelControlClusterHelper;
import com.espressif.matter.OnOffClusterHelper;
import com.espressif.matter.RemoteControlApiHelper;
import com.espressif.matter.TemperatureClusterHelper;
import com.espressif.matter.ThermostatClusterHelper;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.Utils;
import com.espressif.ui.activities.EspDeviceActivity;
import com.espressif.ui.activities.TimeSeriesActivity;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.Service;
import com.espressif.ui.widgets.EspDropDown;
import com.espressif.ui.widgets.PaletteBar;
import com.espressif.utils.NodeUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.larswerkman.holocolorpicker.ColorPicker;
import com.warkiz.tickseekbar.OnSeekChangeListener;
import com.warkiz.tickseekbar.SeekParams;
import com.warkiz.tickseekbar.TickSeekBar;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ParamAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final String TAG = ParamAdapter.class.getSimpleName();

    private final int VIEW_TYPE_PARAM = 1;
    private final int VIEW_TYPE_PUSH_BTN_BIG = 2;
    private final int VIEW_TYPE_HUE = 3;

    private Activity context;
    private final ArrayList<Param> params;

    private final String nodeId;
    private final String deviceName;
    private Device device;
    private DeviceParamUpdates deviceParamUpdates;

    private String matterNodeId;
    private int hueColorValue;
    private EspApplication espApp;
    private int nodeStatus = AppConstants.NODE_STATUS_OFFLINE;

    public ParamAdapter(Activity context, Device device, ArrayList<Param> paramList) {
        this.context = context;
        this.device = device;
        this.params = paramList;
        this.nodeId = device.getNodeId();
        this.deviceName = device.getDeviceName();
        deviceParamUpdates = new DeviceParamUpdates(context, nodeId, deviceName);

        espApp = (EspApplication) context.getApplicationContext();
        nodeStatus = espApp.nodeMap.get(nodeId).getNodeStatus();
        if (espApp.matterRmNodeIdMap.containsKey(nodeId)) {
            matterNodeId = espApp.matterRmNodeIdMap.get(nodeId);
            Log.d(TAG, "Found Matter Node Id : " + matterNodeId);
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        LayoutInflater layoutInflater = LayoutInflater.from(context);
        switch (viewType) {

            case VIEW_TYPE_PUSH_BTN_BIG:
                View switchView = layoutInflater.inflate(R.layout.item_param_switch, parent, false);
                return new SwitchViewHolder(switchView);

            case VIEW_TYPE_HUE:
                View hueView = layoutInflater.inflate(R.layout.item_param_hue, parent, false);
                return new HueViewHolder(hueView);

            case VIEW_TYPE_PARAM:
            default:
                View paramView = layoutInflater.inflate(R.layout.item_param, parent, false);
                return new ParamViewHolder(paramView);
        }
    }

    @Override
    public int getItemViewType(int position) {

        Param param = params.get(position);

        if (param != null) {

            String dataType = param.getDataType();

            if (AppConstants.UI_TYPE_HUE_CIRCLE.equalsIgnoreCase(param.getUiType())) {

                return VIEW_TYPE_HUE;

            } else if (AppConstants.UI_TYPE_PUSH_BTN_BIG.equalsIgnoreCase(param.getUiType())
                    && (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")))) {

                return VIEW_TYPE_PUSH_BTN_BIG;
            }
        }
        return VIEW_TYPE_PARAM;
    }

    @Override
    public int getItemCount() {
        return params.size();
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {

        final Param param = params.get(position);

        if (holder.getItemViewType() == VIEW_TYPE_PARAM) {

            final ParamViewHolder paramViewHolder = (ParamViewHolder) holder;
            String dataType = param.getDataType();

            if (AppConstants.UI_TYPE_SLIDER.equalsIgnoreCase(param.getUiType())) {

                if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("int")
                        || dataType.equalsIgnoreCase("integer")
                        || dataType.equalsIgnoreCase("float")
                        || dataType.equalsIgnoreCase("double"))) {

                    int max = param.getMaxBounds();
                    int min = param.getMinBounds();

                    if ((min < max)) {

                        displaySlider(paramViewHolder, param, position);
                    } else {
                        displayLabel(paramViewHolder, param, position);
                    }
                }
            } else if (AppConstants.UI_TYPE_HUE_SLIDER.equalsIgnoreCase(param.getUiType())) {
                displayPalette(paramViewHolder, param);
            } else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(param.getUiType())) {

                if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("bool")
                        || dataType.equalsIgnoreCase("boolean"))) {

                    displayToggle(paramViewHolder, param, position);

                } else {
                    displayLabel(paramViewHolder, param, position);
                }

            } else if (AppConstants.UI_TYPE_TRIGGER.equalsIgnoreCase(param.getUiType())) {

                if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("bool")
                        || dataType.equalsIgnoreCase("boolean"))) {

                    displayTrigger(paramViewHolder, param);

                } else {
                    displayLabel(paramViewHolder, param, position);
                }

            } else if (AppConstants.UI_TYPE_DROP_DOWN.equalsIgnoreCase(param.getUiType())) {

                if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("string"))) {

                    displaySpinner(paramViewHolder, param, position);

                } else if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("int")
                        || dataType.equalsIgnoreCase("integer"))) {

                    int max = param.getMaxBounds();
                    int min = param.getMinBounds();
                    int stepCount = (int) param.getStepCount();
                    if (stepCount == 0) {
                        stepCount = 1;
                    }
                    ArrayList<String> spinnerValues = new ArrayList<>();
                    for (int i = min; i <= max; i = i + stepCount) {
                        spinnerValues.add(String.valueOf(i));
                    }

                    if ((min < max)) {
                        displaySpinner(paramViewHolder, param, position);
                    } else {
                        if (spinnerValues.size() > 0) {
                            displaySpinner(paramViewHolder, param, position);
                        } else {
                            displayLabel(paramViewHolder, param, position);
                        }
                    }
                } else {
                    displayLabel(paramViewHolder, param, position);
                }
            } else {
                displayLabel(paramViewHolder, param, position);
            }
        } else if (holder.getItemViewType() == VIEW_TYPE_PUSH_BTN_BIG) {

            final SwitchViewHolder switchViewHolder = (SwitchViewHolder) holder;

            if (param.getSwitchStatus()) {
                switchViewHolder.ivSwitch.setImageResource(R.drawable.ic_switch_on);
            } else {
                switchViewHolder.ivSwitch.setImageResource(R.drawable.ic_switch_off);
            }

            if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {

                if (((EspDeviceActivity) context).isNodeOnline()) {

                    switchViewHolder.ivSwitch.setAlpha(1f);
                    switchViewHolder.ivSwitch.setEnabled(true);

                    switchViewHolder.ivSwitch.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            ((EspDeviceActivity) context).stopUpdateValueTask();
                            ((EspDeviceActivity) context).showParamUpdateLoading("Updating...");

                            JsonObject jsonParam = new JsonObject();
                            JsonObject body = new JsonObject();

                            jsonParam.addProperty(param.getName(), !param.getSwitchStatus());
                            body.add(deviceName, jsonParam);

                            deviceParamUpdates.addParamUpdateRequest(body, new ApiResponseListener() {

                                @Override
                                public void onSuccess(Bundle data) {
                                    param.setSwitchStatus(!param.getSwitchStatus());
                                    ((EspDeviceActivity) context).startUpdateValueTask();
                                    ((EspDeviceActivity) context).hideParamUpdateLoading();
                                    if (param.getSwitchStatus()) {
                                        switchViewHolder.ivSwitch.setImageResource(R.drawable.ic_switch_on);
                                    } else {
                                        switchViewHolder.ivSwitch.setImageResource(R.drawable.ic_switch_off);
                                    }
                                }

                                @Override
                                public void onResponseFailure(Exception exception) {
                                    ((EspDeviceActivity) context).startUpdateValueTask();
                                    ((EspDeviceActivity) context).hideParamUpdateLoading();
                                    if (param.getSwitchStatus()) {
                                        switchViewHolder.ivSwitch.setImageResource(R.drawable.ic_switch_on);
                                    } else {
                                        switchViewHolder.ivSwitch.setImageResource(R.drawable.ic_switch_off);
                                    }
                                }

                                @Override
                                public void onNetworkFailure(Exception exception) {
                                    ((EspDeviceActivity) context).startUpdateValueTask();
                                    ((EspDeviceActivity) context).hideParamUpdateLoading();
                                    if (param.getSwitchStatus()) {
                                        switchViewHolder.ivSwitch.setImageResource(R.drawable.ic_switch_on);
                                    } else {
                                        switchViewHolder.ivSwitch.setImageResource(R.drawable.ic_switch_off);
                                    }
                                }
                            });
                        }
                    });
                } else {
                    switchViewHolder.ivSwitch.setAlpha(0.5f);
                    switchViewHolder.ivSwitch.setEnabled(false);
                }
            } else {
                switchViewHolder.ivSwitch.setEnabled(false);
            }

        } else if (holder.getItemViewType() == VIEW_TYPE_HUE) {
            displayHueCircle((HueViewHolder) holder, param);
        }
    }

    public void updateParamList(ArrayList<Param> paramList) {
        ArrayList<Param> newParamList = new ArrayList<>(paramList);
        final ParamDiffCallback diffCallback = new ParamDiffCallback(this.params, newParamList);
        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        this.params.clear();
        this.params.addAll(newParamList);
        diffResult.dispatchUpdatesTo(this);
    }

    private void displayPalette(ParamViewHolder paramViewHolder, final Param param) {

        paramViewHolder.rlUiTypeSlider.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeSwitch.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeLabel.setVisibility(View.GONE);
        paramViewHolder.rlPalette.setVisibility(View.VISIBLE);
        paramViewHolder.rlUiTypeDropDown.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeTrigger.setVisibility(View.GONE);

        paramViewHolder.tvLabelPalette.setText(param.getName());
        paramViewHolder.paletteBar.setColor((int) param.getValue());
        paramViewHolder.paletteBar.setThumbCircleRadius(14);
        paramViewHolder.paletteBar.setTrackMarkHeight(10);

        float max = param.getMaxBounds();
        float min = param.getMinBounds();
        paramViewHolder.tvMinHue.setText(String.valueOf((int) min));
        paramViewHolder.tvMaxHue.setText(String.valueOf((int) max));

        if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {

            if (((EspDeviceActivity) context).isNodeOnline()) {

                paramViewHolder.paletteBar.setEnabled(true);
                paramViewHolder.tvMinHue.setAlpha(1f);
                paramViewHolder.tvMaxHue.setAlpha(1f);

                paramViewHolder.paletteBar.setListener(new PaletteBar.PaletteBarListener() {
                    @Override
                    public void onColorSelected(int colorInt, boolean isMoving) {

                        EspApplication espApp = (EspApplication) context.getApplicationContext();

                        boolean isMatterDeviceOnline = false;
                        if (!TextUtils.isEmpty(matterNodeId) && espApp.chipClientMap.containsKey(matterNodeId)) {
                            isMatterDeviceOnline = true;
                        }

                        if (isMatterDeviceOnline) {
                            if (hueColorValue == colorInt) {
                                Log.d(TAG, "Ignore same hue color value");
                                return;
                            }
                            hueColorValue = colorInt;

                            int hueValue = (int) ((colorInt * 255f) / 360f);
                            BigInteger id = new BigInteger(matterNodeId, 16);
                            long deviceId = id.longValue();

                            if (hueValue == 255) {
                                hueValue = 254;
                            }

                            ColorControlClusterHelper espClusterHelper = new ColorControlClusterHelper(espApp.chipClientMap.get(matterNodeId));
                            espClusterHelper.setHueValueAsync(deviceId, AppConstants.ENDPOINT_1, hueValue);

                        } else {
                            ((EspDeviceActivity) context).setIsUpdateView(!isMoving);
                            if (BuildConfig.isContinuousUpdateEnable) {
                                deviceParamUpdates.processSliderChange(param.getName(), colorInt);
                            } else {
                                if (!isMoving) {
                                    ((EspDeviceActivity) context).stopUpdateValueTask();
                                    JsonObject jsonParam = new JsonObject();
                                    JsonObject body = new JsonObject();

                                    jsonParam.addProperty(param.getName(), colorInt);
                                    body.add(deviceName, jsonParam);
                                    deviceParamUpdates.addParamUpdateRequest(body, new ApiResponseListener() {

                                        @Override
                                        public void onSuccess(Bundle data) {
                                            ((EspDeviceActivity) context).startUpdateValueTask();
                                        }

                                        @Override
                                        public void onResponseFailure(Exception exception) {
                                            ((EspDeviceActivity) context).startUpdateValueTask();
                                        }

                                        @Override
                                        public void onNetworkFailure(Exception exception) {
                                            ((EspDeviceActivity) context).startUpdateValueTask();
                                        }
                                    });
                                }
                            }
                        }
                    }
                });
            } else {
                paramViewHolder.paletteBar.setEnabled(false);
                paramViewHolder.tvMinHue.setAlpha(0.4f);
                paramViewHolder.tvMaxHue.setAlpha(0.4f);
            }
        }
    }

    private void displayHueCircle(HueViewHolder holder, Param param) {
        final HueViewHolder hueViewHolder = holder;

        int hueColor = (int) param.getValue();

        float[] hsv = new float[3];
        hsv[0] = hueColor;
        hsv[1] = 10.0f;
        hsv[2] = 10.0f;

        int mCurrentIntColor = Color.HSVToColor(hsv);
        hueViewHolder.colorPickerView.setShowOldCenterColor(false);
        hueViewHolder.colorPickerView.setColor(mCurrentIntColor);

        if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {

            if (((EspDeviceActivity) context).isNodeOnline()) {

                hueViewHolder.colorPickerView.setAlpha(1f);
                hueViewHolder.colorPickerView.setEnabled(true);

                hueViewHolder.colorPickerView.setOnColorChangedListener(new ColorPicker.OnColorChangedListener() {

                    @Override
                    public void onColorChanged(int color) {
                        ((EspDeviceActivity) context).setIsUpdateView(false);
                        circularColorChange(hueViewHolder, param, color, true);
                    }
                });

                hueViewHolder.colorPickerView.setOnColorSelectedListener(new ColorPicker.OnColorSelectedListener() {
                    @Override
                    public void onColorSelected(int color) {
                        ((EspDeviceActivity) context).setIsUpdateView(true);
                        circularColorChange(hueViewHolder, param, color, false);
                    }
                });
            } else {
                hueViewHolder.colorPickerView.setAlpha(0.5f);
                hueViewHolder.colorPickerView.setEnabled(false);
                hueViewHolder.colorPickerView.setOnColorChangedListener(null);
            }
        } else {
            hueViewHolder.colorPickerView.setEnabled(false);
            hueViewHolder.colorPickerView.setOnColorChangedListener(null);
        }
    }

    private void circularColorChange(HueViewHolder hueViewHolder, Param param, int color, boolean isMoving) {
        float[] newHsv = new float[3];
        Color.colorToHSV(color, newHsv);
        int colorInt = (int) newHsv[0];

        if (BuildConfig.isContinuousUpdateEnable) {
            deviceParamUpdates.processSliderChange(param.getName(), colorInt);
        } else {
            if (!isMoving) {
                JsonObject jsonParam = new JsonObject();
                JsonObject body = new JsonObject();
                jsonParam.addProperty(param.getName(), colorInt);
                body.add(deviceName, jsonParam);

                ((EspDeviceActivity) context).stopUpdateValueTask();
                ((EspDeviceActivity) context).showParamUpdateLoading("Updating...");

                deviceParamUpdates.addParamUpdateRequest(body, new ApiResponseListener() {

                    @Override
                    public void onSuccess(Bundle data) {
                        ((EspDeviceActivity) context).startUpdateValueTask();
                        ((EspDeviceActivity) context).hideParamUpdateLoading();
                    }

                    @Override
                    public void onResponseFailure(Exception exception) {
                        ((EspDeviceActivity) context).startUpdateValueTask();
                        ((EspDeviceActivity) context).hideParamUpdateLoading();
                    }

                    @Override
                    public void onNetworkFailure(Exception exception) {
                        ((EspDeviceActivity) context).startUpdateValueTask();
                        ((EspDeviceActivity) context).hideParamUpdateLoading();
                    }
                });
            } else {
                ((EspDeviceActivity) context).stopUpdateValueTask();
            }
        }
    }

    private void displaySlider(final ParamViewHolder paramViewHolder, final Param param, final int position) {

        paramViewHolder.rlUiTypeSlider.setVisibility(View.VISIBLE);
        paramViewHolder.rlUiTypeSwitch.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeLabel.setVisibility(View.GONE);
        paramViewHolder.rlPalette.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeDropDown.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeTrigger.setVisibility(View.GONE);

        double sliderValue = param.getValue();
        paramViewHolder.tvSliderName.setText(param.getName());
        float max = param.getMaxBounds();
        float min = param.getMinBounds();
        String dataType = param.getDataType();
        EspApplication espApp = (EspApplication) context.getApplicationContext();

        if (AppConstants.PARAM_TYPE_CCT.equals(param.getParamType())) {

            paramViewHolder.ivSliderStart.setImageResource(R.drawable.ic_cct_low);
            paramViewHolder.ivSliderEnd.setImageResource(R.drawable.ic_cct_high);
            paramViewHolder.ivSliderStart.setVisibility(View.VISIBLE);
            paramViewHolder.ivSliderEnd.setVisibility(View.VISIBLE);

        } else if (AppConstants.PARAM_TYPE_BRIGHTNESS.equals(param.getParamType())) {

            paramViewHolder.ivSliderStart.setImageResource(R.drawable.ic_brightness_low);
            paramViewHolder.ivSliderEnd.setImageResource(R.drawable.ic_brightness_high);
            paramViewHolder.ivSliderStart.setVisibility(View.VISIBLE);
            paramViewHolder.ivSliderEnd.setVisibility(View.VISIBLE);

        } else if (AppConstants.PARAM_TYPE_SATURATION.equals(param.getParamType())) {

            paramViewHolder.ivSliderStart.setImageResource(R.drawable.ic_saturation_low);
            paramViewHolder.ivSliderEnd.setImageResource(R.drawable.ic_saturation_high);
            paramViewHolder.ivSliderStart.setVisibility(View.VISIBLE);
            paramViewHolder.ivSliderEnd.setVisibility(View.VISIBLE);

        } else {
            paramViewHolder.ivSliderStart.setVisibility(View.GONE);
            paramViewHolder.ivSliderEnd.setVisibility(View.GONE);
        }

        if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

            paramViewHolder.intSlider.setVisibility(View.VISIBLE);
            paramViewHolder.floatSlider.setVisibility(View.GONE);

            paramViewHolder.intSlider.setMax(max);
            paramViewHolder.intSlider.setMin(min);
            paramViewHolder.intSlider.setTickCount(2);

            if (sliderValue < min) {

                paramViewHolder.intSlider.setProgress(min);

            } else if (sliderValue > max) {

                paramViewHolder.intSlider.setProgress(max);

            } else {
                paramViewHolder.intSlider.setProgress((int) sliderValue);
            }

            if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {

                if (((EspDeviceActivity) context).isNodeOnline()) {

                    boolean shouldEnable = checkDependencies(param);

                    if (!shouldEnable) {
                        paramViewHolder.itemView.setEnabled(false);
                        paramViewHolder.intSlider.setEnabled(false);
                        paramViewHolder.intSlider.setOnSeekChangeListener(null);
                    } else {
                        paramViewHolder.itemView.setEnabled(true);
                        paramViewHolder.intSlider.setEnabled(true);
                        paramViewHolder.intSlider.setOnSeekChangeListener(new OnSeekChangeListener() {

                            @Override
                            public void onSeeking(SeekParams seekParams) {

                                switch (nodeStatus) {

                                    case AppConstants.NODE_STATUS_LOCAL:
                                    case AppConstants.NODE_STATUS_ONLINE:

                                        if (seekParams.fromUser) {
                                            ((EspDeviceActivity) context).setIsUpdateView(false);
                                            if (BuildConfig.isContinuousUpdateEnable) {
                                                deviceParamUpdates.processSliderChange(param.getName(), seekParams.progress);
                                            }
                                        }
                                        break;

                                    case AppConstants.NODE_STATUS_OFFLINE:
                                    case AppConstants.NODE_STATUS_MATTER_LOCAL:
                                    case AppConstants.NODE_STATUS_REMOTELY_CONTROLLABLE:
                                    default:
                                        break;
                                }
                            }

                            @Override
                            public void onStartTrackingTouch(TickSeekBar seekBar) {

                                switch (nodeStatus) {

                                    case AppConstants.NODE_STATUS_LOCAL:
                                    case AppConstants.NODE_STATUS_ONLINE:

                                        ((EspDeviceActivity) context).setIsUpdateView(false);
                                        if (!BuildConfig.isContinuousUpdateEnable) {
                                            ((EspDeviceActivity) context).stopUpdateValueTask();
                                        }
                                        break;

                                    case AppConstants.NODE_STATUS_OFFLINE:
                                    case AppConstants.NODE_STATUS_MATTER_LOCAL:
                                    case AppConstants.NODE_STATUS_REMOTELY_CONTROLLABLE:
                                    default:
                                        break;
                                }
                            }

                            @Override
                            public void onStopTrackingTouch(TickSeekBar seekBar) {

                                String paramType = param.getParamType();
                                int lastProgressValue = seekBar.getProgress();

                                switch (nodeStatus) {

                                    case AppConstants.NODE_STATUS_MATTER_LOCAL: {

                                        BigInteger id = new BigInteger(matterNodeId, 16);
                                        long deviceId = id.longValue();

                                        if (!TextUtils.isEmpty(matterNodeId) && espApp.chipClientMap.containsKey(matterNodeId)) {

                                            if (AppConstants.PARAM_TYPE_BRIGHTNESS.equals(paramType)
                                                    || AppConstants.PARAM_TYPE_SATURATION.equals(paramType)) {

                                                lastProgressValue = (int) (lastProgressValue * 2.55f);
                                                if (lastProgressValue == 255) {
                                                    lastProgressValue = 254;
                                                }
                                                if (AppConstants.PARAM_TYPE_BRIGHTNESS.equals(paramType)) {
                                                    LevelControlClusterHelper espClusterHelper = new LevelControlClusterHelper(espApp.chipClientMap.get(matterNodeId));
                                                    espClusterHelper.setLevelAsync(deviceId, AppConstants.ENDPOINT_1, lastProgressValue);
                                                } else if (AppConstants.PARAM_TYPE_SATURATION.equals(paramType)) {
                                                    ColorControlClusterHelper espClusterHelper = new ColorControlClusterHelper(espApp.chipClientMap.get(matterNodeId));
                                                    espClusterHelper.setSaturationValueAsync(deviceId, AppConstants.ENDPOINT_1, lastProgressValue);
                                                }
                                            } else if (AppConstants.PARAM_TYPE_SETPOINT_TEMPERATURE.equals(paramType)) {

                                                params.get(position).setValue(lastProgressValue);
                                                params.get(position).setLabelValue(String.valueOf(lastProgressValue));
                                                updateParamList(params);

                                                lastProgressValue = Utils.temperatureAppToDeviceConversion(lastProgressValue);

                                                ThermostatClusterHelper espClusterHelper = new ThermostatClusterHelper(espApp.chipClientMap.get(matterNodeId));
                                                if (AppConstants.PARAM_COOLING_POINT.equals(param.getName())) {
                                                    espClusterHelper.setOccupiedCoolingSetpointAsync(deviceId, AppConstants.ENDPOINT_1, lastProgressValue);
                                                } else if (AppConstants.PARAM_HEATING_POINT.equals(param.getName())) {
                                                    espClusterHelper.setOccupiedHeatingSetpointAsync(deviceId, AppConstants.ENDPOINT_1, lastProgressValue);
                                                }
                                            }
                                        }
                                    }
                                    break;

                                    case AppConstants.NODE_STATUS_REMOTELY_CONTROLLABLE: {

                                        lastProgressValue = (int) (lastProgressValue * 2.55f);
                                        if (lastProgressValue == 255) {
                                            lastProgressValue = 254;
                                        }

                                        RemoteControlApiHelper apiHelper = new RemoteControlApiHelper(espApp);

                                        for (Map.Entry<String, HashMap<String, String>> entry : espApp.controllerDevices.entrySet()) {

                                            HashMap<String, String> controllerDevices = entry.getValue();

                                            if (controllerDevices.containsKey(matterNodeId)) {

                                                EspNode controllerNode = espApp.nodeMap.get(entry.getKey());
                                                Service controllerService = NodeUtils.Companion.getService(controllerNode, AppConstants.SERVICE_TYPE_MATTER_CONTROLLER);

                                                if (controllerService != null) {

                                                    for (Param deviceParam : controllerService.getParams()) {

                                                        if (AppConstants.PARAM_TYPE_MATTER_DEVICES.equals(deviceParam.getParamType())) {

                                                            apiHelper.callBrightnessAPI(entry.getKey(), matterNodeId, deviceParam.getName(),
                                                                    controllerService.getName(), ESPControllerAPIKeys.ENDPOINT_ID_1_HEX, lastProgressValue, new ApiResponseListener() {
                                                                        @Override
                                                                        public void onSuccess(@Nullable Bundle data) {
                                                                        }

                                                                        @Override
                                                                        public void onResponseFailure(@NonNull Exception exception) {
                                                                        }

                                                                        @Override
                                                                        public void onNetworkFailure(@NonNull Exception exception) {
                                                                        }
                                                                    });

                                                            break;
                                                        }
                                                    }
                                                }
                                                break;
                                            }
                                        }
                                    }
                                    break;

                                    case AppConstants.NODE_STATUS_LOCAL:
                                    case AppConstants.NODE_STATUS_ONLINE:
                                        ((EspDeviceActivity) context).setIsUpdateView(true);

                                        deviceParamUpdates.clearQueueAndSendLastValue(param.getName(), lastProgressValue, new ApiResponseListener() {
                                            @Override
                                            public void onSuccess(Bundle data) {
                                                ((EspDeviceActivity) context).startUpdateValueTask();
                                            }

                                            @Override
                                            public void onResponseFailure(Exception exception) {
                                                ((EspDeviceActivity) context).startUpdateValueTask();
                                            }

                                            @Override
                                            public void onNetworkFailure(Exception exception) {
                                                ((EspDeviceActivity) context).startUpdateValueTask();
                                            }
                                        });

                                    case AppConstants.NODE_STATUS_OFFLINE:
                                    default:
                                        break;
                                }
                            }
                        });
                    }

                } else {
                    paramViewHolder.intSlider.setEnabled(false);
                }
            } else {
                paramViewHolder.intSlider.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return true;
                    }
                });
            }
        } else {

            paramViewHolder.intSlider.setVisibility(View.GONE);
            paramViewHolder.floatSlider.setVisibility(View.VISIBLE);

            paramViewHolder.floatSlider.setMax(max);
            paramViewHolder.floatSlider.setMin(min);
            paramViewHolder.floatSlider.setTickCount(2);

            if (sliderValue < min) {

                paramViewHolder.floatSlider.setProgress(min);

            } else if (sliderValue > max) {

                paramViewHolder.floatSlider.setProgress(max);

            } else {
                paramViewHolder.floatSlider.setProgress((float) sliderValue);
            }

            if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {

                if (((EspDeviceActivity) context).isNodeOnline()) {

                    paramViewHolder.floatSlider.setEnabled(true);

                    paramViewHolder.floatSlider.setOnSeekChangeListener(new OnSeekChangeListener() {

                        @Override
                        public void onSeeking(SeekParams seekParams) {
                            if (seekParams.fromUser) {
                                ((EspDeviceActivity) context).setIsUpdateView(false);
                                if (BuildConfig.isContinuousUpdateEnable) {
                                    deviceParamUpdates.processSliderChange(param.getName(), seekParams.progressFloat);
                                }
                            }
                        }

                        @Override
                        public void onStartTrackingTouch(TickSeekBar seekBar) {
                            ((EspDeviceActivity) context).setIsUpdateView(false);
                            if (!BuildConfig.isContinuousUpdateEnable) {
                                ((EspDeviceActivity) context).stopUpdateValueTask();
                            }
                        }

                        @Override
                        public void onStopTrackingTouch(TickSeekBar seekBar) {

                            ((EspDeviceActivity) context).setIsUpdateView(true);
                            float lastProgressValue = seekBar.getProgressFloat();

                            deviceParamUpdates.clearQueueAndSendLastValue(param.getName(), lastProgressValue, new ApiResponseListener() {
                                @Override
                                public void onSuccess(Bundle data) {
                                    ((EspDeviceActivity) context).startUpdateValueTask();
                                }

                                @Override
                                public void onResponseFailure(Exception exception) {
                                    ((EspDeviceActivity) context).startUpdateValueTask();
                                }

                                @Override
                                public void onNetworkFailure(Exception exception) {
                                    ((EspDeviceActivity) context).startUpdateValueTask();
                                }
                            });
                        }
                    });
                } else {
                    paramViewHolder.floatSlider.setEnabled(false);
                }

            } else {

                paramViewHolder.floatSlider.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return true;
                    }
                });
            }
        }
    }

    private boolean checkDependencies(Param param) {

        switch (nodeStatus) {

            case AppConstants.NODE_STATUS_ONLINE:
            case AppConstants.NODE_STATUS_LOCAL:
                if (!TextUtils.isEmpty(param.getDependencies())) {

                    String dependencies = param.getDependencies();

                    try {
                        JSONObject jsonObject = new JSONObject(dependencies);
                        Iterator<String> keys = jsonObject.keys();

                        while (keys.hasNext()) {
                            String key = keys.next();
                            String value = jsonObject.getString(key);

                            for (Param p : params) {

                                if (key.equals(p.getName())) {

                                    String paramValue = p.getLabelValue();

                                    if (!TextUtils.isEmpty(value)) {
                                        if (value.equalsIgnoreCase(paramValue)
                                                && AppConstants.PARAM_COOLING_POINT.equals(param.getName())) {
                                            return true;
                                        } else if (value.equalsIgnoreCase(paramValue)
                                                && AppConstants.PARAM_HEATING_POINT.equals(param.getName())) {
                                            return true;
                                        } else {
                                            return false;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    return true;
                }

            case AppConstants.NODE_STATUS_MATTER_LOCAL:
                if (AppConstants.PARAM_COOLING_POINT.equals(param.getName())) {
                    for (Param p : params) {
                        if (AppConstants.PARAM_SYSTEM_MODE.equals(p.getName())) {
                            if (p.getValue() == SystemMode.COOL.getModeValue()) {
                                return true;
                            } else {
                                return false;
                            }
                        }
                    }
                } else if (AppConstants.PARAM_HEATING_POINT.equals(param.getName())) {
                    for (Param p : params) {
                        if (AppConstants.PARAM_SYSTEM_MODE.equals(p.getName())) {
                            if (p.getValue() == SystemMode.HEAT.getModeValue()) {
                                return true;
                            } else {
                                return false;
                            }
                        }
                    }
                }
                return true;
        }
        return true;
    }

    private void displayToggle(final ParamViewHolder paramViewHolder, final Param param, final int position) {

        paramViewHolder.rlUiTypeSlider.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeSwitch.setVisibility(View.VISIBLE);
        paramViewHolder.rlUiTypeLabel.setVisibility(View.GONE);
        paramViewHolder.rlPalette.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeDropDown.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeTrigger.setVisibility(View.GONE);

        paramViewHolder.tvSwitchName.setText(param.getName());
        paramViewHolder.tvSwitchStatus.setVisibility(View.VISIBLE);

        if (AppConstants.ESP_DEVICE_LOCK.equals(device.getDeviceType())) {

            if (param.getSwitchStatus()) {
                paramViewHolder.tvSwitchStatus.setText(LockState.UNLOCK.getLockModeText());
            } else {
                paramViewHolder.tvSwitchStatus.setText(LockState.LOCK.getLockModeText());
            }
        } else {
            if (param.getSwitchStatus()) {
                paramViewHolder.tvSwitchStatus.setText(R.string.text_on);
            } else {
                paramViewHolder.tvSwitchStatus.setText(R.string.text_off);
            }
        }

        if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {

            paramViewHolder.toggleSwitch.setVisibility(View.VISIBLE);
            paramViewHolder.toggleSwitch.setOnCheckedChangeListener(null);
            paramViewHolder.toggleSwitch.setChecked(param.getSwitchStatus());

            if (((EspDeviceActivity) context).isNodeOnline()) {

                paramViewHolder.toggleSwitch.setEnabled(true);

                paramViewHolder.toggleSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                        switch (nodeStatus) {
                            case AppConstants.NODE_STATUS_MATTER_LOCAL:
                                if (!TextUtils.isEmpty(matterNodeId) && espApp.chipClientMap.containsKey(matterNodeId)) {
                                    BigInteger id = new BigInteger(matterNodeId, 16);
                                    long deviceId = id.longValue();

                                    if (AppConstants.ESP_DEVICE_LOCK.equals(device.getDeviceType())) {

                                        DoorLockClusterHelper espClusterHelper = new DoorLockClusterHelper(espApp.chipClientMap.get(matterNodeId));
                                        try {
                                            if (isChecked) {
                                                espClusterHelper.unlockDoorAsync(deviceId, AppConstants.ENDPOINT_1, AppConstants.DOOR_LOCK_PIN);
                                                params.get(position).setLabelValue(LockState.UNLOCK.getLockModeText());
                                            } else {
                                                espClusterHelper.lockDoorAsync(deviceId, AppConstants.ENDPOINT_1, AppConstants.DOOR_LOCK_PIN);
                                                params.get(position).setLabelValue(LockState.LOCK.getLockModeText());
                                            }
                                            params.get(position).setSwitchStatus(isChecked);
                                            updateParamList(params);

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    } else {
                                        OnOffClusterHelper espClusterHelper = new OnOffClusterHelper(espApp.chipClientMap.get(matterNodeId));
                                        espClusterHelper.setOnOffDeviceStateOnOffClusterAsync(deviceId, isChecked, AppConstants.ENDPOINT_1);
                                        param.setSwitchStatus(!isChecked);
                                        if (isChecked) {
                                            paramViewHolder.tvSwitchStatus.setText(R.string.text_on);
                                        } else {
                                            paramViewHolder.tvSwitchStatus.setText(R.string.text_off);
                                        }
                                    }
                                }
                                break;

                            case AppConstants.NODE_STATUS_REMOTELY_CONTROLLABLE:
                                RemoteControlApiHelper apiHelper = new RemoteControlApiHelper(espApp);

                                for (Map.Entry<String, HashMap<String, String>> entry : espApp.controllerDevices.entrySet()) {

                                    HashMap<String, String> controllerDevices = entry.getValue();

                                    if (controllerDevices.containsKey(matterNodeId)) {

                                        EspNode espNode = espApp.nodeMap.get(entry.getKey());
                                        Service controllerService = NodeUtils.Companion.getService(espNode, AppConstants.SERVICE_TYPE_MATTER_CONTROLLER);

                                        if (controllerService != null) {
                                            for (Param deviceParam : controllerService.getParams()) {
                                                if (AppConstants.PARAM_TYPE_MATTER_DEVICES.equals(deviceParam.getParamType())) {

                                                    if (isChecked) {
                                                        apiHelper.callOnAPI(entry.getKey(), matterNodeId, deviceParam.getName(), controllerService.getName(), ESPControllerAPIKeys.ENDPOINT_ID_1_HEX, new ApiResponseListener() {
                                                            @Override
                                                            public void onSuccess(@Nullable Bundle data) {
                                                            }

                                                            @Override
                                                            public void onResponseFailure(@NonNull Exception exception) {
                                                            }

                                                            @Override
                                                            public void onNetworkFailure(@NonNull Exception exception) {
                                                            }
                                                        });
                                                    } else {
                                                        apiHelper.callOffAPI(entry.getKey(), matterNodeId, deviceParam.getName(), controllerService.getName(), ESPControllerAPIKeys.ENDPOINT_ID_1_HEX, new ApiResponseListener() {
                                                            @Override
                                                            public void onSuccess(@Nullable Bundle data) {
                                                            }

                                                            @Override
                                                            public void onResponseFailure(@NonNull Exception exception) {
                                                            }

                                                            @Override
                                                            public void onNetworkFailure(@NonNull Exception exception) {
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

                            case AppConstants.NODE_STATUS_LOCAL:
                            case AppConstants.NODE_STATUS_ONLINE:
                                ((EspDeviceActivity) context).stopUpdateValueTask();

                                if (isChecked) {
                                    paramViewHolder.tvSwitchStatus.setText(R.string.text_on);
                                } else {
                                    paramViewHolder.tvSwitchStatus.setText(R.string.text_off);
                                }

                                JsonObject jsonParam = new JsonObject();
                                JsonObject body = new JsonObject();

                                jsonParam.addProperty(param.getName(), isChecked);
                                body.add(deviceName, jsonParam);

                                deviceParamUpdates.addParamUpdateRequest(body, new ApiResponseListener() {

                                    @Override
                                    public void onSuccess(Bundle data) {
                                        ((EspDeviceActivity) context).startUpdateValueTask();
                                    }

                                    @Override
                                    public void onResponseFailure(Exception exception) {
                                        ((EspDeviceActivity) context).startUpdateValueTask();
                                    }

                                    @Override
                                    public void onNetworkFailure(Exception exception) {
                                        ((EspDeviceActivity) context).startUpdateValueTask();
                                    }
                                });
                                break;

                            case AppConstants.NODE_STATUS_OFFLINE:
                            default:
                                break;
                        }
                    }
                });

            } else {
                paramViewHolder.toggleSwitch.setEnabled(false);
            }

        } else {

            paramViewHolder.toggleSwitch.setVisibility(View.GONE);
        }
    }

    private void displayTrigger(final ParamViewHolder paramViewHolder, final Param param) {

        paramViewHolder.rlUiTypeSlider.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeSwitch.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeLabel.setVisibility(View.GONE);
        paramViewHolder.rlPalette.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeDropDown.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeTrigger.setVisibility(View.VISIBLE);

        paramViewHolder.tvTriggerName.setText(param.getName());

        if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {

            if (((EspDeviceActivity) context).isNodeOnline()) {

                paramViewHolder.btnTrigger.setAlpha(1f);
                paramViewHolder.btnTrigger.setEnabled(true);
                paramViewHolder.btnTrigger.setClickable(true);
                paramViewHolder.btnTrigger.enableLongHold(true);
                paramViewHolder.btnTrigger.setOnButtonClickListener(new TapHoldUpButton.OnButtonClickListener() {

                    @Override
                    public void onLongHoldStart(View v) {
                    }

                    @Override
                    public void onLongHoldEnd(View v) {
                        ((EspDeviceActivity) context).stopUpdateValueTask();
                        JsonObject jsonParam = new JsonObject();
                        JsonObject body = new JsonObject();

                        jsonParam.addProperty(param.getName(), true);
                        body.add(deviceName, jsonParam);

                        deviceParamUpdates.addParamUpdateRequest(body, new ApiResponseListener() {

                            @Override
                            public void onSuccess(Bundle data) {
                                ((EspDeviceActivity) context).startUpdateValueTask();
                            }

                            @Override
                            public void onResponseFailure(Exception exception) {
                                ((EspDeviceActivity) context).startUpdateValueTask();
                            }

                            @Override
                            public void onNetworkFailure(Exception exception) {
                                ((EspDeviceActivity) context).startUpdateValueTask();
                            }
                        });
                    }

                    @Override
                    public void onClick(View v) {
                        ((EspDeviceActivity) context).stopUpdateValueTask();
                        JsonObject jsonParam = new JsonObject();
                        JsonObject body = new JsonObject();

                        jsonParam.addProperty(param.getName(), true);
                        body.add(deviceName, jsonParam);

                        deviceParamUpdates.addParamUpdateRequest(body, new ApiResponseListener() {

                            @Override
                            public void onSuccess(Bundle data) {
                                ((EspDeviceActivity) context).startUpdateValueTask();
                            }

                            @Override
                            public void onResponseFailure(Exception exception) {
                                ((EspDeviceActivity) context).startUpdateValueTask();
                            }

                            @Override
                            public void onNetworkFailure(Exception exception) {
                                ((EspDeviceActivity) context).startUpdateValueTask();
                            }
                        });
                    }
                });

            } else {
                paramViewHolder.btnTrigger.setAlpha(0.3f);
                paramViewHolder.btnTrigger.setEnabled(false);
                paramViewHolder.btnTrigger.setClickable(false);
                paramViewHolder.btnTrigger.enableLongHold(false);
                paramViewHolder.btnTrigger.setOnButtonClickListener(null);
            }

        } else {
            paramViewHolder.btnTrigger.setVisibility(View.GONE);
        }
    }

    private void displayLabel(final ParamViewHolder paramViewHolder, final Param param,
                              final int position) {

        paramViewHolder.rlUiTypeSlider.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeSwitch.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeLabel.setVisibility(View.VISIBLE);
        paramViewHolder.rlPalette.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeDropDown.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeTrigger.setVisibility(View.GONE);

        paramViewHolder.tvLabelName.setText(param.getName());
        paramViewHolder.tvLabelValue.setText(param.getLabelValue());

        if (param.getProperties().contains(AppConstants.KEY_PROPERTY_TS)) {

            paramViewHolder.ivTsArrow.setVisibility(View.VISIBLE);
            paramViewHolder.ivTsArrow.setOnClickListener(v -> startTimeSeriesActivity(param, AppConstants.KEY_PROPERTY_TS));
            paramViewHolder.itemView.setOnClickListener(v -> startTimeSeriesActivity(param, AppConstants.KEY_PROPERTY_TS));

        } else if (param.getProperties().contains(AppConstants.KEY_PROPERTY_TS_SIMPLE)) {

            paramViewHolder.ivTsArrow.setVisibility(View.VISIBLE);
            paramViewHolder.ivTsArrow.setOnClickListener(v -> startTimeSeriesActivity(param, AppConstants.KEY_PROPERTY_TS_SIMPLE));
            paramViewHolder.itemView.setOnClickListener(v -> startTimeSeriesActivity(param, AppConstants.KEY_PROPERTY_TS_SIMPLE));
            
        } else {
            paramViewHolder.ivTsArrow.setVisibility(View.GONE);
            paramViewHolder.ivTsArrow.setOnClickListener(null);
        }

        if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE) && ((EspDeviceActivity) context).isNodeOnline()) {

            paramViewHolder.btnEdit.setVisibility(View.VISIBLE);

            paramViewHolder.btnEdit.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    askForNewValue(paramViewHolder, param, position);
                }
            });
        } else {
            paramViewHolder.btnEdit.setVisibility(View.GONE);
        }

        if (AppConstants.PARAM_TYPE_TEMPERATURE.equals(param.getParamType())) {
            if (nodeStatus == AppConstants.NODE_STATUS_MATTER_LOCAL && device.getDeviceType().equals(AppConstants.ESP_DEVICE_TEMP_SENSOR)) {
                if (!TextUtils.isEmpty(matterNodeId) && espApp.chipClientMap.containsKey(matterNodeId)) {
                    BigInteger id = new BigInteger(matterNodeId, 16);
                    long deviceId = id.longValue();
                    TemperatureClusterHelper espClusterHelper = new TemperatureClusterHelper(espApp.chipClientMap.get(matterNodeId));
                    try {
                        double temp = espClusterHelper.getTemperatureAsync(deviceId, AppConstants.ENDPOINT_1).get();
                        param.setLabelValue(String.valueOf(temp));
                        paramViewHolder.tvLabelValue.setText(param.getLabelValue());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void displaySpinner(final ParamViewHolder paramViewHolder, final Param param,
                                final int position) {

        paramViewHolder.rlUiTypeSlider.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeSwitch.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeLabel.setVisibility(View.GONE);
        paramViewHolder.rlPalette.setVisibility(View.GONE);
        paramViewHolder.rlUiTypeDropDown.setVisibility(View.VISIBLE);

        paramViewHolder.tvSpinnerName.setText(param.getName());
        paramViewHolder.spinner.setVisibility(View.VISIBLE);

        paramViewHolder.spinner.setEnabled(false);
        paramViewHolder.spinner.setOnItemSelectedListener(null);

        final String dataType = param.getDataType();

        if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("string"))) {

            if (param.getValidStrings() != null && param.getValidStrings().size() > 0) {

                ArrayList<String> spinnerValues = new ArrayList<>();
                spinnerValues.addAll(param.getValidStrings());
                ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, spinnerValues);
                dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                paramViewHolder.spinner.setAdapter(dataAdapter);
                boolean isValueFound = false;
                String value = param.getLabelValue();

                for (int i = 0; i < param.getValidStrings().size(); i++) {

                    if (!TextUtils.isEmpty(value)) {

                        if (value.equals(param.getValidStrings().get(i))) {
                            isValueFound = true;
                            paramViewHolder.spinner.setSelection(i, false);
                            paramViewHolder.spinner.setTag(R.id.position, i);
                            break;
                        }
                    }
                }

                if (!isValueFound) {
                    spinnerValues.add(0, "");
                    paramViewHolder.spinner.setSelection(0, false);
                    paramViewHolder.spinner.setTag(R.id.position, 0);
                    dataAdapter.notifyDataSetChanged();
                    String strInvalidValue = "" + value + " (" + context.getString(R.string.invalid) + ")";
                    paramViewHolder.tvSpinnerValue.setText(strInvalidValue);
                    paramViewHolder.tvSpinnerValue.setVisibility(View.VISIBLE);
                } else {
                    paramViewHolder.tvSpinnerValue.setVisibility(View.GONE);
                }
            }
        } else if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("int")
                || dataType.equalsIgnoreCase("integer"))) {

            int sliderValue = (int) param.getValue();
            int max = param.getMaxBounds();
            int min = param.getMinBounds();
            ArrayList<String> spinnerValues = new ArrayList<>();

            int stepCount = (int) param.getStepCount();
            if (stepCount == 0) {
                stepCount = 1;
            }
            for (int i = min; i <= max; i = i + stepCount) {
                spinnerValues.add(String.valueOf(i));
            }

            if (spinnerValues.size() > 0) {

                ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, spinnerValues);
                dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                paramViewHolder.spinner.setAdapter(dataAdapter);

                if (sliderValue < min) {

                    paramViewHolder.spinner.setSelection(0);
                    paramViewHolder.spinner.setTag(R.id.position, 0);

                } else if (sliderValue > max) {

                    int lastPosition = spinnerValues.size() - 1;
                    paramViewHolder.spinner.setSelection(lastPosition);
                    paramViewHolder.spinner.setTag(R.id.position, lastPosition);

                } else {

                    boolean isValueFound = false;
                    for (int i = 0; i < spinnerValues.size(); i++) {

                        String value = spinnerValues.get(i);
                        int intValue = Integer.parseInt(value);

                        if (sliderValue == intValue) {
                            isValueFound = true;
                            paramViewHolder.spinner.setSelection(i, false);
                            paramViewHolder.spinner.setTag(R.id.position, i);
                            break;
                        }
                    }

                    if (!isValueFound) {
                        spinnerValues.add(0, "");
                        paramViewHolder.spinner.setSelection(0, false);
                        paramViewHolder.spinner.setTag(R.id.position, 0);
                        dataAdapter.notifyDataSetChanged();
                        String strInvalidValue = "" + sliderValue + " (" + context.getString(R.string.invalid) + ")";
                        paramViewHolder.tvSpinnerValue.setText(strInvalidValue);
                        paramViewHolder.tvSpinnerValue.setVisibility(View.VISIBLE);
                    } else {
                        paramViewHolder.tvSpinnerValue.setVisibility(View.GONE);
                    }
                }
            } else {
                // Displayed label for this condition.
            }
        }

        if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE) && ((EspDeviceActivity) context).isNodeOnline()) {

            paramViewHolder.spinner.setSpinnerEventsListener(new EspDropDown.OnSpinnerEventsListener() {

                @Override
                public void onSpinnerOpened(Spinner spin) {
                    ((EspDeviceActivity) context).stopUpdateValueTask();
                }

                @Override
                public void onSpinnerClosed(Spinner spin) {
                    ((EspDeviceActivity) context).startUpdateValueTask();
                }
            });

            paramViewHolder.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, final int pos, long id) {

                    Log.d(TAG, "Dropdown list item clicked position :  " + pos);

                    if ((int) paramViewHolder.spinner.getTag(R.id.position) != pos) {

                        final String newValue = parent.getItemAtPosition(pos).toString();
                        paramViewHolder.spinner.setVisibility(View.GONE);
                        paramViewHolder.progressBarSpinner.setVisibility(View.VISIBLE);

                        ((EspDeviceActivity) context).stopUpdateValueTask();

                        if (nodeStatus == AppConstants.NODE_STATUS_MATTER_LOCAL && AppConstants.PARAM_SYSTEM_MODE.equals(param.getName())) {

                            if (!TextUtils.isEmpty(matterNodeId) && espApp.chipClientMap.containsKey(matterNodeId)) {
                                BigInteger matterDeviceId = new BigInteger(matterNodeId, 16);
                                long deviceId = matterDeviceId.longValue();
                                int newModeValue = SystemMode.OFF.getModeValue();

                                if (newValue.equalsIgnoreCase(SystemMode.COOL.getModeName())) {
                                    newModeValue = SystemMode.COOL.getModeValue();
                                } else if (newValue.equalsIgnoreCase(SystemMode.HEAT.getModeName())) {
                                    newModeValue = SystemMode.HEAT.getModeValue();
                                }

                                ThermostatClusterHelper espClusterHelper = new ThermostatClusterHelper(espApp.chipClientMap.get(matterNodeId));
                                espClusterHelper.setSystemModeAsync(deviceId, AppConstants.ENDPOINT_1, newModeValue);
                                paramViewHolder.spinner.setTag(R.id.position, pos);
                                params.get(position).setValue(newModeValue);
                                params.get(position).setLabelValue(newValue);
                                updateParamList(params);
                                paramViewHolder.spinner.setVisibility(View.VISIBLE);
                                paramViewHolder.progressBarSpinner.setVisibility(View.GONE);
                            }
                        } else {

                            JsonObject jsonParam = new JsonObject();
                            JsonObject body = new JsonObject();

                            if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("string"))) {

                                jsonParam.addProperty(param.getName(), newValue);

                            } else if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("int")
                                    || dataType.equalsIgnoreCase("integer"))) {

                                jsonParam.addProperty(param.getName(), Integer.parseInt(newValue));
                            }
                            body.add(deviceName, jsonParam);

                            deviceParamUpdates.addParamUpdateRequest(body, new ApiResponseListener() {

                                @Override
                                public void onSuccess(Bundle data) {

                                    context.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {

                                            paramViewHolder.progressBarSpinner.setVisibility(View.GONE);
                                            paramViewHolder.spinner.setVisibility(View.VISIBLE);
                                            paramViewHolder.spinner.setTag(R.id.position, pos);

                                            if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("string"))) {

                                                params.get(position).setLabelValue(newValue);
                                                paramViewHolder.tvSpinnerValue.setVisibility(View.GONE);

                                            } else if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("int")
                                                    || dataType.equalsIgnoreCase("integer"))) {

                                                params.get(position).setValue(Integer.parseInt(newValue));
                                                paramViewHolder.tvSpinnerValue.setVisibility(View.GONE);
                                            }

                                            updateParamList(params);
                                            ((EspDeviceActivity) context).startUpdateValueTask();
                                        }
                                    });
                                }

                                @Override
                                public void onResponseFailure(Exception exception) {

                                    context.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            paramViewHolder.progressBarSpinner.setVisibility(View.GONE);
                                            paramViewHolder.spinner.setVisibility(View.VISIBLE);
                                            ((EspDeviceActivity) context).startUpdateValueTask();
                                        }
                                    });
                                }

                                @Override
                                public void onNetworkFailure(Exception exception) {

                                    context.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            paramViewHolder.progressBarSpinner.setVisibility(View.GONE);
                                            paramViewHolder.spinner.setVisibility(View.VISIBLE);
                                            ((EspDeviceActivity) context).startUpdateValueTask();
                                        }
                                    });
                                }
                            });
                        }
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            paramViewHolder.spinner.setEnabled(true);

        } else {
            paramViewHolder.spinner.setOnItemSelectedListener(null);
        }
    }

    private void askForNewValue(final ParamViewHolder paramViewHolder, final Param param,
                                final int position) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = context.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_attribute, null);
        builder.setView(dialogView);

        final EditText etAttribute = dialogView.findViewById(R.id.et_attr_value);

        if (!TextUtils.isEmpty(param.getDataType())) {

            String dataType = param.getDataType();

            if (dataType.equalsIgnoreCase("int")
                    || dataType.equalsIgnoreCase("integer")) {

                etAttribute.setInputType(InputType.TYPE_CLASS_NUMBER);
                etAttribute.setText(String.valueOf((int) param.getValue()));

            } else if (dataType.equalsIgnoreCase("float")
                    || dataType.equalsIgnoreCase("double")) {

                etAttribute.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                etAttribute.setText(String.valueOf(param.getValue()));

            } else if (dataType.equalsIgnoreCase("string")) {

                etAttribute.setInputType(InputType.TYPE_CLASS_TEXT);
                if (!TextUtils.isEmpty(param.getLabelValue())) {
                    etAttribute.setText(param.getLabelValue());
                }

                if (param.getParamType() != null && param.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                    etAttribute.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
                }
            }
        }

        etAttribute.setSelection(etAttribute.getText().length());
        etAttribute.requestFocus();

        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                String dataType = param.getDataType();
                final String value = etAttribute.getText().toString();

                if (params.get(position).getParamType() != null && params.get(position).getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {

                    if (TextUtils.isEmpty(value)) {
                        Toast.makeText(context, context.getString(R.string.error_device_name_empty), Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                ((EspDeviceActivity) context).stopUpdateValueTask();

                JsonObject jsonParam = new JsonObject();
                JsonObject body = new JsonObject();

                if (dataType.equalsIgnoreCase("bool")
                        || dataType.equalsIgnoreCase("boolean")) {

                    if (!TextUtils.isEmpty(value)) {

                        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")
                                || value.equalsIgnoreCase("0") || value.equalsIgnoreCase("1")) {

                            boolean isOn = false;

                            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("1")) {
                                isOn = true;
                            }

                            paramViewHolder.btnEdit.setVisibility(View.GONE);
                            paramViewHolder.progressBar.setVisibility(View.VISIBLE);

                            jsonParam.addProperty(param.getName(), isOn);
                            body.add(deviceName, jsonParam);

                            final boolean finalIsOn = isOn;
                            deviceParamUpdates.addParamUpdateRequest(body, new ApiResponseListener() {

                                @Override
                                public void onSuccess(Bundle data) {

                                    context.runOnUiThread(new Runnable() {

                                        @Override
                                        public void run() {

                                            paramViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                            paramViewHolder.progressBar.setVisibility(View.GONE);

                                            if (finalIsOn) {

                                                paramViewHolder.tvLabelValue.setText("true");
                                                params.get(position).setLabelValue("true");

                                            } else {
                                                paramViewHolder.tvLabelValue.setText("false");
                                                params.get(position).setLabelValue("false");
                                            }
                                            ((EspDeviceActivity) context).startUpdateValueTask();
                                        }
                                    });
                                }

                                @Override
                                public void onResponseFailure(Exception exception) {

                                    context.runOnUiThread(new Runnable() {

                                        @Override
                                        public void run() {

                                            paramViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                            paramViewHolder.progressBar.setVisibility(View.GONE);
                                            paramViewHolder.tvLabelValue.setText(param.getLabelValue());
                                            ((EspDeviceActivity) context).startUpdateValueTask();
                                        }
                                    });
                                }

                                @Override
                                public void onNetworkFailure(Exception exception) {

                                    context.runOnUiThread(new Runnable() {

                                        @Override
                                        public void run() {

                                            paramViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                            paramViewHolder.progressBar.setVisibility(View.GONE);
                                            paramViewHolder.tvLabelValue.setText(param.getLabelValue());
                                            ((EspDeviceActivity) context).startUpdateValueTask();
                                        }
                                    });
                                }
                            });

                        } else {

                            dialog.dismiss();
                            Toast.makeText(context, R.string.enter_valid_value, Toast.LENGTH_SHORT).show();
                        }
                    } else {

                        dialog.dismiss();
                        Toast.makeText(context, R.string.enter_valid_value, Toast.LENGTH_SHORT).show();
                    }

                } else if (dataType.equalsIgnoreCase("int")
                        || dataType.equalsIgnoreCase("integer")) {

                    int newValue = Integer.valueOf(value);
                    int max = param.getMaxBounds();
                    int min = param.getMinBounds();

                    if (min != max) {
                        if (newValue < min || newValue > max) {
                            Log.e(TAG, "New value is out of bounds");
                            Toast.makeText(context, context.getString(R.string.error_value_out_of_bound), Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    jsonParam.addProperty(param.getName(), newValue);
                    body.add(deviceName, jsonParam);

                    deviceParamUpdates.addParamUpdateRequest(body, new ApiResponseListener() {

                        @Override
                        public void onSuccess(Bundle data) {

                            context.runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    paramViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                    paramViewHolder.progressBar.setVisibility(View.GONE);
                                    paramViewHolder.tvLabelValue.setText(value);
                                    params.get(position).setLabelValue(value);
                                    ((EspDeviceActivity) context).startUpdateValueTask();
                                }
                            });
                        }

                        @Override
                        public void onResponseFailure(Exception exception) {

                            context.runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    paramViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                    paramViewHolder.progressBar.setVisibility(View.GONE);
                                    paramViewHolder.tvLabelValue.setText(param.getLabelValue());
                                    ((EspDeviceActivity) context).startUpdateValueTask();
                                }
                            });
                        }

                        @Override
                        public void onNetworkFailure(Exception exception) {

                            context.runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    paramViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                    paramViewHolder.progressBar.setVisibility(View.GONE);
                                    paramViewHolder.tvLabelValue.setText(param.getLabelValue());
                                    ((EspDeviceActivity) context).startUpdateValueTask();
                                }
                            });
                        }
                    });

                } else if (dataType.equalsIgnoreCase("float")
                        || dataType.equalsIgnoreCase("double")) {

                    float newValue = Float.valueOf(value);
                    float max = param.getMaxBounds();
                    float min = param.getMinBounds();

                    if (min != max) {
                        if (newValue < min || newValue > max) {
                            Log.e(TAG, "New value is out of bounds");
                            Toast.makeText(context, context.getString(R.string.error_value_out_of_bound), Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    jsonParam.addProperty(param.getName(), newValue);
                    body.add(deviceName, jsonParam);

                    deviceParamUpdates.addParamUpdateRequest(body, new ApiResponseListener() {

                        @Override
                        public void onSuccess(Bundle data) {

                            context.runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    paramViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                    paramViewHolder.progressBar.setVisibility(View.GONE);
                                    paramViewHolder.tvLabelValue.setText(value);
                                    params.get(position).setLabelValue(value);
                                    ((EspDeviceActivity) context).startUpdateValueTask();
                                }
                            });
                        }

                        @Override
                        public void onResponseFailure(Exception exception) {

                            context.runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    paramViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                    paramViewHolder.progressBar.setVisibility(View.GONE);
                                    paramViewHolder.tvLabelValue.setText(param.getLabelValue());
                                    ((EspDeviceActivity) context).startUpdateValueTask();
                                }
                            });
                        }

                        @Override
                        public void onNetworkFailure(Exception exception) {

                            context.runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    paramViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                    paramViewHolder.progressBar.setVisibility(View.GONE);
                                    paramViewHolder.tvLabelValue.setText(param.getLabelValue());
                                    ((EspDeviceActivity) context).startUpdateValueTask();
                                }
                            });
                        }
                    });

                } else {

                    EspNode espNode = espApp.nodeMap.get(nodeId);
                    if (params.get(position).getParamType() != null && params.get(position).getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {

                        if (AppConstants.NODE_TYPE_RM_MATTER.equals(espApp.nodeMap.get(nodeId).getNewNodeType())
                                || AppConstants.NODE_TYPE_PURE_MATTER.equals(espApp.nodeMap.get(nodeId).getNewNodeType())) {

                            JsonObject matterMetadataJson = new JsonObject();

                            // Update metadata
                            String metadata = espNode.getNodeMetadataJson();
                            try {
                                JSONObject metadataJsonObj = new JSONObject(metadata);
                                metadataJsonObj = metadataJsonObj.optJSONObject(AppConstants.KEY_MATTER);
                                Gson gson = new Gson();
                                JsonObject metadataJson = gson.fromJson(metadataJsonObj.toString(), JsonObject.class);
                                metadataJson.remove(AppConstants.KEY_DEVICENAME);
                                metadataJson.addProperty(AppConstants.KEY_DEVICENAME, value);
                                JsonObject matterMetadata = new JsonObject();
                                matterMetadata.add(AppConstants.KEY_MATTER, metadataJson);
                                matterMetadataJson.add(AppConstants.KEY_METADATA, matterMetadata);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            ApiManager apiManager = ApiManager.getInstance(espApp);
                            apiManager.updateNodeMetadata(nodeId, matterMetadataJson, new ApiResponseListener() {
                                @Override
                                public void onSuccess(@Nullable Bundle data) {

                                    context.runOnUiThread(() -> {
                                        paramViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                        paramViewHolder.progressBar.setVisibility(View.GONE);
                                        paramViewHolder.tvLabelValue.setText(value);
                                        params.get(position).setLabelValue(value);
                                        ((EspDeviceActivity) context).updateDeviceNameInTitle(value);
                                    });
                                }

                                @Override
                                public void onResponseFailure(@NonNull Exception exception) {
                                }

                                @Override
                                public void onNetworkFailure(@NonNull Exception exception) {
                                }
                            });
                        }
                    }

                    jsonParam.addProperty(param.getName(), value);
                    body.add(deviceName, jsonParam);

                    if (!AppConstants.NODE_TYPE_PURE_MATTER.equals(espApp.nodeMap.get(nodeId).getNewNodeType())) {
                        deviceParamUpdates.addParamUpdateRequest(body, new ApiResponseListener() {

                            @Override
                            public void onSuccess(Bundle data) {

                                context.runOnUiThread(new Runnable() {

                                    @Override
                                    public void run() {

                                        paramViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                        paramViewHolder.progressBar.setVisibility(View.GONE);
                                        paramViewHolder.tvLabelValue.setText(value);
                                        params.get(position).setLabelValue(value);

                                        if (params.get(position).getParamType() != null && params.get(position).getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {

                                            ((EspDeviceActivity) context).updateDeviceNameInTitle(value);
                                        }
                                        ((EspDeviceActivity) context).startUpdateValueTask();
                                    }
                                });
                            }

                            @Override
                            public void onResponseFailure(Exception exception) {

                                context.runOnUiThread(new Runnable() {

                                    @Override
                                    public void run() {
                                        paramViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                        paramViewHolder.progressBar.setVisibility(View.GONE);
                                        paramViewHolder.tvLabelValue.setText(param.getLabelValue());
                                        ((EspDeviceActivity) context).startUpdateValueTask();
                                    }
                                });
                            }

                            @Override
                            public void onNetworkFailure(Exception exception) {

                                context.runOnUiThread(new Runnable() {

                                    @Override
                                    public void run() {
                                        paramViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                        paramViewHolder.progressBar.setVisibility(View.GONE);
                                        paramViewHolder.tvLabelValue.setText(param.getLabelValue());
                                        ((EspDeviceActivity) context).startUpdateValueTask();
                                    }
                                });
                            }
                        });
                    }
                }
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void startTimeSeriesActivity(Param param, String tsPropertyType) {
        Intent intent = new Intent(context, TimeSeriesActivity.class);
        intent.putExtra(AppConstants.KEY_NODE_ID, nodeId);
        intent.putExtra(AppConstants.KEY_DEVICE_NAME, deviceName);
        intent.putExtra(AppConstants.KEY_PARAM, param);
        intent.putExtra(AppConstants.KEY_PROPERTY_TS_TYPE, tsPropertyType);
        context.startActivity(intent);
    }

    static class ParamViewHolder extends RecyclerView.ViewHolder {

        ImageView ivSliderStart, ivSliderEnd;
        TickSeekBar intSlider, floatSlider;
        SwitchCompat toggleSwitch;
        TextView tvSliderName, tvSwitchName, tvSwitchStatus, tvLabelName, tvLabelValue, tvLabelPalette,
                tvSpinnerName, tvSpinnerValue, tvTriggerName;
        RelativeLayout rlUiTypeSlider, rlUiTypeSwitch, rlUiTypeLabel, rlPalette, rlUiTypeDropDown, rlUiTypeTrigger;
        TextView btnEdit;
        ContentLoadingProgressBar progressBar, progressBarSpinner;
        PaletteBar paletteBar;
        EspDropDown spinner;
        TapHoldUpButton btnTrigger;
        TextView tvMinHue, tvMaxHue;
        ImageView ivTsArrow;

        public ParamViewHolder(View itemView) {
            super(itemView);

            ivSliderStart = itemView.findViewById(R.id.iv_slider_start);
            ivSliderEnd = itemView.findViewById(R.id.iv_slider_end);
            intSlider = itemView.findViewById(R.id.card_int_slider);
            floatSlider = itemView.findViewById(R.id.card_float_slider);
            toggleSwitch = itemView.findViewById(R.id.card_switch);
            tvSliderName = itemView.findViewById(R.id.slider_name);
            tvSwitchName = itemView.findViewById(R.id.switch_name);
            tvSwitchStatus = itemView.findViewById(R.id.tv_switch_status);
            tvLabelName = itemView.findViewById(R.id.tv_label_name);
            tvLabelValue = itemView.findViewById(R.id.tv_label_value);
            tvLabelPalette = itemView.findViewById(R.id.palette_name);
            tvSpinnerName = itemView.findViewById(R.id.tv_spinner_name);
            tvSpinnerValue = itemView.findViewById(R.id.tv_spinner_value);
            tvTriggerName = itemView.findViewById(R.id.tv_trigger_name);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            progressBar = itemView.findViewById(R.id.progress_indicator);
            progressBarSpinner = itemView.findViewById(R.id.progress_indicator_spinner);
            rlUiTypeSlider = itemView.findViewById(R.id.rl_card_slider);
            rlUiTypeSwitch = itemView.findViewById(R.id.rl_card_switch);
            rlUiTypeLabel = itemView.findViewById(R.id.rl_card_label);
            rlPalette = itemView.findViewById(R.id.rl_card_palette);
            rlUiTypeDropDown = itemView.findViewById(R.id.rl_card_drop_down);
            rlUiTypeTrigger = itemView.findViewById(R.id.rl_card_trigger);
            paletteBar = itemView.findViewById(R.id.rl_palette);
            spinner = itemView.findViewById(R.id.card_spinner);
            btnTrigger = itemView.findViewById(R.id.btn_trigger);
            tvMinHue = itemView.findViewById(R.id.tv_palette_start);
            tvMaxHue = itemView.findViewById(R.id.tv_palette_end);
            ivTsArrow = itemView.findViewById(R.id.iv_ts_arrow);
        }
    }

    static class HueViewHolder extends RecyclerView.ViewHolder {

        ColorPicker colorPickerView;

        public HueViewHolder(View itemView) {
            super(itemView);
            colorPickerView = itemView.findViewById(R.id.hue_color_picker);
        }
    }

    static class SwitchViewHolder extends RecyclerView.ViewHolder {

        ImageView ivSwitch;

        public SwitchViewHolder(View itemView) {
            super(itemView);
            ivSwitch = itemView.findViewById(R.id.iv_switch);
        }
    }
}
