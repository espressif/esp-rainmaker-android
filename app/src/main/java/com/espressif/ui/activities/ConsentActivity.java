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

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatCheckBox;

import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.google.android.material.card.MaterialCardView;

public class ConsentActivity extends AppCompatActivity {

    private TextView tvPolicy;
    private MaterialCardView btnProceed;
    private TextView txtProceedBtn;
    private AppCompatCheckBox cbTermsCondition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent);
        initViews();
    }

    private void initViews() {

        tvPolicy = findViewById(R.id.tv_terms_condition);
        cbTermsCondition = findViewById(R.id.cb_terms_condition);
        btnProceed = findViewById(R.id.btn_proceed);
        txtProceedBtn = findViewById(R.id.text_btn);
        txtProceedBtn.setText(R.string.btn_proceed);

        SpannableString stringForPolicy = new SpannableString(getString(R.string.user_agreement));

        ClickableSpan privacyPolicyClick = new ClickableSpan() {

            @Override
            public void onClick(View textView) {
                textView.invalidate();
                Intent openURL = new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_URL));
                startActivity(openURL);
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(getResources().getColor(R.color.colorPrimary));
                ds.setUnderlineText(true);
            }
        };

        ClickableSpan termsOfUseClick = new ClickableSpan() {

            @Override
            public void onClick(View textView) {
                textView.invalidate();
                Intent openURL = new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.TERMS_URL));
                startActivity(openURL);
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(getResources().getColor(R.color.colorPrimary));
                ds.setUnderlineText(true);
            }
        };

        stringForPolicy.setSpan(privacyPolicyClick, 83, 97, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        stringForPolicy.setSpan(termsOfUseClick, 102, 114, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvPolicy.setText(stringForPolicy);
        tvPolicy.setMovementMethod(LinkMovementMethod.getInstance());

        btnProceed.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (cbTermsCondition.isChecked()) {
                    launchLoginScreen();
                } else {
                    displayConsentError();
                }
            }
        });
    }

    private void launchLoginScreen() {
        Intent espMainActivity = new Intent(getApplicationContext(), MainActivity.class);
        espMainActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(espMainActivity);
        finish();
    }

    private void displayConsentError() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_error);
        builder.setMessage(R.string.error_user_agreement);
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });
        AlertDialog userDialog = builder.create();
        userDialog.show();
    }
}
