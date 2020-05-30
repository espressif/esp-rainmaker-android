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
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.rainmaker.R;

import java.util.ArrayList;

public class UserProfileAdapter extends RecyclerView.Adapter<UserProfileAdapter.MyViewHolder> {

    private Context context;
    private ArrayList<String> userInfoList;
    private ArrayList<String> userInfoValueList;
    private View.OnClickListener mOnItemClickListener;
    private boolean isUserInfoView;

    public UserProfileAdapter(Context context, ArrayList<String> userInfoList, ArrayList<String> userInfoValueList, boolean isUserInfo) {

        this.context = context;
        this.userInfoList = userInfoList;
        this.userInfoValueList = userInfoValueList;
        this.isUserInfoView = isUserInfo;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        if (isUserInfoView) {

            LayoutInflater layoutInflater = LayoutInflater.from(context);
            View v = layoutInflater.inflate(R.layout.item_user_profile, parent, false);
            MyViewHolder vh = new MyViewHolder(v); // pass the view to View Holder
            return vh;

        } else {
            LayoutInflater layoutInflater = LayoutInflater.from(context);
            View v = layoutInflater.inflate(R.layout.item_user_profile_terms, parent, false);
            MyViewHolder vh = new MyViewHolder(v); // pass the view to View Holder
            return vh;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, final int position) {

        if (isUserInfoView) {

            myViewHolder.tvUserInfoLabel.setText(userInfoList.get(position));

            if (userInfoValueList != null && !TextUtils.isEmpty(userInfoValueList.get(position))) {

                myViewHolder.tvUserInfoValue.setText(userInfoValueList.get(position));
            }
        } else {
            myViewHolder.tvUserInfoLabel.setText(userInfoList.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return userInfoList.size();
    }

    public void setOnItemClickListener(View.OnClickListener itemClickListener) {
        mOnItemClickListener = itemClickListener;
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {

        // init the item view's
        TextView tvUserInfoLabel, tvUserInfoValue;

        public MyViewHolder(View itemView) {
            super(itemView);

            // get the reference of item view's
            tvUserInfoLabel = itemView.findViewById(R.id.tv_info);
            tvUserInfoValue = itemView.findViewById(R.id.tv_value);
            itemView.setTag(this);
            itemView.setOnClickListener(mOnItemClickListener);
        }
    }
}
