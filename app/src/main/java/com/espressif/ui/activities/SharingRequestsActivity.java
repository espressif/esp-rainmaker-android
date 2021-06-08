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
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.SharingRequestAdapter;
import com.espressif.ui.models.SharingRequest;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;

public class SharingRequestsActivity extends AppCompatActivity {

    private static final String TAG = SharingRequestsActivity.class.getSimpleName();

    private RecyclerView recyclerView;
    private TextView tvNoRequest;
    private RelativeLayout rlNoRequest;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageView ivNoRequest;
    private ContentLoadingProgressBar progressBar;
    private RelativeLayout rlProgress, rlPendingReq;

    private SharingRequestAdapter notificationAdapter;
    private ArrayList<SharingRequest> pendingRequests;
    private ApiManager apiManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sharing_requests);

        pendingRequests = new ArrayList<>();
        apiManager = ApiManager.getInstance(getApplicationContext());
        initViews();
        getSharingRequests();
    }

    private void initViews() {

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setTitle(R.string.title_activity_sharing_requests);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        progressBar = findViewById(R.id.progress_get_sharing_requests);
        rlNoRequest = findViewById(R.id.rl_no_request);
        tvNoRequest = findViewById(R.id.tv_no_request);
        ivNoRequest = findViewById(R.id.iv_no_request);
        recyclerView = findViewById(R.id.rv_request_list);
        swipeRefreshLayout = findViewById(R.id.swipe_container);
        rlPendingReq = findViewById(R.id.rl_pending_requests);
        rlProgress = findViewById(R.id.rl_progress);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationAdapter = new SharingRequestAdapter(this, pendingRequests);
        recyclerView.setAdapter(notificationAdapter);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {

            @Override
            public void onRefresh() {
                getSharingRequests();
            }
        });

        progressBar.setVisibility(View.VISIBLE);
        tvNoRequest.setVisibility(View.GONE);
        ivNoRequest.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
    }

    public void clearPendingRequest() {
        pendingRequests.clear();
        updateUI();
    }

    private void updateUI() {

        if (pendingRequests.size() > 0) {

            rlNoRequest.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

        } else {
            tvNoRequest.setText(R.string.no_sharing_requests);
            rlNoRequest.setVisibility(View.VISIBLE);
            tvNoRequest.setVisibility(View.VISIBLE);
            ivNoRequest.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }
        progressBar.setVisibility(View.GONE);
        notificationAdapter.notifyDataSetChanged();
        swipeRefreshLayout.setRefreshing(false);
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
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                if (exception instanceof CloudException) {
                    Toast.makeText(SharingRequestsActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SharingRequestsActivity.this, R.string.error_get_sharing_request, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                if (exception instanceof CloudException) {
                    Toast.makeText(SharingRequestsActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SharingRequestsActivity.this, R.string.error_get_sharing_request, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void showLoading(String msg) {
        rlPendingReq.setAlpha(0.3f);
        rlProgress.setVisibility(View.VISIBLE);
        TextView progressText = findViewById(R.id.tv_loading);
        progressText.setText(msg);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    public void hideLoading() {
        rlPendingReq.setAlpha(1);
        rlProgress.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }
}
