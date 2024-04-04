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

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import com.espressif.AppConstants
import com.espressif.AppConstants.Companion.UpdateEventType
import com.espressif.EspApplication
import com.espressif.ui.models.EspNode
import com.espressif.ui.models.UpdateEvent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.greenrobot.eventbus.EventBus

class ChipClientHelper constructor(private val espApp: EspApplication) {

    companion object {
        const val TAG = "ChipClientHelper"
    }

    suspend fun initChipClient(matterNodeId: String) {

        Log.d(TAG, "Init ChipController for matter node id : $matterNodeId")
        if (TextUtils.isEmpty(matterNodeId)) {
            Log.e(
                TAG,
                "======= Init ChipController will not be done. Matter node id is not available"
            )
            EventBus.getDefault()
                .post(UpdateEvent(UpdateEventType.EVENT_MATTER_DEVICE_CONNECTIVITY))
            return;
        }

        for ((_, g) in espApp.groupMap.entries) {
            if (g.isMatter) {
                val nodeDetails = g.nodeDetails
                if (nodeDetails != null) {
                    for ((nodeId, mNodeId) in nodeDetails.entries) {
                        var fabricId = ""
                        var ipk = ""
                        var rootCa = ""
                        var groupCatIdOperate = ""
                        if (matterNodeId != mNodeId) {
                            continue
                        }
                        Log.d(
                            TAG,
                            "Node detail, node id : $nodeId and matter node id : $matterNodeId"
                        )
                        if (g.fabricDetails != null) {
                            fabricId = g.fabricDetails.fabricId
                            rootCa = g.fabricDetails.rootCa
                            ipk = g.fabricDetails.ipk
                            groupCatIdOperate = g.fabricDetails.groupCatIdOperate
                            if (!espApp.chipClientMap.containsKey(matterNodeId)) {
                                if (!TextUtils.isEmpty(fabricId) && !TextUtils.isEmpty(rootCa)
                                    && !TextUtils.isEmpty(ipk) && !TextUtils.isEmpty(matterNodeId) && !TextUtils.isEmpty(
                                        matterNodeId
                                    )
                                ) {
                                    val chipClient = ChipClient(
                                        espApp, g.groupId, fabricId, rootCa, ipk, groupCatIdOperate
                                    )
                                    espApp.chipClientMap.put(matterNodeId, chipClient)
                                }
                            }
                            espApp.fetchDeviceMatterInfo(matterNodeId, nodeId)
                            val node: EspNode? = espApp.nodeMap.get(nodeId)
                            if (node != null) {
                                val nodeType = node.newNodeType
                                if (!TextUtils.isEmpty(nodeType) && nodeType == AppConstants.NODE_TYPE_PURE_MATTER) {
                                    espApp.addParamsForMatterOnlyDevice(nodeId, matterNodeId, node)
                                }
                            }
                            Log.d(TAG, "Init and fetch cluster info done for the device")
                        }
                    }
                }
            }
        }
        var updateEvent = UpdateEvent(UpdateEventType.EVENT_MATTER_DEVICE_CONNECTIVITY)
        var data = Bundle()
        data.putString(AppConstants.KEY_MATTER_NODE_ID, matterNodeId)
        updateEvent.data = data
        EventBus.getDefault().post(updateEvent)
    }

    fun initChipClientInBackground(matterNodeId: String) =
        GlobalScope.future { initChipClient(matterNodeId) }
}
