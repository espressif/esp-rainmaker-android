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
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

import com.espressif.AppConstants
import com.espressif.cloudapi.ApiManager
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.cloudapi.CloudException
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.FragmentSignupBinding
import com.espressif.ui.Utils
import com.espressif.ui.activities.MainActivity
import com.espressif.ui.user_module.SignUpConfirmActivity

class SignUpFragment : Fragment(R.layout.fragment_signup) {

    private var _binding: FragmentSignupBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private var email: String? = null
    private var password: String? = null
    private var confirmPassword: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initViews() {

        enableRegisterButton()
        setupLinks()

        binding.etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                email = s.toString()
                enableRegisterButton()
            }
        })

        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                password = s.toString()
                enableRegisterButton()
            }
        })

        binding.etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                confirmPassword = s.toString()
                enableRegisterButton()
            }
        })

        binding.etConfirmPassword.setOnEditorActionListener(TextView.OnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                if (binding.checkboxTermsCondition.isChecked) {
                    doSignUp()
                } else {
                    binding.checkboxTermsCondition.isChecked = true
                }
            }
            false
        })
        binding.btnRegister.layoutBtn.setOnClickListener { doSignUp() }
    }

    private fun setupLinks() {

        binding.tvPrivacy.movementMethod = LinkMovementMethod.getInstance()
        val privacyUrl = "<a href='${Utils.getPrivacyUrl()}'>${getString(R.string.privacy_policy)}</a>"
        binding.tvPrivacy.text = Html.fromHtml(privacyUrl)

        binding.tvTermsOfUse.movementMethod = LinkMovementMethod.getInstance()
        val termsUrl = "<a href='${Utils.getTermsOfUseUrl()}'>${getString(R.string.terms_of_use)}</a>"
        binding.tvTermsOfUse.text = Html.fromHtml(termsUrl)
    }

    private fun doSignUp() {

        binding.etEmail.error = null
        binding.layoutPassword.error = null
        binding.layoutConfirmPassword.error = null
        email = binding.etEmail.text.toString()
        if (TextUtils.isEmpty(email)) {
            binding.etEmail.error = getString(R.string.error_username_empty)
            return
        } else if (!Utils.isValidEmail(email)) {
            binding.etEmail.error = getString(R.string.error_invalid_email)
            return
        }
        password = binding.etPassword.getText().toString()
        if (TextUtils.isEmpty(password)) {
            binding.layoutPassword.error = getString(R.string.error_password_empty)
            return
        }
        confirmPassword = binding.etConfirmPassword.getText().toString()
        if (TextUtils.isEmpty(confirmPassword)) {
            binding.layoutConfirmPassword.error = getString(R.string.error_confirm_password_empty)
            return
        }
        if (password != confirmPassword) {
            binding.layoutConfirmPassword.error = getString(R.string.error_password_not_matched)
            return
        }
        if (!binding.checkboxTermsCondition.isChecked) {
            Toast.makeText(activity, R.string.error_user_agreement, Toast.LENGTH_SHORT).show()
            return
        }

        showLoading()
        val apiManager = ApiManager.getInstance(activity)
        apiManager.createUser(email, password, object : ApiResponseListener {
            override fun onSuccess(data: Bundle?) {
                hideLoading()
                confirmSignUp(email!!, password!!)
            }

            override fun onResponseFailure(exception: Exception) {
                hideLoading()
                if (exception is CloudException) {
                    Utils.showAlertDialog(
                        activity,
                        getString(R.string.dialog_title_sign_up_failed),
                        exception.message,
                        false
                    )
                } else {
                    Utils.showAlertDialog(
                        activity,
                        "",
                        getString(R.string.dialog_title_sign_up_failed),
                        false
                    )
                }
            }

            override fun onNetworkFailure(exception: Exception) {
                hideLoading()
                Utils.showAlertDialog(
                    activity,
                    getString(R.string.dialog_title_no_network),
                    getString(R.string.dialog_msg_no_network),
                    false
                )
            }
        })
    }

    private fun confirmSignUp(email: String, password: String) {
        val intent = Intent(activity, SignUpConfirmActivity::class.java).also {
            it.putExtra(AppConstants.KEY_USER_NAME, email)
            it.putExtra(AppConstants.KEY_PASSWORD, password)
        }

        try {
            requireActivity().startActivityForResult(
                intent,
                MainActivity.SIGN_UP_CONFIRM_ACTIVITY_REQUEST
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun enableRegisterButton() {
        if (TextUtils.isEmpty(email)
            || TextUtils.isEmpty(password)
            || TextUtils.isEmpty(
                confirmPassword
            )
        ) {
            binding.btnRegister.layoutBtn.isEnabled = false
            binding.btnRegister.layoutBtn.alpha = 0.5f
        } else {
            binding.btnRegister.layoutBtn.isEnabled = true
            binding.btnRegister.layoutBtn.alpha = 1f
        }
    }

    private fun showLoading() {
        binding.btnRegister.layoutBtn.isEnabled = false
        binding.btnRegister.layoutBtn.alpha = 0.5f
        binding.btnRegister.textBtn.text = getString(R.string.btn_registering)
        binding.btnRegister.progressIndicator.visibility = View.VISIBLE
        binding.btnRegister.ivArrow.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.btnRegister.layoutBtn.isEnabled = true
        binding.btnRegister.layoutBtn.alpha = 1f
        binding.btnRegister.textBtn.text = getString(R.string.btn_register)
        binding.btnRegister.progressIndicator.visibility = View.GONE
        binding.btnRegister.ivArrow.visibility = View.VISIBLE
    }
}