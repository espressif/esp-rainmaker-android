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

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener
import androidx.fragment.app.Fragment

import com.espressif.AppConstants
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.FragmentForgotPasswordBinding
import com.espressif.ui.Utils
import com.espressif.ui.user_module.ForgotPasswordActivity

class ForgotPasswordFragment : Fragment(R.layout.fragment_forgot_password) {

    private var _binding: FragmentForgotPasswordBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    var email: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val extras: Bundle? = arguments
        if (extras != null) {
            if (extras.containsKey(AppConstants.KEY_USER_NAME)) {
                email = extras.getString(AppConstants.KEY_USER_NAME)
                binding.etEmail.setText(email)
            }
        }

        binding.btnResetPassword.textBtn.text = getString(R.string.btn_reset_password)
        binding.btnResetPassword.ivArrow.visibility = View.GONE
        binding.btnResetPassword.layoutBtn.setOnClickListener { resetPassword() }

        binding.etEmail.setOnEditorActionListener(OnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                resetPassword()
            }
            false
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun resetPassword() {
        email = binding.etEmail.text.toString()
        if (TextUtils.isEmpty(email)) {
            binding.etEmail.error = getString(R.string.error_username_empty)
            return
        } else if (!Utils.isValidEmail(email)) {
            binding.etEmail.error = getString(R.string.error_invalid_email)
            return
        }
        showLoading()
        (activity as ForgotPasswordActivity?)!!.forgotPassword(email)
    }

    private fun showLoading() {
        binding.btnResetPassword.layoutBtn.isEnabled = false
        binding.btnResetPassword.layoutBtn.alpha = 0.5f
        binding.btnResetPassword.textBtn.text = getString(R.string.btn_resetting_password)
        binding.btnResetPassword.progressIndicator.visibility = View.VISIBLE
    }

    fun hideLoading() {
        binding.btnResetPassword.layoutBtn.isEnabled = true
        binding.btnResetPassword.layoutBtn.alpha = 1f
        binding.btnResetPassword.textBtn.text = getString(R.string.btn_reset_password)
        binding.btnResetPassword.progressIndicator.visibility = View.GONE
    }
}