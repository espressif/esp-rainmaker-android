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

package com.espressif.ui.widgets;

import android.content.Context;
import android.util.Log;
import android.widget.TextView;

import com.espressif.rainmaker.R;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

public class EspMarkerView extends MarkerView {

    private TextView tvValue;

    public EspMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        // this marker view only displays a textview
        tvValue = findViewById(R.id.tv_ts_data_pt);
    }

    // callbacks everytime the MarkerView is redrawn, can be used to update the
    // content (user-interface)
    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        String s = "X : " + e.getX() + ", Y : " + e.getY();
        Log.e("TAG", "Marker text : " + s);
        float value = e.getY();
        String str = String.format("%.2f", value);

        if (e.getData() != null) {
            str = str + "\n" + e.getData();
        }
        tvValue.setText(str); // set the entry-value as the display text
        // this will perform necessary layouting
        super.refreshContent(e, highlight);
    }

    private MPPointF mOffset;

    @Override
    public MPPointF getOffset() {

        if (mOffset == null) {
            // center the marker horizontally and vertically
            mOffset = new MPPointF(-(getWidth() / 2), -getHeight());
        }
        return mOffset;
    }
}
