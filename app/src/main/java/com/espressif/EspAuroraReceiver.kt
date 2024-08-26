// Copyright 2025 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager

import cn.jpush.android.api.CustomMessage
import cn.jpush.android.api.JPushMessage
import cn.jpush.android.api.NotificationMessage
import cn.jpush.android.service.JPushMessageReceiver
import org.json.JSONException
import org.json.JSONObject

class EspAuroraReceiver : JPushMessageReceiver() {

    companion object {
        private const val TAG = "EspAuroraReceiver"
    }

    override fun onMessage(context: Context?, message: CustomMessage?) {
        super.onMessage(context, message)
        Log.d(TAG, "onMessage : $message")
        processCustomMessage(context, message)
    }

    override fun onConnected(context: Context?, isConnected: Boolean) {
        super.onConnected(context, isConnected)
    }

    override fun onTagOperatorResult(context: Context?, jPushMessage: JPushMessage?) {
        super.onTagOperatorResult(context, jPushMessage)
        Log.d(TAG, "onTagOperatorResult : $jPushMessage")
    }

    override fun getNotification(context: Context, message: NotificationMessage?): Notification? {
        Log.d(TAG, "getNotificationResult : $message")

        // By default JPush creates a notification channel with Id - "JPush_3_7", and sends RAW JSON in push notification without parsing payload.
        // Therefore deleting the channel, disabling JPUSH from sending push notifications

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.deleteNotificationChannel("JPush_3_7")
        return null
    }

    override fun onCheckTagOperatorResult(context: Context?, jPushMessage: JPushMessage?) {
        super.onCheckTagOperatorResult(context, jPushMessage)
        Log.d(TAG, "onCheckTagOperatorResult : $jPushMessage")
    }

    override fun onAliasOperatorResult(context: Context?, jPushMessage: JPushMessage?) {
        super.onAliasOperatorResult(context, jPushMessage)
        Log.d(TAG, "onAliasOperatorResult : $jPushMessage")
    }

    override fun onMobileNumberOperatorResult(context: Context?, jPushMessage: JPushMessage?) {
        super.onMobileNumberOperatorResult(context, jPushMessage)
        Log.d(TAG, "onMobileNumberOperatorResult : $jPushMessage")
    }

    override fun onNotifyMessageArrived(context: Context, message: NotificationMessage?) {
        super.onNotifyMessageArrived(context, message)
        Log.d(
            TAG,
            "========================================== onNotifyMessageArrived =========================================="
        )

        // By default JPush creates a notification channel with Id - "JPush_3_7", and sends RAW JSON in push notification without parsing payload.
        // Therefore deleting the channel, disabling JPUSH from sending push notifications
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.deleteNotificationChannel("JPush_3_7")

        processNotification(context, message)
    }

    override fun onNotifyMessageOpened(context: Context?, message: NotificationMessage?) {
        super.onNotifyMessageOpened(context, message)
        Log.d(TAG, "onNotifyMessageOpened : $message")
    }

    private fun processNotification(context: Context?, message: NotificationMessage?) {
        if (message != null && context != null) {
            try {
                val messageData = JSONObject(message.notificationContent)
                val eventPayload = messageData.optString("event_data_payload")
                val title = messageData.optString("title", "No Title")
                val body = messageData.optString("body", "No Body")

                Log.d(TAG, "Title : $title")
                Log.d(TAG, "Body : $body")
                Log.d(TAG, "Event payload : $eventPayload")

                if (eventPayload.isNotEmpty()) {
                    val data = Data.Builder()
                        .putString(AppConstants.KEY_TITLE, title)
                        .putString(AppConstants.KEY_BODY, body)
                        .putString(AppConstants.KEY_EVENT_DATA_PAYLOAD, eventPayload)
                        .build()

                    val workRequest = OneTimeWorkRequest.Builder(NotificationWorker::class.java)
                        .setInputData(data)
                        .build()

                    WorkManager.getInstance(context).enqueue(workRequest)
                }
            } catch (e: JSONException) {
                Log.e(TAG, "Failed to parse notification message: ", e)
            }
        } else {
            Log.e(TAG, "Context or message is null")
        }
    }

    private fun processCustomMessage(context: Context?, message: CustomMessage?) {
        if (message != null && context != null) {
            try {
                val messageData = JSONObject(message.message)
                val eventPayload = messageData.optString("event_data_payload")
                val title = messageData.optString("title", "No Title")
                val body = messageData.optString("body", "No Body")

                Log.d(TAG, "Title : $title")
                Log.d(TAG, "Body : $body")
                Log.d(TAG, "Event payload : $eventPayload")

                if (eventPayload.isNotEmpty()) {
                    val data = Data.Builder()
                        .putString(AppConstants.KEY_TITLE, title)
                        .putString(AppConstants.KEY_BODY, body)
                        .putString(AppConstants.KEY_EVENT_DATA_PAYLOAD, eventPayload)
                        .build()

                    val workRequest = OneTimeWorkRequest.Builder(NotificationWorker::class.java)
                        .setInputData(data)
                        .build()

                    WorkManager.getInstance(context).enqueue(workRequest)
                }
            } catch (e: JSONException) {
                Log.e(TAG, "Failed to parse notification message: ", e)
            }
        } else {
            Log.e(TAG, "Context or message is null")
        }
    }
}
