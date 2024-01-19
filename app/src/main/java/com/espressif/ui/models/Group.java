// Copyright 2021 Espressif Systems (Shanghai) PTE LTD
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
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.espressif.AppConstants;
import com.espressif.db.StringArrayListConverters;
import com.espressif.matter.FabricDetails;

import java.util.ArrayList;
import java.util.HashMap;

@Entity(tableName = AppConstants.GROUP_TABLE)
public class Group implements Parcelable {

    @PrimaryKey
    @NonNull
    private String groupId;

    @ColumnInfo(name = "group_name")
    @NonNull
    private String groupName;

    @ColumnInfo(name = "node_list")
    @TypeConverters(StringArrayListConverters.class)
    private ArrayList<String> nodeList;

    @Ignore
    private String fabricId;

    @Ignore
    private boolean isMatter;

    @Ignore
    private boolean isMutuallyExclusive;

    @Ignore
    private boolean isPrimary;

    @Ignore
    private FabricDetails fabricDetails;

    @Ignore
    private HashMap<String, String> nodeDetails;    // Key - Node id, Value - Matter node id

    public Group(String groupName) {
        this.groupName = groupName;
    }

    protected Group(Parcel in) {
        groupId = in.readString();
        groupName = in.readString();
        nodeList = in.createStringArrayList();
        fabricId = in.readString();
        isMatter = in.readByte() != 0;
        isMutuallyExclusive = in.readByte() != 0;
        isPrimary = in.readByte() != 0;
        fabricDetails = in.readParcelable(FabricDetails.class.getClassLoader());
        nodeDetails = (HashMap<String, String>) in.readSerializable();
    }

    public static final Creator<Group> CREATOR = new Creator<Group>() {
        @Override
        public Group createFromParcel(Parcel in) {
            return new Group(in);
        }

        @Override
        public Group[] newArray(int size) {
            return new Group[size];
        }
    };

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public ArrayList<String> getNodeList() {
        return nodeList;
    }

    public void setNodeList(ArrayList<String> nodeList) {
        this.nodeList = nodeList;
    }

    public boolean isMatter() {
        return isMatter;
    }

    public void setMatter(boolean matter) {
        isMatter = matter;
    }

    public String getFabricId() {
        return fabricId;
    }

    public void setFabricId(String fabricId) {
        this.fabricId = fabricId;
    }

    public boolean isMutuallyExclusive() {
        return isMutuallyExclusive;
    }

    public void setMutuallyExclusive(boolean mutuallyExclusive) {
        isMutuallyExclusive = mutuallyExclusive;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }

    public FabricDetails getFabricDetails() {
        return fabricDetails;
    }

    public void setFabricDetails(FabricDetails fabricDetails) {
        this.fabricDetails = fabricDetails;
    }

    public HashMap<String, String> getNodeDetails() {
        return nodeDetails;
    }

    public void setNodeDetails(HashMap<String, String> nodeDetails) {
        this.nodeDetails = nodeDetails;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(groupId);
        dest.writeString(groupName);
        dest.writeStringList(nodeList);
        dest.writeString(fabricId);
        dest.writeByte((byte) (isMatter ? 1 : 0));
        dest.writeByte((byte) (isMutuallyExclusive ? 1 : 0));
        dest.writeByte((byte) (isPrimary ? 1 : 0));
        dest.writeParcelable(fabricDetails, flags);
        dest.writeSerializable(nodeDetails);
    }
}
