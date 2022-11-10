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

package com.espressif.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.util.Patterns;

import androidx.appcompat.app.AlertDialog;

import com.espressif.AppConstants;
import com.espressif.rainmaker.R;
import com.espressif.ui.models.Action;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.Scene;
import com.espressif.ui.models.Schedule;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.TimeZone;

public class Utils {

    public static boolean isValidEmail(CharSequence target) {
        return (!TextUtils.isEmpty(target) && Patterns.EMAIL_ADDRESS.matcher(target).matches());
    }

    public static String getCurrentTimeZone() {
        return TimeZone.getDefault().getID();
    }

    public static Calendar getCalendarForTimeZone(String timeZoneStr) {
        TimeZone tz = TimeZone.getTimeZone(timeZoneStr);
        return Calendar.getInstance(tz);
    }

    public static void showAlertDialog(final Activity activityContext, String title, String msg, final boolean shouldExit) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(activityContext);

        if (!TextUtils.isEmpty(title)) {
            builder.setTitle(title);
        }
        builder.setMessage(msg);
        builder.setNeutralButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (shouldExit) {
                    activityContext.finish();
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static String processScheduleResponse(Schedule schedule, String jsonResponse, int nodeListSize) {

        // Return empty if schedule has only one node (No need to display device name)
        if (nodeListSize == 1) {
            return "";
        }
        StringBuilder deviceNames = new StringBuilder();

        try {
            JSONArray responseJsonArr = new JSONArray(jsonResponse);
            ArrayList<String> failureNodes = new ArrayList<>();

            if (responseJsonArr != null) {
                for (int i = 0; i < responseJsonArr.length(); i++) {
                    JSONObject nodeResJson = responseJsonArr.optJSONObject(i);
                    String nodeId = nodeResJson.optString(AppConstants.KEY_NODE_ID);
                    String status = nodeResJson.optString(AppConstants.KEY_STATUS);
                    if (AppConstants.KEY_FAILURE_RESPONSE.equals(status)) {
                        failureNodes.add(nodeId);
                    }
                }
            }

            // Return empty if request fails for all nodes (No need to display device name)
            if (nodeListSize == failureNodes.size()) {
                return "";
            }

            if (failureNodes.size() > 0) {
                for (int i = 0; i < failureNodes.size(); i++) {
                    String nodeId = failureNodes.get(i);
                    ArrayList<Action> actions = schedule.getActions();
                    for (int j = 0; j < actions.size(); j++) {
                        if (nodeId.equals(actions.get(j).getNodeId())) {
                            if (deviceNames.length() != 0) {
                                deviceNames.append(", ");
                            }
                            deviceNames.append(actions.get(j).getDevice().getUserVisibleName());
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return deviceNames.toString();
    }

    public static String processSceneResponse(Scene scene, String jsonResponse, int nodeListSize) {

        // Return empty if scene has only one node (No need to display device name)
        if (nodeListSize == 1) {
            return "";
        }
        StringBuilder deviceNames = new StringBuilder();

        try {
            JSONArray responseJsonArr = new JSONArray(jsonResponse);
            ArrayList<String> failureNodes = new ArrayList<>();

            if (responseJsonArr != null) {
                for (int i = 0; i < responseJsonArr.length(); i++) {
                    JSONObject nodeResJson = responseJsonArr.optJSONObject(i);
                    String nodeId = nodeResJson.optString(AppConstants.KEY_NODE_ID);
                    String status = nodeResJson.optString(AppConstants.KEY_STATUS);
                    if (AppConstants.KEY_FAILURE_RESPONSE.equals(status)) {
                        failureNodes.add(nodeId);
                    }
                }
            }

            // Return empty if request fails for all nodes (No need to display device name)
            if (nodeListSize == failureNodes.size()) {
                return "";
            }

            if (failureNodes.size() > 0) {
                for (int i = 0; i < failureNodes.size(); i++) {
                    String nodeId = failureNodes.get(i);
                    ArrayList<Action> actions = scene.getActions();
                    for (int j = 0; j < actions.size(); j++) {
                        if (nodeId.equals(actions.get(j).getNodeId())) {
                            if (deviceNames.length() != 0) {
                                deviceNames.append(", ");
                            }
                            deviceNames.append(actions.get(j).getDevice().getUserVisibleName());
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return deviceNames.toString();
    }

    public static ArrayList<Param> getEventDeviceParams(ArrayList<Param> allParams) {
        Iterator itr = allParams.iterator();
        while (itr.hasNext()) {
            Param p = (Param) itr.next();

            if (!p.isDynamicParam()) {
                itr.remove();
            } else if (p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                itr.remove();
            } else if (p.getUiType() != null && p.getUiType().equals(AppConstants.UI_TYPE_HIDDEN)) {
                itr.remove();
            }
        }
        return allParams;
    }

    public static ArrayList<Param> getWritableParams(ArrayList<Param> allParams) {
        Iterator itr = allParams.iterator();
        while (itr.hasNext()) {
            Param p = (Param) itr.next();

            if (!p.isDynamicParam()) {
                itr.remove();
            } else if (p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                itr.remove();
            } else if (!p.getProperties().contains(AppConstants.KEY_PROPERTY_WRITE)) {
                itr.remove();
            } else if (p.getUiType() != null && p.getUiType().equals(AppConstants.UI_TYPE_HIDDEN)) {
                itr.remove();
            }
        }
        return allParams;
    }

    public static JsonObject getParamJson(Device device) {

        JsonObject paramJson = new JsonObject();
        ArrayList<Param> params = device.getParams();

        for (int j = 0; j < params.size(); j++) {

            Param param = params.get(j);

            if (param.isSelected()) {

                String dataType = param.getDataType();

                if (AppConstants.UI_TYPE_SLIDER.equalsIgnoreCase(param.getUiType())) {

                    if (dataType.equalsIgnoreCase("int")
                            || dataType.equalsIgnoreCase("integer")) {

                        int max = param.getMaxBounds();
                        int min = param.getMinBounds();

                        if ((min < max)) {
                            int value = (int) param.getValue();
                            paramJson.addProperty(param.getName(), value);
                        } else {

                            int value = Integer.parseInt(param.getLabelValue());
                            paramJson.addProperty(param.getName(), value);
                        }
                    } else if (dataType.equalsIgnoreCase("float")
                            || dataType.equalsIgnoreCase("double")) {

                        int max = param.getMaxBounds();
                        int min = param.getMinBounds();

                        if ((min < max)) {
                            paramJson.addProperty(param.getName(), param.getValue());
                        } else {

                            float value = Float.parseFloat(param.getLabelValue());
                            paramJson.addProperty(param.getName(), value);
                        }
                    }
                } else if (AppConstants.UI_TYPE_TRIGGER.equalsIgnoreCase(param.getUiType())
                        && dataType.equalsIgnoreCase("bool")
                        || dataType.equalsIgnoreCase("boolean")) {

                    paramJson.addProperty(param.getName(), true);

                } else {
                    if (dataType.equalsIgnoreCase("bool")
                            || dataType.equalsIgnoreCase("boolean")) {

                        paramJson.addProperty(param.getName(), param.getSwitchStatus());

                    } else if (dataType.equalsIgnoreCase("int")
                            || dataType.equalsIgnoreCase("integer")) {

                        int value = (int) param.getValue();
                        paramJson.addProperty(param.getName(), value);

                    } else if (dataType.equalsIgnoreCase("float")
                            || dataType.equalsIgnoreCase("double")) {

                        paramJson.addProperty(param.getName(), param.getValue());

                    } else if (dataType.equalsIgnoreCase("string")) {

                        paramJson.addProperty(param.getName(), param.getLabelValue());
                    }
                }
            }
        }

        return paramJson;
    }

    public static String getEventParamString(ArrayList<Param> params, String condition) {

        StringBuilder paramText = new StringBuilder();

        for (int j = 0; j < params.size(); j++) {

            Param param = params.get(j);
            String dataType = param.getDataType();

            if (param.isSelected()) {
                paramText.append(param.getName());

                if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {
                    paramText.append(":");
                } else if (!TextUtils.isEmpty(condition) && condition.equals("==")) {
                    paramText.append(":");
                } else {
                    paramText.append(condition);
                }
                paramText.append(param.getLabelValue());
            }
        }
        return paramText.toString();
    }

    public static String getActionParamsString(ArrayList<Param> params) {

        ArrayList<Param> paramList = new ArrayList<>(params);
        StringBuilder paramText = new StringBuilder();

        Iterator itr = paramList.iterator();
        while (itr.hasNext()) {
            Param p = (Param) itr.next();
            if (!p.isSelected()) {
                itr.remove();
            }
        }

        for (int j = 0; j < paramList.size(); j++) {

            Param param = paramList.get(j);
            paramText.append(param.getName());
            paramText.append(":");
            paramText.append(param.getLabelValue());

            if (j != (paramList.size() - 1)) {
                paramText.append(",");
            }
        }
        return paramText.toString();
    }
}
