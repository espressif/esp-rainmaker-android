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

package com.espressif.ui.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.rainmaker.databinding.ActivityAutomationDetailBinding;
import com.espressif.ui.Utils;
import com.espressif.ui.adapters.AutomationActionListAdapter;
import com.espressif.ui.models.Action;
import com.espressif.ui.models.Automation;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Iterator;

public class AutomationDetailActivity extends AppCompatActivity {

    private final String TAG = AutomationDetailActivity.class.getSimpleName();

    public static final int REQ_CODE_EVENT_DEVICE = 10;
    public static final int REQ_CODE_ACTIONS = 11;
    private String operation = AppConstants.KEY_OPERATION_ADD;

    private TextView tvEventDeviceName, tvEventParamName;
    private ImageView ivEventDevice;

    private MaterialCardView btnRemoveAutomation;
    private TextView txtRemoveAutomationBtn;
    private ImageView removeAutomationImage;
    private ContentLoadingProgressBar progressBar;

    private MenuItem menuSave;
//    private TextView tvActionDevices;

    private Automation automation;
    private AutomationActionListAdapter actionAdapter;
    private EspApplication espApp;
    private ApiManager apiManager;

    private String automationName = "";
    private ArrayList<Action> actions;

    private ActivityAutomationDetailBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAutomationDetailBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        espApp = (EspApplication) getApplicationContext();
        apiManager = ApiManager.getInstance(this);
        actions = new ArrayList<>();
        automation = getIntent().getParcelableExtra(AppConstants.KEY_AUTOMATION);
        automationName = getIntent().getStringExtra(AppConstants.KEY_NAME);
        initViews();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {

            if (data != null) {
                automation = data.getParcelableExtra(AppConstants.KEY_AUTOMATION);
            }

            switch (requestCode) {

                case REQ_CODE_EVENT_DEVICE:
                    updateEventDeviceUi();
                    break;

                case REQ_CODE_ACTIONS:
                    updateActionDevicesUi();
                    break;
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menuSave = menu.add(Menu.NONE, 1, Menu.NONE, R.string.btn_save);
        menuSave.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

//        if (operation.equals(AppConstants.KEY_OPERATION_ADD)) {
//            if (selectedDevices.size() > 0) {
//                menuSave.setVisible(true);
//            } else {
//                menuSave.setVisible(false);
//            }
//        } else {
        menuSave.setVisible(true);
//        }
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case 1:
                updateAutomation();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initViews() {

        setSupportActionBar(binding.toolbarLayout.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(R.string.title_activity_automation_details);
        binding.toolbarLayout.toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        binding.toolbarLayout.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        tvEventDeviceName = findViewById(R.id.tv_device_name);
        tvEventParamName = findViewById(R.id.tv_param_names);
        ivEventDevice = findViewById(R.id.iv_device);

        btnRemoveAutomation = findViewById(R.id.btn_remove_automation);
        txtRemoveAutomationBtn = findViewById(R.id.text_btn);
        removeAutomationImage = findViewById(R.id.iv_remove);
        progressBar = findViewById(R.id.progress_indicator);

        if (automation != null) {

            automationName = automation.getName();
            getSupportActionBar().setTitle(R.string.title_activity_automation_details);

            updateEventDeviceUi();

            btnRemoveAutomation.setVisibility(View.VISIBLE);
            actions = automation.getActions();
        }

        if (TextUtils.isEmpty(automationName)) {
            binding.layoutAutomationDetail.tvAutomationName.setText(R.string.not_set);
        } else {
            binding.layoutAutomationDetail.tvAutomationName.setText(automationName);
        }

        binding.layoutAutomationDetail.rvActionList.setLayoutManager(new LinearLayoutManager(this));
        actionAdapter = new AutomationActionListAdapter(this, actions);
        binding.layoutAutomationDetail.rvActionList.setAdapter(actionAdapter);

        binding.layoutAutomationDetail.rlAutomationName.setOnClickListener(automationNameClickListener);
        binding.layoutAutomationDetail.tvChangeEvent.setOnClickListener(eventClickListener);
        binding.layoutAutomationDetail.tvChangeActions.setOnClickListener(actionsClickListener);
        btnRemoveAutomation.setOnClickListener(removeAutomationBtnClickListener);
    }

    private View.OnClickListener automationNameClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            askForAutomationName();
        }
    };

    private View.OnClickListener eventClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            gotoEventScreen();
        }
    };

    private View.OnClickListener actionsClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            gotoActionsScreen();
        }
    };

    private View.OnClickListener removeAutomationBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            confirmForRemoveAutomation();
        }
    };

    private void askForAutomationName() {

        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_attribute, null);
        final EditText etAutomationName = dialogView.findViewById(R.id.et_attr_value);
        etAutomationName.setInputType(InputType.TYPE_CLASS_TEXT);
        etAutomationName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});
        final AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle(R.string.dialog_title_add_name)
                .setPositiveButton(R.string.btn_ok, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        etAutomationName.setText(automationName);

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {

                Button buttonPositive = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);

                if (TextUtils.isEmpty(automationName) || automationName.length() < 2) {
                    buttonPositive.setEnabled(false);
                } else {
                    buttonPositive.setEnabled(true);
                }

                buttonPositive.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        final String value = etAutomationName.getText().toString();
                        if (!TextUtils.isEmpty(value)) {
                            automationName = value;
                            binding.layoutAutomationDetail.tvAutomationName.setText(automationName);
                            dialog.dismiss();
                        } else {
                            etAutomationName.setError(getString(R.string.error_invalid_automation_name));
                        }
                    }
                });

                Button buttonNegative = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_NEGATIVE);
                buttonNegative.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();
                    }
                });
            }
        });
        alertDialog.show();

        etAutomationName.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {

                if (TextUtils.isEmpty(s) || s.length() < 2) {
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                } else {
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            }
        });
    }

    private void confirmForRemoveAutomation() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_remove);
        builder.setMessage(R.string.dialog_msg_confirmation);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
                removeAutomation();
            }
        });

        builder.setNegativeButton(R.string.btn_no, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        AlertDialog userDialog = builder.create();
        userDialog.show();
    }

    private void updateEventDeviceUi() {

        Device eventDevice = automation.getEventDevice();
        Utils.setDeviceIcon(ivEventDevice, eventDevice.getDeviceType());
        Log.e(TAG, "Event device name : " + eventDevice.getUserVisibleName());
        tvEventDeviceName.setText(eventDevice.getUserVisibleName());
        tvEventParamName.setText(Utils.getEventParamString(eventDevice.getParams(), automation.getCondition()));
    }

    private void updateActionDevicesUi() {
        actionAdapter.updateActionList(automation.getActions());
    }

    private void removeAutomation() {

        if (automation == null) {
            Log.e(TAG, "Automations is null");
            return;
        }
        showRemoveAutomationLoading();
        apiManager.deleteAutomation(automation.getId(), new ApiResponseListener() {
            @Override
            public void onSuccess(Bundle data) {
                hideRemoveAutomationLoading();
                Toast.makeText(AutomationDetailActivity.this, R.string.success_automation_remove, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onResponseFailure(Exception exception) {
                Log.e(TAG, "Failed to remove automation");
                exception.printStackTrace();
                hideRemoveAutomationLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(AutomationDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AutomationDetailActivity.this, R.string.error_automation_remove, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                Log.e(TAG, "Failed to remove automation");
                exception.printStackTrace();
                hideRemoveAutomationLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(AutomationDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AutomationDetailActivity.this, R.string.error_automation_remove, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateAutomation() {

        // Name
        if (TextUtils.isEmpty(automationName)) {
            Toast.makeText(this, R.string.error_automation_name_empty, Toast.LENGTH_LONG).show();
            return;
        }

        showLoading(getString(R.string.progress_update));
        JsonObject automationJson = new JsonObject();
        JsonArray eventArr = new JsonArray();
        JsonArray actionArr = new JsonArray();
        automationJson.addProperty(AppConstants.KEY_NAME, automationName);
        automationJson.addProperty(AppConstants.KEY_ENABLED, true);

        // Events
        Device eventDevice = automation.getEventDevice();

        if (eventDevice != null) {
            automationJson.addProperty(AppConstants.KEY_NODE_ID, eventDevice.getNodeId());

            JsonObject paramJson = Utils.getParamJson(eventDevice);
            JsonObject deviceJson = new JsonObject();
            deviceJson.add(eventDevice.getDeviceName(), paramJson);

            JsonObject eventJson = new JsonObject();
            eventJson.add(AppConstants.KEY_PARAMS, deviceJson);
            eventJson.addProperty(AppConstants.KEY_CHECK, automation.getCondition());

            eventArr.add(eventJson);
            automationJson.add(AppConstants.KEY_EVENTS, eventArr);
        }

        // Actions
        for (int i = 0; i < automation.getActions().size(); i++) {

            Action action = automation.getActions().get(i);
            Device actionDevice = action.getDevice();

            JsonObject paramJson = Utils.getParamJson(actionDevice);
            JsonObject deviceJson = new JsonObject();
            deviceJson.add(actionDevice.getDeviceName(), paramJson);

            JsonObject actionJson = new JsonObject();
            actionJson.add(AppConstants.KEY_PARAMS, deviceJson);
            actionJson.addProperty(AppConstants.KEY_NODE_ID, actionDevice.getNodeId());
            actionArr.add(actionJson);
        }
        automationJson.add(AppConstants.KEY_ACTIONS, actionArr);

        apiManager.updateAutomation(automation, automationJson, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                hideLoading();
                Toast.makeText(AutomationDetailActivity.this, R.string.success_automation_update, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onResponseFailure(Exception exception) {
                hideLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(AutomationDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AutomationDetailActivity.this, R.string.error_automation_update, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                hideLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(AutomationDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AutomationDetailActivity.this, R.string.error_automation_update, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void gotoEventScreen() {

        Intent intent = new Intent(this, EventDeviceActivity.class);
        intent.putExtra(AppConstants.KEY_AUTOMATION, automation);
        intent.putExtra(AppConstants.KEY_OPERATION, AppConstants.KEY_OPERATION_EDIT);
        ArrayList<Device> devices = espApp.getEventDevices();
        intent.putParcelableArrayListExtra(AppConstants.KEY_DEVICES, devices);
        startActivityForResult(intent, REQ_CODE_EVENT_DEVICE);
    }

    private void gotoActionsScreen() {

        Intent intent = new Intent(this, AutomationActionsActivity.class);
        intent.putExtra(AppConstants.KEY_AUTOMATION, automation);
        intent.putExtra(AppConstants.KEY_OPERATION, AppConstants.KEY_OPERATION_EDIT);
        ArrayList<Device> devices = espApp.getEventDevices();

        Iterator deviceIterator = devices.iterator();
        while (deviceIterator.hasNext()) {

            Device device = (Device) deviceIterator.next();
            ArrayList<Param> params = Utils.getWritableParams(device.getParams());

            if (params.size() <= 0) {
                deviceIterator.remove();
            }
        }

        ArrayList<Device> selectedDevices = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            selectedDevices.add(actions.get(i).getDevice());
        }
        intent.putParcelableArrayListExtra(AppConstants.KEY_DEVICES, devices);
        intent.putParcelableArrayListExtra(AppConstants.KEY_SELECTED_DEVICES, selectedDevices);
        startActivityForResult(intent, REQ_CODE_ACTIONS);
    }

    private void showRemoveAutomationLoading() {

        btnRemoveAutomation.setEnabled(false);
        btnRemoveAutomation.setAlpha(0.5f);
        txtRemoveAutomationBtn.setText(R.string.btn_removing);
        progressBar.setVisibility(View.VISIBLE);
        removeAutomationImage.setVisibility(View.GONE);
    }

    public void hideRemoveAutomationLoading() {

        btnRemoveAutomation.setEnabled(true);
        btnRemoveAutomation.setAlpha(1f);
        txtRemoveAutomationBtn.setText(R.string.btn_remove);
        progressBar.setVisibility(View.GONE);
        removeAutomationImage.setVisibility(View.VISIBLE);
    }

    private void showLoading(String msg) {
        binding.layoutAutomationDetail.rlAutomationDetail.setAlpha(0.3f);
        binding.rlProgressAutomation.setVisibility(View.VISIBLE);
        binding.tvLoading.setText(msg);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private void hideLoading() {
        binding.layoutAutomationDetail.rlAutomationDetail.setAlpha(1);
        binding.rlProgressAutomation.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }
}
