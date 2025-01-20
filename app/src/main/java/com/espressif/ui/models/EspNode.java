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

package com.espressif.ui.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.espressif.AppConstants;

import java.util.ArrayList;

@Entity(tableName = AppConstants.NODE_TABLE)
public class EspNode implements Parcelable {

    @PrimaryKey
    @NonNull
    private String nodeId;

    @Ignore
    private String userRole;

    @Ignore
    private String configVersion;

    @Ignore
    private String nodeName;

    @Ignore
    private String fwVersion;

    @Ignore
    private String nodeType;

    @Ignore
    private String newNodeType;

    @Ignore
    private boolean isOnline;

    @Ignore
    private long timeStampOfStatus; // timestamp of connectivity status

    @Ignore
    private ArrayList<Device> devices;

    @Ignore
    private ArrayList<Param> attributes;

    @Ignore
    private ArrayList<Service> services;

    @Ignore
    private boolean isAvailableLocally;

    @Ignore
    private String ipAddress;

    @Ignore
    private int port;

    @ColumnInfo(name = "config_data")
    private String configData;

    @ColumnInfo(name = "param_data")
    private String paramData;

    @Ignore
    private boolean isSelected;

    @Ignore
    private ArrayList<String> primaryUsers;

    @Ignore
    private ArrayList<String> secondaryUsers;

    @Ignore
    private int scheduleMaxCnt;

    @Ignore
    private int scheduleCurrentCnt;

    @Ignore
    private int sceneMaxCnt;

    @Ignore
    private int sceneCurrentCnt;

    @Ignore
    private boolean isMatterNode;

    @Ignore
    private String matterNodeId;

    @Ignore
    private NodeMetadata nodeMetadata;

    @Ignore
    private String nodeMetadataJson;

    @Ignore
    private boolean isController;

    @Ignore
    private ArrayList<String> sharedGroupIds;

    @Ignore
    private int nodeStatus = AppConstants.NODE_STATUS_OFFLINE;

    public EspNode() {
    }

    public EspNode(String id) {
        nodeId = id;
    }

