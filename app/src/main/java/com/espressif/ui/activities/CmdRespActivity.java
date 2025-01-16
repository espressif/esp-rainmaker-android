package com.espressif.ui.activities;

import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.rainmaker.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.json.JSONObject;
import org.json.JSONArray;

public class CmdRespActivity extends AppCompatActivity {

    private static final String TAG = CmdRespActivity.class.getSimpleName();
    private static final int STATUS_POLLING_INTERVAL = 2000; // 2 seconds

    private String nodeId;
    private TextInputEditText etId, etPayload, etTimeout;
    private TextView tvResponse;
    private Button btnSend;
    private ApiManager apiManager;
    private Handler handler;
    private String requestId;
    private boolean isPolling = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cmd_resp);

        nodeId = getIntent().getStringExtra(AppConstants.KEY_NODE_ID);
        initViews();
        apiManager = ApiManager.getInstance(this);
        handler = new Handler();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(R.string.title_activity_cmd_resp);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        toolbar.setNavigationOnClickListener(v -> finish());

        etId = findViewById(R.id.et_cmd_id);
        etPayload = findViewById(R.id.et_cmd_payload);
        etTimeout = findViewById(R.id.et_cmd_timeout);
        tvResponse = findViewById(R.id.tv_cmd_response);
        btnSend = findViewById(R.id.btn_send_cmd);

        btnSend.setOnClickListener(v -> sendCommand());

        // Add text change listeners to re-enable button
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isPolling) {
                    btnSend.setEnabled(true);
                    btnSend.setOnClickListener(v -> {
                        stopPolling();
                        sendCommand();
                    });
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        etId.addTextChangedListener(textWatcher);
        etPayload.addTextChangedListener(textWatcher);
        etTimeout.addTextChangedListener(textWatcher);
    }

    private void sendCommand() {
        String idStr = etId.getText().toString();
        String payload = etPayload.getText().toString();
        String timeoutStr = etTimeout.getText().toString();

        if (TextUtils.isEmpty(idStr)) {
            etId.setError(getString(R.string.error_cmd_id_empty));
            return;
        }

        try {
            int commandId = Integer.parseInt(idStr);
            if (commandId <= 0) {
                etId.setError(getString(R.string.error_cmd_id_positive));
                return;
            }
        } catch (NumberFormatException e) {
            etId.setError(getString(R.string.error_cmd_id_invalid));
            return;
        }

        if (TextUtils.isEmpty(payload)) {
            etPayload.setError(getString(R.string.error_empty_payload));
            return;
        }

        if (TextUtils.isEmpty(timeoutStr)) {
            etTimeout.setError(getString(R.string.error_cmd_timeout_empty));
            return;
        }

        int timeout;
        try {
            timeout = Integer.parseInt(timeoutStr);
            if (timeout <= 0) {
                etTimeout.setError(getString(R.string.error_cmd_timeout_positive));
                return;
            }
        } catch (NumberFormatException e) {
            etTimeout.setError(getString(R.string.error_cmd_timeout_invalid));
            return;
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty(AppConstants.KEY_CMD, Integer.parseInt(idStr));
        requestBody.addProperty(AppConstants.KEY_IS_BASE64, false);
        requestBody.addProperty(AppConstants.KEY_TIMEOUT, timeout);

        JsonArray nodeIds = new JsonArray();
        nodeIds.add(nodeId);
        requestBody.add(AppConstants.KEY_NODE_IDS, nodeIds);

        // Try parsing as JSON first
        try {
            JsonParser parser = new JsonParser();
            JsonElement jsonElement;
            try {
                jsonElement = parser.parse(payload);
            } catch (JsonSyntaxException e) {
                // Invalid JSON syntax
                etPayload.setError(getString(R.string.error_invalid_payload));
                tvResponse.setText(getString(R.string.error_invalid_payload));
                return;
            }
            
            // Valid JSON syntax, but check if it's an object
            if (!jsonElement.isJsonObject()) {
                etPayload.setError(getString(R.string.error_invalid_json));
                tvResponse.setText(getString(R.string.error_invalid_json));
                return;
            }
            requestBody.add(AppConstants.KEY_DATA, jsonElement);
        } catch (Exception e) {
            // Check if it's base64 encoded
            if (isBase64(payload)) {
                requestBody.addProperty(AppConstants.KEY_DATA, payload);
                requestBody.addProperty(AppConstants.KEY_IS_BASE64, true);
            } else {
                // Neither valid JSON nor base64
                etPayload.setError(getString(R.string.error_invalid_payload));
                tvResponse.setText(getString(R.string.error_invalid_payload));
                return;
            }
        }

        Log.d(TAG, "Command request body: " + requestBody.toString());
        tvResponse.setText("");
        btnSend.setEnabled(false);

        apiManager.sendCommandResponse(requestBody, new ApiResponseListener() {
            @Override
            public void onSuccess(Bundle data) {
                String reqId = data.getString(AppConstants.KEY_REQUEST_ID);
                if (!TextUtils.isEmpty(reqId)) {
                    requestId = reqId;
                    String msg = getString(R.string.request_id_received, requestId);
                    tvResponse.setText(msg);
                    startPolling();
                }
            }

            @Override
            public void onResponseFailure(Exception exception) {
                stopPolling();
                String error = exception.getMessage();
                if (!TextUtils.isEmpty(error)) {
                    tvResponse.setText(error);
                }
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                stopPolling();
                tvResponse.setText(R.string.error_network);
            }
        });
    }

    private boolean isBase64(String str) {
        if (TextUtils.isEmpty(str)) {
            return false;
        }
        try {
            android.util.Base64.decode(str, android.util.Base64.DEFAULT);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void startPolling() {
        if (!isPolling) {
            isPolling = true;
            btnSend.setEnabled(false);
            btnSend.setOnClickListener(v -> stopPolling());
            pollStatus();
        }
    }

    private void stopPolling() {
        isPolling = false;
        handler.removeCallbacksAndMessages(null);
        btnSend.setEnabled(true);
        btnSend.setOnClickListener(v -> sendCommand());
    }

    private void pollStatus() {
        if (!isPolling) {
            return;
        }

        Log.d(TAG, "Polling status for request: " + requestId);

        apiManager.getCommandResponseStatus(requestId, new ApiResponseListener() {
            @Override
            public void onSuccess(Bundle data) {
                Log.d(TAG, "Poll status success: " + data.toString());
                runOnUiThread(() -> {
                    try {
                        String status = data.getString(AppConstants.KEY_STATUS);
                        
                        // Display latest status
                        tvResponse.setText("Status: " + status);
                        
                        // Enable button if status is not "requested" or "in_progress"
                        if (!status.equals("requested") && !status.equals("in_progress")) {
                            btnSend.setEnabled(true);
                        }
                        
                        if ("success".equals(status)) {
                            String responseData = data.getString(AppConstants.KEY_RESPONSE_DATA);
                            if (!TextUtils.isEmpty(responseData)) {
                                tvResponse.append("\nResponse: " + responseData);
                            }
                            stopPolling();
                        } else if ("timed_out".equals(status)) {
                            tvResponse.append("\nReason: Timed Out");
                            stopPolling();
                        } else if ("failure".equals(status)) {
                            String statusDesc = data.getString(AppConstants.KEY_STATUS_DESCRIPTION);
                            if (!TextUtils.isEmpty(statusDesc)) {
                                tvResponse.append("\nReason: " + statusDesc);
                            }
                            stopPolling();
                        } else {
                            handler.postDelayed(() -> pollStatus(), STATUS_POLLING_INTERVAL);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing response", e);
                        tvResponse.setText("Error parsing response");
                        stopPolling();
                    }
                });
            }

            @Override
            public void onResponseFailure(Exception exception) {
                Log.e(TAG, "Poll status response failure", exception);
                runOnUiThread(() -> {
                    tvResponse.setText(R.string.error_network);
                    stopPolling();
                });
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                Log.e(TAG, "Poll status network failure", exception);
                runOnUiThread(() -> {
                    tvResponse.setText(R.string.error_network);
                    stopPolling();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        super.onDestroy();
    }
} 