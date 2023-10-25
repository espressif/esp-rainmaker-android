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

import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment

import com.espressif.EspApplication
import com.espressif.cloudapi.ApiManager
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.cloudapi.CloudException
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityDeleteUserBinding
import com.espressif.ui.Utils
import com.espressif.ui.fragments.DeleteUserConfirmFragment
import com.espressif.ui.fragments.DeleteUserRequestFragment

class DeleteUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeleteUserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeleteUserBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setToolbar()
        loadDeleteUserReqFragment(DeleteUserRequestFragment())
    }

    private fun setToolbar() {
        setSupportActionBar(binding.toolbarDeleteUser.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        binding.toolbarDeleteUser.toolbar.title = getString(R.string.title_activity_delete_user)
        binding.toolbarDeleteUser.toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left)
        binding.toolbarDeleteUser.toolbar.setNavigationOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is DeleteUserConfirmFragment) {
                onBackPressed()
            } else {
                finish()
            }
        }
    }

    fun sendDeleteUserRequest() {
        ApiManager.getInstance(applicationContext)
            .deleteUserRequest(true, object : ApiResponseListener {
                override fun onSuccess(data: Bundle?) {
                    hideLoading()
                    getDeleteUserCode()
                }

                override fun onResponseFailure(exception: Exception) {
                    hideLoading()
                    if (exception is CloudException) {
                        Utils.showAlertDialog(
                            this@DeleteUserActivity,
                            getString(R.string.dialog_title_delete_user_failed),
                            exception.message,
                            false
                        )
                    } else {
                        Utils.showAlertDialog(
                            this@DeleteUserActivity,
                            "",
                            getString(R.string.dialog_title_delete_user_failed),
                            false
                        )
                    }
                }

                override fun onNetworkFailure(exception: Exception) {
                    hideLoading()
                    Utils.showAlertDialog(
                        this@DeleteUserActivity,
                        getString(R.string.dialog_title_no_network),
                        getString(R.string.dialog_msg_no_network),
                        false
                    )
                }
            })
    }

    fun confirmDeleteAccount(verificationCode: String?) {
        ApiManager.getInstance(applicationContext)
            .deleteUserConfirm(verificationCode, object : ApiResponseListener {
                override fun onSuccess(data: Bundle?) {
                    hideLoading()
                    showSuccessDialog(getString(R.string.dialog_title_delete_user_success), "")
                }

                override fun onResponseFailure(exception: Exception) {
                    hideLoading()
                    if (exception is CloudException) {
                        Utils.showAlertDialog(
                            this@DeleteUserActivity,
                            getString(R.string.dialog_title_delete_user_failed),
                            exception.message,
                            false
                        )
                    } else {
                        Utils.showAlertDialog(
                            this@DeleteUserActivity,
                            "",
                            getString(R.string.dialog_title_delete_user_failed),
                            false
                        )
                    }
                }

                override fun onNetworkFailure(exception: Exception) {
                    hideLoading()
                    Utils.showAlertDialog(
                        this@DeleteUserActivity,
                        getString(R.string.dialog_title_no_network),
                        getString(R.string.dialog_msg_no_network),
                        false
                    )
                }
            })
    }

    private fun getDeleteUserCode() {
        val deleteUserConfirmFragment: Fragment = DeleteUserConfirmFragment()
        loadDeleteUserConfirmFragment(deleteUserConfirmFragment)
    }

    private fun loadDeleteUserReqFragment(deleteUserReqFragment: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fragment_container, deleteUserReqFragment)
            commit()
        }
    }

    private fun loadDeleteUserConfirmFragment(deleteUserConfirmFragment: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fragment_container, deleteUserConfirmFragment)
            commit()
        }
    }

    private fun hideLoading() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is DeleteUserRequestFragment) {
            currentFragment.hideLoading()
        } else if (currentFragment is DeleteUserConfirmFragment) {
            currentFragment.hideLoading()
        }
    }

    private fun showSuccessDialog(title: String, msg: String) {
        val builder = AlertDialog.Builder(this)
        if (!TextUtils.isEmpty(title)) {
            builder.setTitle(title)
        }
        builder.setMessage(msg)
        builder.setCancelable(false)
        builder.setNeutralButton(
            R.string.btn_ok,
            DialogInterface.OnClickListener { dialog, _ ->
                dialog.dismiss()
                val espApp = applicationContext as EspApplication
                espApp.clearUserSession()
            })
        val dialog = builder.create()
        dialog.show()
    }
}