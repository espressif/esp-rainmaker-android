// Copyright 2021 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.ui.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.CloudException;
import com.espressif.rainmaker.R;
import com.espressif.ui.adapters.GroupDeviceAdapter;
import com.espressif.ui.adapters.GroupNodeAdapter;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Group;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;

public class GroupDetailActivity extends AppCompatActivity {

    private static final int REQ_ADD_NODE_SELECTION = 10;
    private static final int REQ_EDIT_NODE_SELECTION = 11;
    private MaterialToolbar toolbar;
    private TextView tvGroupName;
    private RelativeLayout rlGroupName, rlAddDevice, rlDevices, rlGroupSharedWith;
    private CardView btnNext;
    private TextView txtNextBtn;
    private RecyclerView rvDevices, rvNodes;
    private RelativeLayout layoutProgress;
    private ConstraintLayout layoutGroupDetail;
    private MaterialCardView btnRemoveGroup;
    private TextView txtRemoveGroupBtn;
    private ImageView removeGroupImage, ivRightArrow;
    private ContentLoadingProgressBar progressBar;
    private GroupDeviceAdapter deviceAdapter;
    private GroupNodeAdapter nodeAdapter;
    private EspApplication espApp;
    private ApiManager apiManager;
    private Group group;
    private String groupName;
    private boolean isDeviceAvailable = false;
    private ArrayList<EspNode> nodes = new ArrayList<>();
    private ArrayList<Device> devices = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);

        espApp = (EspApplication) getApplicationContext();
        apiManager = ApiManager.getInstance(getApplicationContext());
        group = getIntent().getParcelableExtra(AppConstants.KEY_GROUP);
        if (espApp.nodeMap.size() > 0) {
            isDeviceAvailable = true;
        }
        initViews();
        updateUI();
    }

    private View.OnClickListener nextBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            if (TextUtils.isEmpty(groupName)) {
                Toast.makeText(GroupDetailActivity.this, R.string.error_group_name_empty, Toast.LENGTH_LONG).show();
                return;
            } else {
                if (isDeviceAvailable) {
                    Intent intent = new Intent(GroupDetailActivity.this, GroupNodeSelectionActivity.class);
                    intent.putExtra(AppConstants.KEY_GROUP_NAME, groupName);
                    startActivityForResult(intent, REQ_ADD_NODE_SELECTION);
                } else {
                    createGroup();
                }
            }
        }
    };

    private View.OnClickListener groupNameClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            askForGroupName();
        }
    };

    private View.OnClickListener addDeviceClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            Intent intent = new Intent(GroupDetailActivity.this, GroupNodeSelectionActivity.class);
            intent.putExtra(AppConstants.KEY_GROUP, group);
            startActivityForResult(intent, REQ_EDIT_NODE_SELECTION);
        }
    };

    private View.OnClickListener removeGroupBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            if (group != null && group.isPrimary()) {
                confirmRemoveGroup();
            } else {
                confirmLeaveGroup();
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQ_ADD_NODE_SELECTION:
                    finish();
                    break;
                case REQ_EDIT_NODE_SELECTION:
                    group = espApp.groupMap.get(group.getGroupId());
                    updateUI();
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void initViews() {

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(R.string.title_activity_create_group);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        layoutGroupDetail = findViewById(R.id.layout_group_detail);
        layoutProgress = findViewById(R.id.layout_progress);
        tvGroupName = findViewById(R.id.tv_group_name);
        rlGroupName = findViewById(R.id.rl_group_name);
        rlAddDevice = findViewById(R.id.rl_add_device);
        rlDevices = findViewById(R.id.rl_devices);
        rlGroupSharedWith = findViewById(R.id.rl_shared_with);
        rvDevices = findViewById(R.id.rv_device_list);
        rvNodes = findViewById(R.id.rv_node_list);

        btnNext = findViewById(R.id.btn_next);
        txtNextBtn = btnNext.findViewById(R.id.text_btn);
        txtNextBtn.setText(R.string.btn_next);
        btnNext.setOnClickListener(nextBtnClickListener);

        btnRemoveGroup = findViewById(R.id.btn_remove);
        txtRemoveGroupBtn = btnRemoveGroup.findViewById(R.id.text_btn);
        removeGroupImage = btnRemoveGroup.findViewById(R.id.iv_remove);
        progressBar = btnRemoveGroup.findViewById(R.id.progress_indicator);
        btnRemoveGroup.setVisibility(View.GONE);

        rlAddDevice.setOnClickListener(addDeviceClickListener);
        rlGroupName.setOnClickListener(groupNameClickListener);
        ivRightArrow = findViewById(R.id.iv_right_arrow);
        btnRemoveGroup.setOnClickListener(removeGroupBtnClickListener);

        if (!isDeviceAvailable) {
            txtNextBtn.setText(R.string.btn_add);
            findViewById(R.id.iv_arrow).setVisibility(View.GONE);
        }

        ((SimpleItemAnimator) rvDevices.getItemAnimator()).setSupportsChangeAnimations(false);
        deviceAdapter = new GroupDeviceAdapter(this, group, devices, false, true);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 2);
        rvDevices.setLayoutManager(gridLayoutManager);
        rvDevices.setAdapter(deviceAdapter);

        ((SimpleItemAnimator) rvNodes.getItemAnimator()).setSupportsChangeAnimations(false);
        nodeAdapter = new GroupNodeAdapter(this, group, nodes, false);
        rvNodes.setLayoutManager(new LinearLayoutManager(this));
        rvNodes.setAdapter(nodeAdapter);
    }

    private void updateUI() {

        if (group == null) {
            getSupportActionBar().setTitle(R.string.title_activity_create_group);
            rlAddDevice.setVisibility(View.GONE);
            rlDevices.setVisibility(View.GONE);
            btnRemoveGroup.setVisibility(View.GONE);
            btnNext.setVisibility(View.VISIBLE);
            setEnableNextBtn(false);
        } else {
            getSupportActionBar().setTitle(group.getGroupName());
            groupName = group.getGroupName();
            tvGroupName.setText(groupName);

            if(!group.isPrimary()){
                rlGroupName.setOnClickListener(null);
                ivRightArrow.setVisibility(View.INVISIBLE);
            }

            if (group.isPrimary()) {
                txtRemoveGroupBtn.setText(R.string.btn_remove_group);
            } else {
                txtRemoveGroupBtn.setText(R.string.btn_leave_group);
            }

            btnRemoveGroup.setVisibility(View.VISIBLE);
            btnNext.setVisibility(View.GONE);

            if (group.isPrimary() && isDeviceAvailable) {
                rlAddDevice.setVisibility(View.VISIBLE);
            } else {
                rlAddDevice.setVisibility(View.GONE);
            }

            devices.clear();
            nodes.clear();
            ArrayList<String> nodeIds = group.getNodeList();

            if (nodeIds != null && nodeIds.size() > 0) {
                for (int i = 0; i < nodeIds.size(); i++) {
                    EspNode node = espApp.nodeMap.get(nodeIds.get(i));
                    if (node != null) {
                        if (node.getDevices().size() == 1) {
                            devices.add(new Device(node.getDevices().get(0)));
                        } else if (node.getDevices().size() > 1) {
                            nodes.add(new EspNode(node));
                        }
                    }
                }
            }

            if (nodes.size() > 0 || devices.size() > 0) {
                rlDevices.setVisibility(View.VISIBLE);
            } else {
                rlDevices.setVisibility(View.GONE);
            }

            deviceAdapter.notifyDataSetChanged();
            nodeAdapter.notifyDataSetChanged();
        }
    }

    private void askForGroupName() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_attribute, null);
        builder.setView(dialogView);
        builder.setTitle(R.string.dialog_title_group_name);
        final EditText etAttribute = dialogView.findViewById(R.id.et_attr_value);
        etAttribute.setInputType(InputType.TYPE_CLASS_TEXT);
        if (!TextUtils.isEmpty(groupName)) {
            etAttribute.setText(groupName);
        }
        etAttribute.requestFocus();

        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                final String value = etAttribute.getText().toString();

                if (!TextUtils.isEmpty(value)) {

                    setEnableNextBtn(true);
                    dialog.dismiss();

                    if (group != null) {

                        showLoading(getString(R.string.progress_update_group));
                        JsonObject body = new JsonObject();
                        body.addProperty(AppConstants.KEY_GROUP_NAME, value);

                        apiManager.updateGroup(group.getGroupId(), body, new ApiResponseListener() {
                            @Override
                            public void onSuccess(Bundle data) {
                                hideLoading();
                                Toast.makeText(GroupDetailActivity.this, R.string.success_group_name_update, Toast.LENGTH_LONG).show();
                                groupName = value;
                                tvGroupName.setText(groupName);
                            }

                            @Override
                            public void onResponseFailure(Exception exception) {
                                hideLoading();
                                if (exception instanceof CloudException) {
                                    Toast.makeText(GroupDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(GroupDetailActivity.this, R.string.error_group_name_update, Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onNetworkFailure(Exception exception) {
                                hideLoading();
                                if (exception instanceof CloudException) {
                                    Toast.makeText(GroupDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(GroupDetailActivity.this, R.string.error_group_name_update, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    } else {
                        groupName = value;
                        tvGroupName.setText(groupName);
                    }
                } else {

                    if (group == null) {
                        groupName = "";
                        tvGroupName.setText(groupName);
                        setEnableNextBtn(false);
                    }
                    Toast.makeText(GroupDetailActivity.this, R.string.error_group_name_empty, Toast.LENGTH_LONG).show();
                }
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        alertDialog.show();
    }

    private void setEnableNextBtn(boolean enable) {

        btnNext.setEnabled(enable);
        if (enable) {
            btnNext.setAlpha(1f);
        } else {
            btnNext.setAlpha(0.5f);
        }
    }

    public void confirmRemoveDevice(final String nodeId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_msg_confirmation);
        builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                removeDevice(nodeId);
            }
        });
        builder.setNegativeButton(R.string.btn_no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    public void removeDevice(final String nodeId) {

        showLoading(getString(R.string.progress_remove_device));
        JsonObject body = new JsonObject();
        JsonArray nodesArray = new JsonArray();
        nodesArray.add(nodeId);
        body.add(AppConstants.KEY_NODES, nodesArray);
        body.addProperty(AppConstants.KEY_OPERATION, AppConstants.KEY_OPERATION_REMOVE);

        apiManager.updateGroup(group.getGroupId(), body, new ApiResponseListener() {
            @Override
            public void onSuccess(Bundle data) {

                group = espApp.groupMap.get(group.getGroupId());
                hideLoading();
                Toast.makeText(GroupDetailActivity.this, R.string.success_group_update, Toast.LENGTH_LONG).show();
                updateUI();
            }

            @Override
            public void onResponseFailure(Exception exception) {
                hideLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(GroupDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupDetailActivity.this, R.string.error_group_device_remove, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                hideLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(GroupDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupDetailActivity.this, R.string.error_group_device_remove, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void removeGroupPrimary() {

        showRemoveGroupLoading();
        ApiManager apiManager = ApiManager.getInstance(getApplicationContext());
        apiManager.removeGroup(group.getGroupId(), new ApiResponseListener() {
            @Override
            public void onSuccess(Bundle data) {
                hideRemoveGroupLoading();
                Toast.makeText(GroupDetailActivity.this, R.string.success_group_remove, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onResponseFailure(Exception exception) {
                exception.printStackTrace();
                hideRemoveGroupLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(GroupDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupDetailActivity.this, R.string.error_group_remove, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                exception.printStackTrace();
                hideRemoveGroupLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(GroupDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupDetailActivity.this, R.string.error_group_remove, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void removeGroupSecondary() {

        showRemoveGroupLoading();
        ApiManager apiManager = ApiManager.getInstance(getApplicationContext());
        SharedPreferences sharedPreferences = this.getApplicationContext().getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        String userEmail = sharedPreferences.getString(AppConstants.KEY_EMAIL, "");

        apiManager.removeGroupSharing(group.getGroupId(), userEmail, new ApiResponseListener() {
            @Override
            public void onSuccess(Bundle data) {
                hideRemoveGroupLoading();
                Toast.makeText(GroupDetailActivity.this, R.string.success_group_remove, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onResponseFailure(Exception exception) {
                exception.printStackTrace();
                hideRemoveGroupLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(GroupDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupDetailActivity.this, R.string.error_group_remove, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                exception.printStackTrace();
                hideRemoveGroupLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(GroupDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupDetailActivity.this, R.string.error_group_remove, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void confirmRemoveGroup() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_remove);
        builder.setMessage(R.string.dialog_msg_confirmation_remove_group);
        // Set up the buttons
        builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
                removeGroupPrimary();
            }
        });

        builder.setNegativeButton(R.string.btn_no, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        AlertDialog userDialog = builder.create();
        userDialog.show();
    }

    private void confirmLeaveGroup() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_leave_group);
        builder.setMessage(R.string.dialog_msg_confirmation_leave_group);
        // Set up the buttons
        builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
                removeGroupSecondary();
            }
        });

        builder.setNegativeButton(R.string.btn_no, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        AlertDialog userDialog = builder.create();
        userDialog.show();
    }

    private void showLoading(String msg) {
        layoutGroupDetail.setAlpha(0.3f);
        layoutProgress.setVisibility(View.VISIBLE);
        TextView progressText = layoutProgress.findViewById(R.id.tv_loading);
        progressText.setText(msg);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private void hideLoading() {
        layoutGroupDetail.setAlpha(1);
        layoutProgress.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private void showRemoveGroupLoading() {

        btnRemoveGroup.setEnabled(false);
        btnRemoveGroup.setAlpha(0.5f);
        txtRemoveGroupBtn.setText(R.string.btn_removing);
        progressBar.setVisibility(View.VISIBLE);
        removeGroupImage.setVisibility(View.GONE);
    }

    public void hideRemoveGroupLoading() {

        btnRemoveGroup.setEnabled(true);
        btnRemoveGroup.setAlpha(1f);
        txtRemoveGroupBtn.setText(R.string.btn_remove);
        progressBar.setVisibility(View.GONE);
        removeGroupImage.setVisibility(View.VISIBLE);
    }

    private void createGroup() {

        showLoading(getString(R.string.progress_create_group));
        JsonObject body = new JsonObject();
        body.addProperty(AppConstants.KEY_GROUP_NAME, groupName);

        apiManager.createGroup(body, new ApiResponseListener() {
            @Override
            public void onSuccess(Bundle data) {
                Toast.makeText(GroupDetailActivity.this, R.string.success_group_create, Toast.LENGTH_LONG).show();
                hideLoading();
                finish();
            }

            @Override
            public void onResponseFailure(Exception exception) {
                hideLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(GroupDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupDetailActivity.this, R.string.error_group_create, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                hideLoading();
                if (exception instanceof CloudException) {
                    Toast.makeText(GroupDetailActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupDetailActivity.this, R.string.error_group_create, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, 1, Menu.NONE, R.string.btn_group_info).setIcon(R.drawable.ic_node_info).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        MenuItem groupInfoItem = menu.findItem(1);
        if (getSupportActionBar() != null && getSupportActionBar().getTitle() != null) {
            if (getSupportActionBar().getTitle().equals(groupName)) {
                groupInfoItem.setVisible(true);
            } else {
                groupInfoItem.setVisible(false);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                goToGroupShareInfoActivity();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void goToGroupShareInfoActivity() {
        Intent intent = new Intent(GroupDetailActivity.this, GroupInfoActivity.class);
        intent.putExtra(AppConstants.KEY_GROUP, group);
        startActivity(intent);
    }
}
