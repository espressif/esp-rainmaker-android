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

package com.espressif.ui.adapters;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.rainmaker.R;
import com.espressif.ui.EventSelectionListener;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;
import com.google.android.material.button.MaterialButton;
import com.warkiz.tickseekbar.TickSeekBar;

import java.util.ArrayList;

import info.hoang8f.android.segmented.SegmentedGroup;

public class ParamSelectionAdapter extends RecyclerView.Adapter<ParamSelectionAdapter.ParamViewHolder> {

    private Activity context;
    private ArrayList<Param> params;
    private EventSelectionListener eventSelectionListener;
    private Device eventDevice;

    public ParamSelectionAdapter(Activity context, Device device, EventSelectionListener eventSelectionListener) {
        this.context = context;
        eventDevice = device;
        this.params = device.getParams();
        this.eventSelectionListener = eventSelectionListener;
    }

    @Override
    public ParamViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View view = layoutInflater.inflate(R.layout.item_param_selection, parent, false);
        return new ParamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ParamViewHolder paramItemVH, final int position) {

        paramItemVH.tvParam.setText(params.get(position).getName());

        paramItemVH.itemView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                int position = paramItemVH.getAdapterPosition();
                Param param = params.get(position);
                askForEventCondition(param);
            }
        });
    }

    private void askForEventCondition(Param param) {

        String uiType = param.getUiType();
        String dataType = param.getDataType();

        final boolean isNumber = dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")
                || dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double");

        if (AppConstants.UI_TYPE_SLIDER.equalsIgnoreCase(uiType)) {

            if (isNumber) {
                displaySliderDialog(param);
            } else {
                displayIntDialog(param);
            }

        } else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(uiType)) {

            displayBooleanDialog(param);

        } else if (AppConstants.UI_TYPE_TRIGGER.equalsIgnoreCase(uiType)) {

            displayBooleanDialog(param);

        } else if (AppConstants.UI_TYPE_DROP_DOWN.equalsIgnoreCase(uiType)) {

            displayStringDialog(param);

        } else {

            if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {

                displayBooleanDialog(param);

            } else if (isNumber) {

                displayIntDialog(param);

            } else {

                displayStringDialog(param);
            }
        }
    }

    private void displayBooleanDialog(Param param) {
        LayoutInflater inflater = context.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_param_boolean, null);

        final AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setTitle(param.getName())
                .create();

        SwitchCompat switchCompat = dialogView.findViewById(R.id.param_switch);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        switchCompat.setChecked(param.getSwitchStatus());

        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                boolean isChecked = switchCompat.isChecked();
                String condition = "==";
                param.setSwitchStatus(isChecked);
                param.setSelected(true);
                eventSelectionListener.onEventSelected(eventDevice, param, condition);
                alertDialog.dismiss();
            }
        });

        alertDialog.show();
    }

    private void displaySliderDialog(Param param) {
        LayoutInflater inflater = context.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_param_slider, null);

        final AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setTitle(param.getName())
                .create();

        TickSeekBar slider = dialogView.findViewById(R.id.param_slider);
        SegmentedGroup sgCondition = dialogView.findViewById(R.id.sg_condition);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm);

        double sliderValue = param.getValue();
        float max = param.getMaxBounds();
        float min = param.getMinBounds();

        slider.setMax(max);
        slider.setMin(min);
        slider.setTickCount(2);

        if (sliderValue < min) {
            slider.setProgress(min);
        } else if (sliderValue > max) {
            slider.setProgress(max);
        } else {
            slider.setProgress((int) sliderValue);
        }

        btnConfirm.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                String condition = "==";
                int btnId = sgCondition.getCheckedRadioButtonId();
                switch (btnId) {
                    case R.id.radio_btn_greater:
                        condition = ">";
                        break;
                    case R.id.radio_btn_lesser:
                        condition = "<";
                        break;
                }

                int value = slider.getProgress();
                param.setValue(value);
                param.setLabelValue(String.valueOf(value));
                param.setSelected(true);
                eventSelectionListener.onEventSelected(eventDevice, param, condition);
                alertDialog.dismiss();
            }
        });

        alertDialog.show();
    }

    private void displayIntDialog(Param param) {
        LayoutInflater inflater = context.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_param_int, null);

        final AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setTitle(param.getName())
                .create();

        AppCompatEditText editText = dialogView.findViewById(R.id.param_edit_text);
        SegmentedGroup sgCondition = dialogView.findViewById(R.id.sg_condition);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        editText.setText(param.getLabelValue());

        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String condition = "==";
                int btnId = sgCondition.getCheckedRadioButtonId();
                switch (btnId) {
                    case R.id.radio_btn_greater:
                        condition = ">";
                        break;
                    case R.id.radio_btn_lesser:
                        condition = "<";
                        break;
                }

                String labelValue = editText.getText().toString();
                double value = Double.valueOf(labelValue);
                param.setValue(value);
                param.setLabelValue(labelValue);
                param.setSelected(true);
                eventSelectionListener.onEventSelected(eventDevice, param, condition);
                alertDialog.dismiss();
            }
        });

        alertDialog.show();
    }

    private void displayStringDialog(Param param) {
        LayoutInflater inflater = context.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_param_string, null);

        final AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setTitle(param.getName())
                .create();

        AppCompatEditText editText = dialogView.findViewById(R.id.param_edit_text);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        editText.setText(param.getLabelValue());

        btnConfirm.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                String condition = "==";
                String value = editText.getText().toString();
                param.setLabelValue(value);
                param.setSelected(true);
                eventSelectionListener.onEventSelected(eventDevice, param, condition);
                alertDialog.dismiss();
            }
        });

        alertDialog.show();
    }

    @Override
    public int getItemCount() {
        return params.size();
    }

    static class ParamViewHolder extends RecyclerView.ViewHolder {

        TextView tvParam;

        public ParamViewHolder(View itemView) {
            super(itemView);

            tvParam = itemView.findViewById(R.id.tv_item_name);
            itemView.setTag(this);
        }
    }
}
