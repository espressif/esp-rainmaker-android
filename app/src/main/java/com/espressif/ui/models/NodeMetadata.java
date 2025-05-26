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

package com.espressif.ui.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class NodeMetadata implements Parcelable {

    private String controllerNodeId;
    private String deviceName;
    private String deviceType;
    private String groupId;
    private String productId;
    private String vendorId;
    private String serversData;
    private ArrayList<String> endpointsData;

    public NodeMetadata() {
    }

    protected NodeMetadata(Parcel in) {
        controllerNodeId = in.readString();
        deviceName = in.readString();
        deviceType = in.readString();
        groupId = in.readString();
        productId = in.readString();
        vendorId = in.readString();
        serversData = in.readString();
        endpointsData = in.createStringArrayList();
    }

    public static final Creator<NodeMetadata> CREATOR = new Creator<NodeMetadata>() {
        @Override
        public NodeMetadata createFromParcel(Parcel in) {
            return new NodeMetadata(in);
        }

        @Override
        public NodeMetadata[] newArray(int size) {
            return new NodeMetadata[size];
        }
    };

    public String getControllerNodeId() {
        return controllerNodeId;
    }

    public void setControllerNodeId(String controllerNodeId) {
        this.controllerNodeId = controllerNodeId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getVendorId() {
        return vendorId;
    }

    public void setVendorId(String vendorId) {
        this.vendorId = vendorId;
    }

    public String getServersData() {
        return serversData;
    }

    public void setServersData(String serversData) {
        this.serversData = serversData;
    }

    public ArrayList<String> getEndpointsData() {
        return endpointsData;
    }

    public void setEndpointsData(ArrayList<String> endpointsData) {
        this.endpointsData = endpointsData;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(controllerNodeId);
        dest.writeString(deviceName);
        dest.writeString(deviceType);
        dest.writeString(groupId);
        dest.writeString(productId);
        dest.writeString(vendorId);
        dest.writeString(serversData);
        dest.writeStringList(endpointsData);
    }
}
