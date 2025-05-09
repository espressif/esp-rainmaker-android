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
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.espressif.AppConstants
import com.espressif.EspApplication
import com.espressif.cloudapi.ApiManager
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.cloudapi.CloudException
import com.espressif.matter.MatterFabricUtils.Companion.extractActiveTimestamp
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityTbrBinding
import com.espressif.utils.NodeUtils
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.threadnetwork.ThreadBorderAgent
import com.google.android.gms.threadnetwork.ThreadNetwork
import com.google.android.gms.threadnetwork.ThreadNetworkCredentials
import com.google.android.gms.threadnetwork.ThreadNetworkStatusCodes
import com.google.gson.JsonObject
import java.math.BigInteger
import java.util.concurrent.CompletableFuture

class ThreadBRActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ThreadBRActivity"
        const val TBR_SETUP = 1
        const val UPDATE_DATASET = 2
        const val MERGE_DATASET = 3
    }

    private lateinit var binding: ActivityTbrBinding
    private lateinit var preferredCredentialsLauncher: ActivityResultLauncher<IntentSenderRequest>

    private var nodeId: String? = null
    lateinit var espApp: EspApplication
    private var tbrActivityType: Int = 0
    private lateinit var apiManager: ApiManager
    private var checkParamsRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTbrBinding.inflate(layoutInflater)
        val view = binding.root
        espApp = applicationContext as EspApplication
        apiManager = ApiManager.getInstance(espApp)
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

                    Log.d(TAG, "Thread Network name : " + threadNetworkCredentials.networkName)
                    val activeDataset = threadNetworkCredentials.activeOperationalDataset
                    val datasetDs = activeDataset.byteArrayToDs()
                    val datasetByteArray = datasetDs.dsToByteArray()

                    when (tbrActivityType) {
                        TBR_SETUP -> {
                            val matterNodeId = espApp.matterRmNodeIdMap[nodeId]
                            val id = matterNodeId?.let { BigInteger(it, 16) }
                            val deviceId = id?.toLong()
                            Log.d(TAG, "Device id : $deviceId")

                            if (espApp.chipClientMap[matterNodeId] != null) {
                                val clustersHelper =
                                    ThreadBRClusterHelper(espApp.chipClientMap.get(matterNodeId)!!)

                                if (deviceId != null) {
                                    clustersHelper.configureThreadBRAsync(
                                        deviceId, AppConstants.ENDPOINT_1, datasetByteArray
                                    )
                                    hideLoading()
                                    binding.tvTbrProgress.setText(R.string.tbr_setup_done)
                                    binding.tvPleaseWait.visibility = View.GONE

                                    Log.d(TAG, "Reading Thread credentials from Device")
                                    val data: CompletableFuture<String?> =
                                        clustersHelper.readActiveDatasetAsync(deviceId)
                                    val tbrActiveDataset: String? = data.get()

                                    if (tbrActiveDataset != null) {

                                        // If device has active dataset.
                                        Log.d(
                                            TAG,
                                            "Both Phone and device has thread active dataset."
                                        )
                                        var androidThreadActiveDataset = datasetByteArray

                                        val androidActiveTimestamp =
                                            extractActiveTimestamp(androidThreadActiveDataset)
                                        val espActiveTimestamp =
                                            extractActiveTimestamp(tbrActiveDataset.dsToByteArray())

                                        if (androidActiveTimestamp <= espActiveTimestamp) {
                                            // increase phone dataset by timestamp difference.
                                            val diff = espActiveTimestamp - androidActiveTimestamp
                                            androidThreadActiveDataset =
                                                MatterFabricUtils.updateActiveTimestamp(
                                                    androidThreadActiveDataset,
                                                    diff
                                                )
                                        }

                                        Log.e(TAG, "Add delay timer")
                                        androidThreadActiveDataset =
                                            MatterFabricUtils.addDelayTimer(
                                                androidThreadActiveDataset,
                                                60000
                                            )

                                        if (isPendingDatasetFeatureAvailable()) {
                                            clustersHelper.sendPendingDatasetAsync(
                                                deviceId,
                                                AppConstants.ENDPOINT_1,
                                                androidThreadActiveDataset
                                            )
                                            hideLoading()
                                            binding.tvTbrProgress.setText(R.string.tbr_setup_done)
                                            binding.tvPleaseWait.visibility = View.GONE
                                        } else {
                                            hideLoading()
                                            binding.tvTbrProgress.setText(R.string.error_pending_dataset_not_supported)
                                            binding.tvPleaseWait.visibility = View.GONE
                                        }
                                    } else {
                                        // If device does not have active dataset.
                                        clustersHelper.configureThreadBRAsync(
                                            deviceId, AppConstants.ENDPOINT_1, datasetByteArray
                                        )
                                        hideLoading()
                                        binding.tvTbrProgress.setText(R.string.tbr_setup_done)
                                        binding.tvPleaseWait.visibility = View.GONE
                                    }
                                }
                            }
                        }

                        UPDATE_DATASET -> {
                            // Update active dataset param of TBR service.
                            val threadBrService = NodeUtils.getService(
                                espApp.nodeMap[nodeId]!!,
                                AppConstants.SERVICE_TYPE_TBR
                            )

                            if (threadBrService != null) {
                                val paramName =
                                    nodeId?.let {
                                        NodeUtils.getTbrServiceParamName(
                                            it,
                                            AppConstants.PARAM_TYPE_ACTIVE_DATASET,
                                            espApp
                                        )
                                    }

                                val jsonParam = JsonObject()
                                val body = JsonObject()
                                jsonParam.addProperty(paramName, datasetDs)
                                body.add(threadBrService.name, jsonParam)

                                apiManager.updateParamValue(
                                    nodeId,
                                    body,
                                    object : ApiResponseListener {
                                        override fun onSuccess(data: Bundle?) {
                                            Toast.makeText(
                                                this@ThreadBRActivity,
                                                "Thread active dataset is sent successfully.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            finish()
                                        }

                                        override fun onResponseFailure(exception: java.lang.Exception) {
                                            exception.printStackTrace()
                                            hideLoading()

                                            if (exception is CloudException) {
                                                exception.message?.let { displayError(it) }
                                            } else {
                                                displayError("Failed to send thread dataset")
                                            }
                                        }

                                        override fun onNetworkFailure(exception: java.lang.Exception) {
                                            exception.printStackTrace()
                                            hideLoading()
                                            displayError("Failed to send thread dataset")
                                        }
                                    })
                            }
                        }

                        MERGE_DATASET -> {
                            // Update active dataset param of TBR service.
                            val threadBrService = NodeUtils.getService(
                                espApp.nodeMap[nodeId]!!,
                                AppConstants.SERVICE_TYPE_TBR
                            )

                            if (threadBrService != null) {
                                val paramName =
                                    nodeId?.let {
                                        NodeUtils.getTbrServiceParamName(
                                            it,
                                            AppConstants.PARAM_TYPE_PENDING_DATASET,
                                            espApp
                                        )
                                    }

                                val deviceDataset =
                                    NodeUtils.getActiveDatasetFromTbrService(threadBrService)

                                deviceDataset?.let {
                                    Log.e(
                                        TAG,
                                        "Both Phone and device has thread active dataset."
                                    )

                                    var androidThreadActiveDataset = datasetByteArray
                                    val androidActiveTimestamp =
                                        extractActiveTimestamp(androidThreadActiveDataset)

                                    val espActiveTimestamp =
                                        extractActiveTimestamp(deviceDataset.dsToByteArray())

                                    if (androidActiveTimestamp <= espActiveTimestamp) {
                                        // increase phone dataset by timestamp difference.
//                                        val diff = espActiveTimestamp - androidActiveTimestamp
                                        androidThreadActiveDataset =
                                            MatterFabricUtils.updateActiveTimestamp(
                                                androidThreadActiveDataset,
                                                (espActiveTimestamp + 1)
                                            )
                                        Log.e(
                                            TAG,
                                            "Thread dataset after adding time diff : ${androidThreadActiveDataset.byteArrayToDs()}"
                                        )

                                        Log.d(TAG, "Add delay timer")
                                        androidThreadActiveDataset =
                                            MatterFabricUtils.addDelayTimer(
                                                androidThreadActiveDataset,
                                                60000
                                            )

                                        val jsonParam = JsonObject()
                                        val body = JsonObject()
                                        jsonParam.addProperty(
                                            paramName,
                                            androidThreadActiveDataset.byteArrayToDs()
                                        )
                                        body.add(threadBrService.name, jsonParam)

                                        apiManager.updateParamValue(
                                            nodeId,
                                            body,
                                            object : ApiResponseListener {
                                                override fun onSuccess(data: Bundle?) {
                                                    Toast.makeText(
                                                        this@ThreadBRActivity,
                                                        "Thread command sent successfully.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    finish()
                                                }

                                                override fun onResponseFailure(exception: java.lang.Exception) {
                                                    exception.printStackTrace()
                                                    hideLoading()

                                                    if (exception is CloudException) {
                                                        exception.message?.let { displayError(it) }
                                                    } else {
                                                        displayError("Failed to send thread command")
                                                    }
                                                }

                                                override fun onNetworkFailure(exception: java.lang.Exception) {
                                                    exception.printStackTrace()
                                                    hideLoading()
                                                    displayError("Failed to send thread command")
                                                }
                                            })
                                    }
                                }
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
        tbrActivityType = intent.getIntExtra(AppConstants.KEY_TBR_ACTIVITY_REASON, TBR_SETUP)

        when (tbrActivityType) {
            TBR_SETUP -> {
                binding.btnOk.textBtn.text = getString(R.string.btn_ok)
                binding.btnOk.layoutBtn.setOnClickListener { finish() }
            }

            else -> {
                binding.btnOk.layoutBtn.visibility = View.GONE
            }
        }
    }

    private fun configureTbr() {
        getGPSThreadPreferredCredentials(this)
    }

    private fun isPendingDatasetFeatureAvailable(): Boolean {

        val matterNodeId = espApp.matterRmNodeIdMap[nodeId]
        val id = matterNodeId?.let { BigInteger(it, 16) }
        val deviceId = id?.toLong()

        if (espApp.chipClientMap[matterNodeId] != null) {
            val clustersHelper =
                ThreadBRClusterHelper(espApp.chipClientMap[matterNodeId]!!)
            val isFeatureSupported =
                deviceId?.let { it1 -> clustersHelper.readFeatureMapAsync(it1).get() }
            if (isFeatureSupported?.toInt() == 1) {
                Log.d(TAG, "Pending dataset feature is supported.")
                return true
            } else {
                Log.e(TAG, "Pending dataset feature is not supported.")
                return false
            }
        }
        return false
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

                        Log.d(TAG, "No preferred credentials found on phone")

                        when (tbrActivityType) {
                            TBR_SETUP -> {
                                val matterNodeId = espApp.matterRmNodeIdMap[nodeId]
                                val id = matterNodeId?.let { BigInteger(it, 16) }
                                val deviceId = id?.toLong()

                                if (espApp.chipClientMap.get(matterNodeId) != null) {
                                    val clustersHelper =
                                        ThreadBRClusterHelper(espApp.chipClientMap.get(matterNodeId)!!)

                                    if (deviceId != null) {
                                        val data: CompletableFuture<ArrayList<String>> =
                                            clustersHelper.readDatasetAsync(deviceId)
                                        val tbrData: ArrayList<String> = data.get()

                                        var threadNetworkCredentials: ThreadNetworkCredentials

                                        if (tbrData.size == 2) {

                                            // If device has active dataset.
                                            threadNetworkCredentials =
                                                ThreadNetworkCredentials.fromActiveOperationalDataset(
                                                    tbrData[0].dsToByteArray()
                                                )
                                            Log.d(TAG, "Received active dataset from device")
                                            Log.d(
                                                TAG,
                                                "Network Name : " + threadNetworkCredentials.networkName
                                            )
                                            val borderAgentId = tbrData[1]
                                            addCredentials(borderAgentId, threadNetworkCredentials)

                                        } else {
                                            Log.d(
                                                TAG,
                                                "Both device and phone does not have thread credentials."
                                            )
                                            // Create new dataset, add it in phone and send it to device.
                                            threadNetworkCredentials =
                                                ThreadNetworkCredentials.newRandomizedBuilder()
                                                    .setNetworkName("EspThreadNetwork")
                                                    .build()

                                            val data: CompletableFuture<String?> =
                                                clustersHelper.readBorderAgentIdAsync(deviceId)
                                            val borderAgentId: String? = data.get()
                                            Log.d(
                                                TAG,
                                                "Received border Agent Id : ${borderAgentId}"
                                            )

                                            val activeDataset =
                                                threadNetworkCredentials.activeOperationalDataset
                                            val datasetStr =
                                                activeDataset.byteArrayToDs().dsToByteArray()
                                            clustersHelper.configureThreadBRAsync(
                                                deviceId, AppConstants.ENDPOINT_1, datasetStr
                                            )

                                            borderAgentId?.let {
                                                addCredentials(
                                                    borderAgentId,
                                                    threadNetworkCredentials
                                                )
                                            }

                                            hideLoading()
                                            binding.tvTbrProgress.setText(R.string.tbr_setup_done)
                                            binding.tvPleaseWait.visibility = View.GONE
                                        }
                                    }
                                }
                            }

                            MERGE_DATASET -> {
                                hideLoading()
                                finish()
                            }

                            UPDATE_DATASET -> {
                                val threadBrService = NodeUtils.getService(
                                    espApp.nodeMap[nodeId]!!,
                                    AppConstants.SERVICE_TYPE_TBR
                                )

                                if (threadBrService != null) {
                                    val paramName =
                                        nodeId?.let {
                                            NodeUtils.getTbrServiceParamName(
                                                it,
                                                AppConstants.PARAM_TYPE_TBR_CMD,
                                                espApp
                                            )
                                        }

                                    val jsonParam = JsonObject()
                                    val body = JsonObject()
                                    jsonParam.addProperty(paramName, 1)
                                    body.add(threadBrService.name, jsonParam)

                                    apiManager.updateParamValue(
                                        nodeId,
                                        body,
                                        object : ApiResponseListener {
                                            override fun onSuccess(data: Bundle?) {
                                                apiManager.getParamsValues(
                                                    nodeId,
                                                    object : ApiResponseListener {
                                                        override fun onSuccess(data: Bundle?) {

                                                            val tbrService = NodeUtils.getService(
                                                                espApp.nodeMap[nodeId]!!,
                                                                AppConstants.SERVICE_TYPE_TBR
                                                            )

                                                            if (tbrService != null) {
                                                                val activeDataset =
                                                                    NodeUtils.getActiveDatasetFromTbrService(
                                                                        tbrService
                                                                    )
                                                                val borderAgentId =
                                                                    NodeUtils.getBorderAgentIdFromTbrService(
                                                                        tbrService
                                                                    )

                                                                if (!TextUtils.isEmpty(activeDataset)
                                                                    && !TextUtils.isEmpty(
                                                                        borderAgentId
                                                                    )
                                                                ) {
                                                                    // Both parameters are available, proceed with adding credentials
                                                                    val threadNetworkCredentials =
                                                                        ThreadNetworkCredentials.fromActiveOperationalDataset(
                                                                            activeDataset.dsToByteArray()
                                                                        )
                                                                    addCredentials(
                                                                        borderAgentId,
                                                                        threadNetworkCredentials
                                                                    )
                                                                } else {
                                                                    // Parameters not available yet, start checking
                                                                    checkTbrParams()
                                                                }
                                                            }
                                                        }

                                                        override fun onResponseFailure(exception: Exception) {
                                                            Log.e(
                                                                TAG,
                                                                "Failed to get params: ${exception.message}"
                                                            )
                                                        }

                                                        override fun onNetworkFailure(exception: Exception) {
                                                            Log.e(
                                                                TAG,
                                                                "Network failure: ${exception.message}"
                                                            )
                                                        }
                                                    })
                                                Toast.makeText(
                                                    this@ThreadBRActivity,
                                                    "Thread active dataset is created successfully.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                finish()
                                            }

                                            override fun onResponseFailure(exception: java.lang.Exception) {
                                                exception.printStackTrace()
                                                hideLoading()

                                                if (exception is CloudException) {
                                                    exception.message?.let { displayError(it) }
                                                } else {
                                                    displayError("Failed to create thread dataset")
                                                }
                                            }

                                            override fun onNetworkFailure(exception: java.lang.Exception) {
                                                exception.printStackTrace()
                                                hideLoading()
                                                displayError("Failed to create thread dataset")
                                            }
                                        })
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

    private fun addCredentials(
        borderAgentId: String,
        credentialsToBeAdded: ThreadNetworkCredentials
    ) {

        val threadBorderAgent = ThreadBorderAgent.newBuilder(borderAgentId.dsToByteArray()).build()

        ThreadNetwork.getClient(this)
            .addCredentials(threadBorderAgent, credentialsToBeAdded)
            .addOnSuccessListener {
                Log.d(TAG, "Credentials added.")
                Toast.makeText(
                    this,
                    "Credentials added",
                    Toast.LENGTH_LONG
                )
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

    private fun displayError(msg: String) {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)

        builder.setTitle(R.string.error_title)
        builder.setMessage(msg)

        // Set up the buttons
        builder.setPositiveButton(
            R.string.btn_ok
        ) { dialog, which ->
            dialog.dismiss()
            finish()
        }
        builder.show()
    }

    private fun ByteArray.byteArrayToDs(): String =
        joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    private fun String.dsToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun checkTbrParams() {
        checkParamsRunnable = Runnable {
            apiManager.getParamsValues(
                nodeId,
                object : ApiResponseListener {
                    override fun onSuccess(data: Bundle?) {
                        val tbrService = NodeUtils.getService(
                            espApp.nodeMap[nodeId]!!,
                            AppConstants.SERVICE_TYPE_TBR
                        )

                        if (tbrService != null) {
                            val activeDataset = NodeUtils.getActiveDatasetFromTbrService(tbrService)
                            val borderAgentId = NodeUtils.getBorderAgentIdFromTbrService(tbrService)

                            if (!TextUtils.isEmpty(activeDataset) && !TextUtils.isEmpty(
                                    borderAgentId
                                )
                            ) {
                                handler.removeCallbacksAndMessages(null)
                                checkParamsRunnable = null

                                // Both parameters are available, proceed with adding credentials
                                val threadNetworkCredentials =
                                    ThreadNetworkCredentials.fromActiveOperationalDataset(
                                        activeDataset.dsToByteArray()
                                    )
                                addCredentials(borderAgentId, threadNetworkCredentials)
                            } else {
                                // Parameters not available yet, schedule next check
                                handler.postDelayed(checkParamsRunnable!!, 1500)
                            }
                        }
                    }

                    override fun onResponseFailure(exception: Exception) {
                        Log.e(TAG, "Failed to get params: ${exception.message}")
                    }

                    override fun onNetworkFailure(exception: Exception) {
                        Log.e(TAG, "Network failure: ${exception.message}")
                    }
                })
        }
        handler.post(checkParamsRunnable!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove all callbacks and messages
        handler.removeCallbacksAndMessages(null)
        checkParamsRunnable = null
    }
}