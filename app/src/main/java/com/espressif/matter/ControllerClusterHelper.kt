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

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import chip.devicecontroller.model.InvokeElement
import matter.tlv.AnonymousTag
import matter.tlv.ContextSpecificTag
import matter.tlv.TlvWriter
import com.espressif.AppConstants
import com.espressif.AppConstants.Companion.UpdateEventType
import com.espressif.EspApplication
import com.espressif.ui.models.UpdateEvent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.greenrobot.eventbus.EventBus

class ControllerClusterHelper constructor(
    private val chipClient: ChipClient,
    private val context: Context,
) {

    companion object {
        const val TAG = "ControllerCluster"
    }

    suspend fun sendTokenToDevice(
        rmNodeId: String,
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
        refreshToken: String
    ) {
        Log.d(TAG, "Send token to device process - started")
        resetRefreshToken(nodeId, endpointId, clusterId, AppConstants.COMMAND_RESET_REFRESH_TOKEN)
        appendRefreshToken(
            nodeId,
            endpointId,
            clusterId,
            AppConstants.COMMAND_APPEND_REFRESH_TOKEN,
            refreshToken
        )
        authorizeDevice(nodeId, endpointId, clusterId, AppConstants.COMMAND_AUTHORIZE_DEVICE)
        updateUserNoc(nodeId, endpointId, clusterId, AppConstants.COMMAND_UPDATE_USER_NOC)
        updateDeviceList(nodeId, endpointId, clusterId, AppConstants.COMMAND_UPDATE_DEVICE_LIST)

        val sharedPreferences = context.getSharedPreferences(
            AppConstants.ESP_PREFERENCES,
            AppCompatActivity.MODE_PRIVATE
        )
        val editor = sharedPreferences.edit()
        val key = "ctrl_setup_$rmNodeId"
        editor.putBoolean(key, true)
        editor.apply()
        EventBus.getDefault().post(UpdateEvent(UpdateEventType.EVENT_CTRL_CONFIG_DONE))
        Log.d(TAG, "Send token to device process - ended")
    }

    fun sendTokenToDeviceAsync(
        rmNodeId: String,
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
        refreshToken: String
    ) = GlobalScope.future {
        sendTokenToDevice(
            rmNodeId,
            nodeId,
            endpointId,
            clusterId,
            refreshToken
        )
    }

    suspend fun sendUpdateDeviceListEvent(
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
    ) {
        Log.d(TAG, "Update device list process - started")
        authorizeDevice(nodeId, endpointId, clusterId, AppConstants.COMMAND_AUTHORIZE_DEVICE)
        updateUserNoc(nodeId, endpointId, clusterId, AppConstants.COMMAND_UPDATE_USER_NOC)
        updateDeviceList(nodeId, endpointId, clusterId, AppConstants.COMMAND_UPDATE_DEVICE_LIST)
        Log.d(TAG, "Update device list process - ended")
    }

    fun sendUpdateDeviceListEventAsync(
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
    ) = GlobalScope.future {
        sendUpdateDeviceListEvent(
            nodeId,
            endpointId,
            clusterId
        )
    }

    suspend fun resetRefreshToken(
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
        commandId: Long
    ) {
        Log.d(TAG, "Reset refresh token")
        val tlvWriter = TlvWriter()
        tlvWriter.startStructure(AnonymousTag)
        tlvWriter.endStructure()

        val invokeElement =
            InvokeElement.newInstance(
                endpointId,
                clusterId,
                commandId,
                tlvWriter.getEncoded(),
                null
            )

        val devicePtr = chipClient.awaitGetConnectedDevicePointer(nodeId)
        val invoke = chipClient.invoke(devicePtr, invokeElement)
        Log.d(TAG, "resetRefreshToken, result : $invoke")
    }

    fun resetRefreshTokenAsync(
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
        commandId: Long
    ) = GlobalScope.future { resetRefreshToken(nodeId, endpointId, clusterId, commandId) }

    suspend fun appendRefreshToken(
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
        commandId: Long,
        refreshToken: String
    ) {
        Log.d(TAG, "Append refresh token")

        var tokenBytes = refreshToken.toByteArray(Charsets.UTF_8)
        var size: Int = tokenBytes.size

        val a = tokenBytes.copyOfRange(0, (size + 1) / 2)
        val b = tokenBytes.copyOfRange((size + 1) / 2, size)

        var firstHalf: String = String(a)
        var secondHalf: String = String(b)

        val tlvWriter1 = TlvWriter()
        tlvWriter1.startStructure(AnonymousTag)
        tlvWriter1.put(ContextSpecificTag(0), firstHalf)
        tlvWriter1.endStructure()

        val tlvWriter2 = TlvWriter()
        tlvWriter2.startStructure(AnonymousTag)
        tlvWriter2.put(ContextSpecificTag(0), secondHalf)
        tlvWriter2.endStructure()

        val invokeElement1 =
            InvokeElement.newInstance(
                endpointId,
                clusterId,
                commandId,
                tlvWriter1.getEncoded(),
                null
            )

        val invokeElement2 =
            InvokeElement.newInstance(
                endpointId,
                clusterId,
                commandId,
                tlvWriter2.getEncoded(),
                null
            )

        val devicePtr = chipClient.awaitGetConnectedDevicePointer(nodeId)
        val invoke1 = chipClient.invoke(devicePtr, invokeElement1)
        Log.d(TAG, "appendRefreshToken, result 1 : $invoke1")
        val invoke2 = chipClient.invoke(devicePtr, invokeElement2)
        Log.d(TAG, "appendRefreshToken, result 2 : $invoke2")
    }

    fun appendRefreshTokenAsync(
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
        commandId: Long,
        refreshToken: String
    ) = GlobalScope.future {
        appendRefreshToken(
            nodeId,
            endpointId,
            clusterId,
            commandId,
            refreshToken
        )
    }

    suspend fun authorizeDevice(
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
        commandId: Long
    ) {
        Log.d(TAG, "Authorize Device")
        var baseUrlBytes = EspApplication.BASE_URL.toByteArray(Charsets.UTF_8)
        var baseUrl = String(baseUrlBytes)
        val tlvWriter = TlvWriter()
        tlvWriter.startStructure(AnonymousTag)
        tlvWriter.put(
            ContextSpecificTag(0),
            baseUrl
        )
        tlvWriter.endStructure()

        val invokeElement =
            InvokeElement.newInstance(
                endpointId,
                clusterId,
                commandId,
                tlvWriter.getEncoded(),
                null
            )

        val devicePtr = chipClient.awaitGetConnectedDevicePointer(nodeId)
        val invoke = chipClient.invoke(devicePtr, invokeElement, 1000, 5000)
        Log.d(TAG, "authorizeDevice, result : $invoke")
    }

    fun authorizeDeviceAsync(
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
        commandId: Long
    ) = GlobalScope.future { authorizeDevice(nodeId, endpointId, clusterId, commandId) }

    suspend fun updateUserNoc(
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
        commandId: Long
    ) {
        Log.d(TAG, "Update user NOC")
        val tlvWriter = TlvWriter()
        tlvWriter.startStructure(AnonymousTag)
        tlvWriter.endStructure()

        val invokeElement =
            InvokeElement.newInstance(
                endpointId,
                clusterId,
                commandId,
                tlvWriter.getEncoded(),
                null
            )

        val devicePtr = chipClient.awaitGetConnectedDevicePointer(nodeId)
        val invoke = chipClient.invoke(devicePtr, invokeElement, 1000, 10000)
        Log.d(TAG, "updateUserNoc, result : $invoke")
    }

    fun updateUserNocAsync(
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
        commandId: Long
    ) = GlobalScope.future { updateUserNoc(nodeId, endpointId, clusterId, commandId) }

    suspend fun updateDeviceList(
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
        commandId: Long
    ) {
        Log.d(TAG, "Update device list")
        val tlvWriter = TlvWriter()
        tlvWriter.startStructure(AnonymousTag)
        tlvWriter.endStructure()

        val invokeElement =
            InvokeElement.newInstance(
                endpointId,
                clusterId,
                commandId,
                tlvWriter.getEncoded(),
                null
            )

        val devicePtr = chipClient.awaitGetConnectedDevicePointer(nodeId)
        val invoke = chipClient.invoke(devicePtr, invokeElement)
        Log.d(TAG, "updateDeviceList, result : $invoke")
    }

    fun updateDeviceListAsync(
        nodeId: Long,
        endpointId: Int,
        clusterId: Long,
        commandId: Long
    ) = GlobalScope.future { updateDeviceList(nodeId, endpointId, clusterId, commandId) }
}
