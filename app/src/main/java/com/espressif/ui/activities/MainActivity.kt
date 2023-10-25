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

package com.espressif.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

import com.espressif.AppConstants
import com.espressif.EspApplication
import com.espressif.cloudapi.ApiManager
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.cloudapi.CloudException
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityMainBinding
import com.espressif.ui.Utils
import com.espressif.ui.adapters.TabsPagerAdapter
import com.espressif.ui.fragments.LoginFragment
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val SIGN_UP_CONFIRM_ACTIVITY_REQUEST = 10
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setToolbar()
        val tabsPagerAdapter = TabsPagerAdapter(this, supportFragmentManager)
        binding.viewPager.adapter = tabsPagerAdapter
        val tabs = findViewById<TabLayout>(R.id.tabs)
        tabs.setupWithViewPager(binding.viewPager)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "On activity result")
        if (requestCode == SIGN_UP_CONFIRM_ACTIVITY_REQUEST) {
            if (resultCode == RESULT_OK) {
                val userName = data?.getStringExtra(AppConstants.KEY_USER_NAME)
                val password = data?.getStringExtra(AppConstants.KEY_PASSWORD)
                binding.viewPager.currentItem = 0
                val page =
                    supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.view_pager + ":" + binding.viewPager.currentItem)
                if (page != null && page is LoginFragment && userName != null) {
                    page.doLoginWithNewUser(userName, password)
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun launchHomeScreen() {
        (applicationContext as EspApplication).changeAppState(
            EspApplication.AppState.GETTING_DATA,
            null
        )
        Intent(applicationContext, EspMainActivity::class.java).also {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(it)
        }
        finish()
    }

    fun signInUser(email: String?, password: String?) {
        showLoginLoading()
        val apiManager = ApiManager.getInstance(applicationContext)
        apiManager.login(email, password, object : ApiResponseListener {
            override fun onSuccess(data: Bundle?) {
                hideLoginLoading()
                launchHomeScreen()
            }

            override fun onResponseFailure(exception: Exception) {
                hideLoginLoading()
                if (exception is CloudException) {
                    Utils.showAlertDialog(
                        this@MainActivity,
                        getString(R.string.dialog_title_login_failed),
                        exception.message,
                        false
                    )
                } else {
                    Utils.showAlertDialog(
                        this@MainActivity,
                        "",
                        getString(R.string.dialog_title_login_failed),
                        false
                    )
                }
            }

            override fun onNetworkFailure(exception: Exception) {
                hideLoginLoading()
                Utils.showAlertDialog(
                    this@MainActivity,
                    getString(R.string.dialog_title_no_network),
                    getString(R.string.dialog_msg_no_network),
                    false
                )
            }
        })
    }

    private fun setToolbar() {
        binding.toolbar.title = ""
        setSupportActionBar(binding.toolbar)
    }

    private fun showLoginLoading() {
        val page =
            supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.view_pager + ":" + binding.viewPager.currentItem)
        if (page != null && page is LoginFragment) {
            page.showLoading()
        }
    }

    private fun hideLoginLoading() {
        val page =
            supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.view_pager + ":" + binding.viewPager.currentItem)
        if (page != null && page is LoginFragment) {
            page.hideLoading()
        }
    }
}