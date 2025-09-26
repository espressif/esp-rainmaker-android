package com.espressif.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ContentLoadingProgressBar;

import com.amazonaws.services.kinesisvideo.AWSKinesisVideoClient;
import com.amazonaws.services.kinesisvideo.model.ChannelRole;
import com.amazonaws.services.kinesisvideo.model.CreateSignalingChannelRequest;
import com.amazonaws.services.kinesisvideo.model.CreateSignalingChannelResult;
import com.amazonaws.services.kinesisvideo.model.DescribeSignalingChannelRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeSignalingChannelResult;
import com.amazonaws.services.kinesisvideo.model.GetSignalingChannelEndpointRequest;
import com.amazonaws.services.kinesisvideo.model.GetSignalingChannelEndpointResult;
import com.amazonaws.services.kinesisvideo.model.ResourceEndpointListItem;
import com.amazonaws.services.kinesisvideo.model.ResourceNotFoundException;
import com.amazonaws.services.kinesisvideo.model.SingleMasterChannelEndpointConfiguration;
import com.amazonaws.services.kinesisvideosignaling.AWSKinesisVideoSignalingClient;
import com.amazonaws.services.kinesisvideosignaling.model.GetIceServerConfigRequest;
import com.amazonaws.services.kinesisvideosignaling.model.GetIceServerConfigResult;
import com.amazonaws.services.kinesisvideosignaling.model.IceServer;
import com.espressif.EspApplication;
import com.espressif.rainmaker.R;
import com.espressif.webrtc.WebRtcConstants;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class WebRtcConfigActivity extends AppCompatActivity {
    private static final String TAG = WebRtcConfigActivity.class.getSimpleName();
    private ContentLoadingProgressBar progressBar;
    private TextView tvPleaseWait;

    private final List<ResourceEndpointListItem> mEndpointList = new ArrayList<>();
    private final List<IceServer> mIceServerList = new ArrayList<>();
    private String mChannelArn = null;
    private String mStreamArn = null;

    private String channelName, region;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webrtc_config);

        progressBar = findViewById(R.id.progress_bar);
        tvPleaseWait = findViewById(R.id.tv_please_wait);

        startLoaderAnimation();

        channelName = EspApplication.channelName;
        region = EspApplication.region;
        Log.d(TAG, "Channel name : " + channelName);

        runOnUiThread(() -> {
            checkPermissionsAndStartViewer();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLoaderAnimation();
        checkPermissions();
    }

    private void checkPermissionsAndStartViewer() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startViewerActivity();
        } else {
            Toast.makeText(this, "Camera and Microphone permissions are required.", Toast.LENGTH_SHORT).show();
            checkPermissions();
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 9393);
        }
    }

    private void startLoaderAnimation() {
        progressBar.setVisibility(View.VISIBLE);
    }

    private void stopLoaderAnimation() {
//        ivClaiming.setVisibility(View.GONE);
    }

    private void startViewerActivity() {
        Log.e(TAG, "Start Viewer Activity");
        if (!updateSignalingChannelInfo(region, channelName.replaceAll("\\s+$", ""), ChannelRole.VIEWER)) {
            return;
        }

        stopLoaderAnimation();

        if (mChannelArn != null) {
            Bundle extras = setExtras(false);
            Intent intent = new Intent(this, WebRtcActivity.class);
            intent.putExtras(extras);
            startActivity(intent);
            finish();
        }
    }

    private Bundle setExtras(boolean isMaster) {

        final Bundle extras = new Bundle();
        extras.putString(WebRtcConstants.KEY_CHANNEL_NAME, channelName);
        extras.putString(WebRtcConstants.KEY_REGION, region);
        extras.putString(WebRtcConstants.KEY_CHANNEL_ARN, mChannelArn);
        extras.putString(WebRtcConstants.KEY_STREAM_ARN, mStreamArn);
        extras.putBoolean(WebRtcConstants.KEY_IS_MASTER, isMaster);

        if (mIceServerList.size() > 0) {
            ArrayList<String> userNames = new ArrayList<>(mIceServerList.size());
            ArrayList<String> passwords = new ArrayList<>(mIceServerList.size());
            ArrayList<Integer> ttls = new ArrayList<>(mIceServerList.size());
            ArrayList<List<String>> urisList = new ArrayList<>();
            for (final IceServer iceServer : mIceServerList) {
                userNames.add(iceServer.getUsername());
                passwords.add(iceServer.getPassword());
                ttls.add(iceServer.getTtl());
                urisList.add(iceServer.getUris());
            }
            extras.putStringArrayList(WebRtcConstants.KEY_ICE_SERVER_USER_NAME, userNames);
            extras.putStringArrayList(WebRtcConstants.KEY_ICE_SERVER_PASSWORD, passwords);
            extras.putIntegerArrayList(WebRtcConstants.KEY_ICE_SERVER_TTL, ttls);
            extras.putSerializable(WebRtcConstants.KEY_ICE_SERVER_URI, urisList);
        } else {
            extras.putStringArrayList(WebRtcConstants.KEY_ICE_SERVER_USER_NAME, null);
            extras.putStringArrayList(WebRtcConstants.KEY_ICE_SERVER_PASSWORD, null);
            extras.putIntegerArrayList(WebRtcConstants.KEY_ICE_SERVER_TTL, null);
            extras.putSerializable(WebRtcConstants.KEY_ICE_SERVER_URI, null);
        }

        for (ResourceEndpointListItem endpoint : mEndpointList) {
            if (endpoint.getProtocol().equals("WSS")) {
                extras.putString(WebRtcConstants.KEY_WSS_ENDPOINT, endpoint.getResourceEndpoint());
            } else if (endpoint.getProtocol().equals("WEBRTC")) {
                extras.putString(WebRtcConstants.KEY_WEBRTC_ENDPOINT, endpoint.getResourceEndpoint());
            }
        }

        return extras;
    }

    /**
     * Fetches info needed to connect to the Amazon Kinesis Video Streams Signaling channel.
     *
     * @param region      The region the Signaling channel is located in.
     * @param channelName The name of the Amazon Kinesis Video Streams Signaling channel.
     * @param role        The signaling channel role (master or viewer).
     * @return {@code true} on success. {@code false} if unsuccessful.
     */
    private boolean updateSignalingChannelInfo(final String region, final String channelName, final ChannelRole role) {
        mEndpointList.clear();
        mIceServerList.clear();
        mChannelArn = null;
        final UpdateSignalingChannelInfoTask task = new UpdateSignalingChannelInfoTask(this);

        String errorMessage = null;
        try {
            errorMessage = task.execute(region, channelName, role).get();
        } catch (Exception e) {
            Log.e(TAG, "Failed to wait for response of UpdateSignalingChannelInfoTask", e);
        }

        if (errorMessage != null) {
            Log.e(TAG, "updateSignalingChannelInfo() encountered an error: " + errorMessage);
        }
        return errorMessage == null;
    }

    /**
     * Makes backend calls to KVS in order to obtain info needed to start the WebRTC session.
     * <p>
     * The task returns {@code null} upon success, otherwise, it returns an error message.
     */
    static class UpdateSignalingChannelInfoTask extends AsyncTask<Object, String, String> {
        final WeakReference<WebRtcConfigActivity> mFragment;

        UpdateSignalingChannelInfoTask(final WebRtcConfigActivity fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        @Override
        protected String doInBackground(final Object... objects) {
            final String region = (String) objects[0];
            final String channelName = (String) objects[1];
            final ChannelRole role = (ChannelRole) objects[2];

            // Step 1. Create Kinesis Video Client
            final AWSKinesisVideoClient awsKinesisVideoClient;
            try {
                awsKinesisVideoClient = WebRtcConstants.getAwsKinesisVideoClient(region);
            } catch (Exception e) {
                return "Create client failed with " + e.getLocalizedMessage();
            }

            // Step 2. Use the Kinesis Video Client to call DescribeSignalingChannel API.
            //         If that fails with ResourceNotFoundException, the channel does not exist.
            //         If we are connecting as Master, if it doesn't exist, we attempt to create
            //         it by calling CreateSignalingChannel API.
            try {
                final DescribeSignalingChannelResult describeSignalingChannelResult = awsKinesisVideoClient.describeSignalingChannel(
                        new DescribeSignalingChannelRequest()
                                .withChannelName(channelName));

                Log.i(TAG, "Channel ARN is " + describeSignalingChannelResult.getChannelInfo().getChannelARN());
                mFragment.get().mChannelArn = describeSignalingChannelResult.getChannelInfo().getChannelARN();
            } catch (final ResourceNotFoundException e) {
                if (role.equals(ChannelRole.MASTER)) {
                    try {
                        CreateSignalingChannelResult createSignalingChannelResult = awsKinesisVideoClient.createSignalingChannel(
                                new CreateSignalingChannelRequest()
                                        .withChannelName(channelName));

                        mFragment.get().mChannelArn = createSignalingChannelResult.getChannelARN();
                    } catch (Exception ex) {
                        return "Create Signaling Channel failed with Exception " + ex.getLocalizedMessage();
                    }
                } else {
                    return "Signaling Channel " + channelName + " doesn't exist!";
                }
            } catch (Exception ex) {
                return "Describe Signaling Channel failed with Exception " + ex.getLocalizedMessage();
            }

            final String[] protocols = new String[]{"WSS", "HTTPS"};

            // Step 3. Use the Kinesis Video Client to call GetSignalingChannelEndpoint.
            //         Each signaling channel is assigned an HTTPS and WSS endpoint to connect
            //         to for data-plane operations, which we fetch using the GetSignalingChannelEndpoint API,
            //         and a WEBRTC endpoint to for storage data-plane operations.
            //         Attempting to obtain the WEBRTC endpoint if the signaling channel is not configured
            //         will result in an InvalidArgumentException.
            try {
                final GetSignalingChannelEndpointResult getSignalingChannelEndpointResult = awsKinesisVideoClient.getSignalingChannelEndpoint(
                        new GetSignalingChannelEndpointRequest()
                                .withChannelARN(mFragment.get().mChannelArn)
                                .withSingleMasterChannelEndpointConfiguration(
                                        new SingleMasterChannelEndpointConfiguration()
                                                .withProtocols(protocols)
                                                .withRole(role)));

                Log.i(TAG, "Endpoints " + getSignalingChannelEndpointResult.toString());
                mFragment.get().mEndpointList.addAll(getSignalingChannelEndpointResult.getResourceEndpointList());
            } catch (Exception e) {
                return "Get Signaling Endpoint failed with Exception " + e.getLocalizedMessage();
            }

            String dataEndpoint = null;
            for (ResourceEndpointListItem endpoint : mFragment.get().mEndpointList) {
                if (endpoint.getProtocol().equals("HTTPS")) {
                    dataEndpoint = endpoint.getResourceEndpoint();
                }
            }

            // Step 4. Construct the Kinesis Video Signaling Client. The HTTPS endpoint from the
            //         GetSignalingChannelEndpoint response above is used with this client. This
            //         client is just used for getting ICE servers, not for actual signaling.
            // Step 5. Call GetIceServerConfig in order to obtain TURN ICE server info.
            //         Note: the STUN endpoint will be `stun:stun.kinesisvideo.${region}.amazonaws.com:443`
            try {
                final AWSKinesisVideoSignalingClient awsKinesisVideoSignalingClient = WebRtcConstants.getAwsKinesisVideoSignalingClient(region, dataEndpoint);
                GetIceServerConfigResult getIceServerConfigResult = awsKinesisVideoSignalingClient.getIceServerConfig(
                        new GetIceServerConfigRequest().withChannelARN(mFragment.get().mChannelArn).withClientId(role.name()));
                mFragment.get().mIceServerList.addAll(getIceServerConfigResult.getIceServerList());
            } catch (Exception e) {
                return "Get Ice Server Config failed with Exception " + e.getLocalizedMessage();
            }

            return null;
        }

        /**
         * Shows a Dialog box if any errors were returned in {@link #doInBackground(Object...)}.
         *
         * @param result This will be displayed in the Dialog box.
         */
        @Override
        protected void onPostExecute(final String result) {
            if (result != null) {
                new AlertDialog.Builder(mFragment.get())
                        .setPositiveButton("OK", null)
                        .setMessage(result)
                        .create()
                        .show();
            }
        }
    }
}