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

import android.content.Context
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

import com.espressif.AppConstants
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.FragmentDeleteUserConfirmBinding
import com.espressif.ui.user_module.DeleteUserActivity

class DeleteUserConfirmFragment : Fragment(R.layout.fragment_delete_user_confirm) {

    private var _binding: FragmentDeleteUserConfirmBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDeleteUserConfirmBinding.inflate(inflater, container, false)
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

    fun initViews() {
        val sharedPreferences =
            requireActivity().getSharedPreferences(
                AppConstants.ESP_PREFERENCES,
                Context.MODE_PRIVATE
            )
        val userName = sharedPreferences.getString(AppConstants.KEY_EMAIL, "")

        val confMsg =
            getString(R.string.verification_code_sent_instruction) + "<b>" + userName + "</b> "
        binding.tvVerificationCodeMsg.text = Html.fromHtml(confMsg)

        binding.btnDeleteAccountConfirm.textBtn.text = getString(R.string.btn_confirm)
        binding.btnDeleteAccountConfirm.layoutBtnRemove.setOnClickListener(View.OnClickListener {
            if (!isCodeEmpty()) {
                confirmDeleteAccount()
            }
        })
    }

    private fun isCodeEmpty(): Boolean {
        val verCode: String = binding.etVerificationCode.getText().toString()
        if (TextUtils.isEmpty(verCode)) {
            binding.etVerificationCode.error = getString(R.string.error_verification_code_empty)
            return true
        }
        return false
    }

    private fun confirmDeleteAccount() {
        val builder = AlertDialog.Builder(requireActivity())
        builder.setTitle(R.string.dialog_msg_confirmation)

        // Set up the buttons
        builder.setPositiveButton(
            R.string.btn_yes
        ) { dialog, which ->
            dialog.dismiss()
            deleteAccount()
        }
        builder.setNegativeButton(
            R.string.btn_no
        ) { dialog, which -> dialog.dismiss() }
        val userDialog = builder.create()
        userDialog.show()
    }

    private fun deleteAccount() {
        val verCode: String = binding.etVerificationCode.text.toString()
        if (!TextUtils.isEmpty(verCode)) {
            showLoading()
            (activity as DeleteUserActivity?)!!.confirmDeleteAccount(verCode)
        }
    }

    private fun showLoading() {
        binding.btnDeleteAccountConfirm.layoutBtnRemove.isEnabled = false
        binding.btnDeleteAccountConfirm.layoutBtnRemove.alpha = 0.5f
        binding.btnDeleteAccountConfirm.textBtn.text = getString(R.string.btn_deleting_user)
        binding.btnDeleteAccountConfirm.progressIndicator.visibility = View.VISIBLE
    }

    fun hideLoading() {
        binding.btnDeleteAccountConfirm.layoutBtnRemove.isEnabled = true
        binding.btnDeleteAccountConfirm.layoutBtnRemove.alpha = 1f
        binding.btnDeleteAccountConfirm.textBtn.text = getString(R.string.btn_delete_user_confirm)
        binding.btnDeleteAccountConfirm.progressIndicator.visibility = View.GONE
    }
}