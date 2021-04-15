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

package com.espressif.ui.adapters;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.rainmaker.R;
import com.espressif.ui.activities.GroupDetailActivity;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Group;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;

public class GroupNodeAdapter extends RecyclerView.Adapter<GroupNodeAdapter.GroupNodeVH> {

    private Activity context;
    private Group group;
    private ArrayList<EspNode> nodedList;
    private boolean isSelection;

    public GroupNodeAdapter(Activity context, Group group, ArrayList<EspNode> nodedList, boolean isSelection) {
        this.context = context;
        this.group = group;
        this.nodedList = nodedList;
        this.isSelection = isSelection;
    }

    @Override
    public GroupNodeVH onCreateViewHolder(ViewGroup parent, int viewType) {

        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_group_node, parent, false);
        GroupNodeVH groupNodeViewHolder = new GroupNodeVH(v);
        return groupNodeViewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull final GroupNodeVH myViewHolder, final int position) {

        EspNode node = nodedList.get(position);
        myViewHolder.tvNodeName.setText(node.getNodeName());

        if (isSelection) {
            myViewHolder.cbDevice.setVisibility(View.VISIBLE);
            myViewHolder.ivRemove.setVisibility(View.GONE);
        } else {
            myViewHolder.cbDevice.setVisibility(View.GONE);
            myViewHolder.ivRemove.setVisibility(View.VISIBLE);
        }

        myViewHolder.ivRemove.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                if (context instanceof GroupDetailActivity) {
                    String nodeId = nodedList.get(myViewHolder.getAdapterPosition()).getNodeId();
                    ((GroupDetailActivity) context).removeDevice(nodeId);
                }
            }
        });

        myViewHolder.cbDevice.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                nodedList.get(myViewHolder.getAdapterPosition()).setSelected(isChecked);
            }
        });

        myViewHolder.itemView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                myViewHolder.cbDevice.toggle();
            }
        });
        GroupDeviceAdapter adapter = new GroupDeviceAdapter(context, group, node.getDevices(), isSelection, false);
        myViewHolder.rvDevices.setAdapter(adapter);
    }

    @Override
    public int getItemCount() {
        return nodedList.size();
    }

    public class GroupNodeVH extends RecyclerView.ViewHolder {

        TextView tvNodeName;
        ImageView ivRemove;
        MaterialCheckBox cbDevice;
        RecyclerView rvDevices;

        public GroupNodeVH(View itemView) {
            super(itemView);

            tvNodeName = itemView.findViewById(R.id.tv_node_name);
            ivRemove = itemView.findViewById(R.id.iv_remove);
            cbDevice = itemView.findViewById(R.id.cb_node);
            rvDevices = itemView.findViewById(R.id.rv_device_list);

            GridLayoutManager linearLayoutManager = new GridLayoutManager(context, 2);
            rvDevices.setLayoutManager(linearLayoutManager);
        }
    }
}
