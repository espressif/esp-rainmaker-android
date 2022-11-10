// Copyright 2022 Espressif Systems (Shanghai) PTE LTD
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

public class Automation implements Parcelable, Comparable {

    private String id;
    private String name;
    private boolean isEnabled;
    private String nodeId;
    private ArrayList<Action> actions;
    private Device eventDevice;
    private String condition;

    public Automation() {
    }

    protected Automation(Parcel in) {
        id = in.readString();
        name = in.readString();
        isEnabled = in.readByte() != 0;
        nodeId = in.readString();
        actions = in.createTypedArrayList(Action.CREATOR);
        eventDevice = in.readParcelable(Device.class.getClassLoader());
        condition = in.readString();
    }

    public Automation(Automation automation) {
        id = automation.getId();
        name = automation.getName();
        isEnabled = automation.isEnabled();
        nodeId = automation.getNodeId();
        actions = automation.getActions();
        eventDevice = automation.getEventDevice();
        condition = automation.getCondition();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public ArrayList<Action> getActions() {
        return actions;
    }

    public void setActions(ArrayList<Action> actions) {
        this.actions = actions;
    }

    public Device getEventDevice() {
        return eventDevice;
    }

    public void setEventDevice(Device eventDevice) {
        this.eventDevice = eventDevice;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeByte((byte) (isEnabled ? 1 : 0));
        dest.writeString(nodeId);
        dest.writeTypedList(actions);
        dest.writeParcelable(eventDevice, flags);
        dest.writeString(condition);
    }

    public static final Creator<Automation> CREATOR = new Creator<Automation>() {
        @Override
        public Automation createFromParcel(Parcel in) {
            return new Automation(in);
        }

        @Override
        public Automation[] newArray(int size) {
            return new Automation[size];
        }
    };

    @Override
    public int compareTo(Object o) {
        Automation compare = (Automation) o;
        if (compare.id != null && this.id != null && !compare.id.equals(this.id)) {
            return 1;
        } else if (compare.name != null && this.name != null && !compare.name.equals(this.name)) {
            return 1;
        } else if (compare.nodeId != null && this.nodeId != null && !compare.nodeId.equals(this.nodeId)) {
            return 1;
        } else if (compare.actions != null && this.actions != null && !compare.actions.equals(this.actions)) {
            return 1;
        } else if (compare.eventDevice != null && this.eventDevice != null && !compare.eventDevice.equals(this.eventDevice)) {
            return 1;
        } else if (compare.condition != null && this.condition != null && !compare.condition.equals(this.condition)) {
            return 1;
        } else if (compare.isEnabled == this.isEnabled) {
            return 0;
        }
        return 1;
    }
}
