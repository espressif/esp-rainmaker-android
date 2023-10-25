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
import android.text.Html
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

import com.espressif.AppConstants
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.FragmentResetPasswordBinding
import com.espressif.ui.user_module.ForgotPasswordActivity

class ResetPasswordFragment : Fragment(R.layout.fragment_reset_password) {

    private var _binding: FragmentResetPasswordBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    var userName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val extras: Bundle? = arguments
        if (extras != null) {
            if (extras.containsKey(AppConstants.KEY_USER_NAME)) {
                userName = extras.getString(AppConstants.KEY_USER_NAME)
                val confMsg =
                    getString(R.string.verification_code_sent_instruction) + "<b>" + userName + "</b> "
                binding.tvResetPasswordMsg.text = Html.fromHtml(confMsg)
            }
        }

        binding.btnSetPassword.textBtn.text = getString(R.string.btn_set_password)
        binding.btnSetPassword.layoutBtn.setOnClickListener {
            getCode()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getCode() {
        binding.layoutNewPassword.error = null
        binding.layoutConfirmNewPassword.error = null
        val newPassword: String = binding.etNewPassword.text.toString()
        if (TextUtils.isEmpty(newPassword)) {
            binding.layoutNewPassword.error = getString(R.string.error_password_empty)
            return
        }
        val newConfirmPassword: String = binding.etConfirmNewPassword.getText().toString()
        if (TextUtils.isEmpty(newConfirmPassword)) {
            binding.layoutConfirmNewPassword.error =
                getString(R.string.error_confirm_password_empty)
            return
        }
        if (newPassword != newConfirmPassword) {
            binding.layoutConfirmNewPassword.error = getString(R.string.error_password_not_matched)
            return
        }
        val verCode: String = binding.etVerificationCode.text.toString()
        if (TextUtils.isEmpty(verCode)) {
            binding.etVerificationCode.error = getString(R.string.error_verification_code_empty)
            return
        }
        if (!TextUtils.isEmpty(newPassword) && !TextUtils.isEmpty(verCode)) {
            showLoading()
            (activity as ForgotPasswordActivity?)!!.resetPassword(newPassword, verCode)
        }
    }

    private fun showLoading() {
        binding.btnSetPassword.layoutBtn.isEnabled = false
        binding.btnSetPassword.layoutBtn.alpha = 0.5f
        binding.btnSetPassword.textBtn.text = getString(R.string.btn_setting_password)
        binding.btnSetPassword.progressIndicator.visibility = View.VISIBLE
        binding.btnSetPassword.ivArrow.visibility = View.GONE
    }

    fun hideLoading() {
        binding.btnSetPassword.layoutBtn.isEnabled = true
        binding.btnSetPassword.layoutBtn.alpha = 1f
        binding.btnSetPassword.textBtn.text = getString(R.string.btn_set_password)
        binding.btnSetPassword.progressIndicator.visibility = View.GONE
        binding.btnSetPassword.ivArrow.visibility = View.VISIBLE
    }
}