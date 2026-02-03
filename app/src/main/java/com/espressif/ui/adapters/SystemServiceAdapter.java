// Copyright 2023 Espressif Systems (Shanghai) PTE LTD
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
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.NetworkApiManager;
import com.espressif.ble.BleLocalControlManager;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.ui.activities.EspMainActivity;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.Service;
import com.google.gson.JsonObject;

import java.util.ArrayList;

public class SystemServiceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final String TAG = SystemServiceAdapter.class.getSimpleName();

    private Activity activityContext;
    private EspNode node;
    private Service systemService;
    private ArrayList<Param> serviceParams;
    private NetworkApiManager networkApiManager;

    public SystemServiceAdapter(Activity activityContext, EspNode node, Service systemService) {
        this.activityContext = activityContext;
        this.node = node;
        this.systemService = systemService;
        this.serviceParams = systemService.getParams();
        networkApiManager = new NetworkApiManager(activityContext.getApplicationContext());
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        LayoutInflater layoutInflater = LayoutInflater.from(activityContext);
        View serviceView = layoutInflater.inflate(R.layout.item_system_service, parent, false);
        return new SystemServiceViewHolder(serviceView);
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {

        final SystemServiceViewHolder systemServiceViewHolder = (SystemServiceViewHolder) holder;
        systemServiceViewHolder.tvService.setText(serviceParams.get(position).getName());
        systemServiceViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmOperation(systemServiceViewHolder);
            }
        });

        boolean isBleConnected = BleLocalControlManager.Companion.getInstance(activityContext)
                .isConnected(node.getNodeId());
        if (node.isOnline() || isBleConnected) {
            systemServiceViewHolder.itemView.setEnabled(true);
            systemServiceViewHolder.itemView.setAlpha(1f);
        } else {
            systemServiceViewHolder.itemView.setEnabled(false);
            systemServiceViewHolder.itemView.setAlpha(0.7f);
        }
    }

    @Override
    public int getItemCount() {
        return serviceParams.size();
    }

    static class SystemServiceViewHolder extends RecyclerView.ViewHolder {

        TextView tvService;
        ContentLoadingProgressBar loadingServiceOperation;

        public SystemServiceViewHolder(View itemView) {
            super(itemView);
            tvService = itemView.findViewById(R.id.tv_system_service);
            loadingServiceOperation = itemView.findViewById(R.id.progress_operation);
        }
    }

    private void confirmOperation(final SystemServiceViewHolder systemServiceViewHolder) {

        AlertDialog.Builder builder = new AlertDialog.Builder(activityContext);
        int position = systemServiceViewHolder.getAdapterPosition();
        Param serviceParam = serviceParams.get(position);
        String paramType = serviceParam.getParamType();
        builder.setTitle(R.string.dialog_title_delete_user);

        if (!TextUtils.isEmpty(paramType)) {

            if (paramType.equals(AppConstants.PARAM_TYPE_REBOOT)) {

                builder.setMessage(R.string.alert_reboot);

            } else if (paramType.equals(AppConstants.PARAM_TYPE_WIFI_RESET)) {

                builder.setMessage(R.string.alert_wifi_reset);

            } else if (paramType.equals(AppConstants.PARAM_TYPE_FACTORY_RESET)) {

                builder.setMessage(R.string.alert_factory_reset);
            }
        }

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();

                systemServiceViewHolder.loadingServiceOperation.setVisibility(View.VISIBLE);

                JsonObject jsonParam = new JsonObject();
                JsonObject body = new JsonObject();

                jsonParam.addProperty(serviceParam.getName(), true);
                body.add(systemService.getName(), jsonParam);

                networkApiManager.updateParamValue(node.getNodeId(), body, new ApiResponseListener() {

                    @Override
                    public void onSuccess(Bundle data) {
                        systemServiceViewHolder.loadingServiceOperation.setVisibility(View.GONE);

                        boolean isBleConnected = BleLocalControlManager.Companion.getInstance(activityContext)
                                .isConnected(node.getNodeId());

                        if (paramType.equals(AppConstants.PARAM_TYPE_FACTORY_RESET) && isBleConnected) {
                            ApiManager.getInstance(activityContext).removeNode(node.getNodeId(), new ApiResponseListener() {
                                @Override
                                public void onSuccess(Bundle data) {
                                    Log.d(TAG, "Node removed from cloud after BLE factory reset");
                                    goToMainAndFinish();
                                }

                                @Override
                                public void onResponseFailure(Exception exception) {
                                    Log.e(TAG, "Failed to remove node from cloud: " + exception.getMessage());
                                    goToMainAndFinish();
                                }

                                @Override
                                public void onNetworkFailure(Exception exception) {
                                    Log.e(TAG, "Network failure removing node from cloud: " + exception.getMessage());
                                    goToMainAndFinish();
                                }
                            });
                            return;
                        }

                        if (paramType.equals(AppConstants.PARAM_TYPE_FACTORY_RESET)
                                || (paramType.equals(AppConstants.PARAM_TYPE_REBOOT) && isBleConnected)) {
                            goToMainAndFinish();
                        }
                    }

                    @Override
                    public void onResponseFailure(Exception exception) {
                        systemServiceViewHolder.loadingServiceOperation.setVisibility(View.GONE);
                        exception.printStackTrace();

                        if (exception instanceof CloudException) {
                            Toast.makeText(activityContext, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activityContext, R.string.error_add_member, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onNetworkFailure(Exception exception) {
                        systemServiceViewHolder.loadingServiceOperation.setVisibility(View.GONE);
                        exception.printStackTrace();

                        if (exception instanceof CloudException) {
                            Toast.makeText(activityContext, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activityContext, R.string.error_add_member, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });

        builder.setNegativeButton(R.string.btn_no, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void goToMainAndFinish() {
        EspApplication appContext = (EspApplication) activityContext.getApplicationContext();
        Intent mainIntent = new Intent(appContext, EspMainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(mainIntent);
        activityContext.finish();
    }
}
