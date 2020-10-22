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

import com.espressif.ui.models.Action;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.Schedule;
import com.espressif.ui.models.Service;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

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

        if (AppConstants.UI_TYPE_SLIDER.equalsIgnoreCase(param.getUiType())) {

            String labelValue = "";

            if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                int value = deviceJson.optInt(paramName);
                labelValue = String.valueOf(value);
                param.setLabelValue(labelValue);
                param.setSliderValue(value);

            } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                double value = deviceJson.optDouble(paramName);
                labelValue = String.valueOf(value);
                param.setLabelValue(labelValue);
                param.setSliderValue(value);

            } else {

                labelValue = deviceJson.optString(paramName);
                param.setLabelValue(labelValue);
            }
        } else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(param.getUiType())) {

            boolean value = deviceJson.optBoolean(paramName);
            param.setSwitchStatus(value);

        } else {

            String labelValue = "";

            if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {

                boolean value = deviceJson.optBoolean(paramName);
                if (value) {
                    param.setLabelValue("true");
                } else {
                    param.setLabelValue("false");
                }

            } else if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                int value = deviceJson.optInt(paramName);
                labelValue = String.valueOf(value);
                param.setLabelValue(labelValue);

            } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                double value = deviceJson.optDouble(paramName);
                labelValue = String.valueOf(value);
                param.setLabelValue(labelValue);

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
        String nodeId = nodeConfigJson.optString("node_id");

        if (TextUtils.isEmpty(nodeId)) {
            return null;
        }

        if (espNode == null) {
            espNode = new EspNode(nodeId);
        }

        // Node Config
        espNode.setConfigVersion(nodeConfigJson.optString("config_version"));

        JSONObject infoObj = nodeConfigJson.optJSONObject("info");

        if (infoObj != null) {
            espNode.setNodeName(infoObj.optString("name"));
            espNode.setFwVersion(infoObj.optString("fw_version"));
            espNode.setNodeType(infoObj.optString("type"));
        } else {
            Log.d(TAG, "Info object is null");
        }

        // Devices
        JSONArray devicesJsonArray = nodeConfigJson.optJSONArray("devices");
        ArrayList<Device> devices = new ArrayList<>();

        if (devicesJsonArray != null) {

            for (int i = 0; i < devicesJsonArray.length(); i++) {

                JSONObject deviceObj = devicesJsonArray.optJSONObject(i);
                Device device = new Device(nodeId);
                device.setDeviceName(deviceObj.optString("name"));
                device.setDeviceType(deviceObj.optString("type"));
                device.setPrimaryParamName(deviceObj.optString("primary"));

                JSONArray paramsJson = deviceObj.optJSONArray("params");
                ArrayList<Param> params = new ArrayList<>();

                if (paramsJson != null) {

                    for (int j = 0; j < paramsJson.length(); j++) {

                        JSONObject paraObj = paramsJson.optJSONObject(j);
                        Param param = new Param();
                        param.setName(paraObj.optString("name"));
                        param.setParamType(paraObj.optString("type"));
                        param.setDataType(paraObj.optString("data_type"));
                        param.setUiType(paraObj.optString("ui_type"));
                        param.setDynamicParam(true);
                        params.add(param);

                        JSONArray propertiesJson = paraObj.optJSONArray("properties");
                        ArrayList<String> properties = new ArrayList<>();

                        if (propertiesJson != null) {
                            for (int k = 0; k < propertiesJson.length(); k++) {

                                properties.add(propertiesJson.optString(k));
                            }
                        }
                        param.setProperties(properties);

                        JSONObject boundsJson = paraObj.optJSONObject("bounds");

                        if (boundsJson != null) {
                            param.setMaxBounds(boundsJson.optInt("max"));
                            param.setMinBounds(boundsJson.optInt("min"));
                        }
                    }
                }

                JSONArray attributesJson = deviceObj.optJSONArray("attributes");

                if (attributesJson != null) {

                    for (int j = 0; j < attributesJson.length(); j++) {

                        JSONObject attrObj = attributesJson.optJSONObject(j);
                        Param param = new Param();
                        param.setName(attrObj.optString("name"));
                        param.setDataType(attrObj.optString("data_type"));
                        param.setLabelValue(attrObj.optString("value"));
                        params.add(param);
                    }
                }

                device.setParams(params);
                devices.add(device);
            }
        }
        espNode.setDevices(devices);

        // Services
        JSONArray servicesJsonArray = nodeConfigJson.optJSONArray("services");
        ArrayList<Service> services = new ArrayList<>();

        if (servicesJsonArray != null) {

            for (int i = 0; i < servicesJsonArray.length(); i++) {

                JSONObject serviceObj = servicesJsonArray.optJSONObject(i);
                Service service = new Service(nodeId);
                service.setName(serviceObj.optString("name"));
                service.setType(serviceObj.optString("type"));

                JSONArray paramsJson = serviceObj.optJSONArray("params");
                ArrayList<Param> params = new ArrayList<>();

                if (paramsJson != null) {

                    for (int j = 0; j < paramsJson.length(); j++) {

                        JSONObject paraObj = paramsJson.optJSONObject(j);
                        Param param = new Param();
                        param.setName(paraObj.optString("name"));
                        param.setParamType(paraObj.optString("type"));
                        param.setDataType(paraObj.optString("data_type"));
                        param.setDynamicParam(true);
                        params.add(param);

                        JSONArray propertiesJson = paraObj.optJSONArray("properties");
                        ArrayList<String> properties = new ArrayList<>();

                        if (propertiesJson != null) {
                            for (int k = 0; k < propertiesJson.length(); k++) {

                                properties.add(propertiesJson.optString(k));
                            }
                        }
                        param.setProperties(properties);
                    }
                }
                service.setParams(params);
                services.add(service);
            }
        }
        espNode.setServices(services);

        // Attributes
        JSONArray nodeAttributesJson = infoObj.optJSONArray("attributes");
        ArrayList<Param> nodeAttributes = new ArrayList<>();

        if (nodeAttributesJson != null) {

            for (int j = 0; j < nodeAttributesJson.length(); j++) {

                JSONObject attrObj = nodeAttributesJson.optJSONObject(j);
                Param param = new Param();
                param.setName(attrObj.optString("name"));
                param.setLabelValue(attrObj.optString("value"));
                nodeAttributes.add(param);
            }
        }
        espNode.setAttributes(nodeAttributes);

        // Node Params values
        JSONObject paramsJson = nodeConfigJson.optJSONObject("params");
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
        JSONObject statusJson = nodeConfigJson.optJSONObject("status");
        if (statusJson != null) {

            JSONObject connectivityObject = statusJson.optJSONObject("connectivity");

            if (connectivityObject != null) {

                boolean nodeStatus = connectivityObject.optBoolean("connected");
                long timestamp = connectivityObject.optLong("timestamp");
                espNode.setTimeStampOfStatus(timestamp);

                if (espNode.isOnline() != nodeStatus) {
                    espNode.setOnline(nodeStatus);
                }
            } else {
                Log.e(TAG, "Connectivity object is null");
            }
        }
        return espNode;
    }

    public static void setAllParams(EspApplication espAppContext, EspNode node, JSONObject paramsJson) {

        ArrayList<Device> devices = node.getDevices();
        JSONObject scheduleJson = paramsJson.optJSONObject("Schedule");
        String nodeId = node.getNodeId();

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

        // Schedules
        if (scheduleJson != null) {

            JSONArray scheduleArrayJson = scheduleJson.optJSONArray("Schedules");

            if (scheduleArrayJson != null) {

                for (int index = 0; index < scheduleArrayJson.length(); index++) {

                    JSONObject schJson = null;
                    try {
                        schJson = scheduleArrayJson.getJSONObject(index);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    String scheduleId = schJson.optString("id");
                    String key = scheduleId;

                    if (!TextUtils.isEmpty(scheduleId)) {

                        String name = schJson.optString("name");
                        key = key + "_" + name + "_" + schJson.optBoolean("enabled");

                        HashMap<String, Integer> triggers = new HashMap<>();
                        JSONArray triggerArray = schJson.optJSONArray("triggers");
                        for (int t = 0; t < triggerArray.length(); t++) {
                            JSONObject triggerJson = triggerArray.optJSONObject(t);
                            int days = triggerJson.optInt("d");
                            int mins = triggerJson.optInt("m");
                            triggers.put("d", days);
                            triggers.put("m", mins);
                            key = key + "_" + days + "_" + mins;
                        }

                        Schedule schedule = espAppContext.scheduleMap.get(key);
                        if (schedule == null) {
                            schedule = new Schedule();
                        }

                        schedule.setId(scheduleId);
                        schedule.setName(schJson.optString("name"));
                        schedule.setEnabled(schJson.optBoolean("enabled"));
                        schedule.setTriggers(triggers);
                        Log.d(TAG, "=============== Schedule : " + schedule.getName() + " ===============");

                        // Actions
                        JSONObject actionsSchJson = schJson.optJSONObject("action");

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
                                                actionDevice.setSelectedState(1);
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

                                        Iterator<Param> iterator = params.iterator();
                                        while (iterator.hasNext()) {
                                            Param p = iterator.next();
                                            actionParams.add(new Param(p));
                                        }

                                        Iterator itr = actionParams.iterator();

                                        while (itr.hasNext()) {

                                            Param p = (Param) itr.next();

                                            if (!p.isDynamicParam()) {
                                                itr.remove();
                                            } else if (p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                                                itr.remove();
                                            } else if (!p.getProperties().contains("write")) {
                                                itr.remove();
                                            }
                                        }
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
                                            actionDevice.setSelectedState(2); // Partially selected
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
        } else {
            Log.e(TAG, "Schedule JSON is null");
        }
    }
}
