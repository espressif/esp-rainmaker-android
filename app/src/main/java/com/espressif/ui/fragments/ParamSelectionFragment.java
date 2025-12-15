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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.espressif.EspApplication;
import com.espressif.rainmaker.R;
import com.espressif.ui.EventSelectionListener;
import com.espressif.ui.Utils;
import com.espressif.ui.adapters.ParamSelectionAdapter;
import com.espressif.ui.models.Automation;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;

public class ParamSelectionFragment extends BottomSheetDialogFragment implements EventSelectionListener {

    private static final String ARG_DEVICE = "device";
    private static final String ARG_AUTOMATION = "automation";
    
    private Device eventDevice;
    private ArrayList<Param> params;
    private EspApplication espApp;
    private Automation automation;
    private EventSelectionListener eventSelectionListener;

    // Default constructor required for Fragment
    public ParamSelectionFragment() {
    }

    // Factory method to create fragment with arguments
    public static ParamSelectionFragment newInstance(Automation automation, Device device) {
        ParamSelectionFragment fragment = new ParamSelectionFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_AUTOMATION, automation);
        args.putParcelable(ARG_DEVICE, device);
        fragment.setArguments(args);
        return fragment;
    }

    // Deprecated constructor - kept for backward compatibility but should not be used
    @Deprecated
    public ParamSelectionFragment(AppCompatActivity context, Automation automation, Device device, EventSelectionListener listener) {
        this.automation = automation;
        this.eventDevice = device;
        this.eventSelectionListener = listener;
        espApp = (EspApplication) context.getApplicationContext();
        this.params = Utils.getEventDeviceParams(device.getParams());
        eventDevice.setParams(params);
    }

    public void setEventSelectionListener(EventSelectionListener listener) {
        this.eventSelectionListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize from arguments if available
        if (getArguments() != null) {
            automation = getArguments().getParcelable(ARG_AUTOMATION);
            eventDevice = getArguments().getParcelable(ARG_DEVICE);
        }
        
        // Initialize EspApplication and params
        if (getActivity() != null) {
            espApp = (EspApplication) getActivity().getApplicationContext();
        }
        
        if (eventDevice != null) {
            this.params = Utils.getEventDeviceParams(eventDevice.getParams());
            eventDevice.setParams(params);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.param_selection_dialog_layout, container, false);
        init(root);
        return root;
    }

    private void init(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.rv_param_list);
        ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(true);
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL));
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setAdapter(new ParamSelectionAdapter(getActivity(), eventDevice, this));
    }

    @Override
    public void onEventSelected(Device device, Param selectedParam, String condition) {

        if (automation == null) {
            automation = new Automation();
        }
        automation.setEventDevice(eventDevice);
        automation.setCondition(condition);
        eventSelectionListener.onEventSelected(eventDevice, selectedParam, condition);
        dismiss();
    }
}
