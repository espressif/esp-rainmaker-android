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

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

import com.espressif.EspApplication
import com.google.android.gms.home.matter.commissioning.CommissioningCompleteMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningRequestMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningService
import com.google.android.gms.home.matter.commissioning.CommissioningService.CommissioningError

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The CommissioningService that's responsible for commissioning the device on the app's custom
 * fabric. AppCommissioningService is specified when building the
 * [com.google.android.gms.home.matter.commissioning.CommissioningRequest] in
 * [../screens.home.HomeViewModel].
 */
@AndroidEntryPoint
class AppCommissioningService : Service(), CommissioningService.Callback {

    companion object {
        const val TAG = "AppCommissioningService"
    }

    internal lateinit var chipClient: ChipClient

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var commissioningServiceDelegate: CommissioningService

    override fun onCreate() {
        super.onCreate()

        val espApp: EspApplication = applicationContext as EspApplication
        if (espApp.mGroupId != null) {
            Log.d(TAG, "Commissioning service created with RM parameters")
            chipClient = ChipClient(
                applicationContext,
                espApp.mGroupId,
                espApp.mFabricId,
                espApp.mRootCa,
                espApp.mIpk,
                espApp.groupCatIdOperate
            )
        } else {
            Log.d(TAG, "Commissioning service created without RM parameters")
            chipClient = ChipClient(applicationContext, "", "", "", "", "")
        }
        commissioningServiceDelegate = CommissioningService.Builder(this).setCallback(this).build()
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind(): intent [${intent}]")
        return commissioningServiceDelegate.asBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand(): intent [${intent}] flags [${flags}] startId [${startId}]")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")
        serviceJob.cancel()
    }

    override fun onCommissioningRequested(metadata: CommissioningRequestMetadata) {
        Log.d(
            TAG,
            "*** onCommissioningRequested ***:\n" +
                    "\tdeviceDescriptor: " +
                    "deviceType [${metadata.deviceDescriptor.deviceType}] " +
                    "vendorId [${metadata.deviceDescriptor.vendorId}] " +
                    "productId [${metadata.deviceDescriptor.productId}]\n" +
                    "\tnetworkLocation: " +
                    "IP address toString() [${metadata.networkLocation.ipAddress}] " +
                    "IP address hostAddress [${metadata.networkLocation.ipAddress.hostAddress}] " +
                    "port [${metadata.networkLocation.port}]\n" +
                    "\tpassCode [${metadata.passcode}]"
        )

        // CODELAB: onCommissioningRequested()
        // Perform commissioning on custom fabric for the sample app.
        serviceScope.launch {
//            val deviceId = getNextDeviceId(DeviceIdGenerator.Random)
            // TODO generate random  next device id
            val deviceId = 1L
            try {
                Log.d(
                    TAG,
                    "Commissioning: App fabric ESP -> ChipClient.establishPaseConnection(): deviceId [${deviceId}]"
                )
                chipClient.awaitEstablishPaseConnection(
                    deviceId,
                    metadata.networkLocation.ipAddress.hostAddress!!,
                    metadata.networkLocation.port,
                    metadata.passcode
                )

                Log.d(
                    TAG,
                    "Commissioning: App fabric ESP -> ChipClient.commissionDevice(): deviceId [${deviceId}]"
                )
                chipClient.awaitCommissionDevice(deviceId, null)
            } catch (e: Exception) {
                Log.e(TAG, "onCommissioningRequested() failed")
                e.printStackTrace()
                // No way to determine whether this was ATTESTATION_FAILED or DEVICE_UNREACHABLE.
                commissioningServiceDelegate
                    .sendCommissioningError(CommissioningError.OTHER)
                    .addOnSuccessListener {
                        Log.d(
                            TAG,
                            "Commissioning: commissioningServiceDelegate.sendCommissioningError() succeeded"
                        )
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(
                            TAG,
                            "Commissioning: commissioningServiceDelegate.sendCommissioningError() failed"
                        )
                    }
                return@launch
            }

//        Log.e(
//            TAG,
//            "Commissioning: Calling commissioningServiceDelegate.sendCommissioningComplete()"
//        )
            commissioningServiceDelegate
                .sendCommissioningComplete(
                    CommissioningCompleteMetadata.builder().setToken(deviceId.toString()).build()
                )
                .addOnSuccessListener {
                    Log.d(
                        TAG,
                        "Commissioning: commissioningServiceDelegate.sendCommissioningComplete() succeeded"
                    )
                }
                .addOnFailureListener { e ->
                    Log.e(
                        TAG,
                        "Commissioning: commissioningServiceDelegate.sendCommissioningComplete() failed"
                    )
                }
        }
//         CODELAB SECTION END
    }

    /**
     * Generates the device id for the device being commissioned ToDo() move this function into an
     * appropriate class to make it visible in HomeFragmentRecyclerViewTest
     *
     * @param generator the method used to generate the device id
     */
//    private suspend fun getNextDeviceId(generator: DeviceIdGenerator): Long {
//        return when (generator) {
//            DeviceIdGenerator.Incremental -> {
//                devicesRepository.incrementAndReturnLastDeviceId()
//            }
//            DeviceIdGenerator.Random -> {
//                generateNextDeviceId()
//            }
//        }
//    }
}
