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
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.ViewGroup
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import cn.jpush.android.api.JPushInterface

import com.espressif.rainmaker.BuildConfig
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityConsentBinding
import com.espressif.ui.Utils

class ConsentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConsentBinding

    companion object {
        private const val TAG = "ConsentActivity"
        const val ANCHOR_TAG_START: String = "<a href='"
        const val ANCHOR_TAG_END: String = "</a>"
        const val URL_TAG_END: String = "'>"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConsentBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setUrls()
        setAppVersion()
        setProceedBtn()

        if (BuildConfig.isChinaRegion) {
            showWebViewDialog(Utils.getPrivacyUrl())
        }
    }

    private fun setAppVersion() {
        var version = ""
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            version = pInfo.versionName.toString()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        val appVersion = getString(R.string.app_version) + " - v" + version
        binding.tvAppVersion.text = appVersion
    }

    private fun setProceedBtn() {
        binding.btnProceed.textBtn.setText(R.string.btn_proceed)
        binding.btnProceed.layoutBtn.setOnClickListener {
            if (binding.cbTermsCondition.isChecked) {

                if (BuildConfig.isChinaRegion) {
                    // After user agrees
                    JPushInterface.setDebugMode(false)
                    JPushInterface.init(this)
                }
                launchLoginScreen()
            } else {
                displayConsentError()
            }
        }
    }

    private fun launchLoginScreen() {
        Intent(applicationContext, MainActivity::class.java).also {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(it)
        }
        finish()
    }

    private fun displayConsentError() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.dialog_title_error)
        builder.setMessage(R.string.error_user_agreement)
        builder.setPositiveButton(
            R.string.btn_ok
        ) { dialog, _ -> dialog.dismiss() }
        val userDialog = builder.create()
        userDialog.show()
    }

    private fun setUrls() {
        // Set privacy URL
        binding.tvPrivacy.movementMethod = LinkMovementMethod.getInstance()
        val privacyUrl =
            ANCHOR_TAG_START + Utils.getPrivacyUrl() + URL_TAG_END + getString(R.string.privacy_policy) + ANCHOR_TAG_END
        binding.tvPrivacy.text = Html.fromHtml(privacyUrl)

        // Set terms of use URL
        binding.tvTermsOfUse.movementMethod = LinkMovementMethod.getInstance()
        val termsUrl =
            ANCHOR_TAG_START + Utils.getTermsOfUseUrl() + URL_TAG_END + getString(R.string.terms_of_use) + AboutAppActivity.ANCHOR_TAG_END
        binding.tvTermsOfUse.text = Html.fromHtml(termsUrl)
    }

    private fun showWebViewDialog(url: String) {

        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setTitle(R.string.privacy_policy)

        val webView = WebView(this)
        
        // Configure WebView settings
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.setSupportZoom(true)
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        
        // Set WebViewClient with proper error handling
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.e(TAG, "WebView error: ${error?.description} (Code: ${error?.errorCode}) for URL: ${request?.url}")
            }
            
            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e(TAG, "WebView error: $description (Code: $errorCode) for URL: $failingUrl")
            }
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "Page started loading: $url")
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished loading: $url")
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Allow WebView to handle navigation normally
                return false
            }
        }
        
        // Set WebChromeClient for progress tracking
        webView.webChromeClient = WebChromeClient()
        
        // Set explicit size for WebView in AlertDialog
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.9).toInt()
        val height = (displayMetrics.heightPixels * 0.8).toInt()
        
        webView.layoutParams = ViewGroup.LayoutParams(
            width,
            height
        )
        
        builder.setView(webView)
        
        builder.setPositiveButton(R.string.btn_agree) { dialog, _ -> dialog.dismiss() }
        builder.setNegativeButton(R.string.btn_disagree) { dialog, _ ->
            dialog.dismiss()
            finish()
        }

        val dialog = builder.create()
        dialog.show()
        
        // Adjust dialog window size for better WebView display
        dialog.window?.setLayout(
            width,
            height
        )
        
        // Load URL after dialog is shown to ensure WebView is properly initialized
        webView.loadUrl(url)
    }
}