    public EspNode(EspNode node) {

        nodeId = node.getNodeId();
        userRole = node.getUserRole();
        configVersion = node.getConfigVersion();
        nodeName = node.getNodeName();
        fwVersion = node.getFwVersion();
        nodeType = node.getNodeType();
        isOnline = node.isOnline();
        timeStampOfStatus = node.getTimeStampOfStatus();
        devices = node.getDevices();
        attributes = node.getAttributes();
        services = node.getServices();
        isAvailableLocally = node.isAvailableLocally();
        ipAddress = node.getIpAddress();
        port = node.getPort();
        configData = node.getConfigData();
        paramData = node.getParamData();
        isSelected = node.isSelected();
        primaryUsers = node.getPrimaryUsers();
        secondaryUsers = node.getSecondaryUsers();
        scheduleMaxCnt = node.getScheduleMaxCnt();
        scheduleCurrentCnt = node.getScheduleCurrentCnt();
        sceneMaxCnt = node.getSceneMaxCnt();
        sceneCurrentCnt = node.getSceneCurrentCnt();
        matterNodeId = node.getMatterNodeId();
        nodeMetadata = node.getNodeMetadata();
        nodeMetadataJson = node.getNodeMetadataJson();
        isController = node.isController();
        nodeStatus = node.getNodeStatus();
        sharedGroupIds = node.getSharedGroupIds();
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(String configVersion) {
        this.configVersion = configVersion;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getFwVersion() {
        return fwVersion;
    }

    public void setFwVersion(String fwVersion) {
        this.fwVersion = fwVersion;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public long getTimeStampOfStatus() {
        return timeStampOfStatus;
    }

    public void setTimeStampOfStatus(long timeStampOfStatus) {
        this.timeStampOfStatus = timeStampOfStatus;
    }

    public ArrayList<Device> getDevices() {
        return devices;
    }

    public void setDevices(ArrayList<Device> devices) {
        this.devices = devices;
    }

    public ArrayList<Param> getAttributes() {
        return attributes;
    }

    public void setAttributes(ArrayList<Param> attributes) {
        this.attributes = attributes;
    }

    public ArrayList<Service> getServices() {
        return services;
    }

    public void setServices(ArrayList<Service> services) {
        this.services = services;
    }

    public boolean isAvailableLocally() {
        return isAvailableLocally;
    }

    public void setAvailableLocally(boolean availableLocally) {
        isAvailableLocally = availableLocally;
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

    public String getConfigData() {
        return configData;
    }

    public void setConfigData(String configData) {
        this.configData = configData;
    }

    public String getParamData() {
        return paramData;
    }

    public void setParamData(String paramData) {
        this.paramData = paramData;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public ArrayList<String> getPrimaryUsers() {
        if (primaryUsers == null) {
            primaryUsers = new ArrayList<>();
        }
        return primaryUsers;
    }

    public void setPrimaryUsers(ArrayList<String> primaryUsers) {
        this.primaryUsers = primaryUsers;
    }

    public ArrayList<String> getSecondaryUsers() {
        if (secondaryUsers == null) {
            secondaryUsers = new ArrayList<>();
        }
        return secondaryUsers;
    }

    public void setSecondaryUsers(ArrayList<String> secondaryUsers) {
        this.secondaryUsers = secondaryUsers;
    }

    public int getScheduleMaxCnt() {
        return scheduleMaxCnt;
    }

    public void setScheduleMaxCnt(int scheduleMaxCnt) {
        this.scheduleMaxCnt = scheduleMaxCnt;
    }

    public int getScheduleCurrentCnt() {
        return scheduleCurrentCnt;
    }

    public void setScheduleCurrentCnt(int scheduleCurrentCnt) {
        this.scheduleCurrentCnt = scheduleCurrentCnt;
    }

    public int getSceneMaxCnt() {
        return sceneMaxCnt;
    }

    public void setSceneMaxCnt(int sceneMaxCnt) {
        this.sceneMaxCnt = sceneMaxCnt;
    }

    public int getSceneCurrentCnt() {
        return sceneCurrentCnt;
    }

    public void setSceneCurrentCnt(int sceneCurrentCnt) {
        this.sceneCurrentCnt = sceneCurrentCnt;
    }

    public boolean isMatterNode() {
        return isMatterNode;
    }

    public void setMatterNode(boolean matterNode) {
        isMatterNode = matterNode;
    }

    public String getNewNodeType() {
        return newNodeType;
    }

    public void setNewNodeType(String newNodeType) {
        this.newNodeType = newNodeType;
    }

    public String getMatterNodeId() {
        return matterNodeId;
    }

    public void setMatterNodeId(String matterNodeId) {
        this.matterNodeId = matterNodeId;
    }

    public NodeMetadata getNodeMetadata() {
        return nodeMetadata;
    }

    public void setNodeMetadata(NodeMetadata nodeMetadata) {
        this.nodeMetadata = nodeMetadata;
    }

    public String getNodeMetadataJson() {
        return nodeMetadataJson;
    }

    public void setNodeMetadataJson(String nodeMetadataJson) {
        this.nodeMetadataJson = nodeMetadataJson;
    }

    public boolean isController() {
        return isController;
    }

    public void setController(boolean controller) {
        isController = controller;
    }

    public int getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(int nodeStatus) {
        this.nodeStatus = nodeStatus;
    }

    public ArrayList<String> getSharedGroupIds() {
        if (sharedGroupIds == null) {
            sharedGroupIds = new ArrayList<>();
        }
        return sharedGroupIds;
    }

    public void setSharedGroupIds(ArrayList<String> sharedGroupIds) {
        this.sharedGroupIds = sharedGroupIds;
    }

    protected EspNode(Parcel in) {

        nodeId = in.readString();
        userRole = in.readString();
        configVersion = in.readString();
        nodeName = in.readString();
        fwVersion = in.readString();
        nodeType = in.readString();
        isOnline = in.readByte() != 0;
        timeStampOfStatus = in.readLong();
        devices = in.createTypedArrayList(Device.CREATOR);
        attributes = in.createTypedArrayList(Param.CREATOR);
        services = in.createTypedArrayList(Service.CREATOR);
        isAvailableLocally = in.readByte() != 0;
        ipAddress = in.readString();
        port = in.readInt();
        configData = in.readString();
        paramData = in.readString();
        isSelected = in.readByte() != 0;
        primaryUsers = in.createStringArrayList();
        secondaryUsers = in.createStringArrayList();
        scheduleMaxCnt = in.readInt();
        scheduleCurrentCnt = in.readInt();
        sceneMaxCnt = in.readInt();
        sceneCurrentCnt = in.readInt();
        newNodeType = in.readString();
        isMatterNode = in.readByte() != 0;
        matterNodeId = in.readString();
        nodeMetadata = in.readParcelable(NodeMetadata.class.getClassLoader());
        nodeMetadataJson = in.readString();
        isController = in.readByte() != 0;
        nodeStatus = in.readInt();
        sharedGroupIds = in.createStringArrayList();
    }

    public static final Creator<EspNode> CREATOR = new Creator<EspNode>() {
        @Override
        public EspNode createFromParcel(Parcel in) {
            return new EspNode(in);
        }

        @Override
        public EspNode[] newArray(int size) {
            return new EspNode[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

        dest.writeString(nodeId);
        dest.writeString(userRole);
        dest.writeString(configVersion);
        dest.writeString(nodeName);
        dest.writeString(fwVersion);
        dest.writeString(nodeType);
        dest.writeByte((byte) (isOnline ? 1 : 0));
        dest.writeLong(timeStampOfStatus);
        dest.writeTypedList(devices);
        dest.writeTypedList(attributes);
        dest.writeTypedList(services);
        dest.writeByte((byte) (isAvailableLocally ? 1 : 0));
        dest.writeString(ipAddress);
        dest.writeInt(port);
        dest.writeString(configData);
        dest.writeString(paramData);
        dest.writeByte((byte) (isSelected ? 1 : 0));
        dest.writeStringList(primaryUsers);
        dest.writeStringList(secondaryUsers);
        dest.writeInt(scheduleMaxCnt);
        dest.writeInt(scheduleCurrentCnt);
        dest.writeInt(sceneMaxCnt);
        dest.writeInt(sceneCurrentCnt);
        dest.writeString(newNodeType);
        dest.writeByte((byte) (isMatterNode ? 1 : 0));
        dest.writeString(matterNodeId);
        dest.writeParcelable(nodeMetadata, flags);
        dest.writeString(nodeMetadataJson);
        dest.writeByte((byte) (isController ? 1 : 0));
        dest.writeInt(nodeStatus);
        dest.writeStringList(sharedGroupIds);
    }
}
