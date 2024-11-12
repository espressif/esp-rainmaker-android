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

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources

import com.espressif.rainmaker.BuildConfig
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityAboutBinding
import com.espressif.ui.Utils

class AboutAppActivity : AppCompatActivity() {

    companion object {
        const val ANCHOR_TAG_START: String = "<a href='"
        const val ANCHOR_TAG_END: String = "</a>"
        const val URL_TAG_END: String = "'>"
    }

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setToolbar()
        setAppVersion()
        setRegion()
        setUrls()
    }

    private fun setToolbar() {
        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        binding.toolbarLayout.toolbar.title = getString(R.string.title_activity_about)
        binding.toolbarLayout.toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left)
        binding.toolbarLayout.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setAppVersion() {
        // Set app version
        var version = ""
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            version = pInfo.versionName.toString()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        binding.tvAppVersion.text = version
    }

    private fun setRegion() {
        // Set app region
        val region =
            if (BuildConfig.isChinaRegion) getString(R.string.china) else getString(R.string.global)
        binding.tvAppRegion.text = region
    }

    private fun setUrls() {
        // Set documentation URL
        binding.tvDocumentation.movementMethod = LinkMovementMethod.getInstance()
        val docUrl =
            ANCHOR_TAG_START + BuildConfig.DOCUMENTATION_URL + URL_TAG_END + getString(R.string.documentation) + ANCHOR_TAG_END
        binding.tvDocumentation.text = Html.fromHtml(docUrl)

        // Set privacy URL
        binding.tvPrivacy.movementMethod = LinkMovementMethod.getInstance()
        val privacyUrl =
            ANCHOR_TAG_START + Utils.getPrivacyUrl() + URL_TAG_END + getString(R.string.privacy_policy) + ANCHOR_TAG_END
        binding.tvPrivacy.text = Html.fromHtml(privacyUrl)

        // Set terms of use URL
        binding.tvTermsCondition.movementMethod = LinkMovementMethod.getInstance()
        val termsUrl =
            ANCHOR_TAG_START + Utils.getTermsOfUseUrl() + URL_TAG_END + getString(R.string.terms_of_use) + ANCHOR_TAG_END
        binding.tvTermsCondition.text = Html.fromHtml(termsUrl)
    }
}