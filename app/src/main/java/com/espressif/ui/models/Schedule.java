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

import com.espressif.AppConstants;

import java.util.ArrayList;
import java.util.HashMap;

public class Schedule implements Parcelable {

    private String id;
    private String name;
    private boolean isEnabled;
    private ArrayList<Action> actions;
    private HashMap<String, Integer> triggers;
    private int daylightType;
    private int daylightOffset;

    public Schedule() {
    }

    protected Schedule(Parcel in) {
        id = in.readString();
        name = in.readString();
        isEnabled = in.readByte() != 0;
        actions = in.createTypedArrayList(Action.CREATOR);
        triggers = (HashMap<String, Integer>) in.readSerializable();
        daylightType = in.readInt();
        daylightOffset = in.readInt();
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

    public ArrayList<Action> getActions() {
        return actions;
    }

    public void setActions(ArrayList<Action> actions) {
        this.actions = actions;
    }

    public HashMap<String, Integer> getTriggers() {
        return triggers;
    }

    public void setTriggers(HashMap<String, Integer> triggers) {
        this.triggers = triggers;
    }

    /**
     * @return true when the schedule fires relative to sunrise or sunset rather than at a
     * fixed time. Such a trigger carries fields (sr/ss, lat, lon, loc) that this app does
     * not fully model, so it must not be rewritten from the time picker.
     */
    public boolean isDaylightTrigger() {
        return daylightType != AppConstants.DAYLIGHT_TRIGGER_NONE;
    }

    /**
     * @return one of AppConstants.DAYLIGHT_TRIGGER_SUNRISE, DAYLIGHT_TRIGGER_SUNSET or
     * DAYLIGHT_TRIGGER_NONE.
     */
    public int getDaylightType() {
        return daylightType;
    }

    public void setDaylightType(int daylightType) {
        this.daylightType = daylightType;
    }

    /**
     * @return minutes offset from the daylight event. Positive is after the event, negative
     * before it, zero exactly at it.
     */
    public int getDaylightOffset() {
        return daylightOffset;
    }

    public void setDaylightOffset(int daylightOffset) {
        this.daylightOffset = daylightOffset;
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
        dest.writeTypedList(actions);
        dest.writeSerializable(triggers);
        dest.writeInt(daylightType);
        dest.writeInt(daylightOffset);
    }

    public static final Creator<Schedule> CREATOR = new Creator<Schedule>() {
        @Override
        public Schedule createFromParcel(Parcel in) {
            return new Schedule(in);
        }

        @Override
        public Schedule[] newArray(int size) {
            return new Schedule[size];
        }
    };
}
