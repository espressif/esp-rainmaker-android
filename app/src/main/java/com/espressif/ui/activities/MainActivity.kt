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

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import com.espressif.AppConstants
import com.espressif.EspApplication
import com.espressif.cloudapi.ApiManager
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.cloudapi.CloudException
import com.espressif.rainmaker.BuildConfig
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
        const val CLICK_TIME_DURATION = 5000
    }

    private lateinit var binding: ActivityMainBinding

    private var clickCount = 0
    private var startTimeOfClick: Long = 0

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
        binding.ivTitle.setOnClickListener { rmLogoClicked() }
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.isChinaRegion && EspApplication.loggedInUsingWeChat) {
            launchHomeScreen()
        }
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

    private fun rmLogoClicked() {
        val currentTIme = System.currentTimeMillis()

        when (clickCount) {
            0 -> {
                clickCount = 1
                startTimeOfClick = currentTIme
            }

            in 1..3 -> {
                if (currentTIme - startTimeOfClick < CLICK_TIME_DURATION) {
                    clickCount++
                } else {
                    clickCount = 1
                    startTimeOfClick = currentTIme
                }
            }

            4 -> {
                if (currentTIme - startTimeOfClick < CLICK_TIME_DURATION) {
                    clickCount = 0
                    askForBaseUrl()
                } else {
                    clickCount = 1
                    startTimeOfClick = currentTIme
                }
            }
        }
    }

    private fun askForBaseUrl() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setTitle(R.string.base_url_title)
        val layoutInflaterAndroid = LayoutInflater.from(this)
        val view: View = layoutInflaterAndroid.inflate(R.layout.dialog_base_url, null)
        builder.setView(view)
        val etPrefix: EditText = view.findViewById(R.id.et_base_url)
        etPrefix.setText(EspApplication.BASE_URL)
        etPrefix.setSelection(etPrefix.text.length)

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_update,
            DialogInterface.OnClickListener { dialog, which ->
                var baseUrl = etPrefix.text.toString().trim()
                if (TextUtils.isEmpty(baseUrl)) {
                    Toast.makeText(this, R.string.error_base_url_empty, Toast.LENGTH_LONG).show()
                } else {
                    updateBaseUrl(baseUrl.trim())
                    dialog.dismiss()
                }
            })

        builder.setNegativeButton(R.string.btn_reset_to_defaults,
            DialogInterface.OnClickListener { dialog, which ->
                val defaultBaseUrl = BuildConfig.BASE_URL
                updateBaseUrl(defaultBaseUrl)
                etPrefix.setText(defaultBaseUrl)
            })

        builder.setNeutralButton(R.string.btn_cancel,
            DialogInterface.OnClickListener { dialog, which -> dialog.dismiss() })

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
            val defaultBaseUrl = BuildConfig.BASE_URL
            updateBaseUrl(defaultBaseUrl)
            etPrefix.setText(defaultBaseUrl)
        }
    }

    private fun updateBaseUrl(baseUrl: String) {
        var formattedBaseUrl = baseUrl
        if (!formattedBaseUrl.startsWith("http://") && !formattedBaseUrl.startsWith("https://")) {
            formattedBaseUrl = "https://$formattedBaseUrl"
        }
        Log.d(TAG, "Formatted Base URL: $formattedBaseUrl")
        val sharedPreferences =
            getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(AppConstants.KEY_BASE_URL, formattedBaseUrl)
        editor.apply()
        EspApplication.BASE_URL = formattedBaseUrl
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