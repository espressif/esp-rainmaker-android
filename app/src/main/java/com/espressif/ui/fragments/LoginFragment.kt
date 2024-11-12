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

package com.espressif.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment

import com.espressif.cloudapi.ApiManager
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.rainmaker.BuildConfig
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.FragmentLoginBinding
import com.espressif.ui.Utils
import com.espressif.ui.activities.MainActivity
import com.espressif.ui.user_module.ForgotPasswordActivity
import com.tencent.mm.opensdk.modelmsg.SendAuth
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory

class LoginFragment : Fragment(R.layout.fragment_login) {

    companion object {
        const val TAG = "LoginFragment"
        const val ANCHOR_TAG_START: String = "<a href='"
        const val ANCHOR_TAG_END: String = "</a>"
        const val URL_TAG_END: String = "'>"
    }

    private var _binding: FragmentLoginBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private var email: String? = null
    private var password: String? = null

    private lateinit var api: IWXAPI

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {

        super.onResume()
        val activityIntent = requireActivity().intent
        if (activityIntent.data != null && activityIntent.data.toString()
                .contains(BuildConfig.REDIRECT_URI)
        ) {
            Log.d(TAG, "Data : " + activityIntent.data.toString())
            var code = activityIntent.data.toString().replace(BuildConfig.REDIRECT_URI, "")
            code = code.replace("?code=", "")
            code = code.replace("#", "")
            Log.d(TAG, "Code : $code")
            val apiManager = ApiManager.getInstance(requireActivity().applicationContext)

            apiManager.getOAuthToken(code, object : ApiResponseListener {
                override fun onSuccess(data: Bundle?) {
                    Log.d(TAG, "Received success in OAuth")
//                    hideGitHubLoginLoading();
//                    hideGoogleLoginLoading();
                    (activity as MainActivity?)!!.launchHomeScreen()
                }

                override fun onResponseFailure(exception: java.lang.Exception) {
//                    hideGitHubLoginLoading();
//                    hideGoogleLoginLoading();
                    Toast.makeText(activity, R.string.error_login, Toast.LENGTH_SHORT).show()
                }

                override fun onNetworkFailure(exception: java.lang.Exception) {
//                    hideGitHubLoginLoading();
//                    hideGoogleLoginLoading();
                    Toast.makeText(activity, R.string.error_login, Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    fun doLoginWithNewUser(newUserEmail: String, newUserPassword: String?) {
        Log.d(TAG, "Login with new user : $newUserEmail")

        binding.etEmail.setText(newUserEmail)
        binding.etPassword.setText(newUserPassword)
        email = binding.etEmail.text.toString()
        password = binding.etPassword.text.toString()
        binding.etEmail.error = null
        binding.layoutPassword.error = null

        if (TextUtils.isEmpty(email)) {
            binding.etEmail.error = getString(R.string.error_username_empty)
            return
        } else if (!Utils.isValidEmail(email)) {
            binding.etEmail.error = getString(R.string.error_invalid_email)
            return
        }
        if (TextUtils.isEmpty(password)) {
            binding.layoutPassword.error = getString(R.string.error_password_empty)
            return
        }
        (activity as MainActivity?)!!.signInUser(email, password)
    }

    fun initViews() {

        setLinks()
        setAppVersion()

        if (BuildConfig.isChinaRegion) {

            binding.btnLoginWithWeChat.layoutBtn.visibility = View.VISIBLE
            binding.btnLoginWithWeChat.ivOauth.setImageResource(R.drawable.ic_we_chat)
            binding.btnLoginWithWeChat.textBtn.text = getString(R.string.btn_we_chat)
            binding.btnLoginWithGoogle.layoutBtn.visibility = View.GONE
            binding.btnLoginWithGithub.layoutBtnGithub.visibility = View.GONE
            binding.tvUseEmailId.visibility = View.GONE
            binding.etEmail.visibility = View.GONE
            binding.layoutPassword.visibility = View.GONE
            binding.btnLogin.layoutBtn.visibility = View.GONE
            binding.tvForgotPassword.visibility = View.GONE

            api = WXAPIFactory.createWXAPI(
                activity, BuildConfig.CHINA_WE_CHAT_APP_ID,
                true
            )

            api.registerApp(BuildConfig.CHINA_WE_CHAT_APP_ID)

        } else {
            binding.btnLoginWithWeChat.layoutBtn.visibility = View.GONE
        }

        binding.btnLogin.textBtn.text = getString(R.string.btn_login)
        binding.btnLogin.layoutBtn.setOnClickListener { signInUser() }

        binding.btnLoginWithGithub.layoutBtnGithub.setOnClickListener {
//            showGitHubLoginLoading();
            val uriStr = BuildConfig.GITHUB_URL
            val uri = Uri.parse(uriStr)
            val openURL = Intent(Intent.ACTION_VIEW, uri)
            startActivity(openURL)
        }

        binding.btnLoginWithGoogle.layoutBtn.setOnClickListener {
//            showGoogleLoginLoading();
            val uriStr = BuildConfig.GOOGLE_URL
            val uri = Uri.parse(uriStr)
            val openURL = Intent(Intent.ACTION_VIEW, uri)
            startActivity(openURL)
        }

        binding.btnLoginWithWeChat.layoutBtn.setOnClickListener {
            loginUsingWeChat()
        }

        binding.tvForgotPassword.setOnClickListener {
            Intent(activity, ForgotPasswordActivity::class.java).also { startActivity(it) }
        }

        binding.etPassword.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                signInUser()
            }
            false
        }
    }

    private fun setLinks() {
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

    private fun setAppVersion() {
        // Set app version
        var version = ""
        try {
            val pInfo =
                requireActivity().packageManager.getPackageInfo(requireActivity().packageName, 0)
            version = pInfo.versionName.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val appVersion = getString(R.string.app_version) + " - v" + version
        binding.tvAppVersion.text = appVersion
    }

    private fun signInUser() {
        email = binding.etEmail.text.toString()
        password = binding.etPassword.text.toString()
        binding.etEmail.error = null
        binding.layoutPassword.error = null

        if (TextUtils.isEmpty(email)) {
            binding.etEmail.error = getString(R.string.error_username_empty)
            return
        } else if (!Utils.isValidEmail(email)) {
            binding.etEmail.error = getString(R.string.error_invalid_email)
            return
        }
        if (TextUtils.isEmpty(password)) {
            binding.layoutPassword.error = getString(R.string.error_password_empty)
            return
        }
        (activity as MainActivity?)!!.signInUser(email, password)
    }

    fun loginUsingWeChat() {
        Log.d(TAG, "WeChat Login function called")
        if (!api.isWXAppInstalled()) {
            Log.e(TAG, "WeChat is not installed.")
            Toast.makeText(
                activity,
                getString(R.string.error_wechat_app_not_installed),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val req = SendAuth.Req()
        req.scope = "snsapi_userinfo"
        req.state = "wechat_sdk_demo"
        val success = api.sendReq(req)
        Log.d(TAG, "WeChat login success : $success")
    }

    fun showGitHubLoginLoading() {
        binding.btnLoginWithGithub.layoutBtnGithub.isEnabled = false
        binding.btnLoginWithGithub.layoutBtnGithub.alpha = 0.5f
        binding.btnLoginWithGithub.progressIndicator.visibility = View.VISIBLE
    }

    fun hideGitHubLoginLoading() {
        binding.btnLoginWithGithub.layoutBtnGithub.isEnabled = true
        binding.btnLoginWithGithub.layoutBtnGithub.alpha = 1f
        binding.btnLoginWithGithub.progressIndicator.visibility = View.GONE
    }

    fun showGoogleLoginLoading() {
        binding.btnLoginWithGoogle.layoutBtn.isEnabled = false
        binding.btnLoginWithGoogle.layoutBtn.alpha = 0.5f
        binding.btnLoginWithGoogle.progressIndicator.visibility = View.VISIBLE
    }

    fun hideGoogleLoginLoading() {
        binding.btnLoginWithGoogle.layoutBtn.isEnabled = true
        binding.btnLoginWithGoogle.layoutBtn.alpha = 1f
        binding.btnLoginWithGoogle.progressIndicator.visibility = View.GONE
    }

    fun showLoading() {
        binding.btnLogin.layoutBtn.isEnabled = false
        binding.btnLogin.layoutBtn.alpha = 0.5f
        binding.btnLogin.textBtn.text = getString(R.string.btn_signing_in)
        binding.btnLogin.progressIndicator.visibility = View.VISIBLE
        binding.btnLogin.ivArrow.visibility = View.GONE
    }

    fun hideLoading() {
        binding.btnLogin.layoutBtn.isEnabled = true
        binding.btnLogin.layoutBtn.alpha = 1f
        binding.btnLogin.textBtn.text = getString(R.string.btn_login)
        binding.btnLogin.progressIndicator.visibility = View.GONE
        binding.btnLogin.ivArrow.visibility = View.VISIBLE
    }
}