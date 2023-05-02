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
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.aar.tapholdupbutton.TapHoldUpButton;
import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.rainmaker.R;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;
import com.espressif.ui.widgets.EspDropDown;
import com.espressif.ui.widgets.PaletteBar;
import com.warkiz.tickseekbar.OnSeekChangeListener;
import com.warkiz.tickseekbar.SeekParams;
import com.warkiz.tickseekbar.TickSeekBar;

import java.util.ArrayList;

public class AutomationParamAdapter extends RecyclerView.Adapter<AutomationParamAdapter.AutomationParamViewHolder> {

    private final String TAG = AutomationParamAdapter.class.getSimpleName();

    private Activity context;
    private EspApplication espApp;
    private Device device;
    private ArrayList<Param> params;
    private AutomationActionAdapter parentAdapter;

    public AutomationParamAdapter(Activity context, AutomationActionAdapter parentAdapter, Device device, ArrayList<Param> params) {
        this.context = context;
        this.device = device;
        this.params = params;
        this.parentAdapter = parentAdapter;
        espApp = (EspApplication) context.getApplicationContext();
    }

    @Override
    public AutomationParamViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // inflate the item Layout
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_schedule_param, parent, false);
        // set the view's size, margins, paddings and layout parameters
        AutomationParamViewHolder vh = new AutomationParamViewHolder(v); // pass the view to View Holder
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull final AutomationParamViewHolder automationParamVH, int position) {

        Param param = params.get(position);
        automationParamVH.cbParamSelect.setChecked(param.isSelected());

        automationParamVH.cbParamSelect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                params.get(automationParamVH.getAdapterPosition()).setSelected(isChecked);
                params.set(automationParamVH.getAdapterPosition(), params.get(automationParamVH.getAdapterPosition()));

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
                        device.setSelectedState(AppConstants.ACTION_SELECTED_PARTIAL);
                    } else {
                        device.setSelectedState(AppConstants.ACTION_SELECTED_ALL);
                    }
                } else {
                    device.setSelectedState(AppConstants.ACTION_SELECTED_NONE);
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

                    displaySlider(automationParamVH, param);
                } else {
                    displayLabel(automationParamVH, param, position);
                }
            }

        } else if (AppConstants.UI_TYPE_HUE_SLIDER.equalsIgnoreCase(param.getUiType())) {
            displayPalette(automationParamVH, param);
        } else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(param.getUiType())) {

            String dataType = param.getDataType();

            if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("bool")
                    || dataType.equalsIgnoreCase("boolean"))) {

                displayToggle(automationParamVH, param);

            } else {
                displayLabel(automationParamVH, param, position);
            }

        } else if (AppConstants.UI_TYPE_TRIGGER.equalsIgnoreCase(param.getUiType())) {

            String dataType = param.getDataType();

            if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("bool")
                    || dataType.equalsIgnoreCase("boolean"))) {

                displayTrigger(automationParamVH, param);

            } else {
                displayLabel(automationParamVH, param, position);
            }

        } else if (AppConstants.UI_TYPE_DROP_DOWN.equalsIgnoreCase(param.getUiType())) {

            String dataType = param.getDataType();

            if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("string"))) {

                displaySpinner(automationParamVH, param, position);

            } else if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("int")
                    || dataType.equalsIgnoreCase("integer"))) {

                int max = param.getMaxBounds();
                int min = param.getMinBounds();
                int stepCount = (int) param.getStepCount();
                if (stepCount == 0) {
                    stepCount = 1;
                }
                ArrayList<String> spinnerValues = new ArrayList<>();
                for (int i = min; i <= max; i = i + stepCount) {
                    spinnerValues.add(String.valueOf(i));
                }

                if ((min < max)) {
                    displaySpinner(automationParamVH, param, position);
                } else {
                    if (spinnerValues.size() > 0) {
                        displaySpinner(automationParamVH, param, position);
                    } else {
                        displayLabel(automationParamVH, param, position);
                    }
                }
            } else {
                displayLabel(automationParamVH, param, position);
            }
        } else {
            displayLabel(automationParamVH, param, position);
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

    private void displayPalette(AutomationParamViewHolder automationParamVH, final Param param) {

        automationParamVH.rlUiTypeSlider.setVisibility(View.GONE);
        automationParamVH.rlUiTypeSwitch.setVisibility(View.GONE);
        automationParamVH.rlUiTypeLabel.setVisibility(View.GONE);
        automationParamVH.rlUiTypeDropDown.setVisibility(View.GONE);
        automationParamVH.rlUiTypeTrigger.setVisibility(View.GONE);
        automationParamVH.rlPalette.setVisibility(View.VISIBLE);

        automationParamVH.tvSliderName.setVisibility(View.GONE);
        automationParamVH.intSlider.setVisibility(View.GONE);
        automationParamVH.tvLabelPalette.setText(param.getName());
        automationParamVH.paletteBar.setColor((int) param.getValue());
        automationParamVH.paletteBar.setThumbCircleRadius(12);
        automationParamVH.paletteBar.setTrackMarkHeight(6);

        float max = param.getMaxBounds();
        float min = param.getMinBounds();
        automationParamVH.tvMinHue.setText(String.valueOf((int) min));
        automationParamVH.tvMaxHue.setText(String.valueOf((int) max));

        if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {

            automationParamVH.paletteBar.setListener(new PaletteBar.PaletteBarListener() {
                @Override
                public void onColorSelected(int colorHue,boolean isMoving) {
                    param.setValue(colorHue);
                    param.setLabelValue(String.valueOf(colorHue));
                }
            });
        }
    }

    private void displaySlider(final AutomationParamViewHolder automationParamVH, final Param param) {

        automationParamVH.rlUiTypeSlider.setVisibility(View.VISIBLE);
        automationParamVH.rlUiTypeSwitch.setVisibility(View.GONE);
        automationParamVH.rlUiTypeLabel.setVisibility(View.GONE);
        automationParamVH.rlUiTypeDropDown.setVisibility(View.GONE);
        automationParamVH.rlUiTypeTrigger.setVisibility(View.GONE);
        automationParamVH.rlPalette.setVisibility(View.GONE);

        double sliderValue = param.getValue();
        automationParamVH.tvSliderName.setVisibility(View.VISIBLE);
        automationParamVH.tvSliderName.setText(param.getName());
        float max = param.getMaxBounds();
        float min = param.getMinBounds();
        String dataType = param.getDataType();

        if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

            automationParamVH.intSlider.setVisibility(View.VISIBLE);
            automationParamVH.floatSlider.setVisibility(View.GONE);

            automationParamVH.intSlider.setMax(max);
            automationParamVH.intSlider.setMin(min);
            automationParamVH.intSlider.setTickCount(2);

            if (sliderValue < min) {
                automationParamVH.intSlider.setProgress(min);
            } else if (sliderValue > max) {
                automationParamVH.intSlider.setProgress(max);
            } else {
                automationParamVH.intSlider.setProgress((int) sliderValue);
            }

            automationParamVH.intSlider.setOnSeekChangeListener(new OnSeekChangeListener() {

                @Override
                public void onSeeking(SeekParams seekParams) {
                }

                @Override
                public void onStartTrackingTouch(TickSeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(TickSeekBar seekBar) {
                    int progress = seekBar.getProgress();
                    param.setValue(progress);
                    param.setLabelValue(String.valueOf(progress));
                }
            });

        } else {

            automationParamVH.intSlider.setVisibility(View.GONE);
            automationParamVH.floatSlider.setVisibility(View.VISIBLE);

            automationParamVH.floatSlider.setMax(max);
            automationParamVH.floatSlider.setMin(min);
            automationParamVH.floatSlider.setTickCount(2);

            if (sliderValue < min) {
                automationParamVH.floatSlider.setProgress(min);
            } else if (sliderValue > max) {
                automationParamVH.floatSlider.setProgress(max);
            } else {
                automationParamVH.floatSlider.setProgress((float) sliderValue);
            }

            automationParamVH.floatSlider.setOnSeekChangeListener(new OnSeekChangeListener() {

                @Override
                public void onSeeking(SeekParams seekParams) {
                }

                @Override
                public void onStartTrackingTouch(TickSeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(TickSeekBar seekBar) {
                    float progress = seekBar.getProgressFloat();
                    param.setValue(progress);
                    param.setLabelValue(String.valueOf(progress));
                }
            });
        }
    }

    private void displayToggle(final AutomationParamViewHolder automationParamVH, Param param) {

        automationParamVH.rlUiTypeSlider.setVisibility(View.GONE);
        automationParamVH.rlUiTypeSwitch.setVisibility(View.VISIBLE);
        automationParamVH.rlUiTypeLabel.setVisibility(View.GONE);
        automationParamVH.rlUiTypeDropDown.setVisibility(View.GONE);
        automationParamVH.rlUiTypeTrigger.setVisibility(View.GONE);
        automationParamVH.rlPalette.setVisibility(View.GONE);

        automationParamVH.tvSwitchName.setText(param.getName());
        automationParamVH.tvSwitchStatus.setVisibility(View.VISIBLE);

        if (param.getSwitchStatus()) {
            automationParamVH.tvSwitchStatus.setText(R.string.text_on);
        } else {
            automationParamVH.tvSwitchStatus.setText(R.string.text_off);
        }

        automationParamVH.toggleSwitch.setVisibility(View.VISIBLE);
        automationParamVH.toggleSwitch.setChecked(param.getSwitchStatus());
        automationParamVH.toggleSwitch.setEnabled(true);

        automationParamVH.toggleSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if (isChecked) {
                    automationParamVH.tvSwitchStatus.setText(R.string.text_on);
                } else {
                    automationParamVH.tvSwitchStatus.setText(R.string.text_off);
                }
                param.setSwitchStatus(isChecked);
                if (isChecked) {
                    param.setLabelValue("true");
                } else {
                    param.setLabelValue("false");
                }
            }
        });
    }

    private void displayTrigger(final AutomationParamViewHolder automationParamVH, final Param param) {

        automationParamVH.rlUiTypeSlider.setVisibility(View.GONE);
        automationParamVH.rlUiTypeSwitch.setVisibility(View.GONE);
        automationParamVH.rlUiTypeLabel.setVisibility(View.GONE);
        automationParamVH.rlUiTypeDropDown.setVisibility(View.GONE);
        automationParamVH.rlUiTypeTrigger.setVisibility(View.VISIBLE);
        automationParamVH.rlPalette.setVisibility(View.GONE);

        automationParamVH.tvTriggerName.setText(param.getName());
        automationParamVH.btnTrigger.setEnabled(false);
        automationParamVH.btnTrigger.setClickable(false);
        automationParamVH.btnTrigger.enableLongHold(false);
        automationParamVH.btnTrigger.setOnClickListener(null);
        automationParamVH.btnTrigger.setOnButtonClickListener(null);

        param.setSwitchStatus(true);
        param.setLabelValue("true");
    }

    private void displayLabel(final AutomationParamViewHolder automationParamVH, final Param param, final int position) {

        automationParamVH.rlUiTypeSlider.setVisibility(View.GONE);
        automationParamVH.rlUiTypeSwitch.setVisibility(View.GONE);
        automationParamVH.rlUiTypeLabel.setVisibility(View.VISIBLE);
        automationParamVH.rlUiTypeDropDown.setVisibility(View.GONE);
        automationParamVH.rlUiTypeTrigger.setVisibility(View.GONE);
        automationParamVH.rlPalette.setVisibility(View.GONE);

        automationParamVH.tvLabelName.setText(param.getName());
        automationParamVH.tvLabelValue.setText(param.getLabelValue());

        automationParamVH.btnEdit.setVisibility(View.VISIBLE);

        automationParamVH.btnEdit.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                askForNewValue(automationParamVH, param, position);
            }
        });
    }

    private void displaySpinner(final AutomationParamViewHolder automationParamVH, final Param param, final int position) {

        automationParamVH.rlUiTypeSlider.setVisibility(View.GONE);
        automationParamVH.rlUiTypeSwitch.setVisibility(View.GONE);
        automationParamVH.rlUiTypeLabel.setVisibility(View.GONE);
        automationParamVH.rlUiTypeDropDown.setVisibility(View.VISIBLE);
        automationParamVH.rlUiTypeTrigger.setVisibility(View.GONE);
        automationParamVH.rlPalette.setVisibility(View.GONE);

        automationParamVH.tvSpinnerName.setText(param.getName());
        automationParamVH.spinner.setVisibility(View.VISIBLE);

        automationParamVH.spinner.setEnabled(false);
        automationParamVH.spinner.setOnItemSelectedListener(null);

        final String dataType = param.getDataType();

        if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("string"))) {

            if (param.getValidStrings() != null && param.getValidStrings().size() > 0) {

                ArrayList<String> spinnerValues = new ArrayList<>();
                spinnerValues.addAll(param.getValidStrings());
                ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, spinnerValues);
                dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                automationParamVH.spinner.setAdapter(dataAdapter);
                boolean isValueFound = false;
                String value = param.getLabelValue();

                for (int i = 0; i < param.getValidStrings().size(); i++) {

                    if (!TextUtils.isEmpty(value)) {

                        if (value.equals(param.getValidStrings().get(i))) {
                            isValueFound = true;
                            automationParamVH.spinner.setSelection(i, false);
                            automationParamVH.spinner.setTag(R.id.position, i);
                            break;
                        }
                    }
                }

                if (!isValueFound) {
                    spinnerValues.add(0, "");
                    automationParamVH.spinner.setSelection(0, false);
                    automationParamVH.spinner.setTag(R.id.position, 0);
                    dataAdapter.notifyDataSetChanged();
                    String strInvalidValue = "" + value + " (" + context.getString(R.string.invalid) + ")";
                    automationParamVH.tvSpinnerValue.setText(strInvalidValue);
                    automationParamVH.tvSpinnerValue.setVisibility(View.VISIBLE);
                } else {
                    automationParamVH.tvSpinnerValue.setVisibility(View.GONE);
                }
            }
        } else if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("int")
                || dataType.equalsIgnoreCase("integer"))) {

            int sliderValue = (int) param.getValue();
            int max = param.getMaxBounds();
            int min = param.getMinBounds();
            int stepCount = (int) param.getStepCount();
            if (stepCount == 0) {
                stepCount = 1;
            }

            ArrayList<String> spinnerValues = new ArrayList<>();
            for (int i = min; i <= max; i = i + stepCount) {
                spinnerValues.add(String.valueOf(i));
            }

            if (spinnerValues.size() > 0) {

                ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, spinnerValues);
                dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                automationParamVH.spinner.setAdapter(dataAdapter);

                if (sliderValue < min) {

                    automationParamVH.spinner.setSelection(0);
                    automationParamVH.spinner.setTag(R.id.position, 0);

                } else if (sliderValue > max) {

                    int lastPosition = spinnerValues.size() - 1;
                    automationParamVH.spinner.setSelection(lastPosition);
                    automationParamVH.spinner.setTag(R.id.position, lastPosition);

                } else {

                    boolean isValueFound = false;
                    for (int i = 0; i < spinnerValues.size(); i++) {

                        String value = spinnerValues.get(i);
                        int intValue = Integer.parseInt(value);

                        if (sliderValue == intValue) {
                            isValueFound = true;
                            automationParamVH.spinner.setSelection(i, false);
                            automationParamVH.spinner.setTag(R.id.position, i);
                            break;
                        }
                    }

                    if (!isValueFound) {
                        spinnerValues.add(0, "");
                        automationParamVH.spinner.setSelection(0, false);
                        automationParamVH.spinner.setTag(R.id.position, 0);
                        dataAdapter.notifyDataSetChanged();
                        String strInvalidValue = "" + sliderValue + " (" + context.getString(R.string.invalid) + ")";
                        automationParamVH.tvSpinnerValue.setText(strInvalidValue);
                        automationParamVH.tvSpinnerValue.setVisibility(View.VISIBLE);
                    } else {
                        automationParamVH.tvSpinnerValue.setVisibility(View.GONE);
                    }
                }
            } else {
                // Displayed label for this condition.
            }
        }

        if (param.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {

            automationParamVH.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, final int pos, long id) {

                    Log.d(TAG, "Dropdown list item clicked position :  " + automationParamVH.spinner.getTag(R.id.position));

                    if ((int) automationParamVH.spinner.getTag(R.id.position) != pos) {

                        final String newValue = parent.getItemAtPosition(pos).toString();
                        automationParamVH.spinner.setTag(R.id.position, pos);

                        if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("string"))) {

                            params.get(position).setLabelValue(newValue);
                            automationParamVH.tvSpinnerValue.setVisibility(View.GONE);

                        } else if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("int")
                                || dataType.equalsIgnoreCase("integer"))) {

                            params.get(position).setValue(Integer.parseInt(newValue));
                            param.setLabelValue(newValue);
                            automationParamVH.tvSpinnerValue.setVisibility(View.GONE);
                        }
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            automationParamVH.spinner.setEnabled(true);

        } else {
            automationParamVH.spinner.setOnItemSelectedListener(null);
        }
    }

    private void askForNewValue(final AutomationParamViewHolder automationParamVH, final Param param, final int position) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = context.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_attribute, null);
        builder.setView(dialogView);

        final EditText etAttribute = dialogView.findViewById(R.id.et_attr_value);

        if (!TextUtils.isEmpty(param.getDataType())) {

            String dataType = param.getDataType();

            if (dataType.equalsIgnoreCase("int")
                    || dataType.equalsIgnoreCase("integer")) {

                etAttribute.setInputType(InputType.TYPE_CLASS_NUMBER);
                etAttribute.setText(String.valueOf((int) param.getValue()));

            } else if (dataType.equalsIgnoreCase("float")
                    || dataType.equalsIgnoreCase("double")) {

                etAttribute.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                etAttribute.setText(String.valueOf(param.getValue()));

            } else if (dataType.equalsIgnoreCase("string")) {

                etAttribute.setInputType(InputType.TYPE_CLASS_TEXT);
                if (!TextUtils.isEmpty(param.getLabelValue())) {
                    etAttribute.setText(param.getLabelValue());
                }

                if (param.getParamType() != null && param.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                    etAttribute.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
                }
            }
        }

        etAttribute.setSelection(etAttribute.getText().length());
        etAttribute.requestFocus();

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
                                automationParamVH.tvLabelValue.setText("true");
                                params.get(position).setLabelValue("true");
                            } else {
                                automationParamVH.tvLabelValue.setText("false");
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
                    int max = param.getMaxBounds();
                    int min = param.getMinBounds();

                    if (min != max) {
                        if (newValue < min || newValue > max) {
                            Log.e(TAG, "New value is out of bounds");
                            Toast.makeText(context, context.getString(R.string.error_value_out_of_bound), Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    automationParamVH.btnEdit.setVisibility(View.VISIBLE);
                    automationParamVH.tvLabelValue.setText(value);
                    params.get(position).setValue(newValue);
                    param.setLabelValue(String.valueOf(newValue));

                } else if (dataType.equalsIgnoreCase("float")
                        || dataType.equalsIgnoreCase("double")) {

                    float newValue = Float.valueOf(value);
                    float max = param.getMaxBounds();
                    float min = param.getMinBounds();

                    if (min != max) {
                        if (newValue < min || newValue > max) {
                            Log.e(TAG, "New value is out of bounds");
                            Toast.makeText(context, context.getString(R.string.error_value_out_of_bound), Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    automationParamVH.btnEdit.setVisibility(View.VISIBLE);
                    automationParamVH.tvLabelValue.setText(value);
                    params.get(position).setValue(newValue);
                    param.setLabelValue(String.valueOf(newValue));

                } else {

                    automationParamVH.btnEdit.setVisibility(View.VISIBLE);
                    automationParamVH.tvLabelValue.setText(value);
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

    static class AutomationParamViewHolder extends RecyclerView.ViewHolder {

        // init the item view's
        TickSeekBar intSlider, floatSlider;
        SwitchCompat toggleSwitch;
        TextView tvSliderName, tvSwitchName, tvSwitchStatus, tvLabelName, tvLabelValue, tvLabelPalette,
                tvSpinnerName, tvSpinnerValue, tvTriggerName;
        RelativeLayout rlUiTypeSlider, rlUiTypeSwitch, rlUiTypeLabel, rlPalette, rlUiTypeDropDown, rlUiTypeTrigger;
        TextView btnEdit;
        AppCompatCheckBox cbParamSelect;
        PaletteBar paletteBar;
        EspDropDown spinner;
        TapHoldUpButton btnTrigger;
        TextView tvMinHue, tvMaxHue;

        public AutomationParamViewHolder(View itemView) {
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
            tvSpinnerName = itemView.findViewById(R.id.tv_spinner_name);
            tvSpinnerValue = itemView.findViewById(R.id.tv_spinner_value);
            tvTriggerName = itemView.findViewById(R.id.tv_trigger_name);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            cbParamSelect = itemView.findViewById(R.id.cb_param_select);
            rlUiTypeSlider = itemView.findViewById(R.id.rl_card_slider);
            rlUiTypeSwitch = itemView.findViewById(R.id.rl_card_switch);
            rlUiTypeLabel = itemView.findViewById(R.id.rl_card_label);
            rlPalette = itemView.findViewById(R.id.rl_card_palette);
            rlUiTypeDropDown = itemView.findViewById(R.id.rl_card_drop_down);
            rlUiTypeTrigger = itemView.findViewById(R.id.rl_card_trigger);
            paletteBar = itemView.findViewById(R.id.rl_palette);
            spinner = itemView.findViewById(R.id.card_spinner);
            tvSpinnerValue.setVisibility(View.GONE);
            btnTrigger = itemView.findViewById(R.id.btn_trigger);
            tvMinHue = itemView.findViewById(R.id.tv_palette_start);
            tvMaxHue = itemView.findViewById(R.id.tv_palette_end);
        }
    }
}
