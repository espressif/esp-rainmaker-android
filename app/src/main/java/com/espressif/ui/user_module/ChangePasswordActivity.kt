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

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources

import com.espressif.EspApplication
import com.espressif.cloudapi.ApiManager
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.cloudapi.CloudException
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityChangePasswordBinding
import com.espressif.ui.Utils

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangePasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setToolbar()
        initViews()
    }

    private fun setToolbar() {
        setSupportActionBar(binding.toolbarChangePassword.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        binding.toolbarChangePassword.toolbar.title =
            getString(R.string.title_activity_change_password)
        binding.toolbarChangePassword.toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left)
        binding.toolbarChangePassword.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun initViews() {
        binding.btnSetPassword.textBtn.text = getString(R.string.btn_set_password)
        binding.btnSetPassword.ivArrow.visibility = View.GONE

        binding.btnSetPassword.layoutBtn.setOnClickListener { changePassword() }
        binding.etConfirmNewPassword.setOnEditorActionListener(OnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                changePassword()
            }
            false
        })
    }

    private fun changePassword() {
        binding.layoutOldPassword.error = null
        binding.layoutNewPassword.error = null
        binding.layoutConfirmNewPassword.error = null
        val oldPassword: String = binding.etOldPassword.text.toString()
        if (TextUtils.isEmpty(oldPassword)) {
            binding.layoutOldPassword.error = getString(R.string.error_password_empty)
            return
        }
        val newPassword: String = binding.etNewPassword.getText().toString()
        if (TextUtils.isEmpty(newPassword)) {
            binding.layoutNewPassword.error = getString(R.string.error_password_empty)
            return
        }
        val confirmNewPassword: String = binding.etConfirmNewPassword.getText().toString()
        if (TextUtils.isEmpty(confirmNewPassword)) {
            binding.layoutConfirmNewPassword.error =
                getString(R.string.error_confirm_password_empty)
            return
        }
        if (newPassword != confirmNewPassword) {
            binding.layoutConfirmNewPassword.error = getString(R.string.error_password_not_matched)
            return
        }

        showLoading()
        val apiManager = ApiManager.getInstance(applicationContext)
        apiManager.changePassword(oldPassword, newPassword, object : ApiResponseListener {
            override fun onSuccess(data: Bundle?) {
                hideLoading()
                showAlertDialog(
                    getString(R.string.success),
                    getString(R.string.password_change_success)
                )
                clearInput()
            }

            override fun onResponseFailure(exception: Exception) {
                hideLoading()
                if (exception is CloudException) {
                    Utils.showAlertDialog(
                        this@ChangePasswordActivity,
                        getString(R.string.password_change_failure),
                        exception.message,
                        false
                    )
                } else {
                    Utils.showAlertDialog(
                        this@ChangePasswordActivity,
                        "",
                        getString(R.string.password_change_failure),
                        false
                    )
                }
            }

            override fun onNetworkFailure(exception: Exception) {
                hideLoading()
                Utils.showAlertDialog(
                    this@ChangePasswordActivity,
                    getString(R.string.dialog_title_no_network),
                    getString(R.string.dialog_msg_no_network),
                    false
                )
            }
        })
    }

    private fun showAlertDialog(title: String, msg: String) {
        val builder = AlertDialog.Builder(this@ChangePasswordActivity)
        if (!TextUtils.isEmpty(title)) {
            builder.setTitle(title)
        }
        builder.setMessage(msg)
        builder.setNeutralButton(
            R.string.btn_ok
        ) { dialog, which ->
            dialog.dismiss()
            val appContext = applicationContext as EspApplication
            appContext.logout()
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun clearInput() {
        binding.etOldPassword.setText("")
        binding.etNewPassword.setText("")
        binding.etConfirmNewPassword.setText("")
    }

    private fun showLoading() {
        binding.btnSetPassword.layoutBtn.isEnabled = false
        binding.btnSetPassword.layoutBtn.alpha = 0.5f
        binding.btnSetPassword.textBtn.text = getString(R.string.btn_setting_password)
        binding.btnSetPassword.progressIndicator.visibility = View.VISIBLE
    }

    fun hideLoading() {
        binding.btnSetPassword.layoutBtn.isEnabled = true
        binding.btnSetPassword.layoutBtn.alpha = 1f
        binding.btnSetPassword.textBtn.text = getString(R.string.btn_set_password)
        binding.btnSetPassword.progressIndicator.visibility = View.GONE
    }
}