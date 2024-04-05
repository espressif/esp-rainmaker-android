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

package com.espressif.ui.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.GroupShareAdapter;
import com.espressif.ui.models.GroupSharingRequest;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;

public class GroupShareActivity extends AppCompatActivity {
    public static final String Tag = GroupShareActivity.class.getSimpleName();
    private ArrayList<GroupSharingRequest> pendingRequest;
    private String groupId;
    private GroupShareAdapter adapter;
    private ApiManager apiManager;
    private LinearLayout llNoRequest;
    private ContentLoadingProgressBar loadingProgressBar;
    private TextView tvNoRequest;
    private ImageView ivNoRequest;
    private RecyclerView recyclerView;
    private RelativeLayout rlNoRequest;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_share_requests);

        pendingRequest = new ArrayList<>();
        groupId = getIntent().getStringExtra(AppConstants.KEY_GROUP_ID);
        apiManager = ApiManager.getInstance(getApplicationContext());

        initViews();
        getGroupSharingInfo();
    }

    private void getGroupSharingInfo() {

        showLoading(getString(R.string.progress_fetching_group_sharing_request));
        apiManager.getGroupSharingRequests(false, new ApiResponseListener() {
            @Override
            public void onSuccess(@Nullable Bundle data) {
                hideLoading();
                if (data != null) {
                    ArrayList<GroupSharingRequest> groupRequests = data.getParcelableArrayList(AppConstants.KEY_SHARING_REQUESTS);
                    if (groupRequests != null && !groupRequests.isEmpty()) {
                        boolean hasPendingRequests = false;

                        for (GroupSharingRequest request : groupRequests) {

                            if (AppConstants.KEY_REQ_STATUS_PENDING.equals(request.getReqStatus())) {
                                pendingRequest.add(request);
                                hasPendingRequests = true;

                            } else if (AppConstants.KEY_REQ_STATUS_DECLINED.equals(request.getReqStatus())) {
                                Log.d(Tag, "Declined request found for group: " + request.getGroup_names());
                            }
                        }

                        if (!hasPendingRequests) {

                            showNoRequests();
                        }
                    } else {

                        showNoRequests();
                    }
                } else {

                    showNoRequests();
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onResponseFailure(@NonNull Exception exception) {

                hideLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(GroupShareActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupShareActivity.this, "Failed to get sharing requests due to response failure", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(@NonNull Exception exception) {
                hideLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(GroupShareActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupShareActivity.this, "Failed to get sharing requests due to response failure", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }


    private void initViews() {

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setTitle(R.string.title_activity_group_sharing_requests);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        rlNoRequest = findViewById(R.id.rl_no_request);
        llNoRequest = findViewById(R.id.ll_no_request);
        recyclerView = findViewById(R.id.recyclerView);
        loadingProgressBar = findViewById(R.id.progress_fetch_sharing_request);
        tvNoRequest = findViewById(R.id.tv_no_request);
        ivNoRequest = findViewById(R.id.iv_no_request);
        ivNoRequest.setVisibility(View.GONE);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(linearLayoutManager);
        adapter = new GroupShareAdapter(this, pendingRequest);
        recyclerView.setAdapter(adapter);

    }

    public void showLoading(String msg) {
        recyclerView.setAlpha(0.3f);
        tvNoRequest.setText(msg);
        tvNoRequest.setVisibility(View.VISIBLE);
        loadingProgressBar.setVisibility(View.VISIBLE);
        loadingProgressBar.show();
        getWindow().setFlags(View.INVISIBLE, View.INVISIBLE);
    }

    public void hideLoading() {
        recyclerView.setAlpha(1f);
        loadingProgressBar.hide();
        tvNoRequest.setVisibility(View.GONE);
        getWindow().clearFlags(View.INVISIBLE);
    }

    public void refreshRequests() {
        pendingRequest.clear();
        getGroupSharingInfo();
    }

    private void showNoRequests() {
        recyclerView.setVisibility(View.GONE);
        rlNoRequest.setVisibility(View.VISIBLE);
        llNoRequest.setVisibility(View.VISIBLE);
        tvNoRequest.setText(R.string.no_sharing_requests);
        tvNoRequest.setVisibility(View.VISIBLE);
        ivNoRequest.setVisibility(View.VISIBLE);
    }
}
