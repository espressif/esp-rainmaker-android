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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.espressif.AppConstants
import com.espressif.EspApplication
import com.espressif.NetworkApiManager
import com.espressif.cloudapi.ApiManager
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.cloudapi.CloudException
import com.espressif.rainmaker.BuildConfig
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityControllerLoginBinding
import com.espressif.ui.Utils
import com.espressif.ui.models.UpdateEvent
import com.espressif.utils.NodeUtils.Companion.getService
import com.espressif.utils.ParamUtils
import com.google.gson.JsonObject
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.math.BigInteger

class ControllerLoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ControllerLoginActivity"
    }

    private lateinit var binding: ActivityControllerLoginBinding

    private var email: String? = null
    private var password: String? = null
    private var nodeId: String? = null
    private var isCtrlService = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityControllerLoginBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setToolbar()
        init()
    }

    override fun onResume() {

        super.onResume()
        EventBus.getDefault().register(this)
        if (intent.data != null && intent.data.toString()
                .contains(BuildConfig.REDIRECT_URI)
        ) {
            Log.d(TAG, "Data : " + intent.data.toString())
            var code = intent.data.toString().replace(BuildConfig.REDIRECT_URI, "")
            code = code.replace("?code=", "")
            code = code.replace("#", "")
            Log.d(TAG, "Code : $code")
            val apiManager = ApiManager.getInstance(this@ControllerLoginActivity.applicationContext)

            apiManager.getOAuthTokenForController(code, object : ApiResponseListener {
                override fun onSuccess(data: Bundle?) {
                    Log.d(TAG, "Received success in OAuth")
                    if (data != null) {
                        val refreshToken: String =
                            data.getString(AppConstants.KEY_REFRESH_TOKEN, "")
                        sendRefreshToken(refreshToken)
                    }
                }

                override fun onResponseFailure(exception: Exception) {
                    hideLoading()
                    if (exception is CloudException) {
                        Utils.showAlertDialog(
                            this@ControllerLoginActivity,
                            getString(R.string.dialog_title_login_failed),
                            exception.message,
                            false
                        )
                    } else {
                        Utils.showAlertDialog(
                            this@ControllerLoginActivity,
                            "",
                            getString(R.string.dialog_title_login_failed),
                            false
                        )
                    }
                }

                override fun onNetworkFailure(exception: Exception) {
                    hideLoading()
                    Utils.showAlertDialog(
                        this@ControllerLoginActivity,
                        getString(R.string.dialog_title_no_network),
                        getString(R.string.dialog_msg_no_network),
                        false
                    )
                }
            })
        }
    }

    override fun onPause() {
        super.onPause()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public fun onEvent(event: UpdateEvent) {
        Log.d("TAG", "Update Event Received : " + event.eventType)
        if (event.eventType.equals(AppConstants.Companion.UpdateEventType.EVENT_CTRL_CONFIG_DONE)) {
            hideLoading()
            finish()
        }
    }

    private fun setToolbar() {
        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        binding.toolbarLayout.toolbar.title = "Matter Controller Setup"
        binding.toolbarLayout.toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left)
        binding.toolbarLayout.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun init() {

        nodeId = intent.getStringExtra(AppConstants.KEY_NODE_ID)
        isCtrlService = intent.getBooleanExtra(AppConstants.KEY_IS_CTRL_SERVICE, false)
        binding.btnLogin.textBtn.text = getString(R.string.btn_login)
        binding.btnLogin.layoutBtn.setOnClickListener { signInUser() }

        binding.btnLoginWithGithub.layoutBtnGithub.setOnClickListener {
            val uriStr = BuildConfig.GITHUB_URL
            val uri = Uri.parse(uriStr)
            val openURL = Intent(Intent.ACTION_VIEW, uri)
            startActivity(openURL)
        }

        binding.btnLoginWithGoogle.layoutBtn.setOnClickListener {
            val uriStr = BuildConfig.GOOGLE_URL
            val uri = Uri.parse(uriStr)
            val openURL = Intent(Intent.ACTION_VIEW, uri)
            startActivity(openURL)
        }

        binding.etPassword.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                signInUser()
            }
            false
        }
    }

    private fun signInUser() {
        email = binding.etEmail.text.toString()
        password = binding.etPassword.text.toString()
        binding.etEmail.error = null
        binding.layoutPassword.error = null

        if (TextUtils.isEmpty(email)) {
            binding.etEmail.error = getString(R.string.error_username_empty)
            return
        } else if (!Utils.isValidEmail(email)) {
            binding.etEmail.error = getString(R.string.error_invalid_email)
            return
        }
        if (TextUtils.isEmpty(password)) {
            binding.layoutPassword.error = getString(R.string.error_password_empty)
            return
        }
        signInUser(email, password)
    }

    fun signInUser(email: String?, password: String?) {
        showLoading()
        val apiManager = ApiManager.getInstance(applicationContext)
        apiManager.controllerLogin(email, password, object : ApiResponseListener {
            override fun onSuccess(data: Bundle?) {
                if (data != null) {
                    val refreshToken: String = data.getString(AppConstants.KEY_REFRESH_TOKEN, "")
                    sendRefreshToken(refreshToken)
                }
            }

            override fun onResponseFailure(exception: Exception) {
                hideLoading()
                if (exception is CloudException) {
                    Utils.showAlertDialog(
                        this@ControllerLoginActivity,
                        getString(R.string.dialog_title_login_failed),
                        exception.message,
                        false
                    )
                } else {
                    Utils.showAlertDialog(
                        this@ControllerLoginActivity,
                        "",
                        getString(R.string.dialog_title_login_failed),
                        false
                    )
                }
            }

            override fun onNetworkFailure(exception: Exception) {
                hideLoading()
                Utils.showAlertDialog(
                    this@ControllerLoginActivity,
                    getString(R.string.dialog_title_no_network),
                    getString(R.string.dialog_msg_no_network),
                    false
                )
            }
        })
    }

    private fun sendRefreshToken(token: String) {

        val espApp = applicationContext as EspApplication

        if (isCtrlService) {
            val serviceParamJson = JsonObject()

            var baseUrlParamName = AppConstants.PARAM_BASE_URL
            var userTokenParamName = AppConstants.PARAM_USER_TOKEN
            var rmGroupIdParamName = AppConstants.PARAM_RMAKER_GROUP_ID

            nodeId?.let {
                baseUrlParamName = ParamUtils.getParamNameForService(
                    it,
                    AppConstants.SERVICE_TYPE_MATTER_CONTROLLER,
                    AppConstants.PARAM_TYPE_BASE_URL,
                    espApp
                )
                userTokenParamName = ParamUtils.getParamNameForService(
                    it,
                    AppConstants.SERVICE_TYPE_MATTER_CONTROLLER,
                    AppConstants.PARAM_TYPE_USER_TOKEN,
                    espApp
                )
                rmGroupIdParamName = ParamUtils.getParamNameForService(
                    it,
                    AppConstants.SERVICE_TYPE_MATTER_CONTROLLER,
                    AppConstants.PARAM_TYPE_RMAKER_GROUP_ID,
                    espApp
                )
            }

            serviceParamJson.addProperty(baseUrlParamName, EspApplication.BASE_URL)
            serviceParamJson.addProperty(userTokenParamName, token)
            serviceParamJson.addProperty(
                rmGroupIdParamName,
                intent.getStringExtra(AppConstants.KEY_GROUP_ID)
            )

            // Get service name
            var serviceName = AppConstants.KEY_MATTER_CTL
            if (espApp.nodeMap[nodeId] != null) {
                val service = getService(
                    espApp.nodeMap[nodeId]!!,
                    AppConstants.SERVICE_TYPE_MATTER_CONTROLLER
                )
                if (service != null && !TextUtils.isEmpty(service.name)) {
                    serviceName = service.name
                }
            }

            val body = JsonObject()
            body.add(serviceName, serviceParamJson)

            val networkApiManager = NetworkApiManager(espApp)
            networkApiManager.updateParamValue(nodeId, body, object : ApiResponseListener {
                override fun onSuccess(data: Bundle?) {
                    hideLoading()
                    finish()
                }

                override fun onResponseFailure(exception: java.lang.Exception) {
                }

                override fun onNetworkFailure(exception: java.lang.Exception) {
                }
            })
        } else {

            val matterNodeId = espApp.matterRmNodeIdMap[nodeId]
            val id = matterNodeId?.let { BigInteger(it, 16) }
            val deviceId = id?.toLong()
            Log.d(TAG, "Device id : $deviceId")

            if (espApp.chipClientMap[matterNodeId] != null) {
                val clustersHelper =
                    ControllerClusterHelper(espApp.chipClientMap[matterNodeId]!!, this)

                if (deviceId != null) {
                    nodeId?.let {
                        clustersHelper.sendTokenToDeviceAsync(
                            it,
                            deviceId, AppConstants.ENDPOINT_0,
                            AppConstants.CONTROLLER_CLUSTER_ID_HEX, token
                        )
                    }
                }

                val sharedPreferences =
                    getSharedPreferences(AppConstants.ESP_PREFERENCES, MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                val key = "ctrl_setup_$nodeId"
                editor.putBoolean(key, true)
                editor.apply()
            }
        }
    }

    fun showLoading() {
        binding.btnLogin.layoutBtn.isEnabled = false
        binding.btnLogin.layoutBtn.alpha = 0.5f
        binding.btnLogin.textBtn.text = getString(R.string.btn_signing_in)
        binding.btnLogin.progressIndicator.visibility = View.VISIBLE
        binding.btnLogin.ivArrow.visibility = View.GONE
    }

    fun hideLoading() {
        binding.btnLogin.layoutBtn.isEnabled = true
        binding.btnLogin.layoutBtn.alpha = 1f
        binding.btnLogin.textBtn.text = getString(R.string.btn_login)
        binding.btnLogin.progressIndicator.visibility = View.GONE
        binding.btnLogin.ivArrow.visibility = View.VISIBLE
    }
}