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

package com.espressif;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.espressif.AppConstants.Companion.UpdateEventType;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.db.EspDatabase;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.activities.FwUpdateActivity;
import com.espressif.ui.activities.GroupShareActivity;
import com.espressif.ui.activities.NotificationsActivity;
import com.espressif.ui.activities.SplashActivity;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Group;
import com.espressif.ui.models.NotificationEvent;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.UpdateEvent;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;

public class NotificationWorker extends Worker {

    private static final String TAG = NotificationWorker.class.getSimpleName();

    private EspApplication espApp;
    private NotificationManagerCompat notificationManager;
    private boolean isNotificationAllowed;
    private static int notificationId = 0;

    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        espApp = (EspApplication) getApplicationContext();
        notificationManager = NotificationManagerCompat.from(espApp);
    }

    @NonNull
    @Override
    public Result doWork() {

        Log.d(TAG, "Do Notification Work");
        Data data = getInputData();
        String title = data.getString(AppConstants.KEY_TITLE);
        String body = data.getString(AppConstants.KEY_BODY);
        String eventPayload = data.getString(AppConstants.KEY_EVENT_DATA_PAYLOAD);

        try {
            JSONObject eventDataJson = new JSONObject(eventPayload);
            String eventType = eventDataJson.optString(AppConstants.KEY_EVENT_TYPE);
            JSONObject jsonEventData = eventDataJson.optJSONObject(AppConstants.KEY_EVENT_DATA);
            NotificationEvent notificationEvent = new NotificationEvent();
            notificationEvent.setEventType(eventType);
            notificationEvent.setEventVersion(eventDataJson.optString(AppConstants.KEY_EVENT_VERSION));
            notificationEvent.setEventId(eventDataJson.optString(AppConstants.KEY_ID));
            notificationEvent.setEventData(jsonEventData.toString());
            notificationEvent.setEventDescription(eventDataJson.optString(AppConstants.KEY_DESCRIPTION));
            notificationEvent.setTimestamp(eventDataJson.optLong(AppConstants.KEY_TIMESTAMP));
            Log.e(TAG, "Event type : " + eventType);
            isNotificationAllowed = shouldSendNotification();

            if (!TextUtils.isEmpty(eventType)) {

                if (jsonEventData != null) {

                    if (AppConstants.EVENT_NODE_CONNECTED.equals(eventType)
                            || AppConstants.EVENT_NODE_DISCONNECTED.equals(eventType)) {

                        processConnectivityEvent(title, notificationEvent, jsonEventData);

                    } else if (AppConstants.EVENT_NODE_ADDED.equals(eventType)) {

                        processNodeAddedEvent(title, notificationEvent, jsonEventData);

                    } else if (AppConstants.EVENT_NODE_REMOVED.equals(eventType)) {

                        processNodeRemovedEvent(title, notificationEvent, jsonEventData);

                    } else if (AppConstants.EVENT_NODE_SHARING_ADD.equals(eventType)) {

                        processSharingAddEvent(title, notificationEvent, jsonEventData);

                    } else if (AppConstants.EVENT_NODE_AUTOMATION_TRIGGER.equals(eventType)) {

                        processAutomationTriggerEvent(title, notificationEvent, jsonEventData);

                    } else if (AppConstants.EVENT_NODE_OTA.equals(eventType)) {

                        processNodeOta(title, notificationEvent, jsonEventData);

                    } else if (AppConstants.EVENT_NODE_PARAM_MODIFIED.equals(eventType)) {

                        String nodeId = jsonEventData.optString(AppConstants.KEY_NODE_ID);
                        String payload = jsonEventData.optString(AppConstants.KEY_PAYLOAD);
                        Log.d(TAG, "Node Id : " + nodeId);
                        Log.d(TAG, "Payload : " + payload);
                        JSONObject payloadJson = null;
                        try {
                            payloadJson = new JSONObject(payload);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        if (!espApp.nodeMap.containsKey(nodeId)) {
                            ApiManager.getInstance(espApp).getNodeDetails(nodeId);
                        }

                        if (!espApp.nodeMap.containsKey(nodeId)) {
                            loadDataFromLocalStorage();
                        }

                        if (payloadJson != null && espApp.nodeMap.containsKey(nodeId)) {
                            EspNode node = espApp.nodeMap.get(nodeId);
                            JsonDataParser.setAllParams(espApp, node, payloadJson);
                            // Send event for UI update
                            EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
                        }
                    } else if (AppConstants.EVENT_ALERT.equals(eventType)) {
                        processAlertEvent(title, notificationEvent, jsonEventData);
                    } else if (AppConstants.EVENT_GROUP_SHARING_ADD.equals(eventType)) {
                        processGroupSharingAddEvent(title, notificationEvent, jsonEventData);
                    } else if (AppConstants.EVENT_GROUP_SHARE_ADDED.equals(eventType)) {
                        processGroupShareAddedEvent(title, notificationEvent, jsonEventData);
                    } else if (AppConstants.EVENT_GROUP_SHARE_REMOVED.equals(eventType)) {
                        processGroupShareRemovedEvent(title, notificationEvent, jsonEventData);
                    } else {
                        notificationEvent.setEventDescription(body);
                        EspDatabase.getInstance(espApp).getNotificationDao().insertOrUpdate(notificationEvent);
                        if (isNotificationAllowed) {
                            sendNotification(title, body, AppConstants.CHANNEL_ALERT, NotificationsActivity.class);
                        }
                    }
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return Result.success();
    }

    // Event type - Node connected / disconnected

    private void processConnectivityEvent(String title, NotificationEvent notificationEvent, JSONObject jsonEventData) {

        EspApplication espApp = (EspApplication) getApplicationContext();
        JSONObject connectivityJson = jsonEventData.optJSONObject(AppConstants.KEY_CONNECTIVITY);
        String nodeId = jsonEventData.optString(AppConstants.KEY_NODE_ID);
        Log.d(TAG, "Node Id : " + nodeId);
        StringBuilder msgBuilder = new StringBuilder();

        if (connectivityJson != null) {

            boolean nodeStatus = connectivityJson.optBoolean(AppConstants.KEY_CONNECTED);
            long timestamp = connectivityJson.optLong(AppConstants.KEY_TIMESTAMP);

            if (!espApp.nodeMap.containsKey(nodeId)) {
                ApiManager.getInstance(espApp).getNodeDetails(nodeId);
            }

            if (!espApp.nodeMap.containsKey(nodeId)) {
                loadDataFromLocalStorage();
            }

            if (espApp.nodeMap.containsKey(nodeId)) {

                EspNode node = espApp.nodeMap.get(nodeId);
                node.setTimeStampOfStatus(timestamp);
                node.setOnline(nodeStatus);

                if (!Arrays.asList(AppConstants.NODE_STATUS_LOCAL, AppConstants.NODE_STATUS_MATTER_LOCAL,
                        AppConstants.NODE_STATUS_REMOTELY_CONTROLLABLE).contains(node.getNodeStatus())) {
                    if (nodeStatus) {
                        node.setNodeStatus(AppConstants.NODE_STATUS_ONLINE);
                    } else {
                        node.setNodeStatus(AppConstants.NODE_STATUS_OFFLINE);
                    }
                }

                ArrayList<Device> devices = node.getDevices();
                ArrayList<String> deviceNames = new ArrayList<>();
                if (devices != null) {
                    for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
                        deviceNames.add(devices.get(deviceIndex).getUserVisibleName());
                    }
                }

                if (deviceNames.size() > 0) {

                    if (deviceNames.size() == 1) {
                        msgBuilder.append(deviceNames.get(0));
                        if (AppConstants.EVENT_NODE_CONNECTED.equals(notificationEvent.getEventType())) {
                            msgBuilder.append(" is now online.");
                        } else {
                            msgBuilder.append(" is now offline.");
                        }
                    } else {
                        for (int i = 0; i < deviceNames.size(); i++) {

                            if (i != 0) {
                                msgBuilder.append(",");
                                msgBuilder.append(" ");
                            }
                            msgBuilder.append(deviceNames.get(i));
                        }

                        if (AppConstants.EVENT_NODE_CONNECTED.equals(notificationEvent.getEventType())) {
                            msgBuilder.append(" are now online.");
                        } else {
                            msgBuilder.append(" are now offline.");
                        }
                    }
                }
            } else {
                Log.e(TAG, "Node id is not available for this event.");
            }
        } else {
            Log.e(TAG, "Connectivity object is null");
        }

        if (TextUtils.isEmpty(msgBuilder.toString())) {
            if (AppConstants.EVENT_NODE_CONNECTED.equals(notificationEvent.getEventType())) {
                msgBuilder.append(espApp.getString(R.string.notify_node_connected));
            } else {
                msgBuilder.append(espApp.getString(R.string.notify_node_disconnected));
            }
        }
        notificationEvent.setNotificationMsg(msgBuilder.toString());
        EspDatabase.getInstance(espApp).getNotificationDao().insertOrUpdate(notificationEvent);
        Log.d(TAG, "Connectivity Notification inserted in database");

        if (AppConstants.EVENT_NODE_CONNECTED.equals(notificationEvent.getEventType())) {
            if (isNotificationAllowed) {
                sendNotification(title, msgBuilder.toString(), AppConstants.CHANNEL_NODE_ONLINE_ID, SplashActivity.class);
            }
            EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_DEVICE_ONLINE));
        } else {
            if (isNotificationAllowed) {
                sendNotification(title, msgBuilder.toString(), AppConstants.CHANNEL_NODE_OFFLINE_ID, SplashActivity.class);
            }
            EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_DEVICE_OFFLINE));
        }
    }

    // Event type - Node added

    private void processNodeAddedEvent(String title, NotificationEvent notificationEvent, JSONObject jsonEventData) {

        EspApplication espApp = (EspApplication) getApplicationContext();
        StringBuilder msgBuilder = new StringBuilder();
        // If it is more than one node then called API to get all nodes instead of getting specific nodes.
        JSONArray nodeJsonArray = jsonEventData.optJSONArray(AppConstants.KEY_NODES);
        if (nodeJsonArray != null && nodeJsonArray.length() > 0) {

            if (nodeJsonArray.length() == 1) {

                String nodeId = nodeJsonArray.optString(0);
                ApiManager.getInstance(espApp).getNodeDetails(nodeId);

                if (!espApp.nodeMap.containsKey(nodeId)) {
                    loadDataFromLocalStorage();
                }

                if (espApp.nodeMap.containsKey(nodeId)) {
                    ArrayList<String> deviceNames = new ArrayList<>();
                    ArrayList<Device> devices = espApp.nodeMap.get(nodeId).getDevices();
                    if (devices != null) {
                        for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
                            deviceNames.add(devices.get(deviceIndex).getUserVisibleName());
                        }
                    }

                    if (deviceNames.size() > 0) {

                        if (deviceNames.size() == 1) {
                            msgBuilder.append(deviceNames.get(0));
                            msgBuilder.append(" is added.");
                        } else {
                            for (int i = 0; i < deviceNames.size(); i++) {

                                if (i != 0) {
                                    msgBuilder.append(",");
                                    msgBuilder.append(" ");
                                }
                                msgBuilder.append(deviceNames.get(i));
                            }
                            msgBuilder.append(" are added.");
                        }
                    }
                }

                if (TextUtils.isEmpty(msgBuilder.toString())) {
                    msgBuilder.append(espApp.getString(R.string.notify_node_added));
                }

                notificationEvent.setNotificationMsg(msgBuilder.toString());
                EspDatabase.getInstance(espApp).getNotificationDao().insertOrUpdate(notificationEvent);
                Log.d(TAG, "Node added Notification inserted in database");
                if (isNotificationAllowed) {
                    sendNotification(title, msgBuilder.toString(), AppConstants.CHANNEL_NODE_ADDED, SplashActivity.class);
                }
                EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));

            } else {
                ApiManager.getInstance(espApp).getNodes(new ApiResponseListener() {
                    @Override
                    public void onSuccess(Bundle data) {
                        ArrayList<String> deviceNames = new ArrayList<>();
                        for (int nodeIndex = 0; nodeIndex < nodeJsonArray.length(); nodeIndex++) {
                            String nodeId = nodeJsonArray.optString(nodeIndex);
                            if (espApp.nodeMap.containsKey(nodeId)) {
                                ArrayList<Device> devices = espApp.nodeMap.get(nodeId).getDevices();
                                if (devices != null) {
                                    for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
                                        deviceNames.add(devices.get(deviceIndex).getUserVisibleName());
                                    }
                                }
                            }
                        }

                        if (deviceNames.size() > 0) {

                            if (deviceNames.size() == 1) {
                                msgBuilder.append(deviceNames.get(0));
                                msgBuilder.append(" is added.");
                            } else {
                                for (int i = 0; i < deviceNames.size(); i++) {

                                    if (i != 0) {
                                        msgBuilder.append(",");
                                        msgBuilder.append(" ");
                                    }
                                    msgBuilder.append(deviceNames.get(i));
                                }
                                msgBuilder.append(" are added.");
                            }
                        }

                        if (TextUtils.isEmpty(msgBuilder.toString())) {
                            msgBuilder.append(espApp.getString(R.string.notify_node_added));
                        }
                        EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
                    }

                    @Override
                    public void onResponseFailure(Exception exception) {
                        msgBuilder.append(espApp.getString(R.string.notify_node_added));
                        notificationEvent.setNotificationMsg(msgBuilder.toString());
                        EspDatabase.getInstance(espApp).getNotificationDao().insertOrUpdate(notificationEvent);
                        Log.d(TAG, "Node added Notification inserted in database");
                        if (isNotificationAllowed) {
                            sendNotification(title, msgBuilder.toString(), AppConstants.CHANNEL_NODE_ADDED, SplashActivity.class);
                        }
                        EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
                    }

                    @Override
                    public void onNetworkFailure(Exception exception) {
                        msgBuilder.append(espApp.getString(R.string.notify_node_added));
                        notificationEvent.setNotificationMsg(msgBuilder.toString());
                        EspDatabase.getInstance(espApp).getNotificationDao().insertOrUpdate(notificationEvent);
                        Log.d(TAG, "Node added Notification inserted in database");
                        if (isNotificationAllowed) {
                            sendNotification(title, msgBuilder.toString(), AppConstants.CHANNEL_NODE_ADDED, SplashActivity.class);
                        }
                        EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
                    }
                });
            }
        }


    }

    // Event type - Node removed

    private void processNodeRemovedEvent(String title, NotificationEvent notificationEvent, JSONObject jsonEventData) {

        EspApplication espApp = (EspApplication) getApplicationContext();
        JSONArray nodeJsonArray = jsonEventData.optJSONArray(AppConstants.KEY_NODES);
        StringBuilder msgBuilder = new StringBuilder();
        ArrayList<String> deviceNames = new ArrayList<>();

        if (nodeJsonArray != null && nodeJsonArray.length() > 0) {

            for (int nodeIndex = 0; nodeIndex < nodeJsonArray.length(); nodeIndex++) {
                String nodeId = nodeJsonArray.optString(nodeIndex);
                if (espApp.nodeMap.get(nodeId) != null) {
                    ArrayList<Device> devices = espApp.nodeMap.get(nodeId).getDevices();
                    if (devices != null) {
                        for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
                            deviceNames.add(devices.get(deviceIndex).getUserVisibleName());
                        }
                    }
                }
                espApp.removeNodeInformation(nodeId);
            }
        }

        if (deviceNames.size() > 0) {

            if (deviceNames.size() == 1) {
                msgBuilder.append(deviceNames.get(0));
                msgBuilder.append(" is removed.");
            } else {
                for (int i = 0; i < deviceNames.size(); i++) {

                    if (i != 0) {
                        msgBuilder.append(",");
                        msgBuilder.append(" ");
                    }
                    msgBuilder.append(deviceNames.get(i));
                }
                msgBuilder.append(" are removed.");
            }
        }

        if (TextUtils.isEmpty(msgBuilder.toString())) {
            msgBuilder.append(espApp.getString(R.string.notify_node_removed));
        }
        notificationEvent.setNotificationMsg(msgBuilder.toString());
        EspDatabase.getInstance(espApp).getNotificationDao().insertOrUpdate(notificationEvent);
        Log.d(TAG, "Node removed Notification inserted in database");
        if (isNotificationAllowed) {
            sendNotification(title, msgBuilder.toString(), AppConstants.CHANNEL_NODE_REMOVED, SplashActivity.class);
        }
        EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
    }

    // Event type - Node sharing add

    private void processSharingAddEvent(String title, NotificationEvent notificationEvent, JSONObject jsonEventData) {

        EspApplication espApp = (EspApplication) getApplicationContext();
        String primaryUserName = jsonEventData.optString(AppConstants.KEY_PRIMARY_USER_NAME);
        String secondaryUserName = jsonEventData.optString(AppConstants.KEY_SECONDARY_USER_NAME);
        String requestId = jsonEventData.optString(AppConstants.KEY_REQ_ID);
        boolean accepted = jsonEventData.optBoolean(AppConstants.KEY_REQ_ACCEPT);
        JSONObject metadataJson = jsonEventData.optJSONObject(AppConstants.KEY_METADATA);
        StringBuilder msgBuilder = new StringBuilder();
        ArrayList<String> deviceNames = new ArrayList<>();

        if (!jsonEventData.has(AppConstants.KEY_REQ_ACCEPT)) {

            if (!TextUtils.isEmpty(primaryUserName) && !TextUtils.isEmpty(requestId)) {

                msgBuilder.append(primaryUserName);
                msgBuilder.append(" wants to share ");

                if (metadataJson != null) {

                    JSONArray deviceJsonArray = metadataJson.optJSONArray(AppConstants.KEY_DEVICES);

                    if (deviceJsonArray != null) {

                        for (int i = 0; i < deviceJsonArray.length(); i++) {
                            JSONObject deviceObj = deviceJsonArray.optJSONObject(i);
                            if (deviceObj != null) {
                                String deviceName = deviceObj.optString(AppConstants.KEY_NAME);
                                if (!TextUtils.isEmpty(deviceName)) {
                                    deviceNames.add(deviceName);
                                }
                            }
                        }

                        int deviceListSize = deviceNames.size();
                        if (deviceListSize > 0) {

                            if (deviceNames.size() == 1) {
                                msgBuilder.append("device ");
                                msgBuilder.append(" ");
                                msgBuilder.append("(");
                                msgBuilder.append(deviceNames.get(0));
                                msgBuilder.append(")");
                            } else {
                                msgBuilder.append("devices ");
                                msgBuilder.append(" ");
                                msgBuilder.append("(");
                                for (int i = 0; i < deviceListSize; i++) {

                                    msgBuilder.append(deviceNames.get(i));
                                    if (i != (deviceListSize - 1)) {
                                        msgBuilder.append(",");
                                        msgBuilder.append(" ");
                                    }

                                    if (deviceListSize > 3 && i == 2) {
                                        msgBuilder.append("...");
                                        break;
                                    }
                                }
                                msgBuilder.append(")");
                            }
                        } else {
                            msgBuilder.append("device(s)");
                        }
                        msgBuilder.append(" with you.");

                    } else {
                        msgBuilder.append("device(s)");
                        msgBuilder.append(" with you.");
                    }
                } else {
                    msgBuilder.append("device(s)");
                    msgBuilder.append(" with you.");
                }
                msgBuilder.append(" ");
                msgBuilder.append("Tap to accept or decline.");

                Log.d(TAG, "Notification msg string  : " + msgBuilder.toString());
                sendSharingRequestNotification(title, msgBuilder.toString(), requestId);
            }

        } else {

            // Comment details case
            Log.d(TAG, "Secondary User : " + secondaryUserName);
            JSONArray nodeJsonArray = jsonEventData.optJSONArray(AppConstants.KEY_NODES);
            loadDataFromLocalStorage();

            if (nodeJsonArray != null && nodeJsonArray.length() > 0) {
                for (int nodeIndex = 0; nodeIndex < nodeJsonArray.length(); nodeIndex++) {
                    String nodeId = nodeJsonArray.optString(nodeIndex);
                    Log.d(TAG, "Node Id : " + nodeId);
                    if (espApp.nodeMap.containsKey(nodeId)) {
                        ArrayList<Device> devices = espApp.nodeMap.get(nodeId).getDevices();
                        if (devices != null) {
                            for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
                                String deviceName = devices.get(deviceIndex).getUserVisibleName();
                                if (!TextUtils.isEmpty(deviceName)) {
                                    deviceNames.add(deviceName);
                                }
                            }
                        }
                    }
                }
            }

            if (!TextUtils.isEmpty(secondaryUserName)) {

                msgBuilder.append(secondaryUserName);
                if (accepted) {
                    msgBuilder.append(" accepted sharing request for ");
                } else {
                    msgBuilder.append(" declined sharing request for ");
                }

                int deviceListSize = deviceNames.size();
                if (deviceListSize > 0) {

                    if (deviceNames.size() == 1) {
                        msgBuilder.append("device ");
                        msgBuilder.append(" ");
                        msgBuilder.append("(");
                        msgBuilder.append(deviceNames.get(0));
                        msgBuilder.append(").");
                    } else {
                        msgBuilder.append("devices ");
                        msgBuilder.append(" ");
                        msgBuilder.append("(");

                        for (int i = 0; i < deviceListSize; i++) {

                            msgBuilder.append(deviceNames.get(i));
                            if (i != (deviceListSize - 1)) {
                                msgBuilder.append(",");
                                msgBuilder.append(" ");
                            }

                            if (deviceListSize > 3 && i == 2) {
                                msgBuilder.append("...");
                                break;
                            }
                        }
                        msgBuilder.append(").");
                    }
                } else {
                    msgBuilder.append("device(s)");
                }
            }

            Log.d(TAG, "Notification msg string  : " + msgBuilder.toString());
            notificationEvent.setNotificationMsg(msgBuilder.toString());
            EspDatabase.getInstance(espApp).getNotificationDao().insertOrUpdate(notificationEvent);
            Log.d(TAG, "Node sharing add Notification inserted in database");
            sendSharingNotificationForPrimaryUser(title, msgBuilder.toString());
        }
    }

    private void sendSharingNotificationForPrimaryUser(String title, String messageBody) {

        Intent activityIntent = new Intent(espApp, NotificationsActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            contentIntent = PendingIntent.getActivity(espApp,
                    0 /* Request code */,
                    activityIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            contentIntent = PendingIntent.getActivity(espApp,
                    0 /* Request code */,
                    activityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        Notification notification = new NotificationCompat.Builder(espApp, AppConstants.CHANNEL_NODE_SHARING)
                .setSmallIcon(R.drawable.ic_notify_rainmaker)
                .setColor(espApp.getColor(R.color.color_esp_logo))
                .setContentTitle(title)
                .setContentText(messageBody)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(messageBody))
                .setContentIntent(contentIntent)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(notificationId++, notification);
    }

    private void sendSharingRequestNotification(String title, String contentText, String reqId) {

        Log.d(TAG, "Display sharing notification with request id : " + reqId);
        Intent activityIntent = new Intent(espApp, NotificationsActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            contentIntent = PendingIntent.getActivity(espApp,
                    0 /* Request code */,
                    activityIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        } else {
            contentIntent = PendingIntent.getActivity(espApp,
                    0 /* Request code */,
                    activityIntent,
                    PendingIntent.FLAG_ONE_SHOT);
        }

        int id = notificationId++;
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(espApp, AppConstants.CHANNEL_NODE_SHARING)
                .setSmallIcon(R.drawable.ic_notify_rainmaker)
                .setColor(espApp.getColor(R.color.color_esp_logo))
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(contentText))
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent);

        Intent acceptIntent = new Intent(espApp, SplashActivity.class);
        acceptIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        acceptIntent.putExtra(AppConstants.KEY_REQ_ID, reqId);
        acceptIntent.putExtra(AppConstants.KEY_ID, id);
        PendingIntent acceptPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            acceptPendingIntent = PendingIntent.getActivity(espApp,
                    notificationId++ /* Request code */,
                    acceptIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        } else {
            acceptPendingIntent = PendingIntent.getActivity(espApp,
                    notificationId++ /* Request code */,
                    acceptIntent,
                    PendingIntent.FLAG_ONE_SHOT);
        }

        Intent declineIntent = new Intent(espApp, NodeSharingActionReceiver.class);
        declineIntent.setAction(AppConstants.ACTION_DECLINE);
        declineIntent.putExtra(AppConstants.KEY_REQ_ID, reqId);
        declineIntent.putExtra(AppConstants.KEY_ID, id);
        PendingIntent declinePendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            declinePendingIntent = PendingIntent.getBroadcast(espApp,
                    notificationId++ /* Request code */,
                    declineIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        } else {
            declinePendingIntent = PendingIntent.getBroadcast(espApp,
                    notificationId++ /* Request code */,
                    declineIntent,
                    PendingIntent.FLAG_ONE_SHOT);
        }

        notificationBuilder.addAction(R.drawable.ic_notify_accept, espApp.getString(R.string.btn_accept), acceptPendingIntent);
        notificationBuilder.addAction(R.drawable.ic_notify_decline, espApp.getString(R.string.btn_deny), declinePendingIntent);

        notificationManager.notify(id, notificationBuilder.build());
    }

    // Event type - Node automation trigger

    private void processAutomationTriggerEvent(String title, NotificationEvent notificationEvent, JSONObject jsonEventData) {

        EspApplication espApp = (EspApplication) getApplicationContext();
        String automationName = jsonEventData.optString(AppConstants.KEY_AUTOMATION_NAME);
        JSONArray actionJsonArr = jsonEventData.optJSONArray(AppConstants.KEY_ACTIONS);
        JSONArray statusJsonArr = jsonEventData.optJSONArray(AppConstants.KEY_STATUS);

        Log.d(TAG, "Automation name : " + automationName);
        title = "Automation: " + automationName;
        StringBuilder msgBuilder = new StringBuilder();
        ArrayList<String> successDeviceNames = new ArrayList<>();
        ArrayList<String> failedDeviceNames = new ArrayList<>();

        if (actionJsonArr != null) {
            for (int actionIndex = 0; actionIndex < actionJsonArr.length(); actionIndex++) {
                JSONObject actionJson = actionJsonArr.optJSONObject(actionIndex);
                String actionDeviceNodeId = actionJson.optString(AppConstants.KEY_NODE_ID);
                JSONObject paramsJson = actionJson.optJSONObject(AppConstants.KEY_PARAMS);

                if (!espApp.nodeMap.containsKey(actionDeviceNodeId)) {
                    ApiManager.getInstance(espApp).getNodeDetails(actionDeviceNodeId);
                }

                if (!espApp.nodeMap.containsKey(actionDeviceNodeId)) {
                    loadDataFromLocalStorage();
                }

                if (espApp.nodeMap.containsKey(actionDeviceNodeId)) {

                    EspNode node = espApp.nodeMap.get(actionDeviceNodeId);
                    ArrayList<Device> devices = node.getDevices();
                    if (devices != null) {

                        for (Device device : devices) {
                            if (paramsJson.has(device.getDeviceName())) {
                                if (getStatusForNodeId(statusJsonArr, actionDeviceNodeId)) {
                                    successDeviceNames.add(device.getUserVisibleName());
                                } else {
                                    failedDeviceNames.add(device.getUserVisibleName());
                                }
                                break;
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Node id is not available for this event.");
                }
            }
        }


        if (successDeviceNames.size() > 0) {
            msgBuilder.append("Successfully executed action for ");

            if (successDeviceNames.size() == 1) {
                msgBuilder.append("device: ");
            } else {
                msgBuilder.append("devices: ");
            }
            msgBuilder.append(getDeviceNameString(successDeviceNames));
        }

        if (failedDeviceNames.size() > 0) {

            if (!TextUtils.isEmpty(msgBuilder.toString())) {
                msgBuilder.append(";");
            }
            msgBuilder.append("Failed to execute action for ");

            if (failedDeviceNames.size() == 1) {
                msgBuilder.append("device: ");
            } else {
                msgBuilder.append("devices: ");
            }
            msgBuilder.append(getDeviceNameString(failedDeviceNames));
        }

        if (TextUtils.isEmpty(msgBuilder.toString())) {
            msgBuilder.append(espApp.getString(R.string.notify_node_automation_trigger));
        } else {
            msgBuilder.append(".");
        }

        StringBuilder notificationMsg = new StringBuilder();
        notificationMsg.append(title);
        notificationMsg.append("\n");
        notificationMsg.append(msgBuilder);
        notificationEvent.setNotificationMsg(notificationMsg.toString());
        EspDatabase.getInstance(espApp).getNotificationDao().insertOrUpdate(notificationEvent);
        Log.d(TAG, "Automation Notification inserted in database");
        if (isNotificationAllowed) {
            sendNotification(title, msgBuilder.toString(), AppConstants.CHANNEL_NODE_AUTOMATION_TRIGGER, NotificationsActivity.class);
        }
    }

    // Event type - Alert

    private void processAlertEvent(String title, NotificationEvent notificationEvent, JSONObject jsonEventData) {

        EspApplication espApp = (EspApplication) getApplicationContext();
        String nodeId = jsonEventData.optString(AppConstants.KEY_NODE_ID);
        String msgBody = jsonEventData.optString(AppConstants.KEY_MESSAGE_BODY);
        Log.d(TAG, "Node Id : " + nodeId);
        Log.d(TAG, "Message body : " + msgBody);
        StringBuilder msgBuilder = new StringBuilder();
        JSONObject payloadJson = null;

        try {
            payloadJson = new JSONObject(msgBody);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (payloadJson != null) {
            String alertStr = payloadJson.optString(AppConstants.KEY_ALERT_STRING);
            Log.e(TAG, "Alert string : " + alertStr);
            if (TextUtils.isEmpty(alertStr)) {

                if (!espApp.nodeMap.containsKey(nodeId)) {
                    ApiManager.getInstance(espApp).getNodeDetails(nodeId);
                }

                if (!espApp.nodeMap.containsKey(nodeId)) {
                    loadDataFromLocalStorage();
                }

                if (espApp.nodeMap.containsKey(nodeId)) {
                    EspNode node = espApp.nodeMap.get(nodeId);
                    JsonDataParser.setAllParams(espApp, node, payloadJson);
                    ArrayList<Device> devices = node.getDevices();

                    if (devices != null) {
                        for (int i = 0; i < devices.size(); i++) {

                            ArrayList<Param> params = devices.get(i).getParams();
                            String deviceName = devices.get(i).getDeviceName();
                            JSONObject deviceJson = payloadJson.optJSONObject(deviceName);

                            if (deviceJson != null) {

                                msgBuilder.append(devices.get(i).getUserVisibleName());
                                msgBuilder.append(" ");
                                msgBuilder.append("reported");
                                msgBuilder.append(" ");

                                for (int j = 0; j < params.size(); j++) {

                                    Param param = params.get(j);
                                    String key = param.getName();

                                    if (!param.isDynamicParam()) {
                                        continue;
                                    }

                                    if (deviceJson.has(key)) {
                                        msgBuilder.append(key);
                                        msgBuilder.append(" : ");
                                        String dataType = param.getDataType();

                                        if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {
                                            boolean value = deviceJson.optBoolean(key);
                                            msgBuilder.append("" + value);
                                        } else if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {
                                            int value = deviceJson.optInt(key);
                                            msgBuilder.append("" + value);
                                        } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {
                                            double value = deviceJson.optDouble(key);
                                            msgBuilder.append("" + value);
                                        } else if (dataType.equalsIgnoreCase("String")) {
                                            String value = deviceJson.optString(key);
                                            msgBuilder.append(value);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Node is not available in node map");
                }

                if (TextUtils.isEmpty(msgBuilder.toString())) {
                    msgBuilder.append(espApp.getString(R.string.notify_node_alert));
                }
            } else {
                msgBuilder.append(alertStr);
            }
        }

        notificationEvent.setNotificationMsg(msgBuilder.toString());
        EspDatabase.getInstance(espApp).getNotificationDao().insertOrUpdate(notificationEvent);
        Log.d(TAG, "Alert Notification inserted in database");
        if (isNotificationAllowed) {
            sendNotification(title, msgBuilder.toString(), AppConstants.CHANNEL_ALERT, NotificationsActivity.class);
        }
        // Send event for UI update
        EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
    }

    // Event type - Node OTA

    private void processNodeOta(String title, NotificationEvent notificationEvent, JSONObject jsonEventData) {

        EspApplication espApp = (EspApplication) getApplicationContext();
        String nodeId = jsonEventData.optString(AppConstants.KEY_NODE_ID);
        StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append("New OTA is available");
        Log.d(TAG, "Node Id : " + nodeId);

        if (!espApp.nodeMap.containsKey(nodeId)) {
            ApiManager.getInstance(espApp).getNodeDetails(nodeId);
        }

        if (!espApp.nodeMap.containsKey(nodeId)) {
            loadDataFromLocalStorage();
        }

        if (espApp.nodeMap.containsKey(nodeId)) {

            EspNode node = espApp.nodeMap.get(nodeId);
            ArrayList<Device> devices = node.getDevices();
            ArrayList<String> deviceNames = new ArrayList<>();
            if (devices != null) {
                for (int deviceIndex = 0; deviceIndex < devices.size(); deviceIndex++) {
                    deviceNames.add(devices.get(deviceIndex).getUserVisibleName());
                }
            }
            msgBuilder.append(" for ");
            msgBuilder.append(getDeviceNameString(deviceNames));

        } else {
            Log.e(TAG, "Node id is not available for this event.");
        }

        notificationEvent.setNotificationMsg(msgBuilder.toString());
        EspDatabase.getInstance(espApp).getNotificationDao().insertOrUpdate(notificationEvent);
        Log.d(TAG, "OTA Notification inserted in database");
        if (isNotificationAllowed) {
            sendOtaNotification(title, msgBuilder.toString(), AppConstants.CHANNEL_ADMIN);
        }
    }

    private void sendNotification(String title, String messageBody, String channelId, Class activityClass) {

        Intent activityIntent = new Intent(espApp, activityClass);

        if (activityClass.equals(SplashActivity.class)) {
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else if (activityClass.equals(NotificationsActivity.class)) {
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        PendingIntent contentIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            contentIntent = PendingIntent.getActivity(espApp,
                    0 /* Request code */,
                    activityIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            contentIntent = PendingIntent.getActivity(espApp,
                    0 /* Request code */,
                    activityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        Notification notification = new NotificationCompat.Builder(espApp, channelId)
                .setSmallIcon(R.drawable.ic_notify_rainmaker)
                .setColor(espApp.getColor(R.color.color_esp_logo))
                .setContentTitle(title)
                .setContentText(messageBody)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(messageBody))
                .setContentIntent(contentIntent)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        notificationManager.notify(notificationId++, notification);
    }

    private void sendOtaNotification(String title, String messageBody, String nodeId) {

        Intent activityIntent = new Intent(espApp, FwUpdateActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.putExtra(AppConstants.KEY_NODE_ID, nodeId);

        PendingIntent contentIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            contentIntent = PendingIntent.getActivity(espApp,
                    0 /* Request code */,
                    activityIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            contentIntent = PendingIntent.getActivity(espApp,
                    0 /* Request code */,
                    activityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        Notification notification = new NotificationCompat.Builder(espApp, AppConstants.CHANNEL_ADMIN)
                .setSmallIcon(R.drawable.ic_notify_rainmaker)
                .setColor(espApp.getColor(R.color.color_esp_logo))
                .setContentTitle(title)
                .setContentText(messageBody)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(messageBody))
                .setContentIntent(contentIntent)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        notificationManager.notify(notificationId++, notification);
    }

    private void loadDataFromLocalStorage() {

        EspDatabase espDatabase = EspDatabase.getInstance(getApplicationContext());
        ArrayList<EspNode> nodeList = (ArrayList<EspNode>) espDatabase.getNodeDao().getNodesFromStorage();

        for (int nodeIndex = 0; nodeIndex < nodeList.size(); nodeIndex++) {

            EspNode node = nodeList.get(nodeIndex);

            if (node != null) {

                String configData = node.getConfigData();
                String paramData = node.getParamData();

                if (configData != null) {
                    try {
                        node = JsonDataParser.setNodeConfig(node, new JSONObject(configData));
                        if (paramData != null) {
                            JSONObject paramsJson = new JSONObject(paramData);
                            JsonDataParser.setAllParams(espApp, node, paramsJson);
                        } else {
                            Log.e(TAG, "Param configuration is not available.");
                        }
                        espApp.nodeMap.put(node.getNodeId(), node);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e(TAG, "Node configuration is not available.");
                }
            }
        }

        if (BuildConfig.isNodeGroupingSupported) {
            ArrayList<Group> groupList = (ArrayList<Group>) espDatabase.getGroupDao().getGroupsFromStorage();
            for (int groupIndex = 0; groupIndex < groupList.size(); groupIndex++) {

                Group group = groupList.get(groupIndex);
                if (group != null) {
                    espApp.groupMap.put(group.getGroupId(), group);
                }
            }
        }
        Log.d(TAG, "Node list size from local storage : " + espApp.nodeMap.size());
    }

    private String getDeviceNameString(ArrayList<String> deviceNames) {

        StringBuilder deviceNameString = new StringBuilder();
        if (deviceNames.size() > 0) {

            if (deviceNames.size() == 1) {
                deviceNameString.append(deviceNames.get(0));
            } else {
                for (int i = 0; i < deviceNames.size(); i++) {

                    if (i != 0) {
                        deviceNameString.append(",");
                        deviceNameString.append(" ");
                    }
                    deviceNameString.append(deviceNames.get(i));
                }
            }
        }
        return deviceNameString.toString();
    }

    private boolean getStatusForNodeId(JSONArray statusJsonArr, String nodeId) {
        if (statusJsonArr != null) {

            for (int statusIndex = 0; statusIndex < statusJsonArr.length(); statusIndex++) {

                JSONObject statusJson = statusJsonArr.optJSONObject(statusIndex);
                String nId = statusJson.optString(AppConstants.KEY_NODE_ID);

                if (!TextUtils.isEmpty(nId) && nId.equals(nodeId)) {

                    // Node found
                    String status = statusJson.optString(AppConstants.KEY_STATUS);
                    if (!TextUtils.isEmpty(status) && status.equalsIgnoreCase("success")) {
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private boolean shouldSendNotification() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && notificationManager.areNotificationsEnabled())
                || (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU);
    }

    // Event type - Group Share
    private void processGroupSharingAddEvent(String title, NotificationEvent notificationEvent, JSONObject jsonEventData) {
        espApp = (EspApplication) getApplicationContext();
        String eventType = jsonEventData.optString(AppConstants.KEY_EVENT_TYPE);
        String sharedFrom = jsonEventData.optString(AppConstants.KEY_SHARED_FROM);
        String requestId = jsonEventData.optString(AppConstants.KEY_REQ_ID);
        boolean accept = jsonEventData.optBoolean(AppConstants.KEY_REQ_ACCEPT);
        String sharedTo = jsonEventData.optString(AppConstants.KEY_SHARED_TO);

        JSONArray groupsJsonArray = jsonEventData.optJSONArray(AppConstants.KEY_GROUPS);
        String groupName = "";

        if (groupsJsonArray != null && groupsJsonArray.length() > 0) {
            JSONObject groupJson = groupsJsonArray.optJSONObject(0);
            groupName = groupJson.optString(AppConstants.KEY_GROUP_NAME);
        }

        StringBuilder msgBuilder = new StringBuilder();

        if (!TextUtils.isEmpty(sharedFrom) && !TextUtils.isEmpty(groupName) && !accept) {

            msgBuilder.append(sharedFrom);
            msgBuilder.append(" is trying to share group ");
            msgBuilder.append(groupName);
            msgBuilder.append(" with you.");
            msgBuilder.append(" ");
            msgBuilder.append("Tap to accept or decline.");

            sendGroupSharingRequestNotification(title, msgBuilder.toString(), requestId);

        } else if (accept) {

            msgBuilder.append(sharedTo);
            msgBuilder.append(" has accepted your request for group ");
            msgBuilder.append(groupName);
            msgBuilder.append(".");

            sendNotification(title, msgBuilder.toString(), AppConstants.CHANNEL_GROUP_SHARING, SplashActivity.class);

        } else {

            msgBuilder.append(sharedTo);
            msgBuilder.append(" has denied your request for group ");
            msgBuilder.append(groupName);
            msgBuilder.append(".");

            sendNotification(title, msgBuilder.toString(), AppConstants.CHANNEL_GROUP_SHARING, SplashActivity.class);
        }
    }

    private void sendGroupSharingRequestNotification(String title, String contentText, String requestId) {
        Log.d(TAG, "Display group sharing notification with request id : " + requestId + "title, sendGroupSharingRequestNotification : " + title);

        Intent activityIntent = new Intent(espApp, GroupShareActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            contentIntent = PendingIntent.getActivity(espApp,
                    0 /* Request code */,
                    activityIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        } else {
            contentIntent = PendingIntent.getActivity(espApp,
                    0 /* Request code */,
                    activityIntent,
                    PendingIntent.FLAG_ONE_SHOT);
        }
        int id = notificationId++;
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(espApp, AppConstants.CHANNEL_GROUP_SHARING)
                .setSmallIcon(R.drawable.ic_notify_rainmaker)
                .setColor(espApp.getColor(R.color.color_esp_logo))
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(contentText))
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent);

        Intent acceptIntent = new Intent(espApp, GroupSharingActionReceiver.class);
        acceptIntent.setAction(AppConstants.ACTION_ACCEPT);
        acceptIntent.putExtra(AppConstants.KEY_REQ_ID, requestId);
        acceptIntent.putExtra(AppConstants.KEY_ID, id);
        PendingIntent acceptPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            acceptPendingIntent = PendingIntent.getBroadcast(espApp,
                    notificationId++ /* Request code */,
                    acceptIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        } else {
            acceptPendingIntent = PendingIntent.getBroadcast(espApp,
                    notificationId++ /* Request code */,
                    acceptIntent,
                    PendingIntent.FLAG_ONE_SHOT);
        }

        notificationBuilder.addAction(R.drawable.ic_notify_accept, espApp.getString(R.string.btn_accept), acceptPendingIntent);

        Intent declineIntent = new Intent(espApp, GroupSharingActionReceiver.class);
        declineIntent.setAction(AppConstants.ACTION_DECLINE);
        declineIntent.putExtra(AppConstants.KEY_REQ_ID, requestId);
        declineIntent.putExtra(AppConstants.KEY_ID, id);
        PendingIntent declinePendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            declinePendingIntent = PendingIntent.getBroadcast(espApp,
                    notificationId++ /* Request code */,
                    declineIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        } else {
            declinePendingIntent = PendingIntent.getBroadcast(espApp,
                    notificationId++ /* Request code */,
                    declineIntent,
                    PendingIntent.FLAG_ONE_SHOT);
        }

        notificationBuilder.addAction(R.drawable.ic_notify_decline, espApp.getString(R.string.btn_deny), declinePendingIntent);

        notificationManager.notify(id, notificationBuilder.build());

    }

    private void processGroupShareAddedEvent(String title, NotificationEvent notificationEvent, JSONObject jsonEventData) {
        espApp = (EspApplication) getApplicationContext();

        JSONArray groupsJsonArray = jsonEventData.optJSONArray(AppConstants.KEY_GROUPS);
        String groupName = "";

        if (groupsJsonArray != null && groupsJsonArray.length() > 0) {
            JSONObject groupJson = groupsJsonArray.optJSONObject(0);
            groupName = groupJson.optString(AppConstants.KEY_GROUP_NAME);
        }

        String requestId = jsonEventData.optString(AppConstants.KEY_REQ_ID);
        StringBuilder msgBuilder = new StringBuilder();
        if (!TextUtils.isEmpty(groupName)) {
            msgBuilder.append("Group ");
            msgBuilder.append(groupName);
            msgBuilder.append(" was added.");

        } else {
            Log.e(TAG, "Group name is empty");
        }
        sendNotification(title, msgBuilder.toString(), AppConstants.CHANNEL_GROUP_SHARING, SplashActivity.class);
    }

    private void processGroupShareRemovedEvent(String title, NotificationEvent notificationEvent, JSONObject jsonEventData) {
        espApp = (EspApplication) getApplicationContext();

        JSONArray groupsJsonArray = jsonEventData.optJSONArray(AppConstants.KEY_GROUPS);
        String sharedFrom = jsonEventData.optString(AppConstants.KEY_SHARED_FROM);
        boolean selfRemoval = jsonEventData.optBoolean(AppConstants.KEY_SELF_REMOVAL, false);

        StringBuilder msgBuilder = new StringBuilder();

        if (groupsJsonArray != null) {
            for (int i = 0; i < groupsJsonArray.length(); i++) {
                JSONObject groupJson = groupsJsonArray.optJSONObject(i);
                String groupName = groupJson.optString(AppConstants.KEY_GROUP_NAME);

                if (selfRemoval) {
                    msgBuilder.append("You have left the group ");
                    msgBuilder.append(groupName);
                    msgBuilder.append(".");
                } else {
                    if (!TextUtils.isEmpty(sharedFrom)) {
                        msgBuilder.append(sharedFrom);
                        msgBuilder.append(" has removed ");
                        msgBuilder.append(groupName);
                        msgBuilder.append(" group access from you.");
                        msgBuilder.append("\n");
                    }
                }
            }
        }
        sendNotification(title, msgBuilder.toString(), AppConstants.CHANNEL_GROUP_SHARING, SplashActivity.class);
    }
}
