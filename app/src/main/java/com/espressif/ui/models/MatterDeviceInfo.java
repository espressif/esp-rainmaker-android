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

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;

public class MatterDeviceInfo implements Parcelable {

    private String deviceType;
    private HashMap<String, ArrayList<Long>> serverClusters;
    private HashMap<String, ArrayList<Long>> clientClusters;

    public MatterDeviceInfo() {
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public HashMap<String, ArrayList<Long>> getServerClusters() {
        return serverClusters;
    }

    public void setServerClusters(HashMap<String, ArrayList<Long>> serverClusters) {
        this.serverClusters = serverClusters;
    }

    public HashMap<String, ArrayList<Long>> getClientClusters() {
        return clientClusters;
    }

    public void setClientClusters(HashMap<String, ArrayList<Long>> clientClusters) {
        this.clientClusters = clientClusters;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(deviceType);
        dest.writeSerializable(serverClusters);
        dest.writeSerializable(clientClusters);
    }

    public MatterDeviceInfo(Parcel in) {
        deviceType = in.readString();
        serverClusters = (HashMap<String, ArrayList<Long>>) in.readSerializable();
        clientClusters = (HashMap<String, ArrayList<Long>>) in.readSerializable();
    }

    public static final Creator<MatterDeviceInfo> CREATOR = new Creator<MatterDeviceInfo>() {
        @Override
        public MatterDeviceInfo createFromParcel(Parcel in) {
            return new MatterDeviceInfo(in);
        }

        @Override
        public MatterDeviceInfo[] newArray(int size) {
            return new MatterDeviceInfo[size];
        }
    };
}
