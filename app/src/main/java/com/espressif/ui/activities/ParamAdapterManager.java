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

package com.espressif.ui.activities;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;

import com.espressif.AppConstants;
import com.espressif.NetworkApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.rainmaker.BuildConfig;
import com.google.gson.JsonObject;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;

public class ParamAdapterManager {

    private final String TAG = ParamAdapterManager.class.getSimpleName();
    private final Context context;
    private final String deviceName;
    private final String nodeId;
    private final NetworkApiManager networkApiManager;

    private String paramName;
    private final Handler mHandler = new Handler();
    private long lastRequestTime;
    private Number lastSliderValue;
    private final Queue<Number> requestQueue = new LinkedList<>();
    private final int QUEUE_SIZE = 5;
    private final long THROTTLE_DELAY = getThrottleDelay();

    private long getThrottleDelay() {
        if (BuildConfig.continuosUpdateInterval > AppConstants.MIN_THROTTLE_DELAY
                && BuildConfig.continuosUpdateInterval < AppConstants.MAX_THROTTLE_DELAY) {
            return BuildConfig.continuosUpdateInterval;
        }
        return AppConstants.MID_THROTTLE_DELAY;
    }

    private boolean isSending = false;
    private boolean isWait = false;

    private static ParamAdapterManager paramManager;

    public ParamAdapterManager(Context context, String deviceName, String nodeId, NetworkApiManager networkApiManager) {
        this.context = context;
        this.deviceName = deviceName;
        this.nodeId = nodeId;
        this.networkApiManager = networkApiManager;
    }

    public static ParamAdapterManager getInstance(Context context, String deviceName, String nodeId, NetworkApiManager networkApiManager) {
        if (paramManager == null) {
            paramManager = new ParamAdapterManager(context, deviceName, nodeId, networkApiManager);
        }
        return paramManager;
    }

    public void processSliderChange(String paramName, Number sliderValue, boolean isMoving) {
        // Check if enough time has passed since last request
        long currentTime = System.currentTimeMillis();
        this.paramName = paramName;

        if (isMoving) {
            ((EspDeviceActivity) context).stopUpdateValueTask();

            if (sliderValue instanceof Float) {
                if (lastSliderValue != null && lastSliderValue.floatValue() == sliderValue.floatValue()) {
                    return;
                }
            } else {
                if (lastSliderValue != null && lastSliderValue.intValue() == sliderValue.intValue()) {
                    return;
                }
            }

            if (lastRequestTime != 0 && currentTime - lastRequestTime < THROTTLE_DELAY) {
                addToQueue(sliderValue, currentTime, true);
                return;
            } else {
                addToQueue(sliderValue, currentTime, false);
            }

            // Send the positions in the queue one by one
            if (!isSending) {
                isSending = true;
                new Thread(() -> {
                    processQueue();
                    isSending = false;
                }).start();
            }
        } else {
            if (lastSliderValue != sliderValue) {
                lastSliderValue = sliderValue;
            }

            new Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            // Process request when user stop moving and removes fingers.
                            addToQueue(lastSliderValue, currentTime, false);
                            processQueue();

                            // Process last request if "wait" flag is true.
                            if (isWait) {
                                processRequest(lastSliderValue);
                            }
                        }
                    },
                    1000
            );

            ((EspDeviceActivity) context).startUpdateValueTask();
        }
    }

    private void addToQueue(Number sliderValue, long currentTime, boolean clearSome) {
        if (clearSome) {
            if (requestQueue.size() == QUEUE_SIZE) {
                makeSpace();
                processQueue();
            }
            requestQueue.offer(sliderValue);
        } else {
            if (requestQueue.size() < QUEUE_SIZE) { // if queue size reaches 50
                requestQueue.offer(sliderValue); // clear some middle values
            } else if (requestQueue.size() == QUEUE_SIZE) {
                makeSpace();
                requestQueue.offer(sliderValue);
            }
        }
        lastRequestTime = currentTime;
        lastSliderValue = sliderValue;
        ((EspDeviceActivity) context).setLastUpdateRequestTime(lastRequestTime);
    }

    private void makeSpace() {
        if (!requestQueue.isEmpty()) {
            for (int i = 0; i < QUEUE_SIZE; i = i + 2) {
                int counter = 0;
                Queue<Number> tempQueue = new LinkedList<>();
                while (!requestQueue.isEmpty()) {
                    Number item = requestQueue.remove();
                    if (counter != i) {
                        tempQueue.add(item);
                    }
                    counter++;
                }
                while (!tempQueue.isEmpty()) {
                    Number item = tempQueue.remove();
                    requestQueue.offer(item);
                }
            }
        }
    }

    private void processQueue() {
        while (!requestQueue.isEmpty()) {
            processRequest(requestQueue.poll());
        }
    }

    private void processRequest(Number sliderValue) {
        JsonObject jsonParam = new JsonObject();
        JsonObject body = new JsonObject();
        if (sliderValue instanceof Float) {
            jsonParam.addProperty(paramName, sliderValue.floatValue());
        } else {
            jsonParam.addProperty(paramName, sliderValue.intValue());
        }
        body.add(deviceName, jsonParam);
        sendToServer(body);
    }

    private void sendToServer(JsonObject jsonBody) {
        if (!isWait) {
            mHandler.removeCallbacksAndMessages(null);
            Runnable newRunnable = createRunnable(jsonBody);
            mHandler.postDelayed(newRunnable, 50);
        }
    }

    private Runnable createRunnable(final JsonObject body) {
        return () -> sendValues(body);
    }

    private void sendValues(JsonObject body) {
        isWait = true;
        networkApiManager.updateParamValue(nodeId, body, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                isWait = false;
            }

            @Override
            public void onResponseFailure(Exception exception) {
                isWait = false;
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                isWait = false;
            }
        });
    }
}
