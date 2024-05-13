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
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.rainmaker.R;
import com.espressif.ui.Utils;
import com.espressif.ui.activities.SceneDetailActivity;
import com.espressif.ui.fragments.ScenesFragment;
import com.espressif.ui.models.Action;
import com.espressif.ui.models.Scene;
import com.espressif.ui.models.Service;
import com.espressif.utils.NodeUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class SceneAdapter extends RecyclerView.Adapter<SceneAdapter.SceneViewHolder> {

    private final String TAG = SceneAdapter.class.getSimpleName();

    private Activity context;
    private ScenesFragment fragment;
    private ArrayList<Scene> sceneList;
    private ApiManager apiManager;
    private EspApplication espApp;

    public SceneAdapter(Activity context, ScenesFragment fragment, ArrayList<Scene> sceneList) {
        this.context = context;
        this.sceneList = sceneList;
        apiManager = ApiManager.getInstance(context);
        espApp = (EspApplication) context.getApplicationContext();
        this.fragment = fragment;
    }

    @Override
    public SceneViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // inflate the item Layout
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_scene, parent, false);
        // set the view's size, margins, paddings and layout parameters
        SceneViewHolder vh = new SceneViewHolder(v); // pass the view to View Holder
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull final SceneViewHolder sceneViewHolder, final int position) {

        Scene scene = sceneList.get(position);
        sceneViewHolder.tvSceneName.setText(sceneList.get(position).getName());
        sceneViewHolder.progressBar.setVisibility(View.GONE);

        String sceneInfo = scene.getInfo();

        if (TextUtils.isEmpty(sceneInfo)) {
            // Display action devices
            StringBuilder deviceNames = new StringBuilder();
            ArrayList<Action> actions = scene.getActions();

            if (actions != null && actions.size() > 0) {
                for (int i = 0; i < actions.size(); i++) {

                    String deviceName = actions.get(i).getDevice().getUserVisibleName();
                    if (deviceNames.length() != 0) {
                        deviceNames.append(", ");
                    }
                    deviceNames.append(deviceName);
                }
            }
            sceneInfo = deviceNames.toString();
        }
        sceneViewHolder.tvActionDevices.setText(sceneInfo);

        sceneViewHolder.itemView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Scene s = sceneList.get(sceneViewHolder.getAdapterPosition());
                Intent intent = new Intent(context, SceneDetailActivity.class);
                intent.putExtra(AppConstants.KEY_SCENE, s);
                context.startActivity(intent);
            }
        });

        sceneViewHolder.btnActivate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                sceneViewHolder.btnActivate.setVisibility(View.INVISIBLE);
                sceneViewHolder.progressBar.setVisibility(View.VISIBLE);

                Set<String> nodeIdList = new HashSet<>();
                Scene s = sceneList.get(sceneViewHolder.getAdapterPosition());
                ArrayList<Action> actions = s.getActions();
                String operation = AppConstants.KEY_OPERATION_ACTIVATE;
                HashMap<String, JsonObject> nodeIdJsonBodyMap = new HashMap<>();

                JsonObject sceneJson = new JsonObject();
                sceneJson.addProperty(AppConstants.KEY_ID, s.getId());
                sceneJson.addProperty(AppConstants.KEY_OPERATION, operation);

                JsonArray scArr = new JsonArray();
                scArr.add(sceneJson);

                JsonObject finalBody = new JsonObject();
                finalBody.add(AppConstants.KEY_SCENES, scArr);

                for (int i = 0; i < actions.size(); i++) {

                    final String nodeId = actions.get(i).getNodeId();

                    if (!nodeIdList.contains(nodeId)) {

                        nodeIdList.add(nodeId);
                        String serviceName = AppConstants.KEY_SCENES;

                        // Get service name
                        if (espApp.nodeMap.get(nodeId) != null) {
                            Service service = NodeUtils.Companion.getService(espApp.nodeMap.get(nodeId), AppConstants.SERVICE_TYPE_SCENES);
                            if (service != null && !TextUtils.isEmpty(service.getName())) {
                                serviceName = service.getName();
                            }
                        }

                        JsonObject body = new JsonObject();
                        body.add(serviceName, finalBody);
                        nodeIdJsonBodyMap.put(nodeId, body);
                    }
                }

                apiManager.updateParamsForMultiNode(nodeIdJsonBodyMap, new ApiResponseListener() {

                    @Override
                    public void onSuccess(Bundle data) {

                        context.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                if (data != null) {
                                    String jsonResponse = data.getString(AppConstants.KEY_RESPONSE);

                                    if (jsonResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {
                                        String deviceNames = Utils.processSceneResponse(sceneList.get(sceneViewHolder.getAdapterPosition()), jsonResponse, nodeIdJsonBodyMap.size());
                                        if (!TextUtils.isEmpty(deviceNames)) {
                                            String msg = context.getString(R.string.error_scene_activate_partial) + " " + deviceNames;
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                                        } else {
                                            Toast.makeText(context, R.string.error_scene_activate, Toast.LENGTH_LONG).show();
                                        }
                                    } else {
                                        Toast.makeText(context, R.string.msg_scene_activated, Toast.LENGTH_LONG).show();
                                    }
                                }
                                sceneViewHolder.btnActivate.setVisibility(View.VISIBLE);
                                sceneViewHolder.progressBar.setVisibility(View.GONE);
                                fragment.updateSceneList();
                            }
                        });
                    }

                    @Override
                    public void onResponseFailure(Exception exception) {

                        exception.printStackTrace();
                        final String msg = exception.getMessage();

                        context.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                sceneViewHolder.btnActivate.setVisibility(View.VISIBLE);
                                sceneViewHolder.progressBar.setVisibility(View.GONE);
                                Toast.makeText(context, "" + msg, Toast.LENGTH_LONG).show();
                                fragment.updateSceneList();
                            }
                        });
                    }

                    @Override
                    public void onNetworkFailure(final Exception exception) {

                        exception.printStackTrace();
                        final String msg = exception.getMessage();

                        context.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                sceneViewHolder.btnActivate.setVisibility(View.VISIBLE);
                                sceneViewHolder.progressBar.setVisibility(View.GONE);
                                Toast.makeText(context, "" + msg, Toast.LENGTH_LONG).show();
                                fragment.updateSceneList();
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public int getItemCount() {
        return sceneList.size();
    }

    public void updateList(ArrayList<Scene> updatedSceneList) {
        sceneList = updatedSceneList;
        notifyDataSetChanged();
    }

    static class SceneViewHolder extends RecyclerView.ViewHolder {

        TextView tvSceneName, tvActionDevices, btnActivate;
        ContentLoadingProgressBar progressBar;

        public SceneViewHolder(View itemView) {
            super(itemView);

            tvSceneName = itemView.findViewById(R.id.tv_scene_name);
            tvActionDevices = itemView.findViewById(R.id.tv_action_devices);
            btnActivate = itemView.findViewById(R.id.btn_activate);
            progressBar = itemView.findViewById(R.id.scene_progress_indicator);
        }
    }
}
