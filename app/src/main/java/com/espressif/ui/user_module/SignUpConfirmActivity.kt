// Copyright 2023 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.ui.user_module

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources

import com.espressif.AppConstants
import com.espressif.cloudapi.ApiManager
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.cloudapi.CloudException
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivitySignUpConfirmBinding
import com.espressif.ui.Utils

class SignUpConfirmActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpConfirmBinding

    private var userName: String? = null
    private var password: String? = null
    private var userDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpConfirmBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val extras = intent.extras
        if (extras != null) {
            if (extras.containsKey(AppConstants.KEY_USER_NAME)) {
                userName = extras.getString(AppConstants.KEY_USER_NAME)
                password = extras.getString(AppConstants.KEY_PASSWORD)
            }
        }

        if (TextUtils.isEmpty(userName)) {
            Toast.makeText(
                this@SignUpConfirmActivity,
                getString(R.string.error_username_empty),
                Toast.LENGTH_LONG
            ).show()
            finish()
        } else {
            setToolbar()
            initViews()
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED, intent)
        super.onBackPressed()
    }

    private fun setToolbar() {
        setSupportActionBar(binding.toolbarSignupConfirm.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        binding.toolbarSignupConfirm.toolbar.title =
            getString(R.string.title_activity_sign_up_confirm)
        binding.toolbarSignupConfirm.toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left)
        binding.toolbarSignupConfirm.toolbar.setNavigationOnClickListener {
            setResult(RESULT_CANCELED, intent)
            finish()
        }
    }

    private fun initViews() {
        binding.btnConfirm.textBtn.text = getString(R.string.btn_confirm)
        binding.etVerificationCode.requestFocus()

        val confMsg =
            getString(R.string.verification_code_sent_instruction) + "<b>" + userName + "</b> "
        binding.tvSignUpConfirmMsg1.text = Html.fromHtml(confMsg)

        binding.btnConfirm.layoutBtn.setOnClickListener { sendConfCode() }
        binding.tvResendCode.setOnClickListener { reqConfCode() }

        binding.etVerificationCode.setOnEditorActionListener(OnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                sendConfCode()
            }
            false
        })
    }

    private fun sendConfCode() {
        val confirmCode: String = binding.etVerificationCode.text.toString()
        binding.etVerificationCode.error = null
        if (TextUtils.isEmpty(confirmCode)) {
            binding.etVerificationCode.error = getString(R.string.error_confirmation_code_empty)
            return
        }
        showLoading()
        val apiManager = ApiManager.getInstance(applicationContext)
        apiManager.confirmUser(userName, confirmCode, object : ApiResponseListener {
            override fun onSuccess(data: Bundle?) {
                hideLoading()
                showDialogMessage(
                    getString(R.string.success),
                    "$userName has been confirmed!",
                    true
                )
            }

            override fun onResponseFailure(exception: java.lang.Exception) {
                hideLoading()
                if (exception is CloudException) {
                    Utils.showAlertDialog(
                        this@SignUpConfirmActivity,
                        getString(R.string.confirmation_failed),
                        exception.message,
                        false
                    )
                } else {
                    Utils.showAlertDialog(
                        this@SignUpConfirmActivity,
                        "",
                        getString(R.string.confirmation_failed),
                        false
                    )
                }
            }

            override fun onNetworkFailure(exception: java.lang.Exception) {
                hideLoading()
                Utils.showAlertDialog(
                    this@SignUpConfirmActivity,
                    getString(R.string.dialog_title_no_network),
                    getString(R.string.dialog_msg_no_network),
                    false
                )
            }
        })
    }

    private fun reqConfCode() {
        val apiManager = ApiManager.getInstance(applicationContext)
        apiManager.createUser(userName, password, object : ApiResponseListener {
            override fun onSuccess(data: Bundle?) {
                binding.etVerificationCode.requestFocus()
                showDialogMessage(
                    getString(R.string.dialog_title_conf_code_sent),
                    "Code sent to $userName", false
                )
            }

            override fun onResponseFailure(exception: java.lang.Exception) {
                if (exception is CloudException) {
                    Utils.showAlertDialog(
                        this@SignUpConfirmActivity,
                        getString(R.string.dialog_title_sign_up_failed),
                        exception.message,
                        false
                    )
                } else {
                    Utils.showAlertDialog(
                        this@SignUpConfirmActivity,
                        "",
                        getString(R.string.dialog_title_conf_code_req_fail),
                        false
                    )
                }
            }

            override fun onNetworkFailure(exception: java.lang.Exception) {
                Utils.showAlertDialog(
                    this@SignUpConfirmActivity,
                    getString(R.string.dialog_title_no_network),
                    getString(R.string.dialog_msg_no_network),
                    false
                )
            }
        })
    }

    private fun showDialogMessage(title: String, body: String, exitActivity: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title).setMessage(body).setNeutralButton(
            R.string.btn_ok
        ) { dialog, which ->
            try {
                userDialog?.dismiss()
                if (exitActivity) {
                    val intent = Intent()
                    intent.putExtra(AppConstants.KEY_USER_NAME, userName)
                    intent.putExtra(AppConstants.KEY_PASSWORD, password)
                    setResult(RESULT_OK, intent)
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        userDialog = builder.create()
        userDialog!!.show()
    }

    private fun showLoading() {
        binding.btnConfirm.layoutBtn.isEnabled = false
        binding.btnConfirm.layoutBtn.alpha = 0.5f
        binding.btnConfirm.textBtn.text = getString(R.string.btn_confirming)
        binding.btnConfirm.progressIndicator.visibility = View.VISIBLE
        binding.btnConfirm.ivArrow.visibility = View.GONE
    }

    fun hideLoading() {
        binding.btnConfirm.layoutBtn.isEnabled = true
        binding.btnConfirm.layoutBtn.alpha = 1f
        binding.btnConfirm.textBtn.text = getString(R.string.btn_confirm)
        binding.btnConfirm.progressIndicator.visibility = View.GONE
        binding.btnConfirm.ivArrow.visibility = View.VISIBLE
    }
}
