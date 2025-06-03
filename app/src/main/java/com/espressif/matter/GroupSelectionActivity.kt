// Copyright 2024 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.matter

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager

import com.espressif.AppConstants
import com.espressif.EspApplication
import com.espressif.cloudapi.ApiResponseListener
import com.espressif.cloudapi.CloudException
import com.espressif.rainmaker.R
import com.espressif.rainmaker.databinding.ActivityGroupsSelectionBinding
import com.espressif.ui.models.Group
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningRequest

class GroupSelectionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GroupSelectionActivity"
    }

    private lateinit var binding: ActivityGroupsSelectionBinding

    private lateinit var groupAdapter: GroupSelectionAdapter
    private val groups: ArrayList<Group> = ArrayList<Group>()

    // CODELAB: commissionDeviceLauncher declaration
    // The ActivityResultLauncher that launches the "commissionDevice" activity in Google Play
    // Services.
    private lateinit var commissionDeviceLauncher: ActivityResultLauncher<IntentSenderRequest>
    // CODELAB SECTION END

    private var isCtrlService = false
    private lateinit var espApp: EspApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupsSelectionBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setToolbar()
        espApp = applicationContext as EspApplication
        init()
    }

    private fun setToolbar() {
        setSupportActionBar(binding.toolbarGroups.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        binding.toolbarGroups.toolbar.title = getString(R.string.title_activity_manage_groups)
        binding.toolbarGroups.toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left)
        binding.toolbarGroups.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun init() {
        binding.tvNoGroup.visibility = View.GONE
        binding.rlNoGroup.visibility = View.GONE
        binding.rvGroupList.visibility = View.GONE
        binding.layoutProgress.visibility = View.GONE
        isCtrlService = intent.getBooleanExtra(AppConstants.KEY_IS_CTRL_SERVICE, false)

        if (!isCtrlService) {
            setup()
        }

        groupAdapter = GroupSelectionAdapter(this, groups)
        binding.rvGroupList.layoutManager = LinearLayoutManager(this)
        binding.rvGroupList.adapter = groupAdapter
        binding.swipeContainer.isEnabled = false

        var espApp: EspApplication = applicationContext as EspApplication
        if (espApp.groupMap.size > 0) {
            updateUi()
        } else {
            createFabric()
        }
    }

    private fun createFabric() {
        showLoading()

        espApp.createHomeFabric(object : ApiResponseListener {
            override fun onSuccess(data: Bundle?) {
                runOnUiThread {
                    hideLoading()
                    updateUi()
                }
            }

            override fun onResponseFailure(exception: Exception) {
                runOnUiThread {
                    hideLoading()
                    updateUi()
                }
            }

            override fun onNetworkFailure(exception: Exception) {
                runOnUiThread {
                    hideLoading()
                    if (exception is CloudException) {
                        Toast.makeText(
                            this@GroupSelectionActivity,
                            exception.message,
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    } else {
                        Toast.makeText(
                            this@GroupSelectionActivity,
                            R.string.error_fabric_create,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    updateUi()
                }
            }
        })
    }

    private fun updateUi() {

        groups.clear()
        var espApp: EspApplication = applicationContext as EspApplication

        for ((key, group) in espApp.groupMap) {
            if (group != null) {
                groups.add(group)
            }
        }

        Log.d(TAG, "Number of Groups : " + groups.size)

        // Sort groups list to display alphabetically.
        groups.sortWith(Comparator { g1, g2 ->
            g1.groupName.compareTo(
                g2.groupName,
                ignoreCase = true
            )
        })

        if (groups.size > 0) {
            binding.rlNoGroup.visibility = View.GONE
            binding.rvGroupList.visibility = View.VISIBLE
        } else {
            binding.tvNoGroup.setText(R.string.no_groups)
            binding.rlNoGroup.visibility = View.VISIBLE
            binding.tvNoGroup.visibility = View.VISIBLE
            binding.ivNoGroup.visibility = View.VISIBLE
            binding.rvGroupList.visibility = View.GONE
        }
        groupAdapter.notifyDataSetChanged()
    }

    private fun showLoading() {
        binding.layoutProgress.visibility = View.VISIBLE
        binding.rvGroupList.visibility = View.GONE
        binding.tvLoading.text = getString(R.string.progress_create_group)
    }

    private fun hideLoading() {
        binding.layoutProgress.visibility = View.GONE
        binding.rvGroupList.visibility = View.VISIBLE
    }

    private fun setup() {
        commissionDeviceLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                // Commission Device Step 5.
                // The Commission Device activity in GPS (step 4) has completed.
                val resultCode = result.resultCode
                if (resultCode == Activity.RESULT_OK) {
                    Log.e(TAG, "CommissionDevice: Success")
                    // We now need to capture the device information for the app's fabric.
                    // Once this completes, a call is made to the viewModel to persist the information
                    // about that device in the app.
                    // TODO      showNewDeviceAlertDialog(result)
                    finish()
                } else {
                    Log.e(TAG, "Error !")
                    Log.e(TAG, "Result code : $resultCode")
//                        viewModel.commissionDeviceFailed(resultCode)
                }
            }
    }

    public fun commissionDevice() {

        if (isCtrlService) {
            var intent = Intent(this, ControllerLoginActivity::class.java)
            intent.putExtras(getIntent())
            intent.putExtra(AppConstants.KEY_GROUP_ID, espApp.mGroupId)
            startActivity(intent)
            finish()
            return
        }

        var payload: String? = intent.getStringExtra(AppConstants.KEY_ON_BOARD_PAYLOAD)
        Log.d(TAG, "OnboardPayload : $payload")
        var commissionDeviceRequest: CommissioningRequest

        if (TextUtils.isEmpty(payload)) {

            commissionDeviceRequest =
                CommissioningRequest.builder()
                    .setCommissioningService(
                        ComponentName(
                            this,
                            AppCommissioningService::class.java
                        )
                    )
                    .build()

        } else {
            commissionDeviceRequest =
                CommissioningRequest.builder()
                    .setOnboardingPayload(payload)
                    .setCommissioningService(
                        ComponentName(
                            this,
                            AppCommissioningService::class.java
                        )
                    )
                    .build()
        }

        // The call to commissionDevice() creates the IntentSender that will eventually be launched
        // in the fragment to trigger the commissioning activity in GPS.
        Matter.getCommissioningClient(this)
            .commissionDevice(commissionDeviceRequest)
            .addOnSuccessListener { result ->
                Log.d(TAG, "CommissionDevice: Success getting the IntentSender: result [${result}]")
                commissionDeviceLauncher.launch(IntentSenderRequest.Builder(result).build())
                // Communication with fragment is via livedata
//                        _commissionDeviceIntentSender.postValue(result)
            }
            .addOnFailureListener { error ->
//                        Log.e(TAG, error
                error.printStackTrace()
//                        _commissionDeviceStatus.postValue(
//                            TaskStatus.Failed("Setting up the IntentSender failed", error))
            }
    }
}