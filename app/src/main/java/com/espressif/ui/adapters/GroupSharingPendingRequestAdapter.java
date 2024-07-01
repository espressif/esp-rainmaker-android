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
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.ui.activities.GroupInfoActivity;
import com.espressif.ui.models.Group;
import com.espressif.ui.models.GroupSharingRequest;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class GroupSharingPendingRequestAdapter extends RecyclerView.Adapter<GroupSharingPendingRequestAdapter.GroupSharingPendingRequestVH> {

    private static final String TAG = GroupSharingPendingRequestAdapter.class.getSimpleName();
    private ArrayList<GroupSharingRequest> pendingRequests;
    private Activity context;
    private ApiManager apiManager;
    private Group group;

    public GroupSharingPendingRequestAdapter(Activity context, ArrayList<GroupSharingRequest> pendingRequests, Group group) {

        this.context = context;
        this.pendingRequests = pendingRequests;
        this.apiManager = ApiManager.getInstance(context);
        this.group = group;

        sortPendingRequestsByRemainingDays();
    }

    public GroupSharingPendingRequestVH onCreateViewHolder(ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(context).inflate(R.layout.item_group_request_pending, parent, false);
        return new GroupSharingPendingRequestVH(view);
    }

    public void onBindViewHolder(final GroupSharingPendingRequestVH groupSharingPendingRequestVH, int position) {

        GroupSharingRequest groupSharingRequest = pendingRequests.get(position);
        StringBuilder text = new StringBuilder();

        if (!TextUtils.isEmpty(groupSharingRequest.getReqId())) {
            text.append(displayGeneralText(groupSharingRequest));
        }

        groupSharingPendingRequestVH.textView.setText(text.toString());

        if (groupSharingRequest.getReqTime() != 0) {

            int remainingDays = getRemainingDays(groupSharingRequest.getReqTime());
            if (remainingDays >= 0 && remainingDays <= 7) {

                String timeStampString = context.getString(R.string.expires_in);
                timeStampString = timeStampString + " " + remainingDays;
                timeStampString = timeStampString + " " + context.getString(R.string.expire_days);
                if (remainingDays == 0) {
                    groupSharingPendingRequestVH.pendingRequestTime.setText(R.string.expires_today);
                } else {
                    groupSharingPendingRequestVH.pendingRequestTime.setText(timeStampString);
                }
                groupSharingPendingRequestVH.pendingRequestTime.setVisibility(View.VISIBLE);
            } else {
                groupSharingPendingRequestVH.pendingRequestTime.setVisibility(View.GONE);
            }
        } else {
            groupSharingPendingRequestVH.pendingRequestTime.setVisibility(View.GONE);
        }

        groupSharingPendingRequestVH.buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setCancelable(false);
                builder.setTitle(R.string.btn_cancel);
                builder.setMessage(R.string.dialog_msg_confirmation_cancel_group_request);

                builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cancelGroupSharingRequest(groupSharingPendingRequestVH.getAdapterPosition(), groupSharingPendingRequestVH);
                        groupSharingPendingRequestVH.buttonCancel.setVisibility(View.GONE);
                        groupSharingPendingRequestVH.loadingRemoveMember.setVisibility(View.VISIBLE);
                    }
                });

                builder.setNegativeButton(R.string.btn_no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            }
        });
    }

    private void cancelGroupSharingRequest(int position, GroupSharingPendingRequestVH groupSharingPendingRequestVH) {

        apiManager.removeGroupSharingRequest(pendingRequests.get(position).getReqId(), new ApiResponseListener() {

            @Override
            public void onSuccess(@Nullable Bundle data) {

                pendingRequests.remove(position);
                sortPendingRequestsByRemainingDays();
                notifyDataSetChanged();
                groupSharingPendingRequestVH.loadingRemoveMember.setVisibility(View.GONE);
                groupSharingPendingRequestVH.buttonCancel.setVisibility(View.VISIBLE);
                if (pendingRequests.isEmpty()) {
                    ((GroupInfoActivity) context).findViewById(R.id.tv_pending_request).setVisibility(View.GONE);
                }
            }

            @Override
            public void onResponseFailure(@NonNull Exception exception) {
                groupSharingPendingRequestVH.loadingRemoveMember.setVisibility(View.GONE);
                groupSharingPendingRequestVH.buttonCancel.setVisibility(View.VISIBLE);
                if (exception instanceof CloudException) {
                    Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Failed to remove group request due to response failure", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(@NonNull Exception exception) {
                groupSharingPendingRequestVH.loadingRemoveMember.setVisibility(View.GONE);
                groupSharingPendingRequestVH.buttonCancel.setVisibility(View.VISIBLE);
                if (exception instanceof CloudException) {
                    Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Failed to remove group request due to network failure", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void sortPendingRequestsByRemainingDays() {
        Collections.sort(pendingRequests, new Comparator<GroupSharingRequest>() {
            @Override
            public int compare(GroupSharingRequest request1, GroupSharingRequest request2) {
                int remainingDays1 = getRemainingDays(request1.getReqTime());
                int remainingDays2 = getRemainingDays(request2.getReqTime());
                return Integer.compare(remainingDays1, remainingDays2);
            }
        });
    }

    private int getRemainingDays(long timestamp) {
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
        Date date = new Date(timestamp * 1000);
        String pendingReqDate = formatter.format(date);
        String currentDate = formatter.format(new Date());

        Date date1 = null, date2 = null;
        try {
            date1 = formatter.parse(pendingReqDate);
            date2 = formatter.parse(currentDate);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        long diff = date2.getTime() - date1.getTime();
        long diffInDays = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
        int remainingDays = (int) (7 - diffInDays);
        Log.d(TAG, "Remaining days for timestamp " + timestamp + ": " + remainingDays);
        return remainingDays;
    }

    private String displayGeneralText(GroupSharingRequest request) {

        StringBuilder text = new StringBuilder();
        ArrayList<String> usernames = request.getUserName();
        if (usernames != null && !usernames.isEmpty()) {
            text.append(TextUtils.join(", ", usernames));
        }
        return text.toString();
    }

    @Override
    public int getItemCount() {
        return pendingRequests.size();
    }

    public class GroupSharingPendingRequestVH extends RecyclerView.ViewHolder {

        TextView textView, pendingRequestTime;
        ImageView buttonCancel;
        ContentLoadingProgressBar loadingRemoveMember;

        public GroupSharingPendingRequestVH(View view) {

            super(view);
            textView = view.findViewById(R.id.text_view_request_pending_with);
            buttonCancel = view.findViewById(R.id.iv_remove);
            pendingRequestTime = view.findViewById(R.id.pr_time);
            loadingRemoveMember = view.findViewById(R.id.progress_remove_member);
        }
    }
}
