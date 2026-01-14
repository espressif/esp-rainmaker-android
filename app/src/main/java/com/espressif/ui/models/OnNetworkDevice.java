// Copyright 2026 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.ui.models;

import java.io.Serializable;

public class OnNetworkDevice implements Serializable {

    private String nodeId;
    private String serviceName;
    private String ipAddress;
    private int port;
    private int secVersion;
    private boolean popRequired;
    private String chRespEndpoint;

    public OnNetworkDevice() {
    }

    public OnNetworkDevice(String nodeId, String serviceName, String ipAddress, int port, 
                          int secVersion, boolean popRequired, String chRespEndpoint) {
        this.nodeId = nodeId;
        this.serviceName = serviceName;
        this.ipAddress = ipAddress;
        this.port = port;
        this.secVersion = secVersion;
        this.popRequired = popRequired;
        this.chRespEndpoint = chRespEndpoint;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getSecVersion() {
        return secVersion;
    }

    public void setSecVersion(int secVersion) {
        this.secVersion = secVersion;
    }

    public boolean isPopRequired() {
        return popRequired;
    }

    public void setPopRequired(boolean popRequired) {
        this.popRequired = popRequired;
    }

    public String getChRespEndpoint() {
        return chRespEndpoint;
    }

    public void setChRespEndpoint(String chRespEndpoint) {
        this.chRespEndpoint = chRespEndpoint;
    }
}
