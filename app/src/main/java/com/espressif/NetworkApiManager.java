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

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.espressif.AppConstants.Companion.UpdateEventType;
import com.espressif.ble.BleLocalControlManager;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.local_control.LocalControlApiManager;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.UpdateEvent;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.EventBus;

/**
 * This class will decide to call API on local network or on cloud.
 * If device is available on local network then API will be called using local network.
 * And it will call cloud API if device is not available on local network.
 */
public class NetworkApiManager {

    private final String TAG = NetworkApiManager.class.getSimpleName();

    private Context context;
    private EspApplication espApp;
    private ApiManager apiManager;
    private LocalControlApiManager localControlApiManager;

    public NetworkApiManager(Context context) {
        this.context = context;
        espApp = (EspApplication) context.getApplicationContext();
        apiManager = ApiManager.getInstance(context);
        localControlApiManager = new LocalControlApiManager(context);
    }

    /**
     * This method is used to update param values of a device.
     *
     * @param nodeId   Node id.
     * @param body     Json data to be sent in request. It contains new value of a param.
     * @param listener Listener to send success or failure.
     */
    public void updateParamValue(final String nodeId, final JsonObject body, final ApiResponseListener listener) {
        updateParamValue(nodeId, body, listener, true);
    }

    /**
     * @param reportToProxy If true, reportParamsToProxy is called after a successful BLE update.
     *                      Pass false when there are back-to-back BLE calls to avoid concurrent BLE operations.
     */
    public void updateParamValue(final String nodeId, final JsonObject body, final ApiResponseListener listener,
                                 final boolean reportToProxy) {

        if (espApp.localDeviceMap.containsKey(nodeId)) {

            localControlApiManager.updateParamValue(nodeId, body, new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {
                    listener.onSuccess(data);
                }

                @Override
                public void onResponseFailure(Exception exception) {
                    Log.e(TAG, "Error : " + exception.getMessage());
                    Log.e(TAG, "Removing Node id : " + nodeId);
                    espApp.localDeviceMap.remove(nodeId);
                    espApp.nodeMap.get(nodeId).setNodeStatus(AppConstants.NODE_STATUS_ONLINE);
                    updateParamValue(nodeId, body, listener, reportToProxy);
                }

                @Override
                public void onNetworkFailure(Exception exception) {
                    Log.e(TAG, "Error : " + exception.getMessage());
                    Log.e(TAG, "Removing Node id : " + nodeId);
                    espApp.localDeviceMap.remove(nodeId);
                    espApp.nodeMap.get(nodeId).setNodeStatus(AppConstants.NODE_STATUS_ONLINE);
                    updateParamValue(nodeId, body, listener, reportToProxy);
                }
            });

        } else {
            // Priority 2: BLE local control
            Log.d(TAG, "updateParamValue - checking BLE for node: " + nodeId);
            BleLocalControlManager bleManager = BleLocalControlManager.getInstance(context);
            if (bleManager.isConnected(nodeId) || bleManager.isDiscovered(nodeId)) {
                bleManager.connectAndSendParams(nodeId, body, new ApiResponseListener() {
                    @Override
                    public void onSuccess(Bundle data) {
                        Log.d(TAG, "BLE param update success for node: " + nodeId);
                        listener.onSuccess(data);
                        if (reportToProxy) {
                            reportParamsToProxy(nodeId);
                        }
                    }

                    @Override
                    public void onResponseFailure(Exception exception) {
                        Log.e(TAG, "BLE param update failed: " + exception.getMessage());
                        clearBleAndFallbackToCloud(nodeId);
                        apiManager.updateParamValue(nodeId, body, listener);
                    }

                    @Override
                    public void onNetworkFailure(Exception exception) {
                        Log.e(TAG, "BLE connection failed: " + exception.getMessage());
                        clearBleAndFallbackToCloud(nodeId);
                        apiManager.updateParamValue(nodeId, body, listener);
                    }
                });
            } else {
                // Priority 3: Cloud API
                apiManager.updateParamValue(nodeId, body, listener);
            }
        }
    }

    /**
     * This method is used to get param values for a given node id.
     *
     * @param nodeId   Node id.
     * @param listener Listener to send success or failure.
     */
    public void getParamsValues(final String nodeId, final ApiResponseListener listener) {

        if (espApp.localDeviceMap.containsKey(nodeId)) {

            localControlApiManager.getParamsValues(nodeId, new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {
                    listener.onSuccess(data);
                }

                @Override
                public void onResponseFailure(Exception exception) {
                    Log.e(TAG, "Error : " + exception.getMessage());
                    Log.e(TAG, "Removing Node id : " + nodeId);
                    espApp.localDeviceMap.remove(nodeId);
                    espApp.nodeMap.get(nodeId).setNodeStatus(AppConstants.NODE_STATUS_ONLINE);
                    getParamsValues(nodeId, listener);
                }

                @Override
                public void onNetworkFailure(Exception exception) {
                    Log.e(TAG, "Error : " + exception.getMessage());
                    Log.e(TAG, "Removing Node id : " + nodeId);
                    espApp.localDeviceMap.remove(nodeId);
                    espApp.nodeMap.get(nodeId).setNodeStatus(AppConstants.NODE_STATUS_ONLINE);
                    getParamsValues(nodeId, listener);
                }
            });

        } else {
            // Priority 2: BLE local control (only when already connected)
            BleLocalControlManager bleManager = BleLocalControlManager.getInstance(context);
            if (bleManager.isConnected(nodeId) && !bleManager.isProxyReadInProgress(nodeId)) {
                bleManager.queryParams(nodeId, json -> {
                    if (json != null) {
                        EspNode node = espApp.nodeMap.get(nodeId);
                        if (node != null && node.getDevices() != null) {
                            for (com.espressif.ui.models.Device dev : node.getDevices()) {
                                String devName = dev.getDeviceName();
                                if (json.has(devName)) {
                                    try {
                                        org.json.JSONObject deviceParams = json.getJSONObject(devName);
                                        for (com.espressif.ui.models.Param param : dev.getParams()) {
                                            if (deviceParams.has(param.getName())) {
                                                Object val_ = deviceParams.get(param.getName());
                                                if (val_ instanceof Boolean) {
                                                    param.setSwitchStatus((Boolean) val_);
                                                } else if (val_ instanceof Number) {
                                                    param.setValue(((Number) val_).doubleValue());
                                                    param.setLabelValue(val_.toString());
                                                } else if (val_ instanceof String) {
                                                    param.setLabelValue((String) val_);
                                                }
                                            }
                                        }
                                    } catch (org.json.JSONException e) {
                                        Log.e(TAG, "Error parsing BLE params: " + e.getMessage());
                                    }
                                }
                            }
                        }
                        listener.onSuccess(null);
                    } else {
                        Log.e(TAG, "BLE queryParams returned null, falling back to cloud");
                        clearBleAndFallbackToCloud(nodeId);
                        apiManager.getParamsValues(nodeId, listener);
                    }
                    return kotlin.Unit.INSTANCE;
                });
            } else {
                // Priority 3: Cloud API
                apiManager.getParamsValues(nodeId, listener);
            }
        }
    }

    /**
     * This method is used to get node details for a given node id.
     *
     * @param nodeId   Node id.
     * @param listener Listener to send success or failure.
     */
    public void getNodeDetails(final String nodeId, final ApiResponseListener listener) {

        if (espApp.localDeviceMap.containsKey(nodeId)) {

            localControlApiManager.getNodeDetails(nodeId, new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {
                    listener.onSuccess(data);
                }

                @Override
                public void onResponseFailure(Exception exception) {
                    Log.e(TAG, "Error : " + exception.getMessage());
                    Log.e(TAG, "Removing Node id : " + nodeId);
                    espApp.localDeviceMap.remove(nodeId);
                    espApp.nodeMap.get(nodeId).setNodeStatus(AppConstants.NODE_STATUS_ONLINE);
                    getParamsValues(nodeId, listener);
                }

                @Override
                public void onNetworkFailure(Exception exception) {
                    Log.e(TAG, "Error : " + exception.getMessage());
                    Log.e(TAG, "Removing Node id : " + nodeId);
                    espApp.localDeviceMap.remove(nodeId);
                    espApp.nodeMap.get(nodeId).setNodeStatus(AppConstants.NODE_STATUS_ONLINE);
                    getParamsValues(nodeId, listener);
                }
            });

        } else {
            apiManager.getNodeDetails(nodeId, listener);
        }
    }

    /**
     * After a successful BLE param update, gets params with timestamp from the device
     * and reports the signed payload to the cloud via the proxy/params API.
     * This is fire-and-forget: failures are logged but do not affect the caller.
     */
    private void reportParamsToProxy(final String nodeId) {
        BleLocalControlManager bleManager = BleLocalControlManager.getInstance(context);
        bleManager.getParamsWithTimestamp(nodeId, json -> {
            if (json == null) {
                Log.e(TAG, "Failed to get timestamped params for proxy report (node: " + nodeId + ")");
                return kotlin.Unit.INSTANCE;
            }

            try {
                String signature = json.optString("signature", "");
                org.json.JSONObject nodePayloadObj = json.optJSONObject("node_payload");

                if (android.text.TextUtils.isEmpty(signature) || nodePayloadObj == null) {
                    Log.e(TAG, "Missing signature or node_payload in device response for proxy report");
                    return kotlin.Unit.INSTANCE;
                }

                String nodePayloadStr = nodePayloadObj.toString().replace("\\/", "/");

                com.google.gson.JsonObject proxyPayload = new com.google.gson.JsonObject();
                proxyPayload.addProperty("node_payload", nodePayloadStr);
                proxyPayload.addProperty("signature", signature);

                Log.d(TAG, "Reporting params to proxy for node: " + nodeId);
                apiManager.reportProxyParams(nodeId, proxyPayload, new ApiResponseListener() {
                    @Override
                    public void onSuccess(Bundle data) {
                        Log.d(TAG, "Proxy params reported successfully for node: " + nodeId);
                    }

                    @Override
                    public void onResponseFailure(Exception exception) {
                        Log.e(TAG, "Failed to report proxy params: " + exception.getMessage());
                    }

                    @Override
                    public void onNetworkFailure(Exception exception) {
                        Log.e(TAG, "Network failure reporting proxy params: " + exception.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error processing proxy report: " + e.getMessage());
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * Clears BLE connection state for a node after a BLE failure,
     * updates the node status based on cloud connectivity, and
     * posts a UI update event so the "Reachable on BLE" label is removed.
     */
    private void clearBleAndFallbackToCloud(String nodeId) {
        BleLocalControlManager bleManager = BleLocalControlManager.getInstance(context);
        bleManager.disconnectDevice(nodeId);

        EspNode node = espApp.nodeMap.get(nodeId);
        if (node != null) {
            if (node.isOnline()) {
                node.setNodeStatus(AppConstants.NODE_STATUS_ONLINE);
            } else {
                node.setNodeStatus(AppConstants.NODE_STATUS_OFFLINE);
            }
        }
        EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
    }
}
