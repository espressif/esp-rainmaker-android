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

public class TsData implements Parcelable {

    private long timeStamp;
    private Object value;

    public TsData(long timeStamp, Object value) {
        this.timeStamp = timeStamp;
        this.value = value;
    }

    protected TsData(Parcel in) {
        timeStamp = in.readLong();
        value = in.readValue(Object.class.getClassLoader());
    }

    public static final Creator<TsData> CREATOR = new Creator<TsData>() {
        @Override
        public TsData createFromParcel(Parcel in) {
            return new TsData(in);
        }

        @Override
        public TsData[] newArray(int size) {
            return new TsData[size];
        }
    };

    public long getTimeStamp() {
        return timeStamp;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(timeStamp);
        dest.writeValue(value);
    }
}
