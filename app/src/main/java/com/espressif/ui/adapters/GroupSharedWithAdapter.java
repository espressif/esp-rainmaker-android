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
import com.espressif.ui.models.Group;

import java.util.ArrayList;

public class GroupSharedWithAdapter extends RecyclerView.Adapter<GroupSharedWithAdapter.GroupSharedWithVH> {

    private ArrayList<String> primaryUsers;
    private Activity context;
    private ApiManager apiManager;
    private Group group;

    public GroupSharedWithAdapter(Activity context, ArrayList<String> primaryUsers, Group group) {

        this.context = context;
        this.primaryUsers = primaryUsers;
        this.apiManager = ApiManager.getInstance(context);
        this.group = group;
    }

    public GroupSharedWithVH onCreateViewHolder(ViewGroup parent, int viewType) {

        View groupSharedWithView = LayoutInflater.from(context).inflate(R.layout.item_group_request_shared_with, parent, false);
        return new GroupSharedWithVH(groupSharedWithView);
    }

    public void onBindViewHolder(final GroupSharedWithVH groupSharedWithVH, int position) {

        String primaryUser = primaryUsers.get(position);

        groupSharedWithVH.textView.setText(primaryUser);

        if (group.isPrimary()) {
            groupSharedWithVH.buttonRevoke.setVisibility(View.VISIBLE);
        } else {
            groupSharedWithVH.buttonRevoke.setVisibility(View.GONE);
        }

        groupSharedWithVH.buttonRevoke.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setCancelable(false);
                builder.setTitle(R.string.dialog_title_revoke_group_access);
                builder.setMessage(R.string.dialog_msg_confirmation_revoke_group_access);
                int position = groupSharedWithVH.getAdapterPosition();
                builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        removeGroupSharing(position, groupSharedWithVH);
                        groupSharedWithVH.buttonRevoke.setVisibility(View.GONE);
                        groupSharedWithVH.loadingRemoveMember.setVisibility(View.VISIBLE);
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

    private void removeGroupSharing(int position, GroupSharedWithVH groupSharedWithVH) {

        String primaryUser = primaryUsers.get(position);

        apiManager.removeGroupSharing(group.getGroupId(), primaryUser, new ApiResponseListener() {
            @Override
            public void onSuccess(@Nullable Bundle data) {
                primaryUsers.remove(position);
                notifyDataSetChanged();
                groupSharedWithVH.loadingRemoveMember.setVisibility(View.GONE);
                groupSharedWithVH.buttonRevoke.setVisibility(View.VISIBLE);
                Toast.makeText(context, "Group access removed successfully", Toast.LENGTH_SHORT).show();

            }

            @Override
            public void onResponseFailure(@NonNull Exception exception) {
                groupSharedWithVH.loadingRemoveMember.setVisibility(View.GONE);
                groupSharedWithVH.buttonRevoke.setVisibility(View.VISIBLE);
                if (exception instanceof CloudException) {
                    Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Failed to remove group access due to response failure", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(@NonNull Exception exception) {
                groupSharedWithVH.loadingRemoveMember.setVisibility(View.GONE);
                groupSharedWithVH.buttonRevoke.setVisibility(View.VISIBLE);
                if (exception instanceof CloudException) {
                    Toast.makeText(context, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Failed to remove group access due to network failure", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return primaryUsers.size();
    }

    public class GroupSharedWithVH extends RecyclerView.ViewHolder {
        TextView textView;
        ImageView buttonRevoke;
        ContentLoadingProgressBar loadingRemoveMember;

        public GroupSharedWithVH(View view) {

            super(view);
            textView = view.findViewById(R.id.text_view_shared_with);
            buttonRevoke = view.findViewById(R.id.iv_remove);
            loadingRemoveMember = view.findViewById(R.id.progress_remove_member);
        }
    }
}
