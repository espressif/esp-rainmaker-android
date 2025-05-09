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

package com.espressif;

import android.text.TextUtils;
import android.util.Log;

import com.espressif.AppConstants.Companion.SystemMode;
import com.espressif.ui.models.Action;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.Scene;
import com.espressif.ui.models.Schedule;
import com.espressif.ui.models.Service;
import com.espressif.utils.ParamUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class JsonDataParser {

    private static final String TAG = JsonDataParser.class.getSimpleName();

    /**
     * This method is used to set param value received from cloud.
     *
     * @param deviceJson JSON data of device params.
     * @param device     Device object.
     * @param param      Param object in which values to be set.
     */
    public static void setDeviceParamValue(JSONObject deviceJson, Device device, Param param) {

        String dataType = param.getDataType();
        String paramName = param.getName();

        if (AppConstants.UI_TYPE_SLIDER.equalsIgnoreCase(param.getUiType()) || AppConstants.UI_TYPE_HUE_SLIDER.equalsIgnoreCase(param.getUiType())) {

            String labelValue = "";

            if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                int value = deviceJson.optInt(paramName);
                labelValue = String.valueOf(value);
                param.setLabelValue(labelValue);
                param.setValue(value);

            } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                double value = deviceJson.optDouble(paramName);
                labelValue = String.valueOf(value);
                param.setLabelValue(labelValue);
                param.setValue(value);

            } else {

                labelValue = deviceJson.optString(paramName);
                param.setLabelValue(labelValue);
            }
        } else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(param.getUiType())
                || AppConstants.UI_TYPE_TRIGGER.equalsIgnoreCase(param.getUiType())) {

            if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {
                boolean value = deviceJson.optBoolean(paramName);
                param.setSwitchStatus(value);
                if (value) {
                    param.setLabelValue("true");
                } else {
                    param.setLabelValue("false");
                }
            } else {
                param.setLabelValue(deviceJson.optString(paramName));
            }

        } else if (AppConstants.UI_TYPE_DROP_DOWN.equalsIgnoreCase(param.getUiType())) {

            String labelValue = "";

            if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {
                int value = deviceJson.optInt(paramName);
                labelValue = String.valueOf(value);
                param.setLabelValue(labelValue);
                param.setValue(value);
            } else {
                if (AppConstants.ESP_DEVICE_AIR_CONDITIONER.equals(device.getDeviceType())) {
                    labelValue = deviceJson.optString(paramName);
                    param.setLabelValue(labelValue);
                    if (SystemMode.OFF.getModeName().equals(labelValue)) {
                        param.setValue(SystemMode.OFF.getModeValue());
                    } else if (SystemMode.COOL.getModeName().equals(labelValue)) {
                        param.setValue(SystemMode.COOL.getModeValue());
                    } else if (SystemMode.HEAT.getModeName().equals(labelValue)) {
                        param.setValue(SystemMode.HEAT.getModeValue());
                    }
                } else {
                    labelValue = deviceJson.optString(paramName);
                    param.setLabelValue(labelValue);
                }
            }
        } else {

            String labelValue = "";

            if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {

                boolean value = deviceJson.optBoolean(paramName);
                param.setSwitchStatus(value);
                if (value) {
                    param.setLabelValue("true");
                } else {
                    param.setLabelValue("false");
                }

            } else if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                int value = deviceJson.optInt(paramName);
                labelValue = String.valueOf(value);
                param.setLabelValue(labelValue);
                param.setValue(value);

            } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                double value = deviceJson.optDouble(paramName);
                labelValue = String.valueOf(value);
                param.setLabelValue(labelValue);
                param.setValue(value);

            } else {

                labelValue = deviceJson.optString(paramName);
                param.setLabelValue(labelValue);

                if (param.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                    device.setUserVisibleName(labelValue);
                }
            }
        }
    }

    public static EspNode setNodeConfig(EspNode espNode, JSONObject nodeConfigJson) {

        // Node ID
        String nodeId = nodeConfigJson.optString(AppConstants.KEY_NODE_ID);

        if (espNode == null) {
            espNode = new EspNode(nodeId);
        }

        if (TextUtils.isEmpty(nodeId)) {
            nodeId = espNode.getNodeId();
        }

        // Node Config
        espNode.setConfigVersion(nodeConfigJson.optString(AppConstants.KEY_CONFIG_VERSION));

        JSONObject infoObj = nodeConfigJson.optJSONObject(AppConstants.KEY_INFO);

        if (infoObj != null) {
            espNode.setNodeName(infoObj.optString(AppConstants.KEY_NAME));
            espNode.setFwVersion(infoObj.optString(AppConstants.KEY_FW_VERSION));
            espNode.setNodeType(infoObj.optString(AppConstants.KEY_TYPE));
        } else {
            Log.d(TAG, "Info object is null");
        }

        // Devices
        JSONArray devicesJsonArray = nodeConfigJson.optJSONArray(AppConstants.KEY_DEVICES);
        ArrayList<Device> devices = new ArrayList<>();

        if (devicesJsonArray != null) {

            for (int i = 0; i < devicesJsonArray.length(); i++) {

                JSONObject deviceObj = devicesJsonArray.optJSONObject(i);
                Device device = new Device(nodeId);
                device.setDeviceName(deviceObj.optString(AppConstants.KEY_NAME));
                device.setUserVisibleName(deviceObj.optString(AppConstants.KEY_NAME));
                device.setDeviceType(deviceObj.optString(AppConstants.KEY_TYPE));
                device.setPrimaryParamName(deviceObj.optString(AppConstants.KEY_PRIMARY));

                JSONArray paramsJson = deviceObj.optJSONArray(AppConstants.KEY_PARAMS);
                ArrayList<Param> params = new ArrayList<>();

                if (paramsJson != null) {

                    for (int j = 0; j < paramsJson.length(); j++) {

                        JSONObject paraObj = paramsJson.optJSONObject(j);
                        Param param = new Param();
                        param.setName(paraObj.optString(AppConstants.KEY_NAME));
                        param.setParamType(paraObj.optString(AppConstants.KEY_TYPE));
                        param.setDataType(paraObj.optString(AppConstants.KEY_DATA_TYPE));
                        param.setUiType(paraObj.optString(AppConstants.KEY_UI_TYPE));
                        param.setDynamicParam(true);
                        JSONObject dependenciesJson = paraObj.optJSONObject(AppConstants.KEY_DEPENDENCIES);
                        if (dependenciesJson != null) {
                            param.setDependencies(dependenciesJson.toString());
                        }
                        params.add(param);

                        JSONArray propertiesJson = paraObj.optJSONArray(AppConstants.KEY_PROPERTIES);
                        ArrayList<String> properties = new ArrayList<>();

                        if (propertiesJson != null) {
                            for (int k = 0; k < propertiesJson.length(); k++) {

                                properties.add(propertiesJson.optString(k));
                            }
                        }
                        param.setProperties(properties);

                        JSONObject boundsJson = paraObj.optJSONObject(AppConstants.KEY_BOUNDS);

                        if (boundsJson != null) {
                            param.setMaxBounds(boundsJson.optInt(AppConstants.KEY_MAX));
                            param.setMinBounds(boundsJson.optInt(AppConstants.KEY_MIN));
                            param.setStepCount((float) boundsJson.optDouble(AppConstants.KEY_STEP, 0));
                        }

                        JSONArray validValuesJson = paraObj.optJSONArray(AppConstants.KEY_VALID_STRS);
                        ArrayList<String> validVals = new ArrayList<>();

                        if (validValuesJson != null) {
                            for (int k = 0; k < validValuesJson.length(); k++) {
                                validVals.add(validValuesJson.optString(k));
                            }
                        }
                        param.setValidStrings(validVals);
                    }
                }

                JSONArray attributesJson = deviceObj.optJSONArray(AppConstants.KEY_ATTRIBUTES);

                if (attributesJson != null) {

                    for (int j = 0; j < attributesJson.length(); j++) {

                        JSONObject attrObj = attributesJson.optJSONObject(j);
                        Param param = new Param();
                        param.setName(attrObj.optString(AppConstants.KEY_NAME));
                        param.setDataType(attrObj.optString(AppConstants.KEY_DATA_TYPE));
                        param.setLabelValue(attrObj.optString(AppConstants.KEY_VALUE));
                        params.add(param);
                    }
                }

                device.setParams(params);
                devices.add(device);
            }
        }
        espNode.setDevices(devices);

        // Services
        JSONArray servicesJsonArray = nodeConfigJson.optJSONArray(AppConstants.KEY_SERVICES);
        ArrayList<Service> services = new ArrayList<>();

        if (servicesJsonArray != null) {

            for (int i = 0; i < servicesJsonArray.length(); i++) {

                JSONObject serviceObj = servicesJsonArray.optJSONObject(i);
                Service service = new Service(nodeId);
                service.setName(serviceObj.optString(AppConstants.KEY_NAME));
                service.setType(serviceObj.optString(AppConstants.KEY_TYPE));

                JSONArray paramsJson = serviceObj.optJSONArray(AppConstants.KEY_PARAMS);
                ArrayList<Param> params = new ArrayList<>();

                if (paramsJson != null) {

                    for (int j = 0; j < paramsJson.length(); j++) {

                        JSONObject paraObj = paramsJson.optJSONObject(j);
                        Param param = new Param();
                        param.setName(paraObj.optString(AppConstants.KEY_NAME));
                        param.setParamType(paraObj.optString(AppConstants.KEY_TYPE));
                        param.setDataType(paraObj.optString(AppConstants.KEY_DATA_TYPE));
                        param.setDynamicParam(true);
                        params.add(param);

                        JSONArray propertiesJson = paraObj.optJSONArray(AppConstants.KEY_PROPERTIES);
                        ArrayList<String> properties = new ArrayList<>();

                        if (propertiesJson != null) {
                            for (int k = 0; k < propertiesJson.length(); k++) {

                                properties.add(propertiesJson.optString(k));
                            }
                        }
                        param.setProperties(properties);

                        JSONObject boundsJson = paraObj.optJSONObject(AppConstants.KEY_BOUNDS);
                        if (boundsJson != null && AppConstants.SERVICE_TYPE_SCHEDULE.equals(service.getType())) {
                            espNode.setScheduleMaxCnt(boundsJson.optInt(AppConstants.KEY_MAX));
                        } else if (boundsJson != null && AppConstants.SERVICE_TYPE_SCENES.equals(service.getType())) {
                            espNode.setSceneMaxCnt(boundsJson.optInt(AppConstants.KEY_MAX));
                        }
                    }
                }
                service.setParams(params);
                services.add(service);
            }
        }
        espNode.setServices(services);

        // Attributes
        ArrayList<Param> nodeAttributes = new ArrayList<>();
        JSONArray nodeAttributesJson = (infoObj != null) ? infoObj.optJSONArray(AppConstants.KEY_ATTRIBUTES) : null;

        if (nodeAttributesJson != null) {

            for (int j = 0; j < nodeAttributesJson.length(); j++) {

                JSONObject attrObj = nodeAttributesJson.optJSONObject(j);
                Param param = new Param();
                param.setName(attrObj.optString(AppConstants.KEY_NAME));
                param.setLabelValue(attrObj.optString(AppConstants.KEY_VALUE));
                nodeAttributes.add(param);
            }
        }
        espNode.setAttributes(nodeAttributes);

        // Node Params values
        JSONObject paramsJson = nodeConfigJson.optJSONObject(AppConstants.KEY_PARAMS);
        if (paramsJson != null) {

            for (int i = 0; i < devices.size(); i++) {

                ArrayList<Param> params = devices.get(i).getParams();
                String deviceName = devices.get(i).getDeviceName();
                JSONObject deviceJson = paramsJson.optJSONObject(deviceName);

                if (deviceJson != null) {

                    for (int j = 0; j < params.size(); j++) {

                        Param param = params.get(j);
                        String key = param.getName();

                        if (!param.isDynamicParam()) {
                            continue;
                        }

                        if (deviceJson.has(key)) {
                            setDeviceParamValue(deviceJson, devices.get(i), param);
                        }
                    }
                } else {
                    Log.e(TAG, "Device JSON is null");
                }
            }
        }

        // Node Status
        JSONObject statusJson = nodeConfigJson.optJSONObject(AppConstants.KEY_STATUS);
        JSONObject connectivityObject = (statusJson != null) ? statusJson.optJSONObject(AppConstants.KEY_CONNECTIVITY) : null;

        if (connectivityObject != null) {

            boolean nodeStatus = connectivityObject.optBoolean(AppConstants.KEY_CONNECTED);
            long timestamp = connectivityObject.optLong(AppConstants.KEY_TIMESTAMP);
            espNode.setTimeStampOfStatus(timestamp);

            if (espNode.isOnline() != nodeStatus) {
                espNode.setOnline(nodeStatus);
            }
        }
        return espNode;
    }

    public static void setAllParams(EspApplication espAppContext, EspNode node, JSONObject paramsJson) {

        String nodeId = node.getNodeId();
        ArrayList<Device> devices = node.getDevices();
        ArrayList<Service> services = node.getServices();
        JSONObject scheduleJson = paramsJson.optJSONObject(AppConstants.KEY_SCHEDULE);
        JSONObject sceneJson = paramsJson.optJSONObject(AppConstants.KEY_SCENES);
        JSONObject timeJson = paramsJson.optJSONObject(AppConstants.KEY_TIME);
        JSONObject localControlJson = paramsJson.optJSONObject(AppConstants.KEY_LOCAL_CONTROL);
        JSONObject systemServiceJson = paramsJson.optJSONObject(AppConstants.KEY_SYSTEM);
        JSONObject controllerServiceJson = paramsJson.optJSONObject(AppConstants.KEY_MATTER_CONTROLLER);
        JSONObject ctlServiceJson = paramsJson.optJSONObject(AppConstants.KEY_MATTER_CTL);
        JSONObject tbrServiceJson = paramsJson.optJSONObject(AppConstants.KEY_TBR_SERVICE);
        int scheduleCnt = 0, sceneCnt = 0;

        if (devices != null) {
            for (int i = 0; i < devices.size(); i++) {

                ArrayList<Param> params = devices.get(i).getParams();
                String deviceName = devices.get(i).getDeviceName();
                JSONObject deviceJson = paramsJson.optJSONObject(deviceName);

                if (deviceJson != null) {

                    for (int j = 0; j < params.size(); j++) {

                        Param param = params.get(j);
                        String key = param.getName();

                        if (!param.isDynamicParam()) {
                            continue;
                        }

                        if (deviceJson.has(key)) {
                            setDeviceParamValue(deviceJson, devices.get(i), param);
                        }
                    }
                } else {
                    Log.e(TAG, "Device JSON is not available");
                }
            }
        }

        // Schedules
        JSONArray scheduleArrayJson = (scheduleJson != null) ? scheduleJson.optJSONArray(AppConstants.KEY_SCHEDULES) : null;

        if (scheduleArrayJson != null) {

            for (int index = 0; index < scheduleArrayJson.length(); index++) {

                JSONObject schJson = null;
                try {
                    schJson = scheduleArrayJson.getJSONObject(index);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                String scheduleId = schJson.optString(AppConstants.KEY_ID);
                String key = scheduleId;

                if (!TextUtils.isEmpty(scheduleId)) {

                    String name = schJson.optString(AppConstants.KEY_NAME);
                    key = key + "_" + name + "_" + schJson.optBoolean(AppConstants.KEY_ENABLED);

                    HashMap<String, Integer> triggers = new HashMap<>();
                    JSONArray triggerArray = schJson.optJSONArray(AppConstants.KEY_TRIGGERS);
                    for (int t = 0; t < triggerArray.length(); t++) {
                        JSONObject triggerJson = triggerArray.optJSONObject(t);
                        int days = triggerJson.optInt(AppConstants.KEY_DAYS);
                        int mins = triggerJson.optInt(AppConstants.KEY_MINUTES);
                        triggers.put(AppConstants.KEY_DAYS, days);
                        triggers.put(AppConstants.KEY_MINUTES, mins);
                        key = key + "_" + days + "_" + mins;
                    }

                    Schedule schedule = espAppContext.scheduleMap.get(key);
                    if (schedule == null) {
                        schedule = new Schedule();
                    }
                    scheduleCnt++;

                    schedule.setId(scheduleId);
                    schedule.setName(schJson.optString(AppConstants.KEY_NAME));
                    schedule.setEnabled(schJson.optBoolean(AppConstants.KEY_ENABLED));
                    schedule.setTriggers(triggers);
                    Log.d(TAG, "=============== Schedule : " + schedule.getName() + " ===============");

                    // Actions
                    JSONObject actionsSchJson = schJson.optJSONObject(AppConstants.KEY_ACTION);

                    if (actionsSchJson != null) {

                        ArrayList<Action> actions = schedule.getActions();
                        if (actions == null) {
                            actions = new ArrayList<>();
                            schedule.setActions(actions);
                        }

                        for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {

                            Device d = new Device(devices.get(deviceIndex));
                            ArrayList<Param> params = d.getParams();
                            String deviceName = d.getDeviceName();
                            JSONObject deviceAction = actionsSchJson.optJSONObject(deviceName);

                            if (deviceAction != null) {

                                Action action = null;
                                Device actionDevice = null;
                                int actionIndex = -1;

                                for (int aIndex = 0; aIndex < actions.size(); aIndex++) {

                                    Action a = actions.get(aIndex);
                                    if (a.getDevice().getNodeId().equals(nodeId) && deviceName.equals(a.getDevice().getDeviceName())) {
                                        action = actions.get(aIndex);
                                        actionIndex = aIndex;
                                    }
                                }

                                if (action == null) {
                                    action = new Action();
                                    action.setNodeId(nodeId);

                                    for (int k = 0; k < devices.size(); k++) {

                                        if (devices.get(k).getNodeId().equals(nodeId) && devices.get(k).getDeviceName().equals(deviceName)) {
                                            actionDevice = new Device(devices.get(k));
                                            actionDevice.setSelectedState(AppConstants.ACTION_SELECTED_ALL);
                                            break;
                                        }
                                    }

                                    if (actionDevice == null) {
                                        actionDevice = new Device(nodeId);
                                    }
                                    action.setDevice(actionDevice);
                                } else {
                                    actionDevice = action.getDevice();
                                }

                                ArrayList<Param> actionParams = new ArrayList<>();
                                if (params != null) {
                                    actionParams = ParamUtils.Companion.filterActionParams(params);
                                }
                                actionDevice.setParams(actionParams);

                                for (int paramIndex = 0; paramIndex < actionParams.size(); paramIndex++) {

                                    Param p = actionParams.get(paramIndex);
                                    String paramName = p.getName();

                                    if (deviceAction.has(paramName)) {

                                        p.setSelected(true);
                                        setDeviceParamValue(deviceAction, devices.get(deviceIndex), p);
                                    }
                                }

                                for (int paramIndex = 0; paramIndex < actionParams.size(); paramIndex++) {

                                    if (!actionParams.get(paramIndex).isSelected()) {
                                        actionDevice.setSelectedState(AppConstants.ACTION_SELECTED_PARTIAL);
                                    }
                                }

                                if (actionIndex == -1) {
                                    actions.add(action);
                                } else {
                                    actions.set(actionIndex, action);
                                }
                                schedule.setActions(actions);
                            }
                        }
                    }
                    espAppContext.scheduleMap.put(key, schedule);
                }
            }
        }
        node.setScheduleCurrentCnt(scheduleCnt);

        // Scenes
        JSONArray sceneArrayJson = (sceneJson != null) ? sceneJson.optJSONArray(AppConstants.KEY_SCENES) : null;

        if (sceneArrayJson != null) {

            for (int index = 0; index < sceneArrayJson.length(); index++) {

                JSONObject scJson = null;
                try {
                    scJson = sceneArrayJson.getJSONObject(index);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                String sceneId = scJson.optString(AppConstants.KEY_ID);
                String key = sceneId;

                if (!TextUtils.isEmpty(sceneId)) {

                    String name = scJson.optString(AppConstants.KEY_NAME);
                    String info = scJson.optString(AppConstants.KEY_INFO);
                    key = key + "_" + name + "_" + info;

                    Scene scene = espAppContext.sceneMap.get(key);
                    if (scene == null) {
                        scene = new Scene();
                    }
                    sceneCnt++;
                    scene.setId(sceneId);
                    scene.setName(name);
                    scene.setInfo(info);

                    Log.d(TAG, "=============== Scene : " + scene.getName() + " ===============");

                    // Actions
                    JSONObject actionsSceneJson = scJson.optJSONObject(AppConstants.KEY_ACTION);

                    if (actionsSceneJson != null) {

                        ArrayList<Action> actions = scene.getActions();
                        if (actions == null) {
                            actions = new ArrayList<>();
                            scene.setActions(actions);
                        }

                        for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {

                            Device d = new Device(devices.get(deviceIndex));
                            ArrayList<Param> params = d.getParams();
                            String deviceName = d.getDeviceName();
                            JSONObject deviceAction = actionsSceneJson.optJSONObject(deviceName);

                            if (deviceAction != null) {

                                Action action = null;
                                Device actionDevice = null;
                                int actionIndex = -1;

                                for (int aIndex = 0; aIndex < actions.size(); aIndex++) {

                                    Action a = actions.get(aIndex);
                                    if (a.getDevice().getNodeId().equals(nodeId) && deviceName.equals(a.getDevice().getDeviceName())) {
                                        action = actions.get(aIndex);
                                        actionIndex = aIndex;
                                    }
                                }

                                if (action == null) {
                                    action = new Action();
                                    action.setNodeId(nodeId);

                                    for (int k = 0; k < devices.size(); k++) {

                                        if (devices.get(k).getNodeId().equals(nodeId) && devices.get(k).getDeviceName().equals(deviceName)) {
                                            actionDevice = new Device(devices.get(k));
                                            actionDevice.setSelectedState(AppConstants.ACTION_SELECTED_ALL);
                                            break;
                                        }
                                    }

                                    if (actionDevice == null) {
                                        actionDevice = new Device(nodeId);
                                    }
                                    action.setDevice(actionDevice);
                                } else {
                                    actionDevice = action.getDevice();
                                }

                                ArrayList<Param> actionParams = new ArrayList<>();
                                if (params != null) {
                                    actionParams = ParamUtils.Companion.filterActionParams(params);
                                }
                                actionDevice.setParams(actionParams);

                                for (int paramIndex = 0; paramIndex < actionParams.size(); paramIndex++) {

                                    Param p = actionParams.get(paramIndex);
                                    String paramName = p.getName();

                                    if (deviceAction.has(paramName)) {

                                        p.setSelected(true);
                                        setDeviceParamValue(deviceAction, devices.get(deviceIndex), p);
                                    }
                                }

                                for (int paramIndex = 0; paramIndex < actionParams.size(); paramIndex++) {

                                    if (!actionParams.get(paramIndex).isSelected()) {
                                        actionDevice.setSelectedState(AppConstants.ACTION_SELECTED_PARTIAL);
                                    }
                                }

                                if (actionIndex == -1) {
                                    actions.add(action);
                                } else {
                                    actions.set(actionIndex, action);
                                }
                                scene.setActions(actions);
                            }
                        }
                    }
                    espAppContext.sceneMap.put(key, scene);
                }
            }
        }
        node.setSceneCurrentCnt(sceneCnt);

        if (services != null) {

            for (int serviceIdx = 0; serviceIdx < services.size(); serviceIdx++) {
                Service service = services.get(serviceIdx);

                if (AppConstants.SERVICE_TYPE_TIME.equals(service.getType()) && timeJson != null) {
                    // Timezone service
                    ArrayList<Param> timeParams = service.getParams();
                    if (timeParams != null) {
                        for (int paramIdx = 0; paramIdx < timeParams.size(); paramIdx++) {
                            Param timeParam = timeParams.get(paramIdx);
                            String dataType = timeParam.getDataType();
                            if (!TextUtils.isEmpty(dataType)) {
                                if (dataType.equalsIgnoreCase("string")) {
                                    timeParam.setLabelValue(timeJson.optString(timeParam.getName()));
                                }
                            }
                        }
                    }
                } else if (AppConstants.SERVICE_TYPE_LOCAL_CONTROL.equals(service.getType()) && localControlJson != null) {
                    // Local control service
                    ArrayList<Param> localParams = service.getParams();
                    if (localParams != null) {
                        for (int paramIdx = 0; paramIdx < localParams.size(); paramIdx++) {
                            Param localParam = localParams.get(paramIdx);
                            String dataType = localParam.getDataType();
                            if (!TextUtils.isEmpty(dataType)) {
                                if (dataType.equalsIgnoreCase("string")) {
                                    localParam.setLabelValue(localControlJson.optString(localParam.getName()));
                                }
                                if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {
                                    localParam.setValue(localControlJson.optInt(localParam.getName()));
                                }
                            }
                        }
                    }
                } else if (AppConstants.SERVICE_TYPE_SYSTEM.equals(service.getType()) && systemServiceJson != null) {
                    // System service
                    ArrayList<Param> localParams = service.getParams();
                    if (localParams != null) {
                        for (int paramIdx = 0; paramIdx < localParams.size(); paramIdx++) {
                            Param localParam = localParams.get(paramIdx);
                            String dataType = localParam.getDataType();
                            if (!TextUtils.isEmpty(dataType)) {

                                if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {
                                    boolean value = systemServiceJson.optBoolean(localParam.getName());
                                    localParam.setSwitchStatus(value);
                                    if (value) {
                                        localParam.setLabelValue("true");
                                    } else {
                                        localParam.setLabelValue("false");
                                    }
                                }
                            }
                        }
                    }
                } else if ((AppConstants.SERVICE_TYPE_MATTER_CONTROLLER.equals(service.getType()) && controllerServiceJson != null)
                        || (AppConstants.SERVICE_TYPE_MATTER_CONTROLLER.equals(service.getType()) && ctlServiceJson != null)) {

                    // Matter controller service
                    ArrayList<Param> controllerParams = service.getParams();
                    String controllerDataVersion = "";

                    if (controllerParams != null) {

                        for (Param controllerParam : controllerParams) {

                            String type = controllerParam.getParamType();

                            if (!TextUtils.isEmpty(type) && AppConstants.PARAM_TYPE_MATTER_CTRL_DATA_VERSION.equals(type)) {
                                controllerDataVersion = controllerParam.getLabelValue();
                                if (!TextUtils.isEmpty(controllerServiceJson.optString(controllerParam.getName()))) {
                                    controllerDataVersion = controllerServiceJson.optString(controllerParam.getName());
                                    controllerParam.setLabelValue(controllerDataVersion);
                                }
                                break;
                            }
                        }

                        for (Param controllerParam : controllerParams) {

                            String type = controllerParam.getParamType();

                            if (!TextUtils.isEmpty(type) && AppConstants.PARAM_TYPE_MATTER_DEVICES.equals(type)) {

                                JSONObject matterDevicesJson = controllerServiceJson.optJSONObject(controllerParam.getName());
                                Iterator<String> keys = matterDevicesJson.keys();
                                HashMap<String, String> matterDevices = new HashMap<>();

                                while (keys.hasNext()) {
                                    String matterDeviceId = keys.next();
                                    JSONObject matterDeviceJson = matterDevicesJson.optJSONObject(matterDeviceId);

                                    if (matterDeviceId != null && matterDeviceJson != null) {
                                        String value = matterDeviceJson.toString();
                                        matterDevices.put(matterDeviceId, value);
                                    }
                                }

                                if (!matterDevices.isEmpty()) {
                                    espAppContext.controllerDevices.put(nodeId, matterDevices);
                                }
                                setRemoteDeviceParamValues(espAppContext, nodeId, node, controllerDataVersion);
                                break;

                            } else if (!TextUtils.isEmpty(type) &&
                                    (AppConstants.PARAM_TYPE_BASE_URL.equals(type) || AppConstants.PARAM_TYPE_USER_TOKEN.equals(type)
                                            || AppConstants.PARAM_TYPE_RMAKER_GROUP_ID.equals(type))) {
                                if (!TextUtils.isEmpty(ctlServiceJson.optString(controllerParam.getName()))) {
                                    String value = ctlServiceJson.optString(controllerParam.getName());
                                    controllerParam.setLabelValue(value);
                                }
                            }
                        }
                    }
                } else if (AppConstants.SERVICE_TYPE_TBR.equals(service.getType()) && tbrServiceJson != null) {

                    // TBR service
                    ArrayList<Param> tbrParams = service.getParams();
                    if (tbrParams != null) {

                        for (Param param : tbrParams) {

                            String type = param.getParamType();
                            boolean isSupportedType = (!TextUtils.isEmpty(type)) && (AppConstants.PARAM_TYPE_BORDER_AGENT_ID.equals(type)
                                    || AppConstants.PARAM_TYPE_ACTIVE_DATASET.equals(type)
                                    || AppConstants.PARAM_TYPE_PENDING_DATASET.equals(type));

                            if (isSupportedType) {
                                param.setLabelValue(tbrServiceJson.optString(param.getName()));
                            } else if (!TextUtils.isEmpty(type) && AppConstants.PARAM_TYPE_DEVICE_ROLE.equals(type)) {
                                int value = tbrServiceJson.optInt(param.getName());
                                String labelValue = String.valueOf(value);
                                param.setLabelValue(labelValue);
                                param.setValue(value);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void setRemoteDeviceParamValues(EspApplication espApp, String controllerNodeId, EspNode node, String controllerDataVersion) {

        if (espApp.controllerDevices.containsKey(controllerNodeId)) {

            HashMap<String, String> matterOnlyDevices = espApp.controllerDevices.get(controllerNodeId);
            boolean isControllerOnline = node.isOnline();

            for (Map.Entry<String, String> controllerDevice : matterOnlyDevices.entrySet()) {
                String matterDeviceId = controllerDevice.getKey();
                String jsonStr = controllerDevice.getValue();

                try {
                    JSONObject deviceJson = (jsonStr != null) ? new JSONObject(jsonStr) : null;

                    if (deviceJson != null) {

                        boolean enabled = deviceJson.optBoolean(AppConstants.KEY_ENABLED);
                        boolean reachable = deviceJson.optBoolean(AppConstants.KEY_REACHABLE);

                        if (espApp.matterRmNodeIdMap.containsValue(matterDeviceId)) {

                            for (Map.Entry<String, String> matterDevice : espApp.matterRmNodeIdMap.entrySet()) {

                                if (matterDeviceId.equals(matterDevice.getValue())) {

                                    String rmNodeId = matterDevice.getKey();
                                    if (espApp.nodeMap.containsKey(rmNodeId)) {

                                        EspNode remoteNode = espApp.nodeMap.get(rmNodeId);
                                        int nodeStatus = remoteNode.getNodeStatus();

                                        if (nodeStatus != AppConstants.NODE_STATUS_MATTER_LOCAL && nodeStatus != AppConstants.NODE_STATUS_LOCAL) {

                                            if (enabled && reachable && isControllerOnline) {
                                                Log.d(TAG, "Set Node status to remotely controllable for node id : " + rmNodeId);
                                                remoteNode.setNodeStatus(AppConstants.NODE_STATUS_REMOTELY_CONTROLLABLE);
                                            }
                                        }

                                        JSONObject endpointsJson = deviceJson.optJSONObject(ESPControllerAPIKeys.KEY_ENDPOINTS);

                                        if (endpointsJson != null) {

                                            if (!TextUtils.isEmpty(controllerDataVersion) && controllerDataVersion.equals(AppConstants.CONTROLLER_DATA_VERSION)) {

                                                Iterator<String> endpoints = endpointsJson.keys();

                                                while (endpoints.hasNext()) {

                                                    String endpoint = endpoints.next();
                                                    JSONObject endpointJson = endpointsJson.optJSONObject(endpoint);
                                                    JSONObject clusters = (endpointJson != null) ? endpointJson.optJSONObject(ESPControllerAPIKeys.KEY_CLUSTERS) : null;

                                                    if (clusters != null) {

                                                        JSONObject serversJson = clusters.optJSONObject(ESPControllerAPIKeys.KEY_SERVERS);
                                                        JSONObject clients = clusters.optJSONObject(ESPControllerAPIKeys.KEY_CLIENTS);

                                                        if (serversJson != null) {
                                                            Iterator<String> servers = serversJson.keys();

                                                            while (servers.hasNext()) {
                                                                String cluster = servers.next();
                                                                JSONObject clusterJson = serversJson.optJSONObject(cluster);
                                                                int clusterId = Integer.decode(cluster);
                                                                int endpointId = Integer.decode(endpoint);
                                                                ArrayList<Param> params = remoteNode.getDevices().get(0).getParams();

                                                                if (endpointId == ESPControllerAPIKeys.ENDPOINT_ID_1 && clusterJson != null) {

                                                                    JSONObject attributesJson = clusterJson.optJSONObject(AppConstants.KEY_ATTRIBUTES);

                                                                    if (attributesJson != null) {

                                                                        switch (clusterId) {
                                                                            case ESPControllerAPIKeys.CLUSTER_ID_ON_OFF:
                                                                                boolean isPowerOn = attributesJson.optBoolean(ESPControllerAPIKeys.ATTRIBUTE_ID_ON_OFF, false);
                                                                                Log.e(TAG, "CLUSTER_ID_ON_OFF, isPowerOn : " + isPowerOn);
                                                                                for (Param p : params) {
                                                                                    if (p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_POWER)) {
                                                                                        p.setSwitchStatus(isPowerOn);
                                                                                        break;
                                                                                    }
                                                                                }
                                                                                break;

                                                                            case ESPControllerAPIKeys.CLUSTER_ID_LEVEL_CONTROL:
                                                                                int brightness = attributesJson.optInt(ESPControllerAPIKeys.ATTRIBUTE_ID_BRIGHTNESS_LEVEL);
                                                                                for (Param p : params) {
                                                                                    if (p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_BRIGHTNESS)) {
                                                                                        p.setValue(brightness);
                                                                                        break;
                                                                                    }
                                                                                }
                                                                                break;

                                                                            case ESPControllerAPIKeys.CLUSTER_ID_COLOR_CONTROL:
                                                                                int hue = attributesJson.optInt(ESPControllerAPIKeys.ATTRIBUTE_ID_CURRENT_HUE);
                                                                                int saturation = attributesJson.optInt(ESPControllerAPIKeys.ATTRIBUTE_ID_CURRENT_SATURATION);
                                                                                for (Param p : params) {
                                                                                    if (p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_HUE)) {
                                                                                        p.setValue(Integer.valueOf(hue));
                                                                                    } else if (p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_SATURATION)) {
                                                                                        p.setValue(Integer.valueOf(saturation));
                                                                                    }
                                                                                }
                                                                                break;
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {

                                                JSONObject endpoint_1 = endpointsJson.optJSONObject(ESPControllerAPIKeys.ENDPOINT_ID_1_HEX);
                                                if (endpoint_1 != null) {

                                                    JSONObject clusters = endpoint_1.optJSONObject(ESPControllerAPIKeys.KEY_CLUSTERS);
                                                    if (clusters != null) {

                                                        ArrayList<Param> params = remoteNode.getDevices().get(0).getParams();

                                                        for (Param p : params) {

                                                            switch (p.getParamType()) {

                                                                case AppConstants.PARAM_TYPE_POWER:
                                                                    // On off cluster
                                                                    JSONObject onOffCluster = clusters.optJSONObject(ESPControllerAPIKeys.CLUSTER_ID_ON_OFF_HEX);

                                                                    if (onOffCluster != null) {
                                                                        String value = onOffCluster.optString(ESPControllerAPIKeys.ATTRIBUTE_ID_ON_OFF);
                                                                        boolean isPowerOn = false;
                                                                        if (!TextUtils.isEmpty(value) && value.equals("1")) {
                                                                            isPowerOn = true;
                                                                        }
                                                                        p.setSwitchStatus(isPowerOn);
                                                                    }
                                                                    break;

                                                                case AppConstants.PARAM_TYPE_BRIGHTNESS:
                                                                    // Level cluster
                                                                    JSONObject levelCluster = clusters.optJSONObject(ESPControllerAPIKeys.CLUSTER_ID_LEVEL_CONTROL_HEX);

                                                                    if (levelCluster != null) {
                                                                        String value = levelCluster.optString(ESPControllerAPIKeys.ATTRIBUTE_ID_BRIGHTNESS_LEVEL);
                                                                        if (!TextUtils.isEmpty(value)) {
                                                                            int brightness = Integer.valueOf(value);
                                                                            p.setValue(brightness);
                                                                        }
                                                                    }
                                                                    break;

                                                                case AppConstants.PARAM_TYPE_HUE:
                                                                case AppConstants.PARAM_TYPE_SATURATION:
                                                                    // Color cluster
                                                                    JSONObject colorCluster = clusters.optJSONObject(ESPControllerAPIKeys.CLUSTER_ID_COLOR_CONTROL_HEX);

                                                                    if (colorCluster != null) {
                                                                        String hueValue = colorCluster.optString(ESPControllerAPIKeys.ATTRIBUTE_ID_CURRENT_HUE);
                                                                        String saturationValue = colorCluster.optString(ESPControllerAPIKeys.ATTRIBUTE_ID_CURRENT_SATURATION);

                                                                        if (!TextUtils.isEmpty(hueValue)) {
                                                                            p.setValue(Integer.valueOf(hueValue));
                                                                        }

                                                                        if (!TextUtils.isEmpty(saturationValue)) {
                                                                            p.setValue(Integer.valueOf(saturationValue));
                                                                        }
                                                                    }
                                                                    break;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
