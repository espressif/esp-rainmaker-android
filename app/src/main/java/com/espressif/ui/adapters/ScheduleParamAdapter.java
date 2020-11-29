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

package com.espressif.ui.adapters;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.rainmaker.R;
import com.espressif.ui.PaletteBar;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;
import com.warkiz.tickseekbar.OnSeekChangeListener;
import com.warkiz.tickseekbar.SeekParams;
import com.warkiz.tickseekbar.TickSeekBar;

import java.util.ArrayList;

public class ScheduleParamAdapter extends RecyclerView.Adapter<ScheduleParamAdapter.MyViewHolder> {

    private Activity context;

    private Device device;
    private ArrayList<Param> params;
    private ScheduleActionAdapter parentAdapter;

    public ScheduleParamAdapter(Activity context, ScheduleActionAdapter parentAdapter, Device device, ArrayList<Param> params) {
        this.context = context;
        this.device = device;
        this.params = params;
        this.parentAdapter = parentAdapter;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // infalte the item Layout
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_schedule_param, parent, false);
        // set the view's size, margins, paddings and layout parameters
        MyViewHolder vh = new MyViewHolder(v); // pass the view to View Holder
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull final MyViewHolder myViewHolder, int position) {

        Param param = params.get(position);
        myViewHolder.cbParamSelect.setChecked(param.isSelected());

        myViewHolder.cbParamSelect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                params.get(myViewHolder.getAdapterPosition()).setSelected(isChecked);
                params.set(myViewHolder.getAdapterPosition(), params.get(myViewHolder.getAdapterPosition()));

                boolean isSelected = false, isUnselected = false;
                for (int i = 0; i < params.size(); i++) {
                    if (params.get(i).isSelected()) {
                        isSelected = true;
                    } else {
                        isUnselected = true;
                    }
                }

                if (isSelected) {
                    if (isUnselected) {
                        device.setSelectedState(2);
                    } else {
                        device.setSelectedState(1);
                    }
                } else {
                    device.setSelectedState(0);
                }

                device.setParams(params);
                if (parentAdapter != null) {
                    parentAdapter.notifyDataSetChanged();
                }
            }
        });

        if (AppConstants.UI_TYPE_SLIDER.equalsIgnoreCase(param.getUiType())) {

            String dataType = param.getDataType();

            if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("int")
                    || dataType.equalsIgnoreCase("integer")
                    || dataType.equalsIgnoreCase("float")
                    || dataType.equalsIgnoreCase("double"))) {

                int max = param.getMaxBounds();
                int min = param.getMinBounds();

                if ((min < max)) {

                    displaySlider(myViewHolder, param);
                } else {
                    displayLabel(myViewHolder, param, position);
                }
            }

        }else if(AppConstants.UI_TYPE_HUE_SLIDER.equalsIgnoreCase(param.getUiType())){
            displayPalette(myViewHolder, param);
        }
        else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(param.getUiType())) {

            String dataType = param.getDataType();

            if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("bool")
                    || dataType.equalsIgnoreCase("boolean"))) {

                displayToggle(myViewHolder, param);

            } else {
                displayLabel(myViewHolder, param, position);
            }

        } else {
            displayLabel(myViewHolder, param, position);
        }
    }

    @Override
    public int getItemCount() {
        return params.size();
    }

    public void removeItem(int position) {
        params.remove(position);
        // notify the item removed by position
        // to perform recycler view delete animations
        // NOTE: don't call notifyDataSetChanged()
        notifyItemRemoved(position);
    }

    private void displayPalette(MyViewHolder myViewHolder, final Param param) {
        myViewHolder.rvPalette.setVisibility(View.VISIBLE);
        myViewHolder.tvSliderName.setVisibility(View.GONE);
        myViewHolder.intSlider.setVisibility(View.GONE);
        myViewHolder.tvLabelPalette.setText(param.getName());
        myViewHolder.paletteBar.setColor((int) param.getSliderValue());
        myViewHolder.paletteBar.setThumbCircleRadius(12);
        myViewHolder.paletteBar.setTrackMarkHeight(6);
        if (param.getProperties().contains("write")) {

            myViewHolder.paletteBar.setEnabled(true);
            myViewHolder.paletteBar.setListener(new PaletteBar.PaletteBarListener() {
                @Override
                public void onColorSelected(int colorHue) {
                    param.setSliderValue(colorHue);
                }
            });
        }
    }

    private void displaySlider(final MyViewHolder myViewHolder, final Param param) {

        myViewHolder.rvUiTypeSlider.setVisibility(View.VISIBLE);
        myViewHolder.rvUiTypeSwitch.setVisibility(View.GONE);
        myViewHolder.rvUiTypeLabel.setVisibility(View.GONE);

        double sliderValue = param.getSliderValue();
        myViewHolder.tvSliderName.setText(param.getName());
        float max = param.getMaxBounds();
        float min = param.getMinBounds();
        String dataType = param.getDataType();

        if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

            myViewHolder.intSlider.setVisibility(View.VISIBLE);
            myViewHolder.floatSlider.setVisibility(View.GONE);

            myViewHolder.intSlider.setMax(max);
            myViewHolder.intSlider.setMin(min);
            myViewHolder.intSlider.setTickCount(2);

            if (sliderValue < min) {

                myViewHolder.intSlider.setProgress(min);

            } else if (sliderValue > max) {

                myViewHolder.intSlider.setProgress(max);

            } else {
                myViewHolder.intSlider.setProgress((int) sliderValue);
            }

            myViewHolder.intSlider.setOnSeekChangeListener(new OnSeekChangeListener() {

                @Override
                public void onSeeking(SeekParams seekParams) {
                }

                @Override
                public void onStartTrackingTouch(TickSeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(TickSeekBar seekBar) {
                    int progress = seekBar.getProgress();
                    param.setSliderValue(progress);
                }
            });

        } else {

            myViewHolder.intSlider.setVisibility(View.GONE);
            myViewHolder.floatSlider.setVisibility(View.VISIBLE);

            myViewHolder.floatSlider.setMax(max);
            myViewHolder.floatSlider.setMin(min);
            myViewHolder.floatSlider.setTickCount(2);

            if (sliderValue < min) {

                myViewHolder.floatSlider.setProgress(min);

            } else if (sliderValue > max) {

                myViewHolder.floatSlider.setProgress(max);

            } else {
                myViewHolder.floatSlider.setProgress((float) sliderValue);
            }

            myViewHolder.floatSlider.setOnSeekChangeListener(new OnSeekChangeListener() {

                @Override
                public void onSeeking(SeekParams seekParams) {
                }

                @Override
                public void onStartTrackingTouch(TickSeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(TickSeekBar seekBar) {
                    float progress = seekBar.getProgressFloat();
                    param.setSliderValue(progress);
                }
            });
        }
    }

    private void displayToggle(final MyViewHolder myViewHolder, final Param param) {

        myViewHolder.rvUiTypeLabel.setVisibility(View.GONE);
        myViewHolder.rvUiTypeSlider.setVisibility(View.GONE);
        myViewHolder.rvUiTypeSwitch.setVisibility(View.VISIBLE);

        myViewHolder.tvSwitchName.setText(param.getName());
        myViewHolder.tvSwitchStatus.setVisibility(View.VISIBLE);

        if (param.getSwitchStatus()) {

            myViewHolder.tvSwitchStatus.setText(R.string.text_on);

        } else {
            myViewHolder.tvSwitchStatus.setText(R.string.text_off);
        }

        myViewHolder.toggleSwitch.setVisibility(View.VISIBLE);
        myViewHolder.toggleSwitch.setChecked(param.getSwitchStatus());
        myViewHolder.toggleSwitch.setEnabled(true);

        myViewHolder.toggleSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {

                    myViewHolder.tvSwitchStatus.setText(R.string.text_on);

                } else {
                    myViewHolder.tvSwitchStatus.setText(R.string.text_off);
                }
                param.setSwitchStatus(isChecked);
            }
        });
    }

    private void displayLabel(final MyViewHolder myViewHolder, final Param param, final int position) {

        myViewHolder.rvUiTypeSlider.setVisibility(View.GONE);
        myViewHolder.rvUiTypeSwitch.setVisibility(View.GONE);
        myViewHolder.rvUiTypeLabel.setVisibility(View.VISIBLE);

        myViewHolder.tvLabelName.setText(param.getName());
        myViewHolder.tvLabelValue.setText(param.getLabelValue());

        myViewHolder.btnEdit.setVisibility(View.VISIBLE);

        myViewHolder.btnEdit.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                askForNewValue(myViewHolder, param, position);
            }
        });
    }

    private void askForNewValue(final MyViewHolder myViewHolder, final Param param, final int position) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogTheme);
        LayoutInflater inflater = context.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_attribute, null);
        builder.setView(dialogView);

        final EditText etAttribute = dialogView.findViewById(R.id.et_attr_value);

        if (!TextUtils.isEmpty(param.getDataType())) {

            String dataType = param.getDataType();

            if (dataType.equalsIgnoreCase("int")
                    || dataType.equalsIgnoreCase("integer")) {

                etAttribute.setInputType(InputType.TYPE_CLASS_NUMBER);

            } else if (dataType.equalsIgnoreCase("float")
                    || dataType.equalsIgnoreCase("double")) {

                etAttribute.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

            } else if (dataType.equalsIgnoreCase("string")) {

                etAttribute.setInputType(InputType.TYPE_CLASS_TEXT);

                if (param.getParamType() != null && param.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                    etAttribute.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
                }
            }
        }

        if (!TextUtils.isEmpty(param.getLabelValue())) {
            etAttribute.setText(param.getLabelValue());
            etAttribute.setSelection(etAttribute.getText().length());
            etAttribute.requestFocus();
        }

        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                String dataType = param.getDataType();
                final String value = etAttribute.getText().toString();

                if (dataType.equalsIgnoreCase("bool")
                        || dataType.equalsIgnoreCase("boolean")) {

                    if (!TextUtils.isEmpty(value)) {

                        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")
                                || value.equalsIgnoreCase("0") || value.equalsIgnoreCase("1")) {

                            boolean isOn = false;

                            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("1")) {
                                isOn = true;
                            }

                            if (isOn) {

                                myViewHolder.tvLabelValue.setText("true");
                                params.get(position).setLabelValue("true");

                            } else {
                                myViewHolder.tvLabelValue.setText("false");
                                params.get(position).setLabelValue("false");
                            }

                        } else {

                            dialog.dismiss();
                            Toast.makeText(context, R.string.enter_valid_value, Toast.LENGTH_SHORT).show();
                        }
                    } else {

                        dialog.dismiss();
                        Toast.makeText(context, R.string.enter_valid_value, Toast.LENGTH_SHORT).show();
                    }

                } else if (dataType.equalsIgnoreCase("int")
                        || dataType.equalsIgnoreCase("integer")) {

                    int newValue = Integer.valueOf(value);

                    myViewHolder.btnEdit.setVisibility(View.VISIBLE);
                    myViewHolder.progressBar.setVisibility(View.GONE);
                    myViewHolder.tvLabelValue.setText(value);
                    params.get(position).setLabelValue(value);

                } else if (dataType.equalsIgnoreCase("float")
                        || dataType.equalsIgnoreCase("double")) {

                    float newValue = Float.valueOf(value);
                    myViewHolder.btnEdit.setVisibility(View.VISIBLE);
                    myViewHolder.progressBar.setVisibility(View.GONE);
                    myViewHolder.tvLabelValue.setText(value);
                    params.get(position).setLabelValue(value);

                } else {

                    myViewHolder.btnEdit.setVisibility(View.VISIBLE);
                    myViewHolder.progressBar.setVisibility(View.GONE);
                    myViewHolder.tvLabelValue.setText(value);
                    params.get(position).setLabelValue(value);
                }
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {

        // init the item view's
        TickSeekBar intSlider, floatSlider;
        SwitchCompat toggleSwitch;
        TextView tvSliderName, tvSwitchName, tvSwitchStatus, tvLabelName, tvLabelValue, tvLabelPalette;
        RelativeLayout rvUiTypeSlider, rvUiTypeSwitch, rvUiTypeLabel, rvPalette;
        TextView btnEdit;
        ContentLoadingProgressBar progressBar;
        AppCompatCheckBox cbParamSelect;
        PaletteBar paletteBar;

        public MyViewHolder(View itemView) {
            super(itemView);

            // get the reference of item view's
            intSlider = itemView.findViewById(R.id.card_int_slider);
            floatSlider = itemView.findViewById(R.id.card_float_slider);
            toggleSwitch = itemView.findViewById(R.id.card_switch);
            tvSliderName = itemView.findViewById(R.id.slider_name);
            tvSwitchName = itemView.findViewById(R.id.switch_name);
            tvSwitchStatus = itemView.findViewById(R.id.tv_switch_status);
            tvLabelName = itemView.findViewById(R.id.tv_label_name);
            tvLabelValue = itemView.findViewById(R.id.tv_label_value);
            tvLabelPalette = itemView.findViewById(R.id.palette_name);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            progressBar = itemView.findViewById(R.id.progress_indicator);
            cbParamSelect = itemView.findViewById(R.id.cb_param_select);
            rvUiTypeSlider = itemView.findViewById(R.id.rl_card_slider);
            rvUiTypeSwitch = itemView.findViewById(R.id.rl_card_switch);
            rvUiTypeLabel = itemView.findViewById(R.id.rl_card_label);
            rvPalette = itemView.findViewById(R.id.rl_card_palette);
            paletteBar = itemView.findViewById(R.id.rl_palette);
        }
    }
}
