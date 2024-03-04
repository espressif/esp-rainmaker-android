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

import android.util.Log
import chip.devicecontroller.model.ChipAttributePath
import chip.devicecontroller.model.InvokeElement
import chip.tlv.AnonymousTag
import chip.tlv.ContextSpecificTag
import chip.tlv.TlvWriter
import com.espressif.AppConstants
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

class ThreadBRClusterHelper constructor(
    private val chipClient: ChipClient
) {

    companion object {
        const val TAG = "TbrClusterHelper"
    }

    fun configureThreadBRAsync(
        nodeId: Long,
        endpointId: Long,
        clusterId: Long,
        datasetStr: String
    ) = GlobalScope.future {
        configureThreadBR(
            nodeId,
            endpointId,
            clusterId,
            datasetStr
        )
    }

    fun readDatasetAsync(
        nodeId: Long,
    ): CompletableFuture<ArrayList<String>> = GlobalScope.future {
        readDataset(nodeId)
    }

    suspend fun configureThreadBR(
        nodeId: Long,
        endpointId: Long,
        clusterId: Long,
        datasetStr: String
    ) {
        sendDataset(nodeId, endpointId, clusterId, 0L, datasetStr)
        startThread(nodeId, endpointId, clusterId, 1L)
    }

    suspend fun sendDataset(
        nodeId: Long,
        endpointId: Long,
        clusterId: Long,
        commandId: Long,
        datasetStr: String
    ) {
        val tlvWriter1 = TlvWriter()
        tlvWriter1.startStructure(AnonymousTag)
        tlvWriter1.put(ContextSpecificTag(0), datasetStr)
        tlvWriter1.endStructure()

        val invokeElement1 =
            InvokeElement.newInstance(
                endpointId,
                clusterId,
                commandId,
                tlvWriter1.getEncoded(),
                null
            )

        val devicePtr = chipClient.awaitGetConnectedDevicePointer(nodeId)
        val invoke = chipClient.invoke(devicePtr, invokeElement1)
        Log.d(TAG, "Send Dataset, result : $invoke")
    }

    suspend fun startThread(
        nodeId: Long,
        endpointId: Long,
        clusterId: Long,
        commandId: Long
    ) {
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
        Log.d(TAG, "Start Thread, result : $invoke")
    }

    suspend fun readDatasetAttribute(
        nodeId: Long
    ): String? {

        val devicePtr = chipClient.awaitGetConnectedDevicePointer(nodeId)

        // Read active dataset
        val attributePath =
            ChipAttributePath.newInstance(
                AppConstants.ENDPOINT_0.toLong(),
                AppConstants.THREAD_BR_CLUSTER_ID_HEX,
                0x0L
            )
        val data = chipClient.readAttribute(devicePtr, attributePath)
        val datasetValue = data?.value as ByteArray?
        val datasetStr = datasetValue?.byteArrayToDs()
        Log.d(TAG, "Active dataset : $datasetStr")
        return datasetStr
    }

    suspend fun readBaIdAttribute(
        nodeId: Long
    ): String? {

        val devicePtr = chipClient.awaitGetConnectedDevicePointer(nodeId)

        // Read border router id
        val attributePath =
            ChipAttributePath.newInstance(
                AppConstants.ENDPOINT_0.toLong(),
                AppConstants.THREAD_BR_CLUSTER_ID_HEX,
                0x2L
            )

        val data = chipClient.readAttribute(devicePtr, attributePath)
        val baIdValue = data?.value as ByteArray?
        val baIdValueStr = baIdValue?.byteArrayToDs()
        Log.d(TAG, "BA ID : $baIdValueStr")

        return baIdValueStr
    }

    suspend fun readDataset(
        nodeId: Long
    ): ArrayList<String> {

        val tbrData: ArrayList<String> = ArrayList<String>()
        val dataset = readDatasetAttribute(nodeId)
        val baId = readBaIdAttribute(nodeId)

        if (dataset != null) {
            tbrData.add(dataset)
        }
        if (baId != null) {
            tbrData.add(baId)
        }
        return tbrData
    }

    private fun ByteArray.byteArrayToDs(): String =
        joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}
