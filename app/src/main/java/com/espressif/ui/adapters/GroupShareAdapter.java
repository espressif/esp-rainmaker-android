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

package com.espressif.ui.adapters;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.ui.activities.GroupShareActivity;
import com.espressif.ui.models.GroupSharingRequest;

import java.util.ArrayList;


public class GroupShareAdapter extends RecyclerView.Adapter<GroupShareAdapter.GroupSharingRequestVH> {
    private ArrayList<GroupSharingRequest> sharingRequests;
    private Activity context;
    private ApiManager apiManager;

    public GroupShareAdapter(Activity context, ArrayList<GroupSharingRequest> sharingRequests) {

        this.context = context;
        this.sharingRequests = sharingRequests;
        this.apiManager = ApiManager.getInstance(context);
    }

    @NonNull
    @Override
    public GroupSharingRequestVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View groupSharingRequestView = layoutInflater.inflate(R.layout.item_sharing_request, parent, false);
        GroupSharingRequestVH groupSharingRequestVH = new GroupSharingRequestVH(groupSharingRequestView);
        return groupSharingRequestVH;
    }

    @Override
    public void onBindViewHolder(@NonNull final GroupSharingRequestVH groupSharingRequestVH, int position) {

        GroupSharingRequest groupSharingRequest = sharingRequests.get(position);

        StringBuilder text = new StringBuilder();
        text.append(displayGeneralText(groupSharingRequest));

        groupSharingRequestVH.text.setText(text.toString());
        groupSharingRequestVH.accept1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                ((GroupShareActivity) context).showLoading(context.getString(R.string.progress_accepting));

                apiManager.updateGroupSharingRequest(sharingRequests.get(groupSharingRequestVH.getAdapterPosition()).getReqId(), true, new ApiResponseListener() {
                    @Override
                    public void onSuccess(Bundle data) {
                        sharingRequests.remove(groupSharingRequestVH.getAdapterPosition());
                        notifyDataSetChanged();
                        ((GroupShareActivity) context).findViewById(R.id.iv_no_request).setVisibility(View.GONE);
                        ((GroupShareActivity) context).findViewById(R.id.tv_no_request).setVisibility(View.GONE);
                        ((GroupShareActivity) context).hideLoading();
                        ((GroupShareActivity) context).refreshRequests();
                    }

                    @Override
                    public void onResponseFailure(@NonNull Exception exception) {
                        if (exception instanceof CloudException) {
                            Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Failed to accept group request due to response failure", Toast.LENGTH_SHORT).show();
                        }
                        ((GroupShareActivity) context).hideLoading();
                    }

                    @Override
                    public void onNetworkFailure(@NonNull Exception exception) {
                        if (exception instanceof CloudException) {
                            Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Failed to accept group request due to response failure", Toast.LENGTH_SHORT).show();
                        }
                        ((GroupShareActivity) context).hideLoading();
                    }
                });
            }
        });

        groupSharingRequestVH.decline1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((GroupShareActivity) context).showLoading(context.getString(R.string.progress_declining));

                apiManager.updateGroupSharingRequest(sharingRequests.get(groupSharingRequestVH.getAdapterPosition()).getReqId(), false, new ApiResponseListener() {
                    @Override
                    public void onSuccess(Bundle data) {
                        sharingRequests.remove(groupSharingRequestVH.getAdapterPosition());
                        notifyDataSetChanged();
                        ((GroupShareActivity) context).hideLoading();
                        ((GroupShareActivity) context).findViewById(R.id.iv_no_request).setVisibility(View.GONE);
                        ((GroupShareActivity) context).findViewById(R.id.tv_no_request).setVisibility(View.GONE);
                        ((GroupShareActivity) context).refreshRequests();
                    }

                    @Override
                    public void onResponseFailure(@NonNull Exception exception) {
                        if (exception instanceof CloudException) {
                            Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Failed to decline group request due to response failure", Toast.LENGTH_SHORT).show();
                        }
                        ((GroupShareActivity) context).hideLoading();
                    }

                    @Override
                    public void onNetworkFailure(@NonNull Exception exception) {
                        if (exception instanceof CloudException) {
                            Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Failed to decline group request due to network failure", Toast.LENGTH_SHORT).show();
                        }
                        ((GroupShareActivity) context).hideLoading();
                    }
                });
            }
        });
    }


    private String displayGeneralText(GroupSharingRequest request) {
        StringBuilder text = new StringBuilder();
        text.append(context.getString(R.string.group)).append(" ");

        // Group name parsing -- delimiter
        ArrayList<String> groupNames = request.getGroup_names();
        if (groupNames != null && !groupNames.isEmpty()) {
            text.append(TextUtils.join(", ", groupNames));
        }

        text.append(" ").append(context.getString(R.string.was_shared_by)).append(" ");

        // Primary username parsing -- delimiter
        ArrayList<String> primaryUserNames = request.getPrimaryUserName();
        if (primaryUserNames != null && !primaryUserNames.isEmpty()) {
            text.append(TextUtils.join(", ", primaryUserNames));
        }

        text.append(".");
        return text.toString();
    }

    @Override
    public int getItemCount() {
        return sharingRequests.size();
    }

    static class GroupSharingRequestVH extends RecyclerView.ViewHolder {

        TextView text, accept1, decline1;

        public GroupSharingRequestVH(View view) {

            super(view);

            text = view.findViewById(R.id.tv_sharing_text);
            accept1 = view.findViewById(R.id.btn_accept);
            decline1 = view.findViewById(R.id.btn_decline);
        }
    }

}
