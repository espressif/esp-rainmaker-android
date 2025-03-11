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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.NetworkApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.ui.activities.FwUpdateActivity;
import com.espressif.ui.activities.CmdRespActivity;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.Service;
import com.espressif.ui.models.SharingRequest;
import com.espressif.ui.widgets.EspDropDown;
import com.espressif.utils.NodeUtils;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;

public class NodeDetailsAdapter extends RecyclerView.Adapter<NodeDetailsAdapter.NodeDetailViewHolder> {

    private final String TAG = NodeDetailsAdapter.class.getSimpleName();

    private Activity context;
    private ArrayList<String> nodeInfoList;
    private ArrayList<String> nodeInfoValueList;
    private ArrayList<SharingRequest> sharingRequests;
    private EspNode node;

    public NodeDetailsAdapter(Activity context, ArrayList<String> nodeInfoList, ArrayList<String> nodeValueList,
                              EspNode node, ArrayList<SharingRequest> sharingRequests) {
        this.context = context;
        this.nodeInfoList = nodeInfoList;
        this.nodeInfoValueList = nodeValueList;
        this.sharingRequests = sharingRequests;
        this.node = node;
    }

    @Override
    public NodeDetailViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_node_info, parent, false);
        NodeDetailViewHolder vh = new NodeDetailViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull NodeDetailViewHolder nodeDetailVh, final int position) {

        // Default to hide all views
        nodeDetailVh.rvSharedUsers.setVisibility(View.GONE);
        nodeDetailVh.rlTimezone.setVisibility(View.GONE);
        nodeDetailVh.dropDownTimezone.setVisibility(View.GONE);
        nodeDetailVh.tvNodeInfoValue.setVisibility(View.GONE);
        nodeDetailVh.ivCopy.setVisibility(View.GONE);
        nodeDetailVh.ivCopy.setImageResource(R.drawable.ic_copy);  // Reset to copy icon by default

        // set the data in items
        String nodeInfoValue = nodeInfoValueList.get(position);
        String nodeInfoLabel = nodeInfoList.get(position);
        
        if (node.isOnline() || !nodeInfoLabel.equals(context.getString(R.string.system_services))) {
            nodeDetailVh.tvNodeInfoLabel.setText(nodeInfoLabel);
        } else {
            if (nodeInfoLabel.equals(context.getString(R.string.system_services))) {
                String sysServiceStr = nodeInfoLabel + " (" + context.getString(R.string.status_offline) + ")";
                nodeDetailVh.tvNodeInfoLabel.setText(sysServiceStr);
            }
        }

        // Handle visibility based on the label
        if (nodeInfoLabel.equals(context.getString(R.string.node_id))) {

            nodeDetailVh.tvNodeInfoValue.setVisibility(View.VISIBLE);
            nodeDetailVh.ivCopy.setImageResource(R.drawable.ic_copy);
            nodeDetailVh.ivCopy.setVisibility(View.VISIBLE);
            nodeDetailVh.tvNodeInfoValue.setText(nodeInfoValue);
            nodeDetailVh.ivCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(AppConstants.KEY_NODE_ID, nodeInfoValue);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, R.string.text_copied, Toast.LENGTH_SHORT).show();
            });
            nodeDetailVh.tvNodeInfoValue.setOnClickListener(null);  // Remove any previous click listeners

        } else if (nodeInfoLabel.equals(context.getString(R.string.node_fw_update))) {

            nodeDetailVh.tvNodeInfoValue.setVisibility(View.VISIBLE);
            nodeDetailVh.ivCopy.setImageResource(R.drawable.ic_side_arrow);
            nodeDetailVh.ivCopy.setVisibility(View.VISIBLE);
            nodeDetailVh.tvNodeInfoValue.setText(nodeInfoValue);
            View.OnClickListener updateListener = v -> {
                Intent intent = new Intent(context, FwUpdateActivity.class);
                intent.putExtra(AppConstants.KEY_NODE_ID, node.getNodeId());
                context.startActivity(intent);
            };
            nodeDetailVh.tvNodeInfoValue.setOnClickListener(updateListener);
            nodeDetailVh.ivCopy.setOnClickListener(updateListener);

        } else if (nodeInfoLabel.equals(context.getString(R.string.node_shared_with))
                || nodeInfoLabel.equals(context.getString(R.string.node_shared_by))) {

            nodeDetailVh.rvSharedUsers.setVisibility(View.VISIBLE);
            LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
            linearLayoutManager.setOrientation(RecyclerView.VERTICAL);
            nodeDetailVh.rvSharedUsers.setLayoutManager(linearLayoutManager);
            DividerItemDecoration itemDecor = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            nodeDetailVh.rvSharedUsers.addItemDecoration(itemDecor);
            SharedUserAdapter userAdapter = new SharedUserAdapter(context, node, sharingRequests, false);
            nodeDetailVh.rvSharedUsers.setAdapter(userAdapter);

        } else if (nodeInfoLabel.equals(context.getString(R.string.pending_requests))) {

            nodeDetailVh.rvSharedUsers.setVisibility(View.VISIBLE);
            LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
            linearLayoutManager.setOrientation(RecyclerView.VERTICAL);
            nodeDetailVh.rvSharedUsers.setLayoutManager(linearLayoutManager);
            DividerItemDecoration itemDecor = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            nodeDetailVh.rvSharedUsers.addItemDecoration(itemDecor);
            SharedUserAdapter userAdapter = new SharedUserAdapter(context, node, sharingRequests, true);
            nodeDetailVh.rvSharedUsers.setAdapter(userAdapter);

        } else if (nodeInfoLabel.equals(context.getString(R.string.system_services))) {

            nodeDetailVh.rvSharedUsers.setVisibility(View.VISIBLE);
            LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
            linearLayoutManager.setOrientation(RecyclerView.VERTICAL);
            nodeDetailVh.rvSharedUsers.setLayoutManager(linearLayoutManager);
            DividerItemDecoration itemDecor = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            nodeDetailVh.rvSharedUsers.addItemDecoration(itemDecor);
            Service systemService = NodeUtils.Companion.getService(node, AppConstants.SERVICE_TYPE_SYSTEM);
            if (systemService != null) {
                SystemServiceAdapter adapter = new SystemServiceAdapter(context, node, systemService);
                nodeDetailVh.rvSharedUsers.setAdapter(adapter);
            } else {
                Log.e(TAG, "System service is not available for the node : " + node.getNodeId());
            }

        } else if (nodeInfoLabel.equals(context.getString(R.string.node_timezone))) {

            nodeDetailVh.rlTimezone.setVisibility(View.VISIBLE);
            nodeDetailVh.dropDownTimezone.setVisibility(View.VISIBLE);
            nodeDetailVh.dropDownTimezone.setEnabled(false);
            nodeDetailVh.dropDownTimezone.setOnItemSelectedListener(null);

            Service tzService = NodeUtils.Companion.getService(node, AppConstants.SERVICE_TYPE_TIME);
            String tzValue = null, tzPosixValue = null;
            String tzParamName = null;

            if (tzService != null) {
                ArrayList<Param> tzParams = tzService.getParams();
                if (tzParams != null) {
                    for (int paramIdx = 0; paramIdx < tzParams.size(); paramIdx++) {
                        Param timeParam = tzParams.get(paramIdx);
                        if (AppConstants.PARAM_TYPE_TZ.equalsIgnoreCase(timeParam.getParamType())) {
                            tzValue = timeParam.getLabelValue();
                            tzParamName = timeParam.getName();
                        } else if (AppConstants.PARAM_TYPE_TZ_POSIX.equalsIgnoreCase(timeParam.getParamType())) {
                            tzPosixValue = timeParam.getLabelValue();
                        }
                    }
                }

                String[] timeZoneArray = context.getResources().getStringArray(R.array.timezones);
                ArrayList<String> spinnerValues = new ArrayList<>(Arrays.asList(timeZoneArray));
                int tzValueIndex = -1;
                Log.d(TAG, "TZ : " + tzValue);
                Log.d(TAG, "TZ POSIX : " + tzPosixValue);

                if (TextUtils.isEmpty(tzValue) || TextUtils.isEmpty(tzPosixValue)) {
                    spinnerValues.add(0, context.getString(R.string.select_timezone));
                    nodeDetailVh.dropDownTimezone.setTag(R.id.position, 0);
                } else {
                    if (spinnerValues.contains(tzValue)) {
                        tzValueIndex = spinnerValues.indexOf(tzValue);
                        nodeDetailVh.dropDownTimezone.setTag(R.id.position, tzValueIndex);
                    } else {
                        spinnerValues.add(0, context.getString(R.string.select_timezone));
                        nodeDetailVh.dropDownTimezone.setTag(R.id.position, 0);
                    }
                }

                ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, spinnerValues);
                dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                nodeDetailVh.dropDownTimezone.setAdapter(dataAdapter);

                if (tzValueIndex != -1) {
                    nodeDetailVh.dropDownTimezone.setSelection(tzValueIndex, true);
                }
                final Service finalTzService = tzService;
                final String finalTzParamName = tzParamName;
                final int oldTzValueIndex = tzValueIndex;
                nodeDetailVh.dropDownTimezone.setEnabled(true);

                nodeDetailVh.dropDownTimezone.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, final int pos, long id) {

                        if ((int) nodeDetailVh.dropDownTimezone.getTag(R.id.position) != pos) {

                            final String newValue = parent.getItemAtPosition(pos).toString();
                            nodeDetailVh.dropDownTimezone.setEnabled(false);
                            nodeDetailVh.tzProgress.setVisibility(View.VISIBLE);
                            Log.d(TAG, "New timezone value : " + newValue);

                            JsonObject body = new JsonObject();
                            JsonObject jsonParam = new JsonObject();
                            jsonParam.addProperty(finalTzParamName, newValue);
                            body.add(finalTzService.getName(), jsonParam);

                            NetworkApiManager networkApiManager = new NetworkApiManager(context.getApplicationContext());
                            networkApiManager.updateParamValue(node.getNodeId(), body, new ApiResponseListener() {

                                @Override
                                public void onSuccess(Bundle data) {

                                    context.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {

                                            nodeDetailVh.tzProgress.setVisibility(View.GONE);
                                            nodeDetailVh.dropDownTimezone.setEnabled(true);
                                            nodeDetailVh.dropDownTimezone.setTag(R.id.position, pos);
                                        }
                                    });
                                }

                                @Override
                                public void onResponseFailure(Exception exception) {

                                    if (exception instanceof CloudException) {
                                        Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(context, R.string.error_update_timezone, Toast.LENGTH_SHORT).show();
                                    }

                                    context.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            nodeDetailVh.tzProgress.setVisibility(View.GONE);
                                            nodeDetailVh.dropDownTimezone.setEnabled(true);
                                            nodeDetailVh.dropDownTimezone.setSelection(oldTzValueIndex, true);
                                        }
                                    });
                                }

                                @Override
                                public void onNetworkFailure(Exception exception) {

                                    if (exception instanceof CloudException) {
                                        Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(context, R.string.error_update_timezone, Toast.LENGTH_SHORT).show();
                                    }

                                    context.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            nodeDetailVh.tzProgress.setVisibility(View.GONE);
                                            nodeDetailVh.dropDownTimezone.setEnabled(true);
                                            nodeDetailVh.dropDownTimezone.setSelection(oldTzValueIndex, true);
                                        }
                                    });
                                }
                            });
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });
            }

        } else if (nodeInfoLabel.equals(context.getString(R.string.node_cmd_resp))) {

            nodeDetailVh.tvNodeInfoValue.setVisibility(View.VISIBLE);
            nodeDetailVh.ivCopy.setImageResource(R.drawable.ic_side_arrow);
            nodeDetailVh.ivCopy.setVisibility(View.VISIBLE);
            nodeDetailVh.tvNodeInfoValue.setText(nodeInfoValue);
            View.OnClickListener cmdRespListener = v -> {
                Intent intent = new Intent(context, CmdRespActivity.class);
                intent.putExtra(AppConstants.KEY_NODE_ID, node.getNodeId());
                context.startActivity(intent);
            };
            nodeDetailVh.tvNodeInfoValue.setOnClickListener(cmdRespListener);
            nodeDetailVh.ivCopy.setOnClickListener(cmdRespListener);

        } else if (!TextUtils.isEmpty(nodeInfoValueList.get(position))) {

            nodeDetailVh.rvSharedUsers.setVisibility(View.GONE);
            nodeDetailVh.rlTimezone.setVisibility(View.GONE);
            nodeDetailVh.dropDownTimezone.setVisibility(View.GONE);
            nodeDetailVh.tvNodeInfoValue.setVisibility(View.VISIBLE);
            nodeDetailVh.ivCopy.setVisibility(View.GONE);
            nodeDetailVh.tvNodeInfoValue.setText(nodeInfoValueList.get(position));
            nodeDetailVh.tvNodeInfoValue.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return nodeInfoList.size();
    }

    static class NodeDetailViewHolder extends RecyclerView.ViewHolder {

        TextView tvNodeInfoLabel, tvNodeInfoValue;
        RecyclerView rvSharedUsers;
        EspDropDown dropDownTimezone;
        RelativeLayout rlTimezone;
        ContentLoadingProgressBar tzProgress;
        ImageView ivCopy;

        public NodeDetailViewHolder(View itemView) {
            super(itemView);

            tvNodeInfoLabel = itemView.findViewById(R.id.tv_node_label);
            tvNodeInfoValue = itemView.findViewById(R.id.tv_node_value);
            rvSharedUsers = itemView.findViewById(R.id.rv_users_list);
            dropDownTimezone = itemView.findViewById(R.id.dropdown_time_zone);
            rlTimezone = itemView.findViewById(R.id.rl_timezone);
            tzProgress = itemView.findViewById(R.id.progress_indicator_timezone);
            ivCopy = itemView.findViewById(R.id.iv_copy);
        }
    }
}
