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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class FabricDetails implements Parcelable {

    private String fabricId;
    private String groupId;
    private String rootCa;
    private String userNoc;
    private String ipk;
    private String groupCatIdAdmin;
    private String groupCatIdOperate;
    private String matterUserId;
    private String userCatId;

    public FabricDetails(String fabricId) {
        this.fabricId = fabricId;
    }

    protected FabricDetails(Parcel in) {
        fabricId = in.readString();
        groupId = in.readString();
        rootCa = in.readString();
        userNoc = in.readString();
        ipk = in.readString();
        groupCatIdAdmin = in.readString();
        groupCatIdOperate = in.readString();
        matterUserId = in.readString();
        userCatId = in.readString();
    }

    public String getFabricId() {
        return fabricId;
    }

    public void setFabricId(String fabricId) {
        this.fabricId = fabricId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getRootCa() {
        return rootCa;
    }

    public void setRootCa(String rootCa) {
        rootCa = rootCa.replace("-----BEGIN CERTIFICATE-----", "");
        rootCa = rootCa.replace("-----END CERTIFICATE-----", "");
        rootCa = rootCa.replace("\n", "");
        this.rootCa = rootCa;
    }

    public String getUserNoc() {
        return userNoc;
    }

    public void setUserNoc(String userNoc) {
        userNoc = userNoc.replace("-----BEGIN CERTIFICATE-----", "");
        userNoc = userNoc.replace("-----END CERTIFICATE-----", "");
        userNoc = userNoc.replace("\n", "");
        this.userNoc = userNoc;
    }

    public String getIpk() {
        return ipk;
    }

    public void setIpk(String ipk) {
        this.ipk = ipk;
    }

    public String getGroupCatIdAdmin() {
        return groupCatIdAdmin;
    }

    public void setGroupCatIdAdmin(String groupCatIdAdmin) {
        this.groupCatIdAdmin = groupCatIdAdmin;
    }

    public String getGroupCatIdOperate() {
        return groupCatIdOperate;
    }

    public void setGroupCatIdOperate(String groupCatIdOperate) {
        this.groupCatIdOperate = groupCatIdOperate;
    }

    public String getMatterUserId() {
        return matterUserId;
    }

    public void setMatterUserId(String matterUserId) {
        this.matterUserId = matterUserId;
    }

    public String getUserCatId() {
        return userCatId;
    }

    public void setUserCatId(String userCatId) {
        this.userCatId = userCatId;
    }

    public static final Creator<FabricDetails> CREATOR = new Creator<FabricDetails>() {
        @Override
        public FabricDetails createFromParcel(Parcel in) {
            return new FabricDetails(in);
        }

        @Override
        public FabricDetails[] newArray(int size) {
            return new FabricDetails[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(fabricId);
        dest.writeString(groupId);
        dest.writeString(rootCa);
        dest.writeString(userNoc);
        dest.writeString(ipk);
        dest.writeString(groupCatIdAdmin);
        dest.writeString(groupCatIdOperate);
        dest.writeString(matterUserId);
        dest.writeString(userCatId);
    }
}
