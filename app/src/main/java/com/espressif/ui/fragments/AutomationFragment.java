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

package com.espressif.ui.fragments;

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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.rainmaker.R;
import com.espressif.ui.activities.EspMainActivity;
import com.espressif.ui.activities.EventDeviceActivity;
import com.espressif.ui.adapters.AutomationAdapter;
import com.espressif.ui.models.Automation;
import com.espressif.ui.models.Device;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class AutomationFragment extends Fragment {

    private static final String TAG = AutomationFragment.class.getSimpleName();

    private MaterialCardView btnAddAutomation;
    private TextView txtAddAutomationBtn;
    private ImageView arrowImage;

    private RecyclerView rvAutomation;
    private TextView tvNoAutomation, tvAddAutomation;
    private RelativeLayout rlNoAutomation, rlProgressAutomation;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageView ivNoAutomation;

    private EspApplication espApp;
    private ApiManager apiManager;
    private AutomationAdapter automationAdapter;
    private ArrayList<Automation> automations;
    private boolean isGettingAutomations = true;

    public AutomationFragment() {
        // Required empty public constructor
    }

    public static AutomationFragment newInstance() {
        return new AutomationFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_automations, container, false);
        automations = new ArrayList<>();
        espApp = (EspApplication) getActivity().getApplicationContext();
        apiManager = ApiManager.getInstance(espApp);
        init(root);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        getAutomations();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    View.OnClickListener addAutomationBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            askForAutomationName();
        }
    };

    public void updateAutomationList() {
        swipeRefreshLayout.setRefreshing(true);
        getAutomations();
    }

    private void init(View view) {

        rlNoAutomation = view.findViewById(R.id.rl_no_automation);
        rlProgressAutomation = view.findViewById(R.id.rl_progress_automation);
        swipeRefreshLayout = view.findViewById(R.id.swipe_container);
        rvAutomation = view.findViewById(R.id.rv_automation_list);

        tvNoAutomation = view.findViewById(R.id.tv_no_automation);
        tvAddAutomation = view.findViewById(R.id.tv_add_automation);
        ivNoAutomation = view.findViewById(R.id.iv_no_automation);

        btnAddAutomation = view.findViewById(R.id.btn_add_automation);
        txtAddAutomationBtn = btnAddAutomation.findViewById(R.id.text_btn);
        arrowImage = btnAddAutomation.findViewById(R.id.iv_arrow);
        txtAddAutomationBtn.setText(R.string.btn_add_automation);
        arrowImage.setVisibility(View.GONE);
        btnAddAutomation.setVisibility(View.GONE);

        btnAddAutomation.setOnClickListener(addAutomationBtnClickListener);

        if (isGettingAutomations) {
            rlProgressAutomation.setVisibility(View.VISIBLE);
            rlNoAutomation.setVisibility(View.GONE);
            rvAutomation.setVisibility(View.GONE);
        }

        // set a LinearLayoutManager with default orientation
        rvAutomation.setLayoutManager(new LinearLayoutManager(getActivity())); // set LayoutManager to RecyclerView

        automations = new ArrayList<>();
        automationAdapter = new AutomationAdapter(getActivity(), this, automations);
        ((SimpleItemAnimator) rvAutomation.getItemAnimator()).setSupportsChangeAnimations(false);
        rvAutomation.setAdapter(automationAdapter);
        loadAutomations();

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {

            @Override
            public void onRefresh() {
                getAutomations();
            }
        });
    }

    private void getAutomations() {

        if (!espApp.automations.isEmpty()) {
            updateAutomationUi();
        }
        apiManager.getAutomations(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                Log.d(TAG, "Automations received");
                isGettingAutomations = false;
                swipeRefreshLayout.setRefreshing(false);
                updateAutomationUi();
            }

            @Override
            public void onResponseFailure(Exception exception) {
                isGettingAutomations = false;
                swipeRefreshLayout.setRefreshing(false);
                updateAutomationUi();
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                isGettingAutomations = false;
                swipeRefreshLayout.setRefreshing(false);
                updateAutomationUi();
            }
        });
    }

    private void updateAutomationUi() {

        if (isGettingAutomations && espApp.automations.isEmpty()) {
            return;
        }
        loadAutomations();

        Log.d(TAG, "Automation size : " + automations.size());
        rlProgressAutomation.setVisibility(View.GONE);

        if (automations.size() > 0) {

            rlNoAutomation.setVisibility(View.GONE);
            btnAddAutomation.setVisibility(View.GONE);
            rvAutomation.setVisibility(View.VISIBLE);
            swipeRefreshLayout.setVisibility(View.VISIBLE);
            Log.e(TAG, "Display automations");

        } else {

            if (espApp.nodeMap.size() > 0) {
                tvNoAutomation.setText(R.string.no_automations);
                btnAddAutomation.setVisibility(View.VISIBLE);
            } else {
                tvNoAutomation.setText(R.string.no_automations);
                btnAddAutomation.setVisibility(View.GONE);
            }

            rlNoAutomation.setVisibility(View.VISIBLE);
            tvNoAutomation.setVisibility(View.VISIBLE);
            tvAddAutomation.setVisibility(View.GONE);
            ivNoAutomation.setVisibility(View.VISIBLE);
            rvAutomation.setVisibility(View.GONE);
        }

        automationAdapter.updateList(automations);
        if (getActivity() != null && !getActivity().isFinishing()) {
            ((EspMainActivity) getActivity()).updateActionBar();
        }
    }

    private void loadAutomations() {

        if (espApp.automations.size() > 0) {
            ArrayList<Automation> updatedList = new ArrayList<>(espApp.automations.values());
            // Sort automation list by alphabetically.
            Collections.sort(updatedList, new Comparator<Automation>() {

                @Override
                public int compare(Automation s1, Automation s2) {
                    String name1 = s1.getName();
                    String name2 = s2.getName();
                    return name1.compareTo(name2);
                }
            });
            updateAutomationList(updatedList);
        }
    }

    private void updateAutomationList(ArrayList<Automation> paramArrayList) {
        ArrayList<Automation> updatedAutomations = new ArrayList<>();

        for (int i = 0; i < paramArrayList.size(); i++) {
            Automation param = paramArrayList.get(i);
            updatedAutomations.add(new Automation(param));
        }

        if (automations == null) {
            automations = new ArrayList<>();
            automations.addAll(updatedAutomations);
        } else {
            automationAdapter.updateList(updatedAutomations);
        }
    }

    private void askForAutomationName() {

        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_attribute, null);
        final EditText etAutomationName = dialogView.findViewById(R.id.et_attr_value);
        etAutomationName.setInputType(InputType.TYPE_CLASS_TEXT);
        etAutomationName.setHint(R.string.hint_automation_name);
        etAutomationName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});
        final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setView(dialogView)
                .setTitle(R.string.dialog_title_add_name)
                .setPositiveButton(R.string.btn_ok, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {

                Button buttonPositive = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
                buttonPositive.setEnabled(false);
                buttonPositive.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        final String value = etAutomationName.getText().toString();
                        if (!TextUtils.isEmpty(value)) {
                            dialog.dismiss();
                            goToEventDeviceActivity(value);
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

    private void goToEventDeviceActivity(String automationName) {

        Automation automation = new Automation();
        automation.setName(automationName);

        Intent intent = new Intent(getActivity(), EventDeviceActivity.class);
        intent.putExtra(AppConstants.KEY_AUTOMATION, automation);
        ArrayList<Device> devices = espApp.getEventDevices();
        intent.putParcelableArrayListExtra(AppConstants.KEY_DEVICES, devices);
        startActivity(intent);
    }
}
