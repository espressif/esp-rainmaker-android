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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import com.espressif.rainmaker.BuildConfig
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityConsentBinding
import com.espressif.ui.Utils

class ConsentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConsentBinding

    companion object {
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
        ) { dialog, which -> dialog.dismiss() }
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
        builder.setView(webView)
        webView.webViewClient = WebViewClient()
        webView.loadUrl(url)

        builder.setPositiveButton(R.string.btn_agree) { dialog, which -> dialog.dismiss() }
        builder.setNegativeButton(R.string.btn_disagree) { dialog, which ->
            dialog.dismiss()
            finish()
        }

        val dialog = builder.create()
        dialog.show()
    }
}
