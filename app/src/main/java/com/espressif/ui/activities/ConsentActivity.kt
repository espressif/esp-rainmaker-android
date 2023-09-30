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
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import com.espressif.rainmaker.BuildConfig
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityConsentBinding

class ConsentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConsentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConsentBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        
        setPolicyMessage()
        setAppVersion()
        setProceedBtn()
    }

    private fun setPolicyMessage() {

        val privacyPolicyClick: ClickableSpan = object : ClickableSpan() {
            override fun onClick(textView: View) {
                textView.invalidate()
                val openURL = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_URL))
                startActivity(openURL)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = resources.getColor(R.color.colorPrimary)
                ds.isUnderlineText = true
            }
        }

        val termsOfUseClick: ClickableSpan = object : ClickableSpan() {
            override fun onClick(textView: View) {
                textView.invalidate()
                val openURL = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.TERMS_URL))
                startActivity(openURL)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = resources.getColor(R.color.colorPrimary)
                ds.isUnderlineText = true
            }
        }

        val stringForPolicy = SpannableString(getString(R.string.user_agreement))
        stringForPolicy.setSpan(privacyPolicyClick, 83, 97, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        stringForPolicy.setSpan(termsOfUseClick, 102, 114, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.tvTermsCondition.text = stringForPolicy
        binding.tvTermsCondition.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setAppVersion() {
        var version = ""
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            version = pInfo.versionName
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
}
