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

package com.espressif.mdns;

import android.util.Log;

import com.espressif.provisioning.listeners.ResponseListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class is used to send data to device which is available locally.
 */
public class mDNSTransport {

    private String TAG = mDNSTransport.class.getSimpleName();

    private String baseUrl;
    private ExecutorService workerThreadPool;

    public mDNSTransport(String baseUrl) {
        this.baseUrl = baseUrl;
        this.workerThreadPool = Executors.newSingleThreadExecutor();
    }

    private byte[] sendPostRequest(String path, byte[] data, final ResponseListener listener) {
        byte[] responseBytes = null;
        try {
            URL url = new URL(baseUrl + "/" + path);
            Log.e(TAG, "URL : " + url.toString());
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoOutput(true);

            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Accept", "text/plain");
            urlConnection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
            urlConnection.setConnectTimeout(5000);
            urlConnection.setReadTimeout(5000);

            OutputStream os = urlConnection.getOutputStream();
            os.write(data);
            os.close();

            int responseCode = urlConnection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                int n;
                byte[] byteChunk = new byte[4096];
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                InputStream is = urlConnection.getInputStream();
                while ((n = is.read(byteChunk)) > 0) {
                    outputStream.write(byteChunk, 0, n);
                }
                responseBytes = outputStream.toByteArray();
            }
        } catch (MalformedURLException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            listener.onFailure(new RuntimeException("Error ! Connection Lost"));
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            listener.onFailure(new RuntimeException("Error ! Connection Lost"));
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            listener.onFailure(new RuntimeException("Error ! Connection Lost"));
        }

        return responseBytes;
    }

    public void sendData(final String path, final byte[] data, final ResponseListener listener) {
        this.workerThreadPool
                .submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            byte[] returnData = sendPostRequest(path, data, listener);

                            if (returnData == null) {
                                listener.onFailure(new RuntimeException("Response not received."));
                            } else {
                                listener.onSuccess(returnData);
                            }
                        } catch (Exception e) {
                            listener.onFailure(e);
                        }
                    }
                });
    }
}
