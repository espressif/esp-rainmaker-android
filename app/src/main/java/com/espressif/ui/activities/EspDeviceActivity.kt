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

package com.espressif.ui.activities

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.StyleSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import chip.devicecontroller.ReportCallback
import chip.devicecontroller.model.ChipAttributePath
import chip.devicecontroller.model.ChipEventPath
import chip.devicecontroller.model.NodeState
import com.espressif.AppConstants
import com.espressif.AppConstants.Companion.UpdateEventType
import com.espressif.EspApplication
import com.espressif.NetworkApiManager
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.cloudapi.CloudException
import com.espressif.matter.ControllerClusterHelper
import com.espressif.matter.ControllerLoginActivity
import com.espressif.matter.DoorLockClusterHelper
import com.espressif.matter.GroupSelectionActivity
import com.espressif.matter.SubscriptionHelper
import com.espressif.matter.ThreadBRActivity
import com.espressif.rainmaker.BuildConfig
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityEspDeviceBinding
import com.espressif.ui.adapters.AttrParamAdapter
import com.espressif.ui.adapters.ParamAdapter
import com.espressif.ui.models.Device
import com.espressif.ui.models.Param
import com.espressif.ui.models.Service
import com.espressif.ui.models.UpdateEvent
import com.espressif.utils.NodeUtils.Companion.getService
import com.google.android.gms.threadnetwork.ThreadBorderAgent
import com.google.android.gms.threadnetwork.ThreadNetwork
import com.google.android.gms.threadnetwork.ThreadNetworkCredentials
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Calendar

class EspDeviceActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EspDeviceActivity"
        private const val NODE_DETAILS_ACTIVITY_REQUEST = 10
        private const val UPDATE_INTERVAL = 5000
        private const val UI_UPDATE_INTERVAL = 4500
        private const val KEY_LOCK_SETUP_DONE = "lock_setup_done"
    }

    private lateinit var binding: ActivityEspDeviceBinding

    private var espApp: EspApplication? = null
    private var device: Device? = null
    private var networkApiManager: NetworkApiManager? = null
    private var handler: Handler? = null

    private var paramAdapter: ParamAdapter? = null
    private var attrAdapter: AttrParamAdapter? = null

    private var snackbar: Snackbar? = null

    private var paramList: java.util.ArrayList<Param>? = null
    private var attributeList: java.util.ArrayList<Param>? = null

    private var nodeId: String? = null
    private var nodeType: String? = null
    private var matterNodeId: String? = null
    private var nodeStatus = 0
    private var timeStampOfStatus: Long = 0
    private var isControllerClusterAvailable = false
    private var isTbrClusterAvailable: Boolean = false
    private var isCtlAvailable = false
    private var lastUpdateRequestTime: Long = 0
    private var isNetworkAvailable = true
    private var shouldGetParams = true
    private var isUpdateView = true
    
    // Matter subscription related variables
    private var subscriptionHelper: SubscriptionHelper? = null
    private var matterSubscriptionActive = false
    private var lastMatterUpdateTime = 0L
    private val MATTER_UPDATE_THROTTLE_MS = 100L // Throttle updates to max once per 100ms

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEspDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        espApp = applicationContext as EspApplication
        networkApiManager = NetworkApiManager(applicationContext)
        device = intent.getParcelableExtra(AppConstants.KEY_ESP_DEVICE)
        handler = Handler()

        if (device == null) {
            Log.e(TAG, "DEVICE IS NULL")
            finish()
        } else {
            nodeId = device!!.nodeId
            Log.d(TAG, "NODE ID : $nodeId")

            nodeType = espApp!!.nodeMap[nodeId]!!.newNodeType
            nodeStatus = espApp!!.nodeMap[nodeId]!!.nodeStatus
            timeStampOfStatus = espApp!!.nodeMap[nodeId]!!.timeStampOfStatus
            snackbar = Snackbar.make(
                binding.espDeviceLayout.paramsParentLayout,
                R.string.msg_no_internet,
                Snackbar.LENGTH_INDEFINITE
            )

            if (TextUtils.isEmpty(nodeType)) {
                nodeType = AppConstants.NODE_TYPE_RM
            }

            if (nodeType == AppConstants.NODE_TYPE_PURE_MATTER || nodeType == AppConstants.NODE_TYPE_RM_MATTER) {

                if (espApp!!.matterRmNodeIdMap.containsKey(nodeId)) {
                    matterNodeId = espApp!!.matterRmNodeIdMap[nodeId]
                }

                if (!TextUtils.isEmpty(matterNodeId)
                    && espApp!!.availableMatterDevices.contains(matterNodeId)
                    && espApp!!.matterDeviceInfoMap.containsKey(matterNodeId)
                ) {
                    val deviceMatterInfo = espApp!!.matterDeviceInfoMap[matterNodeId]

                    if (!deviceMatterInfo.isNullOrEmpty()) {
                        for ((endpoint, _, serverClusters) in deviceMatterInfo) {
                            if (endpoint == 0 && serverClusters.isNotEmpty()) {
                                for (serverCluster in serverClusters) {
                                    val id = serverCluster as Long
                                    if (id == AppConstants.CONTROLLER_CLUSTER_ID) {
                                        isControllerClusterAvailable = true
                                    }
                                }
                            } else if (endpoint == 1 && serverClusters.isNotEmpty()) {
                                for (serverCluster in serverClusters) {
                                    val id = serverCluster as Long
                                    if (id == AppConstants.THREAD_BR_MANAGEMENT_CLUSTER_ID) {
                                        isTbrClusterAvailable = true
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Matter device info not available")
                }
            } else {
                Log.d(TAG, "RainMaker device type")
            }

            val controllerService = getService(
                espApp!!.nodeMap[device!!.nodeId]!!,
                AppConstants.SERVICE_TYPE_MATTER_CONTROLLER
            )
            isCtlAvailable = controllerService != null
            var matterNodeIdParamAvailable = false

            if (isCtlAvailable) {
                val params = controllerService!!.params

                if (params != null && !params.isEmpty()) {
                    for (param in params) {
                        if (AppConstants.PARAM_TYPE_MATTER_NODE_ID == param.paramType) {
                            matterNodeIdParamAvailable = true
                        }
                    }
                }
            }
            if (!matterNodeIdParamAvailable) {
                isCtlAvailable = false
            }

            setParamList(device!!.params)
            initViews()
            updateUi()
            if (AppConstants.ESP_DEVICE_LOCK.equals(device?.deviceType)) {
                setUserAndCredsForLock()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        getNodeDetails()
        EventBus.getDefault().register(this)
        
        // Setup Matter subscriptions if device is MATTER_LOCAL
        if (nodeStatus == AppConstants.NODE_STATUS_MATTER_LOCAL) {
            setupMatterSubscriptions()
        }
    }

    override fun onPause() {
        super.onPause()
        stopUpdateValueTask()
        stopMatterSubscriptions()
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroy() {
        stopUpdateValueTask()
        stopMatterSubscriptions()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menu.add(Menu.NONE, 1, Menu.NONE, R.string.btn_info).setIcon(R.drawable.ic_node_info)
            .setShowAsAction(
                MenuItem.SHOW_AS_ACTION_ALWAYS
            )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> {
                goToNodeDetailsActivity()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == NODE_DETAILS_ACTIVITY_REQUEST && resultCode == RESULT_OK) {
            finish()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: UpdateEvent) {
        Log.d(TAG, "Update Event Received : " + event.eventType)

        when (event.eventType) {
            UpdateEventType.EVENT_DEVICE_ADDED, UpdateEventType.EVENT_DEVICE_REMOVED, UpdateEventType.EVENT_STATE_CHANGE_UPDATE, UpdateEventType.EVENT_LOCAL_DEVICE_UPDATE, UpdateEventType.EVENT_DEVICE_ONLINE, UpdateEventType.EVENT_DEVICE_OFFLINE -> {}
            UpdateEventType.EVENT_DEVICE_STATUS_UPDATE -> {
                val currentTime = System.currentTimeMillis()
                if (BuildConfig.isContinuousUpdateEnable) {
                    if (isUpdateView && currentTime - lastUpdateRequestTime > UI_UPDATE_INTERVAL) {
                        handler!!.removeCallbacks(updateViewTask)
                        handler!!.post(updateViewTask)
                    }
                } else if (nodeStatus != AppConstants.NODE_STATUS_MATTER_LOCAL) {
                    updateUi()
                }
            }

            UpdateEventType.EVENT_MATTER_DEVICE_CONNECTIVITY -> updateUi()
            UpdateEventType.EVENT_ADD_DEVICE_TIME_OUT -> TODO()
            UpdateEventType.EVENT_CTRL_CONFIG_DONE -> TODO()
        }
    }

    var updateViewTask: Runnable = Runnable { this.updateUi() }

    fun updateDeviceNameInTitle(deviceName: String?) {
        supportActionBar!!.title = deviceName
    }

    fun isNodeOnline(): Boolean {
        var isNodeOnline = false

        if (Arrays.asList(
                AppConstants.NODE_STATUS_ONLINE,
                AppConstants.NODE_STATUS_LOCAL,
                AppConstants.NODE_STATUS_MATTER_LOCAL,
                AppConstants.NODE_STATUS_REMOTELY_CONTROLLABLE
            )
                .contains(nodeStatus)
        ) {
            isNodeOnline = true
        }
        return isNodeOnline
    }

    fun setIsUpdateView(isUpdateView: Boolean) {
        this.isUpdateView = isUpdateView
    }

    fun setLastUpdateRequestTime(lastUpdateRequestTime: Long) {
        this.lastUpdateRequestTime = lastUpdateRequestTime
    }

    fun startUpdateValueTask() {
        if (!TextUtils.isEmpty(nodeType) && nodeType == AppConstants.NODE_TYPE_PURE_MATTER && nodeStatus != AppConstants.NODE_STATUS_REMOTELY_CONTROLLABLE) {
            return
        }
        shouldGetParams = true
        handler!!.removeCallbacks(updateValuesTask)
        handler!!.postDelayed(updateValuesTask, UPDATE_INTERVAL.toLong())
    }

    fun stopUpdateValueTask() {
        shouldGetParams = false
        handler!!.removeCallbacks(updateValuesTask)
    }

    private fun goToNodeDetailsActivity() {
        val intent = Intent(this@EspDeviceActivity, NodeDetailsActivity::class.java)
        intent.putExtra(AppConstants.KEY_NODE_ID, nodeId)
        startActivityForResult(intent, NODE_DETAILS_ACTIVITY_REQUEST)
    }

    private val updateValuesTask: Runnable = object : Runnable {
        override fun run() {
            if (shouldGetParams) {
                if (BuildConfig.isContinuousUpdateEnable) {
                    val currentTime = System.currentTimeMillis()
                    if (isUpdateView && currentTime - lastUpdateRequestTime >= UI_UPDATE_INTERVAL) {
                        getValues()
                    } else {
                        handler!!.removeCallbacks(this)
                        handler!!.postDelayed(this, UI_UPDATE_INTERVAL.toLong())
                    }
                } else {
                    getValues()
                }
            }
        }
    }

    private fun initViews() {

        setToolbar()

        val linearLayoutManager = LinearLayoutManager(applicationContext)
        linearLayoutManager.orientation = RecyclerView.VERTICAL
        binding.espDeviceLayout.rvDynamicParamList.setLayoutManager(linearLayoutManager)

        val linearLayoutManager1 = LinearLayoutManager(applicationContext)
        linearLayoutManager1.orientation = RecyclerView.VERTICAL
        binding.espDeviceLayout.rvStaticParamList.setLayoutManager(linearLayoutManager1)

        paramAdapter = ParamAdapter(this, device, paramList ?: ArrayList())
        binding.espDeviceLayout.rvDynamicParamList.setAdapter(paramAdapter)

        attrAdapter = AttrParamAdapter(this, device, attributeList ?: ArrayList())
        binding.espDeviceLayout.rvStaticParamList.setAdapter(attrAdapter)

        binding.espDeviceLayout.swipeContainer.setOnRefreshListener(OnRefreshListener { getNodeDetails() })

        binding.espDeviceLayout.btnUpdate.setOnClickListener(View.OnClickListener {

            if (isCtlAvailable) {
                val serviceParamJson = JsonObject()
                serviceParamJson.addProperty("MTCtlCMD", 2)

                // Get service name
                var serviceName = AppConstants.KEY_MATTER_CTL
                val service = getService(
                    espApp?.nodeMap?.get(nodeId)!!,
                    AppConstants.SERVICE_TYPE_MATTER_CONTROLLER
                )
                if (service != null && !TextUtils.isEmpty(service.name)) {
                    serviceName = service.name
                }

                val body = JsonObject()
                body.add(serviceName, serviceParamJson)

                val networkApiManager = NetworkApiManager(espApp)
                networkApiManager.updateParamValue(nodeId, body, object : ApiResponseListener {
                    override fun onSuccess(data: Bundle?) {
                    }

                    override fun onResponseFailure(exception: java.lang.Exception) {
                    }

                    override fun onNetworkFailure(exception: java.lang.Exception) {
                    }
                })
            } else {
                val id = BigInteger(matterNodeId, 16)
                val deviceId = id.toLong()
                if (espApp!!.chipClientMap.containsKey(matterNodeId)) {
                    val espClusterHelper = ControllerClusterHelper(
                        espApp!!.chipClientMap[matterNodeId]!!,
                        espApp!!
                    )
                    espClusterHelper.sendUpdateDeviceListEventAsync(
                        deviceId,
                        AppConstants.ENDPOINT_0,
                        AppConstants.CONTROLLER_CLUSTER_ID_HEX
                    )
                }
            }
        })

        binding.espDeviceLayout.tvControllerLogin.setOnClickListener(View.OnClickListener {
            var intent = Intent(
                this,
                ControllerLoginActivity::class.java
            )

            if (isCtlAvailable) {
                intent = Intent(this, GroupSelectionActivity::class.java)
            }
            intent.putExtra(AppConstants.KEY_NODE_ID, nodeId)
            intent.putExtra(AppConstants.KEY_IS_CTRL_SERVICE, isCtlAvailable)
            startActivity(intent)
        })

        binding.espDeviceLayout.tvTbrSetup.setOnClickListener(View.OnClickListener {
            val intent = Intent(this, ThreadBRActivity::class.java)
            intent.putExtra(AppConstants.KEY_NODE_ID, nodeId)
            intent.putExtra(AppConstants.KEY_TBR_ACTIVITY_REASON, ThreadBRActivity.TBR_SETUP)
            startActivity(intent)
        })

        if (isControllerClusterAvailable) {
            if (AppConstants.NODE_TYPE_PURE_MATTER != nodeType) {
                binding.espDeviceLayout.rlControllerLogin.visibility = View.VISIBLE
                binding.espDeviceLayout.rlMatterController.visibility = View.VISIBLE
            } else {
                binding.espDeviceLayout.rlControllerLogin.visibility = View.GONE
                binding.espDeviceLayout.rlMatterController.visibility = View.VISIBLE
                binding.espDeviceLayout.rlMatterController.isEnabled = false
                binding.espDeviceLayout.btnUpdate.isEnabled = false
                binding.espDeviceLayout.btnUpdate.alpha = 0.7f
                val fullText = "Controller (Unauthorised)"
                val spannable = SpannableString(fullText)
                val startIndex = fullText.indexOf(" (Unauthorised)")
                spannable.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    startIndex,
                    fullText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.espDeviceLayout.tvControllerLabel.text = spannable
            }
        } else if (isCtlAvailable) {
            binding.espDeviceLayout.rlControllerLogin.visibility = View.GONE
            binding.espDeviceLayout.rlMatterController.visibility = View.VISIBLE
        } else {
            binding.espDeviceLayout.rlControllerLogin.visibility = View.GONE
            binding.espDeviceLayout.rlMatterController.visibility = View.GONE
        }

        if (isTbrClusterAvailable) {
            binding.espDeviceLayout.rlThreadBr.visibility = View.VISIBLE
        } else {
            binding.espDeviceLayout.rlThreadBr.visibility = View.GONE
        }

        val tbrService: Service? =
            getService(espApp!!.nodeMap[nodeId]!!, AppConstants.SERVICE_TYPE_TBR)
        if (tbrService != null) {
            binding.espDeviceLayout.rlUpdateThreadDataset.visibility = View.VISIBLE
            binding.espDeviceLayout.rlMergeThreadDataset.visibility = View.VISIBLE

            binding.espDeviceLayout.btnUpdateDataset.setOnClickListener {
                var activeDataset = ""
                for (p in tbrService.params) {
                    if (AppConstants.PARAM_TYPE_ACTIVE_DATASET == p.paramType) {
                        activeDataset = p.labelValue
                    }
                }

                if (TextUtils.isEmpty(activeDataset)) {
                    val intent = Intent(this@EspDeviceActivity, ThreadBRActivity::class.java)
                    intent.putExtra(AppConstants.KEY_NODE_ID, nodeId)
                    intent.putExtra(
                        AppConstants.KEY_TBR_ACTIVITY_REASON,
                        ThreadBRActivity.UPDATE_DATASET
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        this@EspDeviceActivity,
                        "Thread active dataset is already created.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            binding.espDeviceLayout.btnMergeDataset.setOnClickListener {
                val intent = Intent(this@EspDeviceActivity, ThreadBRActivity::class.java)
                intent.putExtra(AppConstants.KEY_NODE_ID, nodeId)
                intent.putExtra(
                    AppConstants.KEY_TBR_ACTIVITY_REASON,
                    ThreadBRActivity.MERGE_DATASET
                )
                startActivity(intent)
            }
        } else {
            binding.espDeviceLayout.rlUpdateThreadDataset.visibility = View.GONE
            binding.espDeviceLayout.rlMergeThreadDataset.visibility = View.GONE
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
            }
            .addOnFailureListener { e: Exception ->
                Log.e(TAG, "ERROR: [${e}]")
            }
    }

    private fun String.dsToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun setToolbar() {
        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        if (TextUtils.isEmpty(device!!.userVisibleName)) {
            device!!.userVisibleName = device!!.deviceName
        }
        binding.toolbarLayout.toolbar.title = device!!.userVisibleName

        binding.toolbarLayout.toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left)
        binding.toolbarLayout.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun getNodeDetails() {
        stopUpdateValueTask()

        networkApiManager!!.getNodeDetails(nodeId, object : ApiResponseListener {
            override fun onSuccess(data: Bundle?) {
                runOnUiThread {
                    isNetworkAvailable = true
                    hideLoading()
                    snackbar!!.dismiss()
                    binding.espDeviceLayout.swipeContainer.isRefreshing = false
                    updateUi()
                    startUpdateValueTask()
                }
            }

            override fun onResponseFailure(exception: java.lang.Exception) {
                isNetworkAvailable = true
                hideLoading()
                snackbar!!.dismiss()
                binding.espDeviceLayout.swipeContainer.isRefreshing = false
                if (exception is CloudException) {
                    Toast.makeText(this@EspDeviceActivity, exception.message, Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(
                        this@EspDeviceActivity,
                        "Failed to get node details",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                updateUi()
            }

            override fun onNetworkFailure(exception: java.lang.Exception) {
                hideLoading()
                binding.espDeviceLayout.swipeContainer.isRefreshing = false
                if (exception is CloudException) {
                    Toast.makeText(this@EspDeviceActivity, exception.message, Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(
                        this@EspDeviceActivity,
                        "Failed to get node details",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                updateUi()
            }
        })
    }

    private fun getValues() {

        when (nodeStatus) {
            AppConstants.NODE_STATUS_REMOTELY_CONTROLLABLE -> {
                if (!TextUtils.isEmpty(nodeType) && nodeType == AppConstants.NODE_TYPE_PURE_MATTER) {
                    var controllerNodeId = ""

                    for ((key, controllerDevices) in espApp!!.controllerDevices) {
                        if (controllerDevices.containsKey(matterNodeId)) {
                            controllerNodeId = key
                            break
                        }
                    }

                    if (!TextUtils.isEmpty(controllerNodeId)) {
                        getParamValuesForDevice(controllerNodeId!!)
                    }
                }
            }

//            AppConstants.NODE_STATUS_MATTER_LOCAL -> getParamValuesForMatterDevice(nodeId!!)
            else -> getParamValuesForDevice(nodeId!!)
        }
    }

    private fun getParamValuesForDevice(rmNodeId: String) {
        networkApiManager!!.getParamsValues(rmNodeId, object : ApiResponseListener {
            override fun onSuccess(data: Bundle?) {
                runOnUiThread {
                    isNetworkAvailable = true
                    hideLoading()
                    binding.espDeviceLayout.swipeContainer.isRefreshing = false
                    updateUi()
                    handler?.removeCallbacks(updateValuesTask)
                    handler?.postDelayed(
                        updateValuesTask,
                        UPDATE_INTERVAL.toLong()
                    )
                }
            }

            override fun onResponseFailure(exception: java.lang.Exception) {
                stopUpdateValueTask()
                isNetworkAvailable = true
                hideLoading()
                binding.espDeviceLayout.swipeContainer.isRefreshing = false
                if (exception is CloudException) {
                    Toast.makeText(this@EspDeviceActivity, exception.message, Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(
                        this@EspDeviceActivity,
                        "Failed to get param values",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                updateUi()
            }

            override fun onNetworkFailure(exception: java.lang.Exception) {
                stopUpdateValueTask()
                hideLoading()
                binding.espDeviceLayout.swipeContainer.isRefreshing = false
                if (exception is CloudException) {
                    Toast.makeText(this@EspDeviceActivity, exception.message, Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(
                        this@EspDeviceActivity,
                        "Failed to get param values",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                updateUi()
            }
        })
    }

    private fun setUserAndCredsForLock() {
        val sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val lockSetupDone = sharedPreferences.getBoolean(KEY_LOCK_SETUP_DONE, false)

        if (!lockSetupDone) {
            lifecycleScope.launch {
                if (nodeStatus == AppConstants.NODE_STATUS_MATTER_LOCAL
                    && !TextUtils.isEmpty(matterNodeId)
                    && espApp!!.chipClientMap.containsKey(matterNodeId)
                ) {
                    try {
                        val id = BigInteger(matterNodeId, 16)
                        val deviceId = id.toLong()
                        val espClusterHelper =
                            DoorLockClusterHelper(espApp!!.chipClientMap[matterNodeId]!!)
                        espClusterHelper.setUser(deviceId, AppConstants.ENDPOINT_1)

                        espClusterHelper.setCredential(
                            deviceId,
                            AppConstants.ENDPOINT_1,
                            AppConstants.DOOR_LOCK_PIN
                        )
                        editor.putBoolean(KEY_LOCK_SETUP_DONE, true)
                        editor.apply()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun setParamList(paramArrayList: ArrayList<Param>?) {
        val params = ArrayList<Param>()
        val attributes = ArrayList<Param>()

        if (paramArrayList != null) {
            for (i in paramArrayList.indices) {
                val param = paramArrayList[i]
                if (param.isDynamicParam) {
                    params.add(Param(param))
                } else {
                    attributes.add(Param(param))
                }
            }
            arrangeParamList(params)
        }

        if (paramList == null || attributeList == null) {
            paramList = ArrayList()
            attributeList = ArrayList()
            paramList!!.addAll(params)
            attributeList!!.addAll(attributes)
        } else {
            paramAdapter?.updateParamList(params)
            attrAdapter?.updateAttributeList(attributes)
        }
    }

    private fun arrangeParamList(paramList: java.util.ArrayList<Param>) {
        var firstParamIndex = -1
        for (i in paramList.indices) {
            val param = paramList[i]
            if (param != null && AppConstants.UI_TYPE_HUE_CIRCLE.equals(
                    param.uiType,
                    ignoreCase = true
                )
            ) {
                firstParamIndex = i
                break
            }
        }

        if (firstParamIndex != -1) {
            val paramToBeMoved = paramList.removeAt(firstParamIndex)
            paramList.add(0, paramToBeMoved)
        } else {
            for (i in paramList.indices) {
                val param = paramList[i]
                if (param != null) {
                    val dataType = param.dataType
                    if (AppConstants.UI_TYPE_PUSH_BTN_BIG.equals(param.uiType, ignoreCase = true)
                        && (!TextUtils.isEmpty(dataType) && (dataType.equals(
                            "bool",
                            ignoreCase = true
                        ) || dataType.equals("boolean", ignoreCase = true)))
                    ) {
                        firstParamIndex = i
                        break
                    }
                }
            }

            if (firstParamIndex != -1) {
                val paramToBeMoved = paramList.removeAt(firstParamIndex)
                paramList.add(0, paramToBeMoved)
            }
        }

        var paramNameIndex = -1
        for (i in paramList.indices) {
            val param = paramList[i]
            if (param != null) {
                if (param.paramType != null && param.paramType == AppConstants.PARAM_TYPE_NAME) {
                    paramNameIndex = i
                    break
                }
            }
        }

        if (paramNameIndex != -1) {
            val paramToBeMoved = paramList.removeAt(paramNameIndex)
            if (firstParamIndex != -1) {
                paramList.add(1, paramToBeMoved)
            } else {
                paramList.add(0, paramToBeMoved)
            }
        }

        val paramIterator = paramList.iterator()
        while (paramIterator.hasNext()) {
            val p = paramIterator.next()
            if (p.uiType != null && p.uiType == AppConstants.UI_TYPE_HIDDEN) {
                paramIterator.remove()
            }
        }
    }

    private fun updateUi() {
        var deviceFound = false
        var updatedDevice: Device? = null
        lastUpdateRequestTime = System.currentTimeMillis()

        if (espApp!!.nodeMap.containsKey(nodeId)) {
            val devices = espApp!!.nodeMap[nodeId]!!
                .devices
            if (devices == null || devices.isEmpty()) {
                Log.e(TAG, "Node devices are not available")
                return
            }
            timeStampOfStatus = espApp!!.nodeMap[nodeId]!!.timeStampOfStatus
            nodeStatus = espApp!!.nodeMap[nodeId]!!.nodeStatus

            for (i in devices.indices) {
                if (device!!.deviceName != null && device!!.deviceName == devices[i].deviceName) {
                    updatedDevice = Device(devices[i])
                    deviceFound = true
                    break
                }
            }
        } else {
            Log.e(TAG, "Node does not exist in list. It may be deleted.")
            finish()
            return
        }

        var isMatterOnly = false
        if (!TextUtils.isEmpty(nodeType) && nodeType == AppConstants.NODE_TYPE_PURE_MATTER) {
            isMatterOnly = true
        }

        if (!deviceFound && !isMatterOnly) {
            Log.e(TAG, "Device does not exist in node list.")
            finish()
            return
        }

        if (updatedDevice == null) {
            updatedDevice = device
        }

        setParamList(updatedDevice!!.params)

        when (nodeStatus) {
            AppConstants.NODE_STATUS_MATTER_LOCAL -> if (espApp!!.appState == EspApplication.AppState.GET_DATA_SUCCESS) {
                if (!TextUtils.isEmpty(matterNodeId) && espApp!!.availableMatterDevices.contains(
                        matterNodeId
                    )
                    && espApp!!.matterDeviceInfoMap.containsKey(matterNodeId)
                ) {
                    binding.espDeviceLayout.rlNodeStatus.visibility = View.VISIBLE
                    binding.espDeviceLayout.tvDeviceStatus.setText(R.string.status_local)
                }
            } else {
                binding.espDeviceLayout.rlNodeStatus.visibility = View.INVISIBLE
            }

            AppConstants.NODE_STATUS_REMOTELY_CONTROLLABLE -> if (espApp!!.appState == EspApplication.AppState.GET_DATA_SUCCESS) {
                binding.espDeviceLayout.rlNodeStatus.visibility = View.VISIBLE
                binding.espDeviceLayout.tvDeviceStatus.setText(R.string.status_remote)
            } else {
                binding.espDeviceLayout.rlNodeStatus.visibility = View.INVISIBLE
            }

            AppConstants.NODE_STATUS_LOCAL -> {
                val localDevice = espApp!!.localDeviceMap[nodeId]

                if (espApp!!.appState == EspApplication.AppState.GET_DATA_SUCCESS) {
                    binding.espDeviceLayout.rlNodeStatus.visibility = View.VISIBLE

                    if (espApp!!.localDeviceMap.containsKey(nodeId)) {
                        if (localDevice!!.securityType == 1 || localDevice.securityType == 2) {
                            binding.espDeviceLayout.ivSecureLocal.setVisibility(View.VISIBLE)
                        } else {
                            binding.espDeviceLayout.ivSecureLocal.setVisibility(View.GONE)
                        }
                        binding.espDeviceLayout.tvDeviceStatus.setText(R.string.local_device_text)
                    }
                } else {
                    binding.espDeviceLayout.rlNodeStatus.visibility = View.INVISIBLE
                }
            }

            AppConstants.NODE_STATUS_OFFLINE -> if (espApp!!.appState == EspApplication.AppState.GET_DATA_SUCCESS) {
                binding.espDeviceLayout.ivSecureLocal.setVisibility(View.GONE)
                var offlineText = getString(R.string.status_offline)
                binding.espDeviceLayout.tvDeviceStatus.text = offlineText

                if (timeStampOfStatus != 0L) {
                    val calendar = Calendar.getInstance()
                    val day = calendar[Calendar.DATE]

                    calendar.timeInMillis = timeStampOfStatus
                    val offlineDay = calendar[Calendar.DATE]

                    if (day == offlineDay) {
                        val formatter = SimpleDateFormat("HH:mm")
                        val time = formatter.format(calendar.time)
                        offlineText = getString(R.string.offline_at) + " " + time
                    } else {
                        val formatter = SimpleDateFormat("dd/MM/yy, HH:mm")
                        val time = formatter.format(calendar.time)
                        offlineText = getString(R.string.offline_at) + " " + time
                    }
                    binding.espDeviceLayout.tvDeviceStatus.text = offlineText
                }
            } else {
                binding.espDeviceLayout.rlNodeStatus.visibility = View.INVISIBLE
            }

            AppConstants.NODE_STATUS_ONLINE -> binding.espDeviceLayout.rlNodeStatus.visibility =
                View.INVISIBLE

            else -> binding.espDeviceLayout.rlNodeStatus.visibility = View.INVISIBLE
        }
        paramAdapter!!.updateParamList(paramList)
        attrAdapter!!.updateAttributeList(attributeList)

        if (paramList!!.size <= 0 && attributeList!!.size <= 0) {
            binding.espDeviceLayout.tvNoParams.visibility = View.VISIBLE
            binding.espDeviceLayout.rvDynamicParamList.visibility = View.GONE
            binding.espDeviceLayout.rvStaticParamList.visibility = View.GONE
        } else {
            binding.espDeviceLayout.tvNoParams.visibility = View.GONE
            binding.espDeviceLayout.rvDynamicParamList.visibility = View.VISIBLE
            binding.espDeviceLayout.rvStaticParamList.visibility = View.VISIBLE
        }

        supportActionBar!!.title = device!!.userVisibleName

        if (!isNetworkAvailable) {
            if (!snackbar!!.isShown) {
                snackbar = Snackbar.make(
                    binding.espDeviceLayout.paramsParentLayout,
                    R.string.msg_no_internet,
                    Snackbar.LENGTH_INDEFINITE
                )
            }
            snackbar!!.show()
        }
    }

    private fun showLoading() {
        binding.espDeviceLayout.progressGetParams.visibility = View.VISIBLE
        binding.espDeviceLayout.swipeContainer.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.espDeviceLayout.progressGetParams.visibility = View.GONE
        binding.espDeviceLayout.swipeContainer.visibility = View.VISIBLE
    }

    fun showParamUpdateLoading(msg: String?) {
        binding.espDeviceLayout.paramsParentLayout.setAlpha(0.3f)
        binding.rlProgress.visibility = View.VISIBLE
        val progressText = findViewById<TextView>(R.id.tv_loading)
        progressText.text = msg
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
    }

    fun hideParamUpdateLoading() {
        binding.espDeviceLayout.paramsParentLayout.setAlpha(1f)
        binding.rlProgress.visibility = View.GONE
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    // Matter Subscription Methods
    private fun setupMatterSubscriptions() {
        if (matterSubscriptionActive || matterNodeId.isNullOrEmpty() || !espApp!!.chipClientMap.containsKey(
                matterNodeId
            )
        ) {
            Log.d(TAG, "Cannot setup Matter subscriptions - already active or device not available")
            return
        }

        Log.d(TAG, "Setting up Matter subscriptions for device: $matterNodeId")
        
        lifecycleScope.launch {
            try {
                val chipClient = espApp!!.chipClientMap[matterNodeId]!!
                subscriptionHelper = SubscriptionHelper(chipClient)
                
                val deviceId = BigInteger(matterNodeId, 16).toLong()
                val connectedDevicePtr = chipClient.getConnectedDevicePointer(deviceId)
                
                // Create subscriptions based on device type
                val subscriptions = subscriptionHelper!!.createSubscriptionsForDevice(
                    device?.deviceType ?: "esp.device.switch",
                    AppConstants.ENDPOINT_1.toLong()
                )

                Log.d(
                    TAG,
                    "Created ${subscriptions.size} subscriptions for device type: ${device?.deviceType}"
                )

                // Setup subscription callbacks
                val reportCallback = createMatterReportCallback()
                val subscriptionEstablishedCallback =
                    SubscriptionHelper.SubscriptionEstablishedCallbackForDevice(deviceId)
                val resubscriptionAttemptCallback =
                    SubscriptionHelper.ResubscriptionAttemptCallbackForDevice(deviceId)

                // Start subscriptions
                subscriptionHelper!!.subscribeToMultipleAttributes(
                    connectedDevicePtr,
                    subscriptions,
                    subscriptionEstablishedCallback,
                    resubscriptionAttemptCallback,
                    reportCallback
                )
                
                matterSubscriptionActive = true
                Log.d(TAG, "Matter subscriptions setup completed successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to setup Matter subscriptions", e)
                matterSubscriptionActive = false
            }
        }
    }
    
    private fun stopMatterSubscriptions() {
        if (!matterSubscriptionActive) {
            return
        }
        
        Log.d(TAG, "Stopping Matter subscriptions")
        matterSubscriptionActive = false
        subscriptionHelper = null
    }
    
    private fun createMatterReportCallback(): ReportCallback {
        return object : ReportCallback {

            override fun onError(
                attributePath: ChipAttributePath?,
                eventPath: ChipEventPath?,
                e: java.lang.Exception
            ) {
                Log.e(TAG, "Matter subscription error", e)
            }

            override fun onReport(nodeState: NodeState) {
                Log.d(TAG, "Matter subscription report received for node: ${nodeId}")

                try {
                    for ((endpointId, endpoint) in nodeState.endpointStates) {
                        for ((clusterId, cluster) in endpoint.clusterStates) {
                            for ((attributeId, attribute) in cluster.attributeStates) {
                                Log.d(
                                    TAG,
                                    "Attribute update - Endpoint: $endpointId, Cluster: $clusterId, Attribute: $attributeId, Value: ${attribute.value}"
                                )

                                // Update UI based on cluster and attribute with throttling
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastMatterUpdateTime > MATTER_UPDATE_THROTTLE_MS) {
                                    lastMatterUpdateTime = currentTime
                                    runOnUiThread {
                                        updateParameterFromMatterAttribute(
                                            clusterId,
                                            attributeId,
                                            attribute.value
                                        )
                                    }
                                } else {
                                    Log.d(
                                        TAG,
                                        "Throttling Matter update for cluster $clusterId attribute $attributeId"
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing Matter subscription report", e)
                }
            }

            override fun onDone() {
                Log.d(TAG, "Matter subscription done")
            }
        }
    }

    private fun updateParameterFromMatterAttribute(
        clusterId: Long,
        attributeId: Long,
        value: Any?
    ) {
        if (paramList == null || value == null) {
            return
        }

        Log.d(
            TAG,
            "Updating parameter from Matter attribute - Cluster: $clusterId, Attribute: $attributeId, Value: $value"
        )

        try {
            var paramUpdated = false

            when (clusterId) {
                6L -> { // OnOff Cluster
                    if (attributeId == 0L) { // OnOff attribute
                        for (param in paramList!!) {
                            if (param.paramType == AppConstants.PARAM_TYPE_POWER || param.name.equals(
                                    "Power",
                                    true
                                )
                            ) {
                                val boolValue = value as? Boolean ?: false
                                param.switchStatus = boolValue
                                param.labelValue = if (boolValue) "true" else "false"
                                paramUpdated = true
                                break
                            }
                        }
                    }
                }

                8L -> { // Level Control Cluster
                    if (attributeId == 0L) { // CurrentLevel attribute
                        for (param in paramList!!) {
                            if (param.paramType == AppConstants.PARAM_TYPE_BRIGHTNESS || param.name.equals(
                                    "Brightness",
                                    true
                                )
                            ) {
                                val intValue = value as? Int ?: 0
                                val percentage =
                                    (intValue * 100) / 254 // Convert from 0-254 to 0-100
                                param.value = percentage.toDouble()
                                param.labelValue = percentage.toString()
                                paramUpdated = true
                                break
                            }
                        }
                    }
                }

                768L -> { // Color Control Cluster
                    when (attributeId) {
                        0L -> { // CurrentHue attribute
                            for (param in paramList!!) {
                                if (param.paramType == AppConstants.PARAM_TYPE_HUE || param.name.equals(
                                        "Hue",
                                        true
                                    )
                                ) {
                                    val intValue = value as? Int ?: 0
                                    val hueValue =
                                        (intValue * 360) / 254 // Convert from 0-254 to 0-360
                                    param.value = hueValue.toDouble()
                                    param.labelValue = hueValue.toString()
                                    paramUpdated = true
                                    break
                                }
                            }
                        }

                        1L -> { // CurrentSaturation attribute
                            for (param in paramList!!) {
                                if (param.paramType == AppConstants.PARAM_TYPE_SATURATION || param.name.equals(
                                        "Saturation",
                                        true
                                    )
                                ) {
                                    val intValue = value as? Int ?: 0
                                    val percentage =
                                        (intValue * 100) / 254 // Convert from 0-254 to 0-100
                                    param.value = percentage.toDouble()
                                    param.labelValue = percentage.toString()
                                    paramUpdated = true
                                    break
                                }
                            }
                        }

                        7L -> { // ColorTemperature attribute
                            for (param in paramList!!) {
                                if (param.paramType == AppConstants.PARAM_TYPE_CCT || param.name.equals(
                                        AppConstants.PARAM_CCT,
                                        true
                                    )
                                ) {
                                    val miredsValue = value as? Int ?: 0
                                    // Convert mireds to Kelvin: K = 1,000,000 / M
                                    val kelvinValue = if (miredsValue > 0) {
                                        1000000 / miredsValue
                                    } else {
                                        0
                                    }

                                    // Clamp Kelvin value between 2000K and 7000K
                                    val clampedKelvin = kelvinValue.coerceIn(2000, 7000)

                                    Log.d(
                                        TAG,
                                        "CCT: Mireds=$miredsValue, Raw Kelvin=$kelvinValue, Clamped Kelvin=$clampedKelvin"
                                    )

                                    // Store the clamped Kelvin value
                                    param.value = clampedKelvin.toDouble()
                                    param.labelValue = clampedKelvin.toString()
                                    paramUpdated = true
                                    break
                                }
                            }
                        }
                    }
                }

                1026L -> { // Temperature Measurement Cluster
                    if (attributeId == 0L) { // MeasuredValue attribute
                        for (param in paramList!!) {
                            if (param.paramType == AppConstants.PARAM_TYPE_TEMPERATURE || param.name.equals(
                                    "Temperature",
                                    true
                                )
                            ) {
                                val intValue = value as? Int ?: 0
                                val temperatureValue =
                                    intValue / 100.0 // Convert from centi-degrees to degrees
                                param.value = temperatureValue
                                param.labelValue = String.format("%.1f", temperatureValue)
                                paramUpdated = true
                                break
                            }
                        }
                    }
                }

//                257L -> { // Door Lock Cluster
//                    if (attributeId == 0L) { // LockState attribute
//                        for (param in paramList!!) {
//                            if (param.paramType == AppConstants.PARAM_TYPE_LOCK_STATE || param.name.equals("Lock", true)) {
//                                val intValue = value as? Int ?: 0
//                                val isLocked = intValue == 1
//                                param.setSwitchStatus(!isLocked) // UI shows unlocked state as true
//                                param.labelValue = if (isLocked) "Locked" else "Unlocked"
//                                paramUpdated = true
//                                break
//                            }
//                        }
//                    }
//                }

                513L -> { // Thermostat Cluster
                    when (attributeId) {
                        0L -> { // LocalTemperature attribute
                            for (param in paramList!!) {
                                if (param.paramType == AppConstants.PARAM_TYPE_TEMPERATURE || param.name.equals(
                                        "Temperature",
                                        true
                                    )
                                ) {
                                    val intValue = value as? Int ?: 0
                                    val temperatureValue = intValue / 100.0
                                    param.value = temperatureValue
                                    param.labelValue = String.format("%.1f", temperatureValue)
                                    paramUpdated = true
                                    break
                                }
                            }
                        }

                        17L -> { // OccupiedCoolingSetpoint
                            for (param in paramList!!) {
                                if (param.name.equals(AppConstants.PARAM_COOLING_POINT, true)) {
                                    val intValue = value as? Int ?: 0
                                    val temperatureValue = intValue / 100.0
                                    param.value = temperatureValue
                                    param.labelValue = String.format("%.1f", temperatureValue)
                                    paramUpdated = true
                                    break
                                }
                            }
                        }

                        18L -> { // OccupiedHeatingSetpoint
                            for (param in paramList!!) {
                                if (param.name.equals(AppConstants.PARAM_HEATING_POINT, true)) {
                                    val intValue = value as? Int ?: 0
                                    val temperatureValue = intValue / 100.0
                                    param.value = temperatureValue
                                    param.labelValue = String.format("%.1f", temperatureValue)
                                    paramUpdated = true
                                    break
                                }
                            }
                        }

                        28L -> { // SystemMode
                            for (param in paramList!!) {
                                if (param.name.equals(AppConstants.PARAM_SYSTEM_MODE, true)) {
                                    val intValue = value as? Int ?: 0
                                    param.value = intValue.toDouble()
                                    param.labelValue = when (intValue) {
                                        0 -> "Off"
                                        1 -> "Auto"
                                        3 -> "Cool"
                                        4 -> "Heat"
                                        else -> "Unknown"
                                    }
                                    paramUpdated = true
                                    break
                                }
                            }
                        }
                    }
                }

                514L -> { // Fan Control Cluster
                    if (attributeId == 0L) { // FanMode attribute
                        for (param in paramList!!) {
                            if (param.paramType == AppConstants.PARAM_TYPE_SPEED || param.name.equals(
                                    "Speed",
                                    true
                                )
                            ) {
                                val intValue = value as? Int ?: 0
                                param.value = intValue.toDouble()
                                param.labelValue = intValue.toString()
                                paramUpdated = true
                                break
                            }
                        }
                    }
                }

                else -> {
                    Log.d(TAG, "Unhandled cluster ID: $clusterId for attribute: $attributeId")
                }
            }

            if (paramUpdated) {
                Log.d(TAG, "Parameter updated from Matter subscription - refreshing UI")
                // Find the specific parameter position and update only that item
                val paramPosition = findParameterPosition(clusterId, attributeId)
                if (paramPosition >= 0) {
                    paramAdapter?.notifyItemChanged(paramPosition)
                } else {
                    // Fallback to full refresh if position not found
                    paramAdapter?.notifyDataSetChanged()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating parameter from Matter attribute", e)
        }
    }

    private fun findParameterPosition(clusterId: Long, attributeId: Long): Int {
        if (paramList == null) return -1

        for (i in paramList!!.indices) {
            val param = paramList!![i]

            // Map cluster/attribute to param types for position finding
            val matches = when (clusterId) {
                6L -> if (attributeId == 0L) param.paramType == AppConstants.PARAM_TYPE_POWER || param.name.equals(
                    "Power",
                    true
                ) else false

                8L -> if (attributeId == 0L) param.paramType == AppConstants.PARAM_TYPE_BRIGHTNESS || param.name.equals(
                    "Brightness",
                    true
                ) else false

                768L -> when (attributeId) {
                    0L -> param.paramType == AppConstants.PARAM_TYPE_HUE || param.name.equals(
                        AppConstants.PARAM_HUE,
                        true
                    )

                    1L -> param.paramType == AppConstants.PARAM_TYPE_SATURATION || param.name.equals(
                        AppConstants.PARAM_SATURATION,
                        true
                    )

                    7L -> param.paramType == AppConstants.PARAM_TYPE_CCT || param.name.equals(
                        AppConstants.PARAM_CCT,
                        true
                    )

                    else -> false
                }

                1026L -> if (attributeId == 0L) param.paramType == AppConstants.PARAM_TYPE_TEMPERATURE || param.name.equals(
                    "Temperature",
                    true
                ) else false

                513L -> when (attributeId) {
                    0L -> param.paramType == AppConstants.PARAM_TYPE_TEMPERATURE || param.name.equals(
                        "Temperature",
                        true
                    )

                    17L -> param.name.equals(AppConstants.PARAM_COOLING_POINT, true)
                    18L -> param.name.equals(AppConstants.PARAM_HEATING_POINT, true)
                    28L -> param.name.equals(AppConstants.PARAM_SYSTEM_MODE, true)
                    else -> false
                }

                514L -> if (attributeId == 0L) param.paramType == AppConstants.PARAM_TYPE_SPEED || param.name.equals(
                    "Speed",
                    true
                ) else false

                else -> false
            }

            if (matches) {
                Log.d(
                    TAG,
                    "Found parameter ${param.name} at position $i for cluster $clusterId attribute $attributeId"
                )
                return i
            }
        }

        Log.d(TAG, "Parameter not found for cluster $clusterId attribute $attributeId")
        return -1
    }
}