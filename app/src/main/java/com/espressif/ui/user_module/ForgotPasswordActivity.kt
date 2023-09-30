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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment

import com.espressif.AppConstants
import com.espressif.cloudapi.ApiManager
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.cloudapi.CloudException
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityForgotPasswordBinding
import com.espressif.ui.Utils
import com.espressif.ui.fragments.ForgotPasswordFragment
import com.espressif.ui.fragments.ResetPasswordFragment

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding

    private var email: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setToolbar()
        loadForgotPasswordFragment(ForgotPasswordFragment())
    }

    override fun onBackPressed() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is ResetPasswordFragment) {
            val forgotPasswordFragment: Fragment = ForgotPasswordFragment()
            val data = Bundle()
            data.putString(AppConstants.KEY_USER_NAME, email)
            forgotPasswordFragment.arguments = data
            loadForgotPasswordFragment(forgotPasswordFragment)
        } else {
            super.onBackPressed()
        }
    }

    private fun setToolbar() {
        setSupportActionBar(binding.toolbarForgotPassword.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        binding.toolbarForgotPassword.toolbar.title =
            getString(R.string.title_activity_forgot_password)
        binding.toolbarForgotPassword.toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left)
        binding.toolbarForgotPassword.toolbar.setNavigationOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is ResetPasswordFragment) {
                onBackPressed()
            } else {
                finish()
            }
        }
    }

    private fun loadForgotPasswordFragment(forgotPasswordFragment: Fragment) {
        binding.toolbarForgotPassword.toolbar.title =
            getString(R.string.title_activity_forgot_password)
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fragment_container, forgotPasswordFragment)
            commit()
        }
    }

    private fun loadResetPasswordFragment(resetPasswordFragment: Fragment) {
        binding.toolbarForgotPassword.toolbar.title =
            getString(R.string.title_activity_reset_password)
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fragment_container, resetPasswordFragment)
            commit()
        }
    }

    private fun getForgotPasswordCode(email: String) {
        val resetPasswordFragment: Fragment = ResetPasswordFragment()
        val data = Bundle()
        data.putString(AppConstants.KEY_USER_NAME, email)
        resetPasswordFragment.arguments = data
        loadResetPasswordFragment(resetPasswordFragment)
    }

    fun forgotPassword(emailStr: String?) {
        email = emailStr
        val apiManager = ApiManager.getInstance(applicationContext)
        apiManager.forgotPassword(email, object : ApiResponseListener {
            override fun onSuccess(data: Bundle?) {
                hideLoading()
                email?.let { getForgotPasswordCode(it) }
            }

            override fun onResponseFailure(exception: Exception) {
                hideLoading()
                if (exception is CloudException) {
                    Utils.showAlertDialog(
                        this@ForgotPasswordActivity,
                        getString(R.string.dialog_title_forgot_password_failed),
                        exception.message,
                        false
                    )
                } else {
                    Utils.showAlertDialog(
                        this@ForgotPasswordActivity,
                        "",
                        getString(R.string.dialog_title_forgot_password_failed),
                        false
                    )
                }
            }

            override fun onNetworkFailure(exception: Exception) {
                hideLoading()
                Utils.showAlertDialog(
                    this@ForgotPasswordActivity,
                    getString(R.string.dialog_title_no_network),
                    getString(R.string.dialog_msg_no_network),
                    false
                )
            }
        })
    }

    fun resetPassword(newPassword: String?, verificationCode: String?) {
        val apiManager = ApiManager.getInstance(applicationContext)
        apiManager.resetPassword(
            email,
            newPassword,
            verificationCode,
            object : ApiResponseListener {
                override fun onSuccess(data: Bundle?) {
                    hideLoading()
                    Utils.showAlertDialog(
                        this@ForgotPasswordActivity,
                        getString(R.string.success),
                        getString(R.string.dialog_title_password_changed),
                        true
                    )
                }

                override fun onResponseFailure(exception: java.lang.Exception) {
                    hideLoading()
                    if (exception is CloudException) {
                        Utils.showAlertDialog(
                            this@ForgotPasswordActivity,
                            getString(R.string.dialog_title_forgot_password_failed),
                            exception.message,
                            false
                        )
                    } else {
                        Utils.showAlertDialog(
                            this@ForgotPasswordActivity,
                            "",
                            getString(R.string.dialog_title_forgot_password_failed),
                            false
                        )
                    }
                }

                override fun onNetworkFailure(exception: java.lang.Exception) {
                    hideLoading()
                    Utils.showAlertDialog(
                        this@ForgotPasswordActivity,
                        getString(R.string.dialog_title_no_network),
                        getString(R.string.dialog_msg_no_network),
                        false
                    )
                }
            })
    }

    private fun hideLoading() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is ForgotPasswordFragment) {
            currentFragment.hideLoading()
        } else if (currentFragment is ResetPasswordFragment) {
            currentFragment.hideLoading()
        }
    }
}