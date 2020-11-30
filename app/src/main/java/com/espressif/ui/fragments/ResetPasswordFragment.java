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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;

import com.espressif.rainmaker.R;
import com.espressif.ui.user_module.ForgotPasswordActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ResetPasswordFragment extends Fragment {

    private TextView tvResetPasswordMsg;
    private TextInputEditText etPassword, etConfirmPassword, etVerificationCode;
    private TextInputLayout layoutPassword, layoutConfirmPassword, layoutVerificationCode;
    private CardView btnSetPassword;
    private TextView txtSetPasswordBtn;
    private ImageView arrowImage;
    private ContentLoadingProgressBar progressBar;

    public ResetPasswordFragment() {
        // Required empty public constructor
    }

    public static ResetPasswordFragment newInstance() {
        return new ResetPasswordFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_reset_password, container, false);

        Bundle extras = getArguments();
        init(rootView, extras);
        return rootView;
    }

    private View.OnClickListener setPasswordBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            getCode();
        }
    };

    private void init(View view, Bundle extras) {

        tvResetPasswordMsg = view.findViewById(R.id.tv_reset_password_msg);
        layoutPassword = view.findViewById(R.id.layout_new_password);
        etPassword = view.findViewById(R.id.et_new_password);
        layoutConfirmPassword = view.findViewById(R.id.layout_confirm_new_password);
        etConfirmPassword = view.findViewById(R.id.et_confirm_new_password);
        layoutVerificationCode = view.findViewById(R.id.layout_verification_code);
        etVerificationCode = view.findViewById(R.id.et_verification_code);
        btnSetPassword = view.findViewById(R.id.btn_set_password);
        txtSetPasswordBtn = view.findViewById(R.id.text_btn);
        arrowImage = view.findViewById(R.id.iv_arrow);
        progressBar = view.findViewById(R.id.progress_indicator);

        if (extras != null) {
            if (extras.containsKey("destination")) {
                String dest = extras.getString("destination");
                String delMed = extras.getString("deliveryMed");
                String textToDisplay = "To set a new password we sent a verification code to " + dest + " via " + delMed;
                tvResetPasswordMsg.setText(textToDisplay);
            }
        }

        txtSetPasswordBtn.setText(R.string.btn_set_password);
        btnSetPassword.setOnClickListener(setPasswordBtnClickListener);
    }

    private void getCode() {

        String newPassword = etPassword.getText().toString();
        if (TextUtils.isEmpty(newPassword)) {

            layoutPassword.setError(getString(R.string.error_password_empty));
            return;
        }

        String newConfirmPassword = etConfirmPassword.getText().toString();
        if (TextUtils.isEmpty(newConfirmPassword)) {

            layoutConfirmPassword.setError(getString(R.string.error_confirm_password_empty));
            return;
        }

        if (!newPassword.equals(newConfirmPassword)) {

            layoutConfirmPassword.setError(getString(R.string.error_password_not_matched));
            return;
        }

        String verCode = etVerificationCode.getText().toString();
        if (TextUtils.isEmpty(verCode)) {

            layoutVerificationCode.setError(getString(R.string.error_verification_code_empty));
            return;
        }

        if (!TextUtils.isEmpty(newPassword) && !TextUtils.isEmpty(verCode)) {
            showLoading();
            ((ForgotPasswordActivity) getActivity()).resetPassword(newPassword, verCode);
        }
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
