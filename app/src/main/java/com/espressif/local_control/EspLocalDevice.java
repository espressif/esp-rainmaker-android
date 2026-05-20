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

package com.espressif.local_control;

import android.text.TextUtils;
import android.util.Log;

import com.espressif.provisioning.listeners.ResponseListener;
import com.espressif.provisioning.security.Security;
import com.espressif.provisioning.security.Security0;
import com.espressif.provisioning.security.Security1;
import com.espressif.provisioning.security.Security2;
import com.espressif.rainmaker.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class EspLocalDevice {

    public static final String TAG = EspLocalDevice.class.getSimpleName();

    private static final String VERSION_ENDPOINT = "esp_local_ctrl/version";
    private static final String VERSION_JSON_LOCAL_CTRL = "local_ctrl";
    private static final String VERSION_JSON_SEC_PATCH_VER = "sec_patch_ver";

    private String nodeId;
    private String serviceName;
    private String ipAddr;
    private int port;
    private HashMap<String, String> endpointList;
    private String pop = "", userName = "";
    private int securityType;
    private int secPatchVersion = 0;
    private int propertyCount;

    private EspLocalSession session;
    private EspLocalTransport transport;
    private SessionState sessionState = SessionState.NOT_CREATED;

    enum SessionState {
        NOT_CREATED,
        CREATING,
        CREATED,
        FAILED
    }

    public EspLocalDevice(String nodeId, String ipAddr, int port) {
        this.nodeId = nodeId;
        this.ipAddr = ipAddr;
        this.port = port;
        this.propertyCount = -1;
    }

    private void initSession(final ResponseListener listener) {

        if (sessionState.equals(SessionState.CREATING)) {
            Log.e(TAG, "Incorrect session initialisation for local device.");
            return;
        }
        sessionState = SessionState.CREATING;
        Log.d(TAG, "========= Init Session for local device =========");

        final String url = "http://" + getIpAddr() + ":" + getPort();

        if (securityType == 2) {
            // Firmware built against ESP-IDF v5.4+ advertises sec_patch_ver via the
            // version endpoint. Older firmware does not expose the endpoint or the
            // field, in which case we fall back to patchVersion=0 (legacy static IV).
            fetchSecPatchVersion(url, new ResponseListener() {
                @Override
                public void onSuccess(byte[] data) {
                    establishSession(url, listener);
                }

                @Override
                public void onFailure(Exception e) {
                    Log.w(TAG, "Version endpoint unavailable, using sec2 patchVersion=0: " + e.getMessage());
                    secPatchVersion = 0;
                    establishSession(url, listener);
                }
            });
        } else {
            establishSession(url, listener);
        }
    }

    private void fetchSecPatchVersion(String url, final ResponseListener listener) {
        EspLocalTransport versionTransport = new EspLocalTransport(url);
        // protocomm_httpd rejects POST with content_len <= 0 ("Content length not found"),
        // so mirror esp_prov.py and send "---" as the dummy payload. The version handler
        // ignores the request body and just returns the version JSON.
        versionTransport.sendConfigData(VERSION_ENDPOINT, "---".getBytes(), new ResponseListener() {
            @Override
            public void onSuccess(byte[] data) {
                try {
                    JSONObject root = new JSONObject(new String(data));
                    JSONObject localCtrl = root.optJSONObject(VERSION_JSON_LOCAL_CTRL);
                    if (localCtrl != null && localCtrl.has(VERSION_JSON_SEC_PATCH_VER)) {
                        secPatchVersion = localCtrl.optInt(VERSION_JSON_SEC_PATCH_VER, 0);
                        Log.d(TAG, "Device advertises sec_patch_ver=" + secPatchVersion);
                    } else {
                        secPatchVersion = 0;
                        Log.d(TAG, "Device did not advertise sec_patch_ver; defaulting to 0");
                    }
                } catch (JSONException e) {
                    Log.w(TAG, "Could not parse version JSON, using sec_patch_ver=0: " + e.getMessage());
                    secPatchVersion = 0;
                }
                listener.onSuccess(null);
            }

            @Override
            public void onFailure(Exception e) {
                listener.onFailure(e);
            }
        });
    }

    private void establishSession(String url, final ResponseListener listener) {
        Security security;
        if (securityType == 2) {
            String user = !TextUtils.isEmpty(userName)
                ? userName
                : BuildConfig.LOCAL_CONTROL_SECURITY_2_USERNAME;
            security = new Security2(user, pop, secPatchVersion);
            Log.d(TAG, "Created security 2 with username: " + user + ", pop: " + pop
                    + ", patchVersion: " + secPatchVersion);
        } else if (securityType == 1) {
            security = new Security1(pop);
            Log.d(TAG, "Created security 1 with pop : " + pop);
        } else {
            security = new Security0();
        }
        Log.d(TAG, "POP : " + pop);
        Log.d(TAG, "Type : " + securityType);
        transport = new EspLocalTransport(url);
        session = new EspLocalSession(transport, security);

        session.init(null, new EspLocalSession.SessionListener() {

            @Override
            public void OnSessionEstablished() {
                sessionState = SessionState.CREATED;
                Log.d(TAG, "========= Session established on local network");
                listener.onSuccess(null);
            }

            @Override
            public void OnSessionEstablishFailed(Exception e) {
                sessionState = SessionState.FAILED;
                listener.onFailure(e);
            }
        });
    }

    public void sendData(final String path, final byte[] data, final ResponseListener listener) {

        Log.d(TAG, "Send data to device on path : " + path);

        if (session == null || !session.isEstablished()) {

            initSession(new ResponseListener() {

                @Override
                public void onSuccess(byte[] returnData) {
                    sendData(path, data, listener);
                }

                @Override
                public void onFailure(Exception e) {
                    e.printStackTrace();
                    if (listener != null) {
                        listener.onFailure(new RuntimeException("Failed to create session."));
                    }
                }
            });
        } else {
            Log.d(TAG, "Session is already created");
            session.sendDataToDevice(path, data, new ResponseListener() {
                @Override
                public void onSuccess(byte[] returnData) {
                    listener.onSuccess(returnData);
                }

                @Override
                public void onFailure(Exception e) {
                    initSession(new ResponseListener() {

                        @Override
                        public void onSuccess(byte[] returnData) {
                            Log.d(TAG, "======== Session established again");
                            sendData(path, data, listener);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            e.printStackTrace();
                            if (listener != null) {
                                listener.onFailure(new RuntimeException("Failed to create session."));
                            }
                        }
                    });
                }
            });
        }
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getIpAddr() {
        return ipAddr;
    }

    public int getPort() {
        return port;
    }

    public HashMap<String, String> getEndpointList() {
        return endpointList;
    }

    public void setEndpointList(HashMap<String, String> endpointList) {
        this.endpointList = endpointList;
    }

    public String getPop() {
        return pop;
    }

    public void setPop(String pop) {
        Log.d(TAG, "========= Set POP : " + pop);
        this.pop = pop;
    }

    public int getSecurityType() {
        return securityType;
    }

    public void setSecurityType(int securityType) {
        Log.d(TAG, "========= Set Security Type : " + securityType);
        this.securityType = securityType;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        Log.d(TAG, "========= Set User name : " + userName);
        this.userName = userName;
    }

    public int getPropertyCount() {
        return propertyCount;
    }

    public void setPropertyCount(int propertyCount) {
        this.propertyCount = propertyCount;
    }
}
