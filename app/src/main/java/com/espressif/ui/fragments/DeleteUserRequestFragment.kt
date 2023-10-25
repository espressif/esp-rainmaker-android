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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

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
        binding.btnDeleteUser.layoutBtnRemove.setOnClickListener { displayWarningDeleteAccount() }
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
