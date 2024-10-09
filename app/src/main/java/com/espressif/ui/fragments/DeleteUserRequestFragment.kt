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
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

import com.espressif.AppConstants
import com.espressif.rainmaker.BuildConfig
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.FragmentDeleteUserReqBinding
import com.espressif.ui.user_module.DeleteUserActivity

class DeleteUserRequestFragment : Fragment(R.layout.fragment_delete_user_req) {

    private var _binding: FragmentDeleteUserReqBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDeleteUserReqBinding.inflate(inflater, container, false)
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
        if (!BuildConfig.isChinaRegion) {
            binding.btnDeleteUser.layoutBtnRemove.setOnClickListener { displayWarningDeleteAccount() }
        } else {
            binding.btnDeleteUser.layoutBtnRemove.visibility = View.GONE

            val emailClick: ClickableSpan = object : ClickableSpan() {
                override fun onClick(textView: View) {

                    val sharedPreferences =
                        activity?.getSharedPreferences(
                            AppConstants.ESP_PREFERENCES,
                            Context.MODE_PRIVATE
                        )

                    if (sharedPreferences != null) {
                        val userName = sharedPreferences.getString(AppConstants.KEY_EMAIL, "")
                        val userId = sharedPreferences.getString(AppConstants.KEY_USER_ID, "")

                        val emailIntent = Intent(Intent.ACTION_SENDTO)
                        emailIntent.setData(Uri.parse("mailto:"))
                        emailIntent.putExtra(
                            Intent.EXTRA_EMAIL,
                            arrayOf("esp-rainmaker-admin@espressif.com")
                        )

                        val subject = "ESP RainMaker (China) Account delete request"
                        val text = "User name : $userName \nUser ID : $userId"

                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
                        emailIntent.putExtra(Intent.EXTRA_TEXT, text)
                        startActivity(emailIntent)
                        activity?.finish()

                    } else {
                        Toast.makeText(
                            activity, getString(R.string.error_user_id_not_found), Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = resources.getColor(R.color.colorPrimary)
                    ds.isUnderlineText = true
                }
            }

            val stringForDeleteAccount =
                SpannableString(getString(R.string.delete_user_msg_china_region))
            stringForDeleteAccount.setSpan(emailClick, 48, 81, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            binding.tvDeleteUser.text = stringForDeleteAccount
            binding.tvDeleteUser.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun displayWarningDeleteAccount() {
        val builder = AlertDialog.Builder(requireActivity())
        builder.setTitle(R.string.dialog_title_delete_user)
        builder.setMessage(R.string.dialog_msg_delete_user)

        // Set up the buttons
        builder.setPositiveButton(
            R.string.btn_proceed
        ) { dialog, which ->
            dialog.dismiss()
            deleteUser()
        }
        builder.setNegativeButton(
            R.string.btn_cancel
        ) { dialog, which -> dialog.dismiss() }
        val userDialog = builder.create()
        userDialog.show()
    }

    private fun deleteUser() {
        showLoading()
        (activity as DeleteUserActivity?)!!.sendDeleteUserRequest()
    }

    private fun showLoading() {
        binding.btnDeleteUser.layoutBtnRemove.isEnabled = false
        binding.btnDeleteUser.layoutBtnRemove.alpha = 0.5f
        binding.btnDeleteUser.textBtn.text = getString(R.string.btn_deleting_user)
        binding.btnDeleteUser.progressIndicator.visibility = View.VISIBLE
    }

    fun hideLoading() {
        binding.btnDeleteUser.layoutBtnRemove.isEnabled = true
        binding.btnDeleteUser.layoutBtnRemove.alpha = 1f
        binding.btnDeleteUser.textBtn.text = getString(R.string.btn_delete_user)
        binding.btnDeleteUser.progressIndicator.visibility = View.GONE
    }
}
