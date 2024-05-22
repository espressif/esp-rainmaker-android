// Copyright 2024 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.matter;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.espressif.AppConstants;
import com.espressif.ESPControllerAPIKeys;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;

public class RemoteControlApiHelper {

    private static final String TAG = "RemoteControlApiHelper";

    private EspApplication espApp;

    public RemoteControlApiHelper(EspApplication context) {
        espApp = context;
    }

    public void callOffAPI(String controllerNodeId, String matterNodeId, String paramName,
                           String serviceName, String endpointId, ApiResponseListener listener) {

        JsonObject jsonCommand = new JsonObject();
        jsonCommand.addProperty(ESPControllerAPIKeys.KEY_COMMAND_ID, ESPControllerAPIKeys.COMMAND_ID_OFF);

        JsonArray commands = new JsonArray();
        commands.add(jsonCommand);

        JsonObject cluster = new JsonObject();
        cluster.addProperty(ESPControllerAPIKeys.KEY_CLUSTER_ID, ESPControllerAPIKeys.CLUSTER_ID_ON_OFF);
        cluster.add(ESPControllerAPIKeys.KEY_COMMANDS, commands);

        JsonArray clusters = new JsonArray();
        clusters.add(cluster);

        JsonObject endpt = new JsonObject();
        endpt.addProperty(ESPControllerAPIKeys.KEY_ENDPOINT_ID, endpointId);
        endpt.add(ESPControllerAPIKeys.KEY_CLUSTERS, clusters);

        JsonArray endpoints = new JsonArray();
        endpoints.add(endpt);

        JsonObject matterNode = new JsonObject();
        matterNode.addProperty(ESPControllerAPIKeys.KEY_MATTER_NODE_ID, matterNodeId);
        matterNode.add(ESPControllerAPIKeys.KEY_ENDPOINTS, endpoints);

        JsonArray matterNodes = new JsonArray();
        matterNodes.add(matterNode);

        JsonObject matterDevices = new JsonObject();
        matterDevices.add(ESPControllerAPIKeys.KEY_MATTER_NODES, matterNodes);

        JsonObject matterDevicesJson = new JsonObject();
        matterDevicesJson.add(paramName, matterDevices);

        JsonObject serviceJson = new JsonObject();
        serviceJson.add(serviceName, matterDevicesJson);

        ApiManager apiManager = ApiManager.getInstance(espApp);

        apiManager.updateParamValue(controllerNodeId, serviceJson, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                listener.onSuccess(data);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                listener.onResponseFailure(exception);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                listener.onNetworkFailure(exception);
            }
        });
    }

    public void callOnAPI(String controllerNodeId, String matterNodeId, String paramName,
                          String serviceName, String endpointId, ApiResponseListener listener) {

        JsonObject jsonCommand = new JsonObject();
        jsonCommand.addProperty(ESPControllerAPIKeys.KEY_COMMAND_ID, ESPControllerAPIKeys.COMMAND_ID_ON);

        JsonArray commands = new JsonArray();
        commands.add(jsonCommand);

        JsonObject cluster = new JsonObject();
        cluster.addProperty(ESPControllerAPIKeys.KEY_CLUSTER_ID, ESPControllerAPIKeys.CLUSTER_ID_ON_OFF);
        cluster.add(ESPControllerAPIKeys.KEY_COMMANDS, commands);

        JsonArray clusters = new JsonArray();
        clusters.add(cluster);

        JsonObject endpt = new JsonObject();
        endpt.addProperty(ESPControllerAPIKeys.KEY_ENDPOINT_ID, endpointId);
        endpt.add(ESPControllerAPIKeys.KEY_CLUSTERS, clusters);

        JsonArray endpoints = new JsonArray();
        endpoints.add(endpt);

        JsonObject matterNode = new JsonObject();
        matterNode.addProperty(ESPControllerAPIKeys.KEY_MATTER_NODE_ID, matterNodeId);
        matterNode.add(ESPControllerAPIKeys.KEY_ENDPOINTS, endpoints);

        JsonArray matterNodes = new JsonArray();
        matterNodes.add(matterNode);

        JsonObject matterDevices = new JsonObject();
        matterDevices.add(ESPControllerAPIKeys.KEY_MATTER_NODES, matterNodes);

        JsonObject matterDevicesJson = new JsonObject();
        matterDevicesJson.add(paramName, matterDevices);

        JsonObject serviceJson = new JsonObject();
        serviceJson.add(serviceName, matterDevicesJson);

        ApiManager apiManager = ApiManager.getInstance(espApp);

        apiManager.updateParamValue(controllerNodeId, serviceJson, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                listener.onSuccess(data);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                listener.onResponseFailure(exception);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                listener.onNetworkFailure(exception);
            }
        });
    }

    public void callToggleAPI(String controllerNodeId, String matterNodeId, String paramName,
                              String serviceName, String endpointId, ApiResponseListener listener) {

        JsonObject jsonCommand = new JsonObject();
        jsonCommand.addProperty(ESPControllerAPIKeys.KEY_COMMAND_ID, ESPControllerAPIKeys.COMMAND_ID_TOGGLE);

        JsonArray commands = new JsonArray();
        commands.add(jsonCommand);

        JsonObject cluster = new JsonObject();
        cluster.addProperty(ESPControllerAPIKeys.KEY_CLUSTER_ID, ESPControllerAPIKeys.CLUSTER_ID_ON_OFF);
        cluster.add(ESPControllerAPIKeys.KEY_COMMANDS, commands);

        JsonArray clusters = new JsonArray();
        clusters.add(cluster);

        JsonObject endpt = new JsonObject();
        endpt.addProperty(ESPControllerAPIKeys.KEY_ENDPOINT_ID, endpointId);
        endpt.add(ESPControllerAPIKeys.KEY_CLUSTERS, clusters);

        JsonArray endpoints = new JsonArray();
        endpoints.add(endpt);

        JsonObject matterNode = new JsonObject();
        matterNode.addProperty(ESPControllerAPIKeys.KEY_MATTER_NODE_ID, matterNodeId);
        matterNode.add(ESPControllerAPIKeys.KEY_ENDPOINTS, endpoints);

        JsonArray matterNodes = new JsonArray();
        matterNodes.add(matterNode);

        JsonObject matterDevices = new JsonObject();
        matterDevices.add(ESPControllerAPIKeys.KEY_MATTER_NODES, matterNodes);

        JsonObject matterDevicesJson = new JsonObject();
        matterDevicesJson.add(paramName, matterDevices);

        JsonObject serviceJson = new JsonObject();
        serviceJson.add(serviceName, matterDevicesJson);

        ApiManager apiManager = ApiManager.getInstance(espApp);

        apiManager.updateParamValue(controllerNodeId, serviceJson, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                listener.onSuccess(data);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                listener.onResponseFailure(exception);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                listener.onNetworkFailure(exception);
            }
        });
    }

    public void callBrightnessAPI(String controllerNodeId, String matterNodeId, String paramName,
                                  String serviceName, String endpointId, int brightness, ApiResponseListener listener) {

        JsonObject data = new JsonObject();
        data.addProperty("0:U8", brightness);
        data.addProperty("1:U16", 0);
        data.addProperty("2:U8", 0);
        data.addProperty("3:U8", 0);

        JsonObject jsonCommand = new JsonObject();
        jsonCommand.addProperty(ESPControllerAPIKeys.KEY_COMMAND_ID, ESPControllerAPIKeys.COMMAND_ID_MOVE_TO_LEVEL_WITH_ON_OFF);
        jsonCommand.add(ESPControllerAPIKeys.KEY_DATA, data);

        JsonArray commands = new JsonArray();
        commands.add(jsonCommand);

        JsonObject cluster = new JsonObject();
        cluster.addProperty(ESPControllerAPIKeys.KEY_CLUSTER_ID, ESPControllerAPIKeys.CLUSTER_ID_LEVEL_CONTROL);
        cluster.add(ESPControllerAPIKeys.KEY_COMMANDS, commands);

        JsonArray clusters = new JsonArray();
        clusters.add(cluster);

        JsonObject endpt = new JsonObject();
        endpt.addProperty(ESPControllerAPIKeys.KEY_ENDPOINT_ID, endpointId);
        endpt.add(ESPControllerAPIKeys.KEY_CLUSTERS, clusters);

        JsonArray endpoints = new JsonArray();
        endpoints.add(endpt);

        JsonObject matterNode = new JsonObject();
        matterNode.addProperty(ESPControllerAPIKeys.KEY_MATTER_NODE_ID, matterNodeId);
        matterNode.add(ESPControllerAPIKeys.KEY_ENDPOINTS, endpoints);

        JsonArray matterNodes = new JsonArray();
        matterNodes.add(matterNode);

        JsonObject matterDevices = new JsonObject();
        matterDevices.add(ESPControllerAPIKeys.KEY_MATTER_NODES, matterNodes);

        JsonObject matterDevicesJson = new JsonObject();
        matterDevicesJson.add(paramName, matterDevices);

        JsonObject serviceJson = new JsonObject();
        serviceJson.add(serviceName, matterDevicesJson);

        ApiManager apiManager = ApiManager.getInstance(espApp);

        apiManager.updateParamValue(controllerNodeId, serviceJson, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                listener.onSuccess(data);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                listener.onResponseFailure(exception);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                listener.onNetworkFailure(exception);
            }
        });
    }

    public void callSaturationAPI(String controllerNodeId, String matterNodeId, String paramName,
                                  String serviceName, String endpointId, int saturation, ApiResponseListener listener) {

        JsonObject data = new JsonObject();
        data.addProperty("0:U8", saturation);
        data.addProperty("1:U16", 0);
        data.addProperty("2:U8", 0);
        data.addProperty("3:U8", 0);

        JsonObject jsonCommand = new JsonObject();
        jsonCommand.addProperty(ESPControllerAPIKeys.KEY_COMMAND_ID, ESPControllerAPIKeys.COMMAND_ID_MOVE_TO_SATURATION);
        jsonCommand.add(ESPControllerAPIKeys.KEY_DATA, data);

        JsonArray commands = new JsonArray();
        commands.add(jsonCommand);

        JsonObject cluster = new JsonObject();
        cluster.addProperty(ESPControllerAPIKeys.KEY_CLUSTER_ID, ESPControllerAPIKeys.CLUSTER_ID_COLOR_CONTROL);
        cluster.add(ESPControllerAPIKeys.KEY_COMMANDS, commands);

        JsonArray clusters = new JsonArray();
        clusters.add(cluster);

        JsonObject endpt = new JsonObject();
        endpt.addProperty(ESPControllerAPIKeys.KEY_ENDPOINT_ID, endpointId);
        endpt.add(ESPControllerAPIKeys.KEY_CLUSTERS, clusters);

        JsonArray endpoints = new JsonArray();
        endpoints.add(endpt);

        JsonObject matterNode = new JsonObject();
        matterNode.addProperty(ESPControllerAPIKeys.KEY_MATTER_NODE_ID, matterNodeId);
        matterNode.add(ESPControllerAPIKeys.KEY_ENDPOINTS, endpoints);

        JsonArray matterNodes = new JsonArray();
        matterNodes.add(matterNode);

        JsonObject matterDevices = new JsonObject();
        matterDevices.add(ESPControllerAPIKeys.KEY_MATTER_NODES, matterNodes);

        JsonObject matterDevicesJson = new JsonObject();
        matterDevicesJson.add(paramName, matterDevices);

        JsonObject serviceJson = new JsonObject();
        serviceJson.add(serviceName, matterDevicesJson);

        ApiManager apiManager = ApiManager.getInstance(espApp);

        apiManager.updateParamValue(controllerNodeId, serviceJson, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                listener.onSuccess(data);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                listener.onResponseFailure(exception);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                listener.onNetworkFailure(exception);
            }
        });
    }

    public void callHueAPI(String controllerNodeId, String matterNodeId, String paramName,
                           String serviceName, String endpointId, int hueValue, ApiResponseListener listener) {

        JsonObject data = new JsonObject();
        data.addProperty("0:U8", hueValue);
        data.addProperty("1:U16", 0);
        data.addProperty("2:U16", 0);
        data.addProperty("3:U8", 0);
        data.addProperty("4:U8", 0);

        JsonObject jsonCommand = new JsonObject();
        jsonCommand.addProperty(ESPControllerAPIKeys.KEY_COMMAND_ID, ESPControllerAPIKeys.COMMAND_ID_MOVE_TO_HUE);
        jsonCommand.add(ESPControllerAPIKeys.KEY_DATA, data);

        JsonArray commands = new JsonArray();
        commands.add(jsonCommand);

        JsonObject cluster = new JsonObject();
        cluster.addProperty(ESPControllerAPIKeys.KEY_CLUSTER_ID, ESPControllerAPIKeys.CLUSTER_ID_COLOR_CONTROL);
        cluster.add(ESPControllerAPIKeys.KEY_COMMANDS, commands);

        JsonArray clusters = new JsonArray();
        clusters.add(cluster);

        JsonObject endpt = new JsonObject();
        endpt.addProperty(ESPControllerAPIKeys.KEY_ENDPOINT_ID, endpointId);
        endpt.add(ESPControllerAPIKeys.KEY_CLUSTERS, clusters);

        JsonArray endpoints = new JsonArray();
        endpoints.add(endpt);

        JsonObject matterNode = new JsonObject();
        matterNode.addProperty(ESPControllerAPIKeys.KEY_MATTER_NODE_ID, matterNodeId);
        matterNode.add(ESPControllerAPIKeys.KEY_ENDPOINTS, endpoints);

        JsonArray matterNodes = new JsonArray();
        matterNodes.add(matterNode);

        JsonObject matterDevices = new JsonObject();
        matterDevices.add(ESPControllerAPIKeys.KEY_MATTER_NODES, matterNodes);

        JsonObject matterDevicesJson = new JsonObject();
        matterDevicesJson.add(paramName, matterDevices);

        JsonObject serviceJson = new JsonObject();
        serviceJson.add(serviceName, matterDevicesJson);

        ApiManager apiManager = ApiManager.getInstance(espApp);

        apiManager.updateParamValue(controllerNodeId, serviceJson, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                listener.onSuccess(data);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                listener.onResponseFailure(exception);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                listener.onNetworkFailure(exception);
            }
        });
    }

    /// Get on off cluster value
    /// - Parameters:
    ///   - controllerNodeId: controller node id
    ///   - matterNodeId: matter node id
    /// - Returns: on/off status
    public boolean getOnOffValue(String controllerNodeId, String matterNodeId) {

        if (espApp.controllerDevices.containsKey(controllerNodeId)) {
            HashMap<String, String> matterDevices = espApp.controllerDevices.get(controllerNodeId);
            if (matterDevices.containsKey(matterNodeId)) {
                String myJson = matterDevices.get(matterNodeId);

                try {
                    JSONObject matterDeviceJson = new JSONObject(myJson);
                    if (matterDeviceJson != null) {

                        JSONObject endpointsJson = matterDeviceJson.optJSONObject(AppConstants.KEY_ENDPOINTS);
                        Iterator<String> endpointKeys = endpointsJson.keys();

                        while (endpointKeys.hasNext()) {
                            String endpointId = endpointKeys.next();
                            JSONObject endpointJson = matterDeviceJson.optJSONObject(endpointId);

                            if (endpointJson != null) {
                                JSONObject tempJson = endpointJson.optJSONObject(ESPControllerAPIKeys.ENDPOINT_ID_1);

                                if (tempJson != null) {
                                    JSONObject clusters = tempJson.optJSONObject("clusters");
                                    JSONObject onOffCLuster = clusters.optJSONObject(ESPControllerAPIKeys.CLUSTER_ID_ON_OFF);
                                    String attrValue = onOffCLuster.getString(ESPControllerAPIKeys.ATTRIBUTE_ID_ON_OFF);
                                    if (attrValue != null && attrValue.equals("1")) {
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
                    return false;
                }
            }
        }
        return false;
    }

    /// Get brightness level
    /// - Parameters:
    ///   - controllerNodeId: controller node id
    ///   - matterNodeId: matter node id
    /// - Returns: brightness level
    public int getBrightnessLevel(String controllerNodeId, String matterNodeId) {

        if (espApp.controllerDevices.containsKey(controllerNodeId)) {
            HashMap<String, String> matterDevices = espApp.controllerDevices.get(controllerNodeId);
            if (matterDevices.containsKey(matterNodeId)) {
                String myJson = matterDevices.get(matterNodeId);

                try {
                    JSONObject matterDeviceJson = new JSONObject(myJson);
                    if (matterDeviceJson != null) {

                        JSONObject endpointsJson = matterDeviceJson.optJSONObject(AppConstants.KEY_ENDPOINTS);
                        Iterator<String> endpointKeys = endpointsJson.keys();

                        while (endpointKeys.hasNext()) {
                            String endpointId = endpointKeys.next();
                            JSONObject endpointJson = matterDeviceJson.optJSONObject(endpointId);

                            if (endpointJson != null) {
                                JSONObject tempJson = endpointJson.optJSONObject(ESPControllerAPIKeys.ENDPOINT_ID_1);

                                if (tempJson != null) {
                                    JSONObject clusters = tempJson.optJSONObject("clusters");
                                    JSONObject onOffCLuster = clusters.optJSONObject(ESPControllerAPIKeys.CLUSTER_ID_LEVEL_CONTROL);
                                    String attrValue = onOffCLuster.getString(ESPControllerAPIKeys.ATTRIBUTE_ID_BRIGHTNESS_LEVEL);
                                    return Integer.valueOf(attrValue);
                                }
                            }
                        }
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    return 0;
                }
            }
        }
        return 0;
    }

    public int getCurrentHue(String controllerNodeId, String matterNodeId) {

        if (espApp.controllerDevices.containsKey(controllerNodeId)) {
            HashMap<String, String> matterDevices = espApp.controllerDevices.get(controllerNodeId);
            if (matterDevices.containsKey(matterNodeId)) {
                String myJson = matterDevices.get(matterNodeId);

                try {
                    JSONObject matterDeviceJson = new JSONObject(myJson);
                    if (matterDeviceJson != null) {

                        JSONObject endpointsJson = matterDeviceJson.optJSONObject(AppConstants.KEY_ENDPOINTS);
                        Iterator<String> endpointKeys = endpointsJson.keys();

                        while (endpointKeys.hasNext()) {
                            String endpointId = endpointKeys.next();
                            JSONObject endpointJson = matterDeviceJson.optJSONObject(endpointId);

                            if (endpointJson != null) {
                                JSONObject tempJson = endpointJson.optJSONObject(ESPControllerAPIKeys.ENDPOINT_ID_1);

                                if (tempJson != null) {
                                    JSONObject clusters = tempJson.optJSONObject("clusters");
                                    JSONObject onOffCLuster = clusters.optJSONObject(ESPControllerAPIKeys.CLUSTER_ID_COLOR_CONTROL);
                                    String attrValue = onOffCLuster.getString(ESPControllerAPIKeys.ATTRIBUTE_ID_CURRENT_HUE);
                                    if (!TextUtils.isEmpty(attrValue)) {
                                        return Integer.valueOf(attrValue);
                                    }
                                }
                            }
                        }
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    return 0;
                }
            }
        }
        return 0;
    }

    public int getCurrentSaturation(String controllerNodeId, String matterNodeId) {

        if (espApp.controllerDevices.containsKey(controllerNodeId)) {
            HashMap<String, String> matterDevices = espApp.controllerDevices.get(controllerNodeId);
            if (matterDevices.containsKey(matterNodeId)) {
                String myJson = matterDevices.get(matterNodeId);

                try {
                    JSONObject matterDeviceJson = new JSONObject(myJson);
                    if (matterDeviceJson != null) {

                        JSONObject endpointsJson = matterDeviceJson.optJSONObject(AppConstants.KEY_ENDPOINTS);
                        Iterator<String> endpointKeys = endpointsJson.keys();

                        while (endpointKeys.hasNext()) {
                            String endpointId = endpointKeys.next();
                            JSONObject endpointJson = matterDeviceJson.optJSONObject(endpointId);

                            if (endpointJson != null) {
                                JSONObject tempJson = endpointJson.optJSONObject(ESPControllerAPIKeys.ENDPOINT_ID_1);

                                if (tempJson != null) {
                                    JSONObject clusters = tempJson.optJSONObject("clusters");
                                    JSONObject onOffCLuster = clusters.optJSONObject(ESPControllerAPIKeys.CLUSTER_ID_COLOR_CONTROL);
                                    String attrValue = onOffCLuster.getString(ESPControllerAPIKeys.ATTRIBUTE_ID_CURRENT_SATURATION);
                                    if (!TextUtils.isEmpty(attrValue)) {
                                        return Integer.valueOf(attrValue);
                                    }
                                }
                            }
                        }
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    return 0;
                }
            }
        }
        return 0;
    }
}
