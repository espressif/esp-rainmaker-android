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

package com.espressif

import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

import org.json.JSONException

class EspFcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "EspFcmService"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        // If you want to send messages to this application instance or manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
//        sendRegistrationToServer(token);
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.e(
            TAG,
            "========================================== onMessageReceived =========================================="
        )
        val msgData: Map<String, String> = remoteMessage.data
        val title = msgData[AppConstants.KEY_TITLE]
        val body = msgData[AppConstants.KEY_BODY]
        val eventPayload = msgData[AppConstants.KEY_EVENT_DATA_PAYLOAD]

        Log.d(TAG, "Title : $title")
        Log.d(TAG, "Body : $body")
        Log.d(TAG, "Event payload : $eventPayload")
        Log.d(TAG, "remoteMessage : " + remoteMessage.data.toString())

        if (eventPayload != null) {
            try {
                val data = Data.Builder()
                    .putString(AppConstants.KEY_TITLE, title)
                    .putString(AppConstants.KEY_BODY, body)
                    .putString(AppConstants.KEY_EVENT_DATA_PAYLOAD, eventPayload)
                    .build()
                val workRequest = OneTimeWorkRequest.Builder(NotificationWorker::class.java)
                    .setInputData(data)
                    .build()
                WorkManager.getInstance().enqueue(workRequest)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }
}