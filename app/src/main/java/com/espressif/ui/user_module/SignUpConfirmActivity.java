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
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.ContentLoadingProgressBar;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserCodeDeliveryDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.GenericHandler;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.VerificationHandler;
import com.espressif.rainmaker.R;
import com.espressif.ui.Utils;
import com.espressif.ui.theme_manager.WindowThemeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SignUpConfirmActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private TextView tvConfMsg;
    private TextInputEditText etEmail,  etConfCode;
    private TextInputLayout layoutEmail, layoutConfCode;
    private MaterialButton btnConfirm;
    private ContentLoadingProgressBar progressBar;
    private TextView tvResendCode;

    private String email, password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up_confirm);
        WindowThemeManager WindowTheme = new WindowThemeManager(this, false);
        WindowTheme.applyWindowTheme(getWindow());

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.title_activity_sign_up_confirm);

        init();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_toolbar, menu);
        MenuItem tvCancel = menu.findItem(R.id.action_done);
        tvCancel.setVisible(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_cancel:
                cancelButtonVoid();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {

        setResult(RESULT_CANCELED, getIntent());
        super.onBackPressed();
    }

    private void init() {

        tvConfMsg = findViewById(R.id.tv_sign_up_confirm_msg_1);
        layoutEmail = findViewById(R.id.layout_email);
        etEmail = findViewById(R.id.et_email);
        layoutConfCode = findViewById(R.id.layout_verification_code);
        etConfCode = findViewById(R.id.et_verification_code);
        btnConfirm = findViewById(R.id.btn_material);
        progressBar = findViewById(R.id.progress_indicator);
        tvResendCode = findViewById(R.id.tv_resend_code);

        btnConfirm.setText(R.string.btn_confirm);

        Bundle extras = getIntent().getExtras();

        if (extras != null) {

            if (extras.containsKey("name")) {

                email = extras.getString("name");
                password = extras.getString("password");
                etEmail.setText(email);
                etConfCode.requestFocus();

                if (extras.containsKey("destination")) {

                    String dest = extras.getString("destination");
                    String delMed = extras.getString("deliveryMed");
                    delMed = delMed.toLowerCase();

                    if (dest != null && delMed != null && dest.length() > 0 && delMed.length() > 0) {

                        String confMsg = "A confirmation code was sent to " + dest + " via " + delMed;
                        confMsg = confMsg + ". " + getString(R.string.signup_confirm_msg);
                        tvConfMsg.setText(confMsg);

                    } else {

                        String confMsg = "A confirmation code was sent.";
                        confMsg = confMsg + " " + getString(R.string.signup_confirm_msg);
                        tvConfMsg.setText(confMsg);
                    }
                }
            } else {
                tvConfMsg.setText(R.string.req_confirmation_code);
            }
        }

        btnConfirm.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                sendConfCode();
            }
        });

        tvResendCode.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                reqConfCode();
            }
        });

        etConfCode.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

                if (actionId == EditorInfo.IME_ACTION_GO) {
                    sendConfCode();
                }
                return false;
            }
        });
    }

    private void cancelButtonVoid() {

            setResult(RESULT_CANCELED, getIntent());
            finish();
    };

    private void sendConfCode() {

        email = etEmail.getText().toString();
        String confirmCode = etConfCode.getText().toString();
        layoutEmail.setError(null);
        layoutConfCode.setError(null);

        if (TextUtils.isEmpty(email)) {

            layoutEmail.setError(getString(R.string.error_email_empty));
            return;

        } else if (!Utils.isValidEmail(email)) {

            layoutEmail.setError(getString(R.string.error_invalid_email));
            return;

        } else if (TextUtils.isEmpty(confirmCode)) {

            layoutConfCode.setError(getString(R.string.error_confirmation_code_empty));
            return;
        }

        showLoading();
        AppHelper.getPool().getUser(email).confirmSignUpInBackground(confirmCode, true, confHandler);
    }

    private void reqConfCode() {

        email = etEmail.getText().toString();

        if (TextUtils.isEmpty(email)) {

            layoutEmail.setError(getString(R.string.error_email_empty));
            return;
        }
        AppHelper.getPool().getUser(email).resendConfirmationCodeInBackground(resendConfCodeHandler);
    }

    GenericHandler confHandler = new GenericHandler() {

        @Override
        public void onSuccess() {

            hideLoading();
            showDialogMessage(getString(R.string.success), email + " has been confirmed!", true);
        }

        @Override
        public void onFailure(Exception exception) {

            hideLoading();
            showDialogMessage(getString(R.string.confirmation_failed), AppHelper.formatException(exception), false);
        }
    };

    VerificationHandler resendConfCodeHandler = new VerificationHandler() {

        @Override
        public void onSuccess(CognitoUserCodeDeliveryDetails cognitoUserCodeDeliveryDetails) {

            etConfCode.requestFocus();
            showDialogMessage(getString(R.string.dialog_title_conf_code_sent), "Code sent to " + cognitoUserCodeDeliveryDetails.getDestination() + " via " + cognitoUserCodeDeliveryDetails.getDeliveryMedium() + ".", false);
        }

        @Override
        public void onFailure(Exception exception) {

            showDialogMessage(getString(R.string.dialog_title_conf_code_req_fail), AppHelper.formatException(exception), false);
        }
    };

    private void showDialogMessage(String title, String body, final boolean exitActivity) {

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_MaterialAlertDialog);
        builder.setTitle(title).setMessage(body).setNeutralButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    dialog.dismiss();

                    if (exitActivity) {

                        Intent intent = new Intent();
                        if (email == null)
                            email = "";
                        intent.putExtra("email", email);
                        intent.putExtra("password", password);
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        builder.show();
    }

    private void showLoading() {

        btnConfirm.setEnabled(false);
        btnConfirm.setText(R.string.btn_confirming);
        btnConfirm.setIcon(null);
        progressBar.setVisibility(View.VISIBLE);
    }

    public void hideLoading() {

        btnConfirm.setEnabled(true);
        btnConfirm.setText(R.string.btn_confirm);
        btnConfirm.setIconResource(R.drawable.ic_fluent_arrow_right_filled);
        progressBar.setVisibility(View.GONE);
    }
}
