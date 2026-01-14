// Copyright 2026 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.local_control;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.text.TextUtils;
import android.util.Log;

import com.espressif.ui.models.OnNetworkDevice;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChallengeRespServiceDiscovery {

    private static final String TAG = ChallengeRespServiceDiscovery.class.getSimpleName();

    private String serviceType;
    private Context context;
    private ChallengeRespDiscoveryListener listener;

    private NsdManager mNsdManager;
    private NsdManager.ResolveListener resolveListener;
    private NsdManager.DiscoveryListener discoveryListener;
    private AtomicBoolean resolveListenerBusy = new AtomicBoolean(false);
    private ConcurrentLinkedQueue<NsdServiceInfo> pendingNsdServices = new ConcurrentLinkedQueue<>();
    private List<NsdServiceInfo> resolvedNsdServices = Collections.synchronizedList(new ArrayList<>());

    public interface ChallengeRespDiscoveryListener {
        void deviceFound(OnNetworkDevice device);
    }

    public ChallengeRespServiceDiscovery(Context context, String serviceType, ChallengeRespDiscoveryListener listener) {
        this.context = context;
        this.serviceType = serviceType;
        this.listener = listener;
        this.mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    }

    public void initializeNsd() {
        Log.d(TAG, "Initialize Network service discovery");
        initializeResolveListener();
    }

    public void discoverServices() {
        Log.d(TAG, "Discover Services");
        stopDiscovery();
        initializeDiscoveryListener();
        mNsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    public void stopDiscovery() {
        Log.d(TAG, "Stop Discovery");
        if (discoveryListener != null) {
            try {
                mNsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping discovery", e);
            }
            discoveryListener = null;
        }
    }

    private void initializeDiscoveryListener() {
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Service discovery started : " + regType);
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service discovery success : " + serviceInfo);
                if (serviceInfo.getServiceType().equals(serviceType)) {
                    if (resolveListenerBusy.compareAndSet(false, true)) {
                        if (resolveListener != null) {
                            mNsdManager.resolveService(serviceInfo, resolveListener);
                        }
                    } else {
                        String serviceName = serviceInfo.getServiceName();
                        Iterator<NsdServiceInfo> iterator = pendingNsdServices.iterator();
                        boolean isExist = false;
                        while (iterator.hasNext()) {
                            NsdServiceInfo nsdServiceInfo = iterator.next();
                            if (nsdServiceInfo.getServiceName().equals(serviceName)) {
                                isExist = true;
                                break;
                            }
                        }
                        if (!isExist) {
                            pendingNsdServices.add(serviceInfo);
                        }
                    }
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.d(TAG, "Service lost : " + service);
                Iterator<NsdServiceInfo> iterator = pendingNsdServices.iterator();
                while (iterator.hasNext()) {
                    if (iterator.next().getServiceName().equals(service.getServiceName())) {
                        iterator.remove();
                    }
                }
                synchronized (resolvedNsdServices) {
                    iterator = resolvedNsdServices.iterator();
                    while (iterator.hasNext()) {
                        if (iterator.next().getServiceName().equals(service.getServiceName())) {
                            iterator.remove();
                        }
                    }
                }
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                stopDiscovery();
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Stop discovery failed: Error code:" + errorCode);
            }
        };
    }

    private void initializeResolveListener() {
        resolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Resolve failed " + errorCode);
                resolveNextInQueue();
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Resolve Succeeded. " + serviceInfo.getHost());
                resolvedNsdServices.add(serviceInfo);

                InetAddress hostAddress = serviceInfo.getHost();
                int hostPort = serviceInfo.getPort();
                Map<String, byte[]> attributes = serviceInfo.getAttributes();

                String nodeId = "";
                int secVersion = 0;
                boolean popRequired = false;
                String chRespEndpoint = "ch_resp";

                for (Map.Entry<String, byte[]> entry : attributes.entrySet()) {
                    String key = entry.getKey();
                    String value = new String(entry.getValue());
                    Log.d(TAG, "TXT Record - Key: " + key + ", Value: " + value);

                    if (key.equals("node_id")) {
                        nodeId = value;
                    } else if (key.equals("sec_version")) {
                        try {
                            secVersion = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Failed to parse sec_version: " + value);
                        }
                    } else if (key.equals("pop_required")) {
                        popRequired = Boolean.parseBoolean(value) || value.equals("1") || value.equals("true");
                    } else if (key.equals("ch_resp")) {
                        chRespEndpoint = value;
                    }
                }

                if (!TextUtils.isEmpty(nodeId)) {
                    String ipAddress = hostAddress.toString().replace("/", "");
                    OnNetworkDevice device = new OnNetworkDevice(nodeId, serviceInfo.getServiceName(),
                            ipAddress, hostPort, secVersion, popRequired, chRespEndpoint);

                    if (listener != null) {
                        listener.deviceFound(device);
                    }
                }

                resolveNextInQueue();
            }
        };
    }

    private void resolveNextInQueue() {
        Log.d(TAG, "resolveNextInQueue");
        NsdServiceInfo nextNsdService = pendingNsdServices.poll();
        if (nextNsdService != null) {
            mNsdManager.resolveService(nextNsdService, resolveListener);
        } else {
            resolveListenerBusy.set(false);
        }
    }
}
