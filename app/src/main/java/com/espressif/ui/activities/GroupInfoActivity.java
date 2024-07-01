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

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.GroupSharedWithAdapter;
import com.espressif.ui.adapters.GroupSharingPendingRequestAdapter;
import com.espressif.ui.models.Group;
import com.espressif.ui.models.GroupSharingRequest;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;

public class GroupInfoActivity extends AppCompatActivity {

    private static final String Tag = GroupInfoActivity.class.getSimpleName();
    private ArrayList<GroupSharingRequest> pendingRequest;
    private ArrayList<String> primaryUsers;
    private ContentLoadingProgressBar loadingProgressBar, loadingAddMember;
    private RecyclerView recyclerViewPending, recyclerViewApproved;
    private GroupSharingPendingRequestAdapter groupSharingPendingRequestAdapter;
    private GroupSharedWithAdapter groupSharedWithAdapter;
    private ApiManager apiManager;
    private Group group;
    private TextView tvPendingRequest, tvApprovedRequest, tvGroupName, tvAddMember, tvGroupIsMatter;
    private ImageView ivRightArrow;
    private EspApplication espApp;
    private LinearLayout llNoRequest;
    private RelativeLayout rlNoRequest, rlRemoveRequest, rlAddMember;
    private boolean isPendingRequestsLoaded = false;
    private boolean isSharedWithLoaded = false;

    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_info);

        initViews();

        showLoading(getString(R.string.progress_fetching_group_sharing_request));
        getGroupSharingInfoForPendingRequests();
        getGroupSharingInfoSharedWith();
        handleAddMemberClick();

    }

    private void initViews() {

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setTitle(R.string.title_activity_group_detail);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left);

        toolbar.setNavigationOnClickListener(v -> finish());

        loadingProgressBar = findViewById(R.id.progress_fetch_sharing_request);
        loadingAddMember = findViewById(R.id.progress_add_member);
        ivRightArrow = findViewById(R.id.iv_right_arrow);
        llNoRequest = findViewById(R.id.ll_no_request);
        rlNoRequest = findViewById(R.id.rl_no_request);
        rlRemoveRequest = findViewById(R.id.rl_remove_requests);
        tvApprovedRequest = findViewById(R.id.tv_approved_requests);
        tvPendingRequest = findViewById(R.id.tv_pending_request);
        tvGroupName = findViewById(R.id.tv_group_name);
        tvAddMember = findViewById(R.id.tv_add_member);
        tvAddMember.setText(R.string.add_member);
        rlAddMember = findViewById(R.id.rl_add_member);
        tvGroupIsMatter = findViewById(R.id.tv_group_isMatter_value);

        pendingRequest = new ArrayList<>();
        primaryUsers = new ArrayList<>();
        espApp = (EspApplication) this.getApplicationContext();
        apiManager = ApiManager.getInstance(getApplicationContext());
        group = getIntent().getParcelableExtra(AppConstants.KEY_GROUP);
        tvGroupName.setText(group.getGroupName());

        if (!group.isPrimary()) {
            rlAddMember.setVisibility(View.GONE);
        }

        if (group.isMatter()) {
            tvGroupIsMatter.setText(R.string.matter_fabric_yes);
        } else {
            tvGroupIsMatter.setText(R.string.matter_fabric_no);
        }

        LinearLayoutManager linearLayoutManagerPending = new LinearLayoutManager(getApplicationContext());
        recyclerViewPending = findViewById(R.id.rv_pending_request);
        recyclerViewPending.setLayoutManager(linearLayoutManagerPending);
        groupSharingPendingRequestAdapter = new GroupSharingPendingRequestAdapter(this, pendingRequest, group);
        recyclerViewPending.setAdapter(groupSharingPendingRequestAdapter);
        DividerItemDecoration dividerItemDecorationPending = new DividerItemDecoration(recyclerViewPending.getContext(), linearLayoutManagerPending.getOrientation());
        recyclerViewPending.addItemDecoration(dividerItemDecorationPending);

        LinearLayoutManager linearLayoutManagerApproved = new LinearLayoutManager(getApplicationContext());
        recyclerViewApproved = findViewById(R.id.recyclerView_approved);
        recyclerViewApproved.setLayoutManager(linearLayoutManagerApproved);
        groupSharedWithAdapter = new GroupSharedWithAdapter(this, primaryUsers, group);
        recyclerViewApproved.setAdapter(groupSharedWithAdapter);
        DividerItemDecoration dividerItemDecorationApproved = new DividerItemDecoration(recyclerViewApproved.getContext(), linearLayoutManagerApproved.getOrientation());
        recyclerViewApproved.addItemDecoration(dividerItemDecorationApproved);

    }

    private void handleAddMemberClick() {
        rlAddMember.setOnClickListener(v -> {
            shareGroupRequest();
        });
    }

    private void getGroupSharingInfoForPendingRequests() {

        apiManager.getGroupSharingRequests(true, new ApiResponseListener() {
            @Override
            public void onSuccess(Bundle data) {

                boolean hasPendingRequests = false;
                if (data != null) {
                    ArrayList<GroupSharingRequest> groupSharingRequests = data.getParcelableArrayList(AppConstants.KEY_SHARING_REQUESTS);
                    if (groupSharingRequests != null && !groupSharingRequests.isEmpty()) {
                        for (GroupSharingRequest request : groupSharingRequests) {
                            if (AppConstants.KEY_REQ_STATUS_PENDING.equals(request.getReqStatus()) && request.getGroup_ids().contains(group.getGroupId())) {
                                if (!pendingRequest.contains(request)) {
                                    pendingRequest.add(request);
                                }
                                hasPendingRequests = true;
                            }
                        }
                    }
                }
                if (hasPendingRequests) {
                    tvPendingRequest.setVisibility(View.VISIBLE);
                }

                groupSharingPendingRequestAdapter.sortPendingRequestsByRemainingDays();
                groupSharingPendingRequestAdapter.notifyDataSetChanged();
                isPendingRequestsLoaded = true;
                checkLoadingComplete();

            }

            @Override
            public void onResponseFailure(@NonNull Exception exception) {
                if (exception instanceof CloudException) {
                    Toast.makeText(GroupInfoActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupInfoActivity.this, "Failed to get group requests due to response failure", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(@NonNull Exception exception) {
                if (exception instanceof CloudException) {
                    Toast.makeText(GroupInfoActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupInfoActivity.this, "Failed to get group requests due to network failure", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void showLoading(String msg) {
        recyclerViewPending.setAlpha(0.3f);
        loadingProgressBar.setVisibility(View.VISIBLE);
        loadingProgressBar.show();
        getWindow().setFlags(View.INVISIBLE, View.INVISIBLE);
    }

    public void hideLoading() {
        recyclerViewPending.setAlpha(1f);
        loadingProgressBar.setVisibility(View.GONE);
        getWindow().clearFlags(View.INVISIBLE);
    }

    private void getGroupSharingInfoSharedWith() {

        SharedPreferences sharedPreferences = this.getApplicationContext().getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        String userEmail = sharedPreferences.getString(AppConstants.KEY_EMAIL, "");
        apiManager.getGroupSharing(group.getGroupId(), new ApiResponseListener() {
            @Override
            public void onSuccess(Bundle data) {

                Log.d(Tag, "data in getgroupsharing" + data);
                boolean hasApprovedRequests = false;

                if (data != null) {

                    ArrayList<String> primaryUsersName = data.getStringArrayList(AppConstants.KEY_PRIMARY_USERS);
                    ArrayList<String> secondaryUsersName = data.getStringArrayList(AppConstants.KEY_SECONDARY_USERS);
                    if (primaryUsersName != null && secondaryUsersName != null) {

                        if (primaryUsersName.contains(userEmail)) {

                            primaryUsersName.remove(userEmail);
                            primaryUsers.addAll(primaryUsersName);
                            primaryUsers.addAll(secondaryUsersName);
                        } else {
                            primaryUsers.addAll(primaryUsersName);
                            tvApprovedRequest.setText(R.string.node_shared_by);
                        }
                        hasApprovedRequests = !primaryUsers.isEmpty();
                    }
                }
                if (hasApprovedRequests) {
                    tvApprovedRequest.setVisibility(View.VISIBLE);
                }

                isSharedWithLoaded = true;
                checkLoadingComplete();
            }

            @Override
            public void onResponseFailure(@NonNull Exception exception) {

                if (exception instanceof CloudException) {
                    Toast.makeText(GroupInfoActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupInfoActivity.this, "Failed to get group requests due to response failure", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(@NonNull Exception exception) {

                if (exception instanceof CloudException) {
                    Toast.makeText(GroupInfoActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupInfoActivity.this, "Failed to get group requests due to network failure", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void checkLoadingComplete() {
        if (isPendingRequestsLoaded && isSharedWithLoaded) {
            hideLoading();
            loadingAddMember.setVisibility(View.GONE);
            ivRightArrow.setVisibility(View.VISIBLE);
            updateUI();
        }
    }

    private void shareGroupRequest() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        LayoutInflater inflater = LayoutInflater.from(this);
        final View dialogView = inflater.inflate(R.layout.dialog_group_share_email, null);
        builder.setView(dialogView);

        final EditText etEmail = dialogView.findViewById(R.id.et_email);
        final CheckBox checkboxPrimary = dialogView.findViewById(R.id.checkbox_primary);
        builder.setTitle(R.string.add_member);

        builder.setPositiveButton(R.string.btn_share, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                String userName = etEmail.getText().toString();
                userName = userName.trim();
                boolean isPrimary = checkboxPrimary.isChecked();

                if (TextUtils.isEmpty(userName)) {
                    etEmail.setError(getString(R.string.error_username_empty));
                } else {
                    dialog.dismiss();
                    ivRightArrow.setVisibility(View.GONE);
                    loadingAddMember.setVisibility(View.VISIBLE);

                    apiManager.shareGroupWithUser(group.getGroupId(), userName, isPrimary, new ApiResponseListener() {
                        @Override
                        public void onSuccess(@Nullable Bundle data) {

                            loadingAddMember.setVisibility(View.VISIBLE);
                            pendingRequest.clear();
                            getGroupSharingInfoForPendingRequests();
                        }

                        @Override
                        public void onResponseFailure(@NonNull Exception exception) {
                            loadingAddMember.setVisibility(View.GONE);
                            ivRightArrow.setVisibility(View.VISIBLE);
                            if (exception instanceof CloudException) {
                                Toast.makeText(GroupInfoActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(GroupInfoActivity.this, "Failed to share group due to response failure", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onNetworkFailure(@NonNull Exception exception) {
                            loadingAddMember.setVisibility(View.GONE);
                            ivRightArrow.setVisibility(View.VISIBLE);
                            if (exception instanceof CloudException) {
                                Toast.makeText(GroupInfoActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(GroupInfoActivity.this, "Failed to share group due to network failure", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }


    private void updateUI() {

        if (pendingRequest.isEmpty() && primaryUsers.isEmpty()) {

            recyclerViewPending.setVisibility(View.GONE);
            recyclerViewApproved.setVisibility(View.GONE);
            rlNoRequest.setVisibility(View.VISIBLE);
            llNoRequest.setVisibility(View.VISIBLE);

        } else {

            recyclerViewPending.setVisibility(View.VISIBLE);
            recyclerViewApproved.setVisibility(View.VISIBLE);
            groupSharingPendingRequestAdapter.notifyDataSetChanged();
            groupSharedWithAdapter.notifyDataSetChanged();
            rlNoRequest.setVisibility(View.GONE);
            llNoRequest.setVisibility(View.GONE);

        }
    }
}
