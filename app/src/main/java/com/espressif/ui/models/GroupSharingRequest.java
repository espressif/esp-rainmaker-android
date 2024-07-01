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

public class GroupSharingRequest implements Parcelable {

    private String reqId;
    private String reqStatus;
    private ArrayList<String> userName;
    private ArrayList<String> primaryUserName;
    private long reqTime;
    private ArrayList<String> group_ids;
    private ArrayList<String> group_names;
    private String metadata;

    public GroupSharingRequest(String id) {
        reqId = id;
    }

    protected GroupSharingRequest(Parcel in) {
        reqId = in.readString();
        reqStatus = in.readString();
        userName = in.createStringArrayList();
        primaryUserName = in.createStringArrayList();
        reqTime = in.readLong();
        group_ids = in.createStringArrayList();
        group_names = in.createStringArrayList();
        metadata = in.readString();
    }

    public static final Creator<GroupSharingRequest> CREATOR = new Creator<GroupSharingRequest>() {
        @Override
        public GroupSharingRequest createFromParcel(Parcel in) {
            return new GroupSharingRequest(in);
        }

        @Override
        public GroupSharingRequest[] newArray(int size) {
            return new GroupSharingRequest[size];
        }
    };

    public String getReqId() {
        return reqId;
    }

    public void setReqId(String reqId) {
        this.reqId = reqId;
    }

    public String getReqStatus() {
        return reqStatus;
    }

    public void setReqStatus(String reqStatus) {
        this.reqStatus = reqStatus;
    }

    public long getReqTime() {
        return reqTime;
    }

    public void setReqTime(long reqTime) {
        this.reqTime = reqTime;
    }

    public ArrayList<String> getUserName() {
        return userName;
    }

    public void setUserName(ArrayList<String> userName) {

        this.userName = userName;
    }

    public ArrayList<String> getPrimaryUserName() {
        return primaryUserName;
    }

    public void setPrimaryUserName(ArrayList<String> primaryUserName) {

        this.primaryUserName = primaryUserName;
    }

    public ArrayList<String> getGroup_ids() {
        return group_ids;
    }

    public void setGroup_ids(ArrayList<String> group_ids) {
        this.group_ids = group_ids;
    }

    public ArrayList<String> getGroup_names() {
        return group_names;
    }

    public void setGroup_names(ArrayList<String> group_names) {
        this.group_names = group_names;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(reqId);
        dest.writeString(reqStatus);
        dest.writeStringList(userName);
        dest.writeStringList(primaryUserName);
        dest.writeLong(reqTime);
        dest.writeStringList(group_ids);
        dest.writeStringList(group_names);
        dest.writeString(metadata);
    }
}
