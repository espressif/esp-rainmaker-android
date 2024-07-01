// Copyright 2024 Espressif Systems (Shanghai) PTE LTD
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.ui.activities.GroupDetailActivity;
import com.espressif.ui.activities.SplashActivity;

public class GroupSharingActionReceiver extends BroadcastReceiver {

    private static final String TAG = "GroupSharingReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        String requestId = intent.getStringExtra(AppConstants.KEY_REQ_ID);
        int notificationId = intent.getIntExtra(AppConstants.KEY_ID, 0);

        if (!TextUtils.isEmpty(requestId)) {

            if (AppConstants.ACTION_ACCEPT.equals(action)) {

                ApiManager.getInstance(context).updateGroupSharingRequest(requestId, true, new ApiResponseListener() {

                    @Override
                    public void onSuccess(@Nullable Bundle data) {

                        Log.d(TAG, "Group sharing accepted.");
                        NotificationManagerCompat.from(context).cancel(notificationId);
                        Intent splashIntent = new Intent(context, SplashActivity.class);
                        splashIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(splashIntent);
                    }

                    @Override
                    public void onResponseFailure(@NonNull Exception exception) {

                        if (exception instanceof CloudException) {
                            Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Failed to accept group sharing due to response failure", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onNetworkFailure(@NonNull Exception exception) {

                        if (exception instanceof CloudException) {
                            Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Failed to accept group sharing due to network failure", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            } else if (AppConstants.ACTION_DECLINE.equals(action)) {

                ApiManager.getInstance(context).updateGroupSharingRequest(requestId, false, new ApiResponseListener() {
                    @Override
                    public void onSuccess(@Nullable Bundle data) {

                        Log.d(TAG, "Group sharing declined.");
                        NotificationManagerCompat.from(context).cancel(notificationId);
                    }

                    @Override
                    public void onResponseFailure(@NonNull Exception exception) {

                        if (exception instanceof CloudException) {
                            Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Failed to decline group sharing due to response failure", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onNetworkFailure(@NonNull Exception exception) {

                        if (exception instanceof CloudException) {
                            Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Failed to decline group sharing due to network failure", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }
    }
}
