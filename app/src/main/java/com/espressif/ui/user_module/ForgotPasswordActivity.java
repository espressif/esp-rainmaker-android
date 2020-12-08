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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ForgotPasswordContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.ForgotPasswordHandler;
import com.espressif.rainmaker.R;
import com.espressif.ui.fragments.ForgotPasswordFragment;
import com.espressif.ui.fragments.ResetPasswordFragment;
import com.espressif.ui.theme_manager.WindowThemeManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ForgotPasswordActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private ForgotPasswordContinuation forgotPasswordContinuation;

    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        WindowThemeManager WindowTheme = new WindowThemeManager(this, false);
        WindowTheme.applyWindowTheme(getWindow());

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        init();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_toolbar, menu);
        MenuItem tvCancel = menu.findItem(R.id.action_cancel);
        tvCancel.setVisible(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_cancel:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {

        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

        if (currentFragment instanceof ResetPasswordFragment) {

            Fragment forgotPasswordFragment = new ForgotPasswordFragment();
            Bundle data = new Bundle();
            data.putString("email", email);
            forgotPasswordFragment.setArguments(data);
            loadForgotPasswordFragment(forgotPasswordFragment);

        } else {
            super.onBackPressed();
        }
    }

    private void init() {

        Fragment forgotPasswordFragment = new ForgotPasswordFragment();
        loadForgotPasswordFragment(forgotPasswordFragment);
    }

    private void loadForgotPasswordFragment(Fragment forgotPasswordFragment) {

        toolbar.setTitle(R.string.title_activity_forgot_password);
        toolbar.setNavigationIcon(null);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, forgotPasswordFragment);
        transaction.commit();
    }

    private void loadResetPasswordFragment(Fragment resetPasswordFragment) {

        toolbar.setTitle(R.string.title_activity_reset_password);
        toolbar.setNavigationIcon(R.drawable.ic_fluent_arrow_left);
        toolbar.setNavigationOnClickListener(backButtonClickListener);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, resetPasswordFragment);
        transaction.commit();
    }

    public void forgotPassword(String email) {

        this.email = email;
        AppHelper.getPool().getUser(email).forgotPasswordInBackground(forgotPasswordHandler);
    }

    public void resetPassword(String newPassword, String verificationCode) {

        forgotPasswordContinuation.setPassword(newPassword);
        forgotPasswordContinuation.setVerificationCode(verificationCode);
        forgotPasswordContinuation.continueTask();
    }

    View.OnClickListener backButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

            if (currentFragment instanceof ResetPasswordFragment) {

                onBackPressed();
            }
        }
    };


    // Callbacks
    ForgotPasswordHandler forgotPasswordHandler = new ForgotPasswordHandler() {

        @Override
        public void onSuccess() {

            hideLoading();
            showDialogMessage(getString(R.string.dialog_title_password_changed), "", true);
        }

        @Override
        public void getResetCode(ForgotPasswordContinuation forgotPasswordContinuation) {

            hideLoading();
            getForgotPasswordCode(forgotPasswordContinuation);
        }

        @Override
        public void onFailure(Exception e) {

            hideLoading();
            showDialogMessage(getString(R.string.dialog_title_forgot_password_failed), AppHelper.formatException(e), false);
        }
    };

    private void getForgotPasswordCode(ForgotPasswordContinuation forgotPasswordContinuation) {

        this.forgotPasswordContinuation = forgotPasswordContinuation;

        Fragment resetPasswordFragment = new ResetPasswordFragment();
        Bundle data = new Bundle();
        data.putString("destination", forgotPasswordContinuation.getParameters().getDestination());
        data.putString("deliveryMed", forgotPasswordContinuation.getParameters().getDeliveryMedium());
        resetPasswordFragment.setArguments(data);
        loadResetPasswordFragment(resetPasswordFragment);
    }

    private void showDialogMessage(String title, String body, final boolean shouldExit) {

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_MaterialAlertDialog);
        builder.setTitle(title).setMessage(body).setNeutralButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                try {
                    dialog.dismiss();

                    if (shouldExit) {
                        finish();
                        // TODO Clear back-stack and start new screen.
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        builder.show();
    }

    private void hideLoading() {

        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

        if (currentFragment instanceof ForgotPasswordFragment) {

            ((ForgotPasswordFragment) currentFragment).hideLoading();

        } else if (currentFragment instanceof ResetPasswordFragment) {

            ((ResetPasswordFragment) currentFragment).hideLoading();
        }
    }
}
