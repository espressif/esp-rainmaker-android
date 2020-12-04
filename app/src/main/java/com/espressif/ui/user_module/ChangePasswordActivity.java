// Copyright 2020 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.ui.user_module;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ContentLoadingProgressBar;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.GenericHandler;
import com.espressif.rainmaker.R;
import com.espressif.ui.activities.AddDeviceActivity;
import com.espressif.ui.theme_manager.WindowThemeManager;
import com.google.android.material.textfield.TextInputLayout;

public class ChangePasswordActivity extends AppCompatActivity {

    private TextView tvTitle, tvBack, tvCancel;
    private EditText etOldPassword, etNewPassword, etConfirmNewPassword;
    private TextInputLayout layoutOldPassword, layoutNewPassword, layoutConfirmPassword;
    private CardView btnSetPassword;
    private TextView txtSetPasswordBtn;
    private ImageView arrowImage;
    private ContentLoadingProgressBar progressBar;
    private AlertDialog userDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowThemeManager WindowTheme = new WindowThemeManager(this, false);
        WindowTheme.applyWindowTheme(getWindow());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        init();
    }

    private void init() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvCancel = findViewById(R.id.btn_cancel);

        tvTitle.setText(R.string.title_activity_change_password);
        tvBack.setVisibility(View.VISIBLE);
        tvCancel.setVisibility(View.VISIBLE);

        tvBack.setOnClickListener(backButtonClickListener);
        tvCancel.setOnClickListener(cancelButtonClickListener);

        etOldPassword = findViewById(R.id.et_old_password);
        etNewPassword = findViewById(R.id.et_new_password);
        etConfirmNewPassword = findViewById(R.id.et_confirm_new_password);
        layoutOldPassword = findViewById(R.id.layout_old_password);
        layoutNewPassword = findViewById(R.id.layout_new_password);
        layoutConfirmPassword = findViewById(R.id.layout_confirm_new_password);
        btnSetPassword = findViewById(R.id.btn_set_password);
        txtSetPasswordBtn = findViewById(R.id.text_btn);
        arrowImage = findViewById(R.id.iv_arrow);
        progressBar = findViewById(R.id.progress_indicator);

        txtSetPasswordBtn.setText(R.string.btn_set_password);
        btnSetPassword.setOnClickListener(setPasswordBtnClickListener);

        etConfirmNewPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

                if (actionId == EditorInfo.IME_ACTION_GO) {
                    changePassword();
                }
                return false;
            }
        });
    }

    private View.OnClickListener backButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            finish();
        }
    };

    private View.OnClickListener cancelButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            finish();
        }
    };

    private View.OnClickListener setPasswordBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            changePassword();
        }
    };

    private void changePassword() {

        layoutOldPassword.setError(null);
        layoutNewPassword.setError(null);
        layoutConfirmPassword.setError(null);

        String oldPassword = etOldPassword.getText().toString();
        if (TextUtils.isEmpty(oldPassword)) {

            layoutOldPassword.setError(getString(R.string.error_password_empty));
            return;
        }

        String newPassword = etNewPassword.getText().toString();
        if (TextUtils.isEmpty(newPassword)) {

            layoutNewPassword.setError(getString(R.string.error_password_empty));
            return;
        }

        String confirmNewPassword = etConfirmNewPassword.getText().toString();
        if (TextUtils.isEmpty(confirmNewPassword)) {

            layoutConfirmPassword.setError(getString(R.string.error_confirm_password_empty));
            return;
        }

        if (!newPassword.equals(confirmNewPassword)) {

            layoutConfirmPassword.setError(getString(R.string.error_password_not_matched));
            return;
        }

        showLoading();
        AppHelper.getPool().getUser(AppHelper.getCurrUser()).changePasswordInBackground(oldPassword, newPassword, callback);
    }

    GenericHandler callback = new GenericHandler() {

        @Override
        public void onSuccess() {

            hideLoading();
            showDialogMessage(getString(R.string.success), getString(R.string.password_change_success), true);
            clearInput();
        }

        @Override
        public void onFailure(Exception exception) {

            exception.printStackTrace();
            hideLoading();
            showDialogMessage(getString(R.string.password_change_failure), AppHelper.formatException(exception), false);
        }
    };

    private void clearInput() {

        etOldPassword.setText("");
        etNewPassword.setText("");
        etConfirmNewPassword.setText("");
    }

    private void showDialogMessage(String title, String body, final boolean exitActivity) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogTheme);
        builder.setTitle(title).setMessage(body).setNeutralButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                try {
                    userDialog.dismiss();
                    if (exitActivity) {
                        onBackPressed();
                    }
                } catch (Exception e) {
                    onBackPressed();
                }
            }
        });
        userDialog = builder.create();
        userDialog.show();
    }

    private void showLoading() {

        btnSetPassword.setEnabled(false);
        btnSetPassword.setAlpha(0.5f);
        txtSetPasswordBtn.setText(R.string.btn_setting_password);
        progressBar.setVisibility(View.VISIBLE);
        arrowImage.setVisibility(View.GONE);
    }

    public void hideLoading() {

        btnSetPassword.setEnabled(true);
        btnSetPassword.setAlpha(1f);
        txtSetPasswordBtn.setText(R.string.btn_set_password);
        progressBar.setVisibility(View.GONE);
        arrowImage.setVisibility(View.VISIBLE);
    }
}
