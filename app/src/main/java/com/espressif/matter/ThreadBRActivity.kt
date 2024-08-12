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
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.espressif.AppConstants
import com.espressif.EspApplication
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityTbrBinding
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.threadnetwork.ThreadBorderAgent
import com.google.android.gms.threadnetwork.ThreadNetwork
import com.google.android.gms.threadnetwork.ThreadNetworkCredentials
import com.google.android.gms.threadnetwork.ThreadNetworkStatusCodes
import java.math.BigInteger
import java.util.concurrent.CompletableFuture

class ThreadBRActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ThreadBRActivity"
    }

    private lateinit var binding: ActivityTbrBinding
    private lateinit var preferredCredentialsLauncher: ActivityResultLauncher<IntentSenderRequest>

    private var nodeId: String? = null
    lateinit var espApp: EspApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTbrBinding.inflate(layoutInflater)
        val view = binding.root
        espApp = applicationContext as EspApplication
        setContentView(view)
        setToolbar()
        init()
        setup()
        showLoading()
        configureTbr()
    }

    private fun setup() {
        preferredCredentialsLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {

                    val threadNetworkCredentials =
                        ThreadNetworkCredentials.fromIntentSenderResultData(result.data!!)

                    Log.d(TAG, "Network name : " + threadNetworkCredentials.networkName)
                    val activeDataset = threadNetworkCredentials.activeOperationalDataset
                    val datasetStr = activeDataset.byteArrayToDs()
                    Log.d(TAG, "Active dataset : $datasetStr")

                    val matterNodeId = espApp.matterRmNodeIdMap.get(nodeId)
                    val id = matterNodeId?.let { BigInteger(it, 16) }
                    val deviceId = id?.toLong()
                    Log.d(TAG, "Device id : $deviceId")

                    if (espApp.chipClientMap.get(matterNodeId) != null) {
                        val clustersHelper =
                            ThreadBRClusterHelper(espApp.chipClientMap.get(matterNodeId)!!)

                        if (deviceId != null) {
                            nodeId?.let {
                                clustersHelper.configureThreadBRAsync(
                                    deviceId, AppConstants.ENDPOINT_0.toLong(),
                                    AppConstants.THREAD_BR_CLUSTER_ID_HEX, datasetStr
                                )
                                hideLoading()
                                binding.tvTbrProgress.setText(R.string.tbr_setup_done)
                                binding.tvPleaseWait.visibility = View.GONE
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "User denied request.")
                    hideLoading()
                    binding.tvTbrProgress.visibility = View.GONE
                    binding.tvPleaseWait.visibility = View.VISIBLE
                    binding.tvPleaseWait.setText("User denied request for reading thread credentials")
                }
            }
    }

    private fun setToolbar() {
        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        binding.toolbarLayout.toolbar.title = getString(R.string.title_activity_tbr)
        binding.toolbarLayout.toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left)
        binding.toolbarLayout.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun init() {
        nodeId = intent.getStringExtra(AppConstants.KEY_NODE_ID)
        binding.btnOk.textBtn.text = getString(R.string.btn_ok)
        binding.btnOk.layoutBtn.setOnClickListener { finish() }
    }

    private fun configureTbr() {
        getGPSThreadPreferredCredentials(this)
    }

    fun getGPSThreadPreferredCredentials(activity: Activity) {
        Log.d(TAG, "ThreadClient: getPreferredCredentials intent sent")
        ThreadNetwork.getClient(activity)
            .preferredCredentials
            .addOnSuccessListener { intentSenderResult ->
                intentSenderResult.intentSender?.let { intentSender ->
                    Log.d(TAG, "ThreadClient: intent returned result")
                    preferredCredentialsLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                }
                    ?: run {
                        Log.d(TAG, "ThreadClient: no preferred credentials found")

                        val matterNodeId = espApp.matterRmNodeIdMap.get(nodeId)
                        val id = matterNodeId?.let { BigInteger(it, 16) }
                        val deviceId = id?.toLong()
                        Log.d(TAG, "Device id : $deviceId")

                        if (espApp.chipClientMap.get(matterNodeId) != null) {
                            val clustersHelper =
                                ThreadBRClusterHelper(espApp.chipClientMap.get(matterNodeId)!!)

                            if (deviceId != null) {
                                val data: CompletableFuture<ArrayList<String>> =
                                    clustersHelper.readDatasetAsync(deviceId)
                                val tbrData: ArrayList<String> = data.get()
                                Log.d(TAG, "Received data : ${tbrData}")

                                val threadNetworkCredentials =
                                    ThreadNetworkCredentials.fromActiveOperationalDataset(
                                        tbrData.get(
                                            0
                                        ).dsToByteArray()
                                    )

                                Log.d(TAG, "Network Name : " + threadNetworkCredentials.networkName)
                                Log.d(TAG, "panId : " + threadNetworkCredentials.panId)
                                Log.d(
                                    TAG,
                                    "extendedPanId : " + threadNetworkCredentials.extendedPanId
                                )

                                val threadBorderAgent =
                                    ThreadBorderAgent.newBuilder(tbrData.get(1).dsToByteArray())
                                        .build()

                                ThreadNetwork.getClient(this)
                                    .addCredentials(threadBorderAgent, threadNetworkCredentials)
                                    .addOnSuccessListener {
                                        Log.d(TAG, "Credentials added.")
                                        Toast.makeText(this, "Credentials added", Toast.LENGTH_LONG)
                                            .show()
                                        hideLoading()
                                        binding.tvTbrProgress.setText(R.string.tbr_setup_done)
                                        binding.tvPleaseWait.visibility = View.GONE
                                    }
                                    .addOnFailureListener { e: Exception ->
                                        Log.e(TAG, "ERROR: [${e}]")
                                        hideLoading()
                                        binding.tvTbrProgress.visibility = View.GONE
                                        binding.tvPleaseWait.visibility = View.VISIBLE
                                        binding.tvPleaseWait.setText("Failed to add thread credentials")
                                    }
                            }
                        }
                    }
            }
            .addOnFailureListener { e: Exception ->
                Log.d(
                    TAG,
                    "ThreadClient: " + ThreadNetworkStatusCodes.getStatusCodeString((e as ApiException).statusCode)
                )
                hideLoading()
            }
    }

    private fun showLoading() {
        val rotate = RotateAnimation(
            0f,
            180f,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        )
        rotate.duration = 3000
        rotate.repeatCount = Animation.INFINITE
        rotate.interpolator = LinearInterpolator()
        binding.ivClaiming.startAnimation(rotate)
        binding.tvTbrProgress.setText(R.string.progress_tbr_config)
        binding.tvPleaseWait.setText(R.string.process_take_time)

        binding.btnOk.layoutBtn.isEnabled = false
        binding.btnOk.layoutBtn.alpha = 0.5f
        binding.btnOk.textBtn.text = getString(R.string.btn_configuring)
        binding.btnOk.progressIndicator.visibility = View.VISIBLE
        binding.btnOk.ivArrow.visibility = View.GONE
    }

    private fun hideLoading() {

        binding.ivClaiming.clearAnimation()
        binding.ivClaiming.visibility = View.GONE

        binding.btnOk.layoutBtn.isEnabled = true
        binding.btnOk.layoutBtn.alpha = 1f
        binding.btnOk.textBtn.text = getString(R.string.btn_done)
        binding.btnOk.progressIndicator.visibility = View.GONE
        binding.btnOk.ivArrow.visibility = View.GONE
    }

    private fun ByteArray.byteArrayToDs(): String =
        joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    private fun String.dsToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}