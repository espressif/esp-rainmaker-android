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

package com.espressif.ui.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.rainmaker.R;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.SharingRequest;

import java.util.ArrayList;

public class NodeDetailsAdapter extends RecyclerView.Adapter<NodeDetailsAdapter.NodeDetailViewHolder> {

    private Context context;
    private ArrayList<String> nodeInfoList;
    private ArrayList<String> nodeInfoValueList;
    private ArrayList<SharingRequest> sharingRequests;
    private SharedUserAdapter userAdapter;
    private EspNode node;

    public NodeDetailsAdapter(Context context, ArrayList<String> nodeInfoList, ArrayList<String> nodeValueList,
                              EspNode node, ArrayList<SharingRequest> sharingRequests) {
        this.context = context;
        this.nodeInfoList = nodeInfoList;
        this.nodeInfoValueList = nodeValueList;
        this.sharingRequests = sharingRequests;
        this.node = node;
    }

    @Override
    public NodeDetailViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_node_info, parent, false);
        NodeDetailViewHolder vh = new NodeDetailViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull NodeDetailViewHolder myViewHolder, final int position) {

        // set the data in items
        myViewHolder.tvNodeInfoLabel.setText(nodeInfoList.get(position));

        if (nodeInfoList.get(position).equals(context.getString(R.string.node_shared_with))
                || nodeInfoList.get(position).equals(context.getString(R.string.node_shared_by))) {

            myViewHolder.rvSharedUsers.setVisibility(View.VISIBLE);
            myViewHolder.tvNodeInfoValue.setVisibility(View.GONE);

            // set a LinearLayoutManager with default orientation
            LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
            linearLayoutManager.setOrientation(RecyclerView.VERTICAL);
            myViewHolder.rvSharedUsers.setLayoutManager(linearLayoutManager);
            DividerItemDecoration itemDecor = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            myViewHolder.rvSharedUsers.addItemDecoration(itemDecor);
            userAdapter = new SharedUserAdapter(context, node, sharingRequests, false);
            myViewHolder.rvSharedUsers.setAdapter(userAdapter);

        } else if (nodeInfoList.get(position).equals(context.getString(R.string.pending_requests))) {

            myViewHolder.rvSharedUsers.setVisibility(View.VISIBLE);
            myViewHolder.tvNodeInfoValue.setVisibility(View.GONE);

            // set a LinearLayoutManager with default orientation
            LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
            linearLayoutManager.setOrientation(RecyclerView.VERTICAL);
            myViewHolder.rvSharedUsers.setLayoutManager(linearLayoutManager);
            DividerItemDecoration itemDecor = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            myViewHolder.rvSharedUsers.addItemDecoration(itemDecor);
            userAdapter = new SharedUserAdapter(context, node, sharingRequests, true);
            myViewHolder.rvSharedUsers.setAdapter(userAdapter);

        } else if (!TextUtils.isEmpty(nodeInfoValueList.get(position))) {

            myViewHolder.rvSharedUsers.setVisibility(View.GONE);
            myViewHolder.tvNodeInfoValue.setVisibility(View.VISIBLE);
            myViewHolder.tvNodeInfoValue.setText(nodeInfoValueList.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return nodeInfoList.size();
    }

    static class NodeDetailViewHolder extends RecyclerView.ViewHolder {

        TextView tvNodeInfoLabel, tvNodeInfoValue;
        RecyclerView rvSharedUsers;

        public NodeDetailViewHolder(View itemView) {
            super(itemView);

            tvNodeInfoLabel = itemView.findViewById(R.id.tv_node_label);
            tvNodeInfoValue = itemView.findViewById(R.id.tv_node_value);
            rvSharedUsers = itemView.findViewById(R.id.rv_users_list);
        }
    }
}
