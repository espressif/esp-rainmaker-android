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

import android.util.Log;

import com.espressif.provisioning.listeners.ResponseListener;
import com.espressif.provisioning.security.Security;
import com.espressif.provisioning.security.Security0;
import com.espressif.provisioning.security.Security1;
import com.espressif.provisioning.security.Security2;
import com.espressif.rainmaker.BuildConfig;

import java.util.HashMap;

public class EspLocalDevice {

    public static final String TAG = EspLocalDevice.class.getSimpleName();

    private String nodeId;
    private String serviceName;
    private String ipAddr;
    private int port;
    private HashMap<String, String> endpointList;
    private String pop = "", userName = "";
    private int securityType;
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

        if (!sessionState.equals(SessionState.CREATING)) {

            sessionState = SessionState.CREATING;
            Log.d(TAG, "========= Init Session for local device =========");

            final String url = "http://" + getIpAddr() + ":" + getPort();
            Security security = null;
            if (securityType == 2) {
                security = new Security2(BuildConfig.LOCAL_CONTROL_SECURITY_2_USERNAME, pop);
                Log.d(TAG, "Created security 2 with pop : " + pop);
            } else if (securityType == 1) {
                security = new Security1(pop);
                Log.d(TAG, "Created security 1 with pop : " + pop);
            } else if (securityType == 0) {
                security = new Security0();
            } else {
                // Consider Sec0 for other
                security = new Security0();
//            listener.onFailure(new RuntimeException("Security type " + securityType + " not supported"));
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
        } else {
            Log.e(TAG, "Incorrect session initialisation for local device.");
        }
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
