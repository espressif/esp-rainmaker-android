// Copyright 2021 Espressif Systems (Shanghai) PTE LTD
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

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.db.EspDatabase;
import com.espressif.rainmaker.R;
import com.espressif.rainmaker.databinding.ActivityNotificationsBinding;
import com.espressif.ui.adapters.NotificationAdapter;
import com.espressif.ui.adapters.SharingRequestAdapter;
import com.espressif.ui.models.NotificationEvent;
import com.espressif.ui.models.SharingRequest;

import java.util.ArrayList;

public class NotificationsActivity extends AppCompatActivity {

    private static final String TAG = NotificationsActivity.class.getSimpleName();

    private SharingRequestAdapter sharingRequestAdapter;
    private NotificationAdapter notificationAdapter;
    private ArrayList<SharingRequest> pendingRequests;
    private ArrayList<NotificationEvent> notifications;
    private ApiManager apiManager;

    private ActivityNotificationsBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        pendingRequests = new ArrayList<>();
        notifications = new ArrayList<>();
        apiManager = ApiManager.getInstance(getApplicationContext());

        EspDatabase espDatabase = EspDatabase.getInstance(getApplicationContext());
        ArrayList<NotificationEvent> events = (ArrayList<NotificationEvent>) espDatabase.getNotificationDao().getNotificationsFromStorage();
        if (events != null && events.size() > 0) {
            notifications = events;
        }
        Log.e(TAG, "Notification list size : " + notifications.size());
        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSharingRequests();
        getNotifications();
    }

    private void initViews() {

        setSupportActionBar(binding.toolbarLayout.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(R.string.title_activity_sharing_requests);
        binding.toolbarLayout.toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        binding.toolbarLayout.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        binding.rvRequestList.setLayoutManager(new LinearLayoutManager(this));
        sharingRequestAdapter = new SharingRequestAdapter(this, pendingRequests);
        binding.rvRequestList.setAdapter(sharingRequestAdapter);

        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        notificationAdapter = new NotificationAdapter(this, notifications);
        binding.rvNotifications.setAdapter(notificationAdapter);

        binding.swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {

            @Override
            public void onRefresh() {
                getSharingRequests();
                getNotifications();
            }
        });

        binding.progressGetSharingRequests.setVisibility(View.VISIBLE);
        binding.tvNoRequest.setVisibility(View.GONE);
        binding.ivNoRequest.setVisibility(View.GONE);
        binding.rlSharingRequests.setVisibility(View.GONE);
        binding.rlNotifications.setVisibility(View.GONE);
    }

    public void clearPendingRequest() {
        pendingRequests.clear();
        updateUI();
    }

    private void updateUI() {

        if ((pendingRequests.size() + notifications.size()) > 0) {

            binding.rlNoRequest.setVisibility(View.GONE);

            if (pendingRequests.size() > 0) {
                binding.rlSharingRequests.setVisibility(View.VISIBLE);
            } else {
                binding.rlSharingRequests.setVisibility(View.GONE);
            }
            if (notifications.size() > 0) {
                binding.rlNotifications.setVisibility(View.VISIBLE);
            } else {
                binding.rlNotifications.setVisibility(View.GONE);
            }

        } else {
            binding.tvNoRequest.setText(R.string.no_sharing_requests);
            binding.rlNoRequest.setVisibility(View.VISIBLE);
            binding.tvNoRequest.setVisibility(View.VISIBLE);
            binding.ivNoRequest.setVisibility(View.VISIBLE);
            binding.rlSharingRequests.setVisibility(View.GONE);
            binding.rlNotifications.setVisibility(View.GONE);
        }
        binding.progressGetSharingRequests.setVisibility(View.GONE);
        sharingRequestAdapter.notifyDataSetChanged();
        binding.swipeContainer.setRefreshing(false);
    }

    private void getSharingRequests() {

        apiManager.getSharingRequests(false, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                pendingRequests.clear();
                if (data != null) {
                    ArrayList<SharingRequest> requests = data.getParcelableArrayList(AppConstants.KEY_SHARING_REQUESTS);
                    if (requests != null && requests.size() > 0) {
                        for (int i = 0; i < requests.size(); i++) {
                            SharingRequest req = requests.get(i);
                            if (AppConstants.KEY_REQ_STATUS_PENDING.equals(req.getReqStatus())) {
                                pendingRequests.add(req);
                            }
                        }
                    }
                }
                updateUI();
                binding.progressGetSharingRequests.setVisibility(View.GONE);
                binding.swipeContainer.setRefreshing(false);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                binding.progressGetSharingRequests.setVisibility(View.GONE);
                binding.swipeContainer.setRefreshing(false);
                if (exception instanceof CloudException) {
                    Toast.makeText(NotificationsActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(NotificationsActivity.this, R.string.error_get_sharing_request, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                binding.progressGetSharingRequests.setVisibility(View.GONE);
                binding.swipeContainer.setRefreshing(false);
                if (exception instanceof CloudException) {
                    Toast.makeText(NotificationsActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(NotificationsActivity.this, R.string.error_notification, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void getNotifications() {
        EspDatabase espDatabase = EspDatabase.getInstance(getApplicationContext());
        ArrayList<NotificationEvent> events = (ArrayList<NotificationEvent>) espDatabase.getNotificationDao().getNotificationsFromStorage();
        if (events != null && events.size() > 0) {
            notifications = events;
        }
        notificationAdapter.updateList(notifications);
    }

    public void showLoading(String msg) {
        binding.rlPendingRequests.setAlpha(0.3f);
        binding.rlProgress.setVisibility(View.VISIBLE);
        TextView progressText = findViewById(R.id.tv_loading);
        progressText.setText(msg);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    public void hideLoading() {
        binding.rlPendingRequests.setAlpha(1);
        binding.rlProgress.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }
}
