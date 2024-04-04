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

package com.espressif.matter

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast

import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.RecyclerView

import com.espressif.AppConstants
import com.espressif.EspApplication
import com.espressif.cloudapi.ApiManager
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.cloudapi.CloudException
import com.espressif.rainmaker.R
import com.espressif.ui.Utils
import com.espressif.ui.models.Group

class GroupSelectionAdapter(val context: Activity, val groups: MutableList<Group>) :
    RecyclerView.Adapter<GroupSelectionAdapter.GroupViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val layoutInflater = LayoutInflater.from(context)
        val v =
            layoutInflater.inflate(R.layout.item_group_selection, parent, false)
        return GroupViewHolder(v)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group: Group = groups.get(position)
        holder.tvGroupName.text = group.groupName
        holder.progressBar.visibility = View.GONE

        holder.itemView.setOnClickListener(View.OnClickListener {

            val clickedGroup: Group = groups.get(holder.getAdapterPosition())
            val espApp: EspApplication = context.applicationContext as EspApplication

            if (clickedGroup.isMatter) {
                espApp.mGroupId = clickedGroup.groupId
                espApp.mFabricId = clickedGroup.fabricId
                if (clickedGroup.fabricDetails != null) {
                    espApp.mRootCa = clickedGroup.fabricDetails.rootCa
                    espApp.mIpk = clickedGroup.fabricDetails.ipk
                    espApp.groupCatIdOperate = clickedGroup.fabricDetails.groupCatIdOperate
                    startCommissioningFlow()
                }
            } else {

                holder.progressBar.visibility = View.VISIBLE
                val apiManager = ApiManager.getInstance(context.applicationContext)
                apiManager.convertGroupToFabric(clickedGroup.groupId, object : ApiResponseListener {

                    override fun onSuccess(data: Bundle?) {
                        holder.progressBar.visibility = View.GONE

                        if (data != null) {
                            espApp.mGroupId = clickedGroup.groupId
                            espApp.mFabricId = data.getString(AppConstants.KEY_FABRIC_ID, "")
                            espApp.mRootCa = data.getString(AppConstants.KEY_ROOT_CA, "")
                            espApp.mIpk = data.getString(AppConstants.KEY_IPK, "")
                            espApp.groupCatIdOperate =
                                data.getString(AppConstants.KEY_GROUP_CAT_ID_OPERATE, "")
                        }
                        startCommissioningFlow()
                    }

                    override fun onResponseFailure(exception: Exception) {
                        holder.progressBar.visibility = View.GONE
                        if (exception is CloudException) {
                            Toast.makeText(context, exception.message, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Failed to convert group to fabric",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onNetworkFailure(exception: Exception) {
                        holder.progressBar.visibility = View.GONE
                        Utils.showAlertDialog(
                            context,
                            context.getString(R.string.dialog_title_no_network),
                            context.getString(R.string.dialog_msg_no_network),
                            false
                        )
                    }
                })
            }
        })
    }

    override fun getItemCount(): Int {
        return groups.size
    }

    private fun startCommissioningFlow() {
        val activity: GroupSelectionActivity = context as GroupSelectionActivity
        activity.commissionDevice()
//        context.finish()
    }

    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var tvGroupName: TextView
        var progressBar: ContentLoadingProgressBar

        init {
            tvGroupName = itemView.findViewById(R.id.tv_group_name)
            progressBar = itemView.findViewById(R.id.sch_progress_indicator)
        }
    }
}