/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.espressif.matter

import android.util.Log
import chip.devicecontroller.ChipDeviceController

/**
 * ChipDeviceController uses a CompletionListener for callbacks. This is a "base" default
 * implementation for that CompletionListener.
 */
abstract class BaseCompletionListener : ChipDeviceController.CompletionListener {

    companion object {
        const val TAG: String = "BaseCompletionListener"
    }

    override fun onConnectDeviceComplete() {
        Log.d(TAG, "onConnectDeviceComplete()")
    }

    override fun onStatusUpdate(status: Int) {
        Log.d(TAG, "onStatusUpdate(): status [${status}]")
    }

    override fun onPairingComplete(code: Long) {
        Log.d(TAG, "onPairingComplete(): code [${code}]")
    }

    override fun onPairingDeleted(errorCode: Long) {
        Log.d(TAG, "onPairingDeleted(): code [${errorCode}]")
    }

    override fun onCommissioningComplete(nodeId: Long, errorCode: Long) {
        Log.d(TAG, "onCommissioningComplete(): nodeId [${nodeId}] errorCode [${errorCode}]")
    }

    override fun onNotifyChipConnectionClosed() {
        Log.d(TAG, "onNotifyChipConnectionClosed()")
    }

    override fun onCloseBleComplete() {
        Log.d(TAG, "onCloseBleComplete()")
    }

    override fun onError(error: Throwable) {
        error.printStackTrace()
        Log.e(TAG, "onError()")
    }

    override fun onOpCSRGenerationComplete(csr: ByteArray) {
        Log.d(TAG, "onOpCSRGenerationComplete() csr [${csr}]")
    }

    override fun onReadCommissioningInfo(
        vendorId: Int,
        productId: Int,
        wifiEndpointId: Int,
        threadEndpointId: Int
    ) {
        Log.d(
            TAG,
            "onReadCommissioningInfo: vendorId [${vendorId}]  productId [${productId}]  wifiEndpointId [${wifiEndpointId}] threadEndpointId [${threadEndpointId}]"
        )
    }

    override fun onCommissioningStatusUpdate(nodeId: Long, stage: String?, errorCode: Long) {
        Log.d(
            TAG,
            "onCommissioningStatusUpdate nodeId [${nodeId}]  stage [${stage}]  errorCode [${errorCode}]"
        )
    }
}
