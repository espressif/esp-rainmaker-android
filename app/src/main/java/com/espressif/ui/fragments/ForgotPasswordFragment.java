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

package com.espressif.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;

import com.espressif.rainmaker.R;
import com.espressif.ui.Utils;
import com.espressif.ui.user_module.ForgotPasswordActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ForgotPasswordFragment extends Fragment {

    private TextInputEditText etEmail;
    private TextInputLayout layoutEmail;
    private MaterialButton btnResetPassword;
    private ContentLoadingProgressBar progressBar;
    private String email;

    public ForgotPasswordFragment() {
        // Required empty public constructor
    }

    public static ForgotPasswordFragment newInstance() {
        return new ForgotPasswordFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_forgot_password, container, false);
        Bundle extras = getArguments();
        init(rootView, extras);
        return rootView;
    }

    private View.OnClickListener resetPasswordBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            resetPassword();
        }
    };

    private void init(View view, Bundle extras) {

        etEmail = view.findViewById(R.id.et_email);
        layoutEmail = view.findViewById(R.id.layout_email);
        btnResetPassword = view.findViewById(R.id.btn_material);
        progressBar = view.findViewById(R.id.progress_indicator);

        if (extras != null) {
            if (extras.containsKey("email")) {
                this.email = extras.getString("email");
                etEmail.setText(email);
            }
        }

        btnResetPassword.setText(R.string.btn_reset_password);
        btnResetPassword.setOnClickListener(resetPasswordBtnClickListener);
    }

    private void resetPassword() {

        email = etEmail.getText().toString();
        if (TextUtils.isEmpty(email)) {

            layoutEmail.setError(getString(R.string.error_email_empty));
            return;

        } else if (!Utils.isValidEmail(email)) {

            layoutEmail.setError(getString(R.string.error_invalid_email));
            return;
        }

        showLoading();
        ((ForgotPasswordActivity) getActivity()).forgotPassword(email);
    }

    private void showLoading() {

        btnResetPassword.setEnabled(false);
        btnResetPassword.setText(R.string.btn_resetting_password);
        btnResetPassword.setIcon(null);
        progressBar.setVisibility(View.VISIBLE);
    }

    public void hideLoading() {

        btnResetPassword.setEnabled(true);
        btnResetPassword.setText(R.string.btn_reset_password);
        btnResetPassword.setIconResource(R.drawable.ic_fluent_arrow_right_filled);
        progressBar.setVisibility(View.GONE);
    }
}
