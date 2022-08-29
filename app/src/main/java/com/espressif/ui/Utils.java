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
import com.espressif.ui.models.Scene;
import com.espressif.ui.models.Schedule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import java.util.Calendar;
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
}
