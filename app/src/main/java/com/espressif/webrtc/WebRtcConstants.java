package com.espressif.webrtc;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.kinesisvideo.common.logging.LogLevel;
import com.amazonaws.kinesisvideo.common.logging.OutputChannel;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.kinesisvideo.util.AndroidLogOutputChannel;
import com.amazonaws.regions.Region;
import com.amazonaws.services.kinesisvideo.AWSKinesisVideoClient;
import com.amazonaws.services.kinesisvideosignaling.AWSKinesisVideoSignalingClient;

public class WebRtcConstants {

    public static final String KEY_REGION = "region";
    public static final String KEY_CHANNEL_ARN = "channelArn";
    public static final String KEY_STREAM_ARN = "streamArn";
    public static final String KEY_WSS_ENDPOINT = "wssEndpoint";
    public static final String KEY_WEBRTC_ENDPOINT = "webrtcEndpoint";
    public static final String KEY_IS_MASTER = "isMaster";
    public static final String KEY_ICE_SERVER_USER_NAME = "iceServerUserName";
    public static final String KEY_ICE_SERVER_PASSWORD = "iceServerPassword";
    public static final String KEY_ICE_SERVER_TTL = "iceServerTTL";
    public static final String KEY_ICE_SERVER_URI = "iceServerUri";
    public static final String KEY_CAMERA_FRONT_FACING = "cameraFrontFacing";

    // Set to true to show mic/video ON/OFF toggle buttons in WebRTC camera view
    public static final boolean ENABLE_MEDIA_TOGGLE_UI = false;

    // Set to true to request audio receive in the SDP offer
    public static final boolean OFFER_AUDIO = true;

    // Set to true to send audio (microphone) by default when a session starts
    public static final boolean SEND_AUDIO_BY_DEFAULT = false;

    // Set to true to mute incoming (remote) audio by default
    public static final boolean INCOMING_AUDIO_MUTED_BY_DEFAULT = false;
    // Set to true to flip the video vertically (display only, does not affect the stream)
    public static final boolean FLIP_VIDEO_VERTICAL = false;

    public static final String KEY_CLIENT_ID = "clientId";
    public static final String KEY_CHANNEL_NAME = "channelName";

    // Custom credentials provider to support IoT credentials
    private static AWSCredentialsProvider customCredentialsProvider = null;

    public static AWSCredentialsProvider getCredentialsProvider() {
        if (customCredentialsProvider != null) {
            return customCredentialsProvider;
        }
        final OutputChannel outputChannel = new AndroidLogOutputChannel();
        final com.amazonaws.kinesisvideo.common.logging.Log log =
                new com.amazonaws.kinesisvideo.common.logging.Log(outputChannel, LogLevel.VERBOSE, "WebRtc");
        return AWSMobileClient.getInstance();
    }

    // Method to set a custom credentials provider
    public static void setCredentialsProvider(AWSCredentialsProvider provider) {
        customCredentialsProvider = provider;
    }

    public static AWSKinesisVideoClient getAwsKinesisVideoClient(final String region) {
        final AWSKinesisVideoClient awsKinesisVideoClient = new AWSKinesisVideoClient(
                getCredentialsProvider().getCredentials());
        awsKinesisVideoClient.setRegion(Region.getRegion(region));
        awsKinesisVideoClient.setSignerRegionOverride(region);
        awsKinesisVideoClient.setServiceNameIntern("kinesisvideo");
        return awsKinesisVideoClient;
    }

    public static AWSKinesisVideoSignalingClient getAwsKinesisVideoSignalingClient(final String region, final String endpoint) {
        final AWSKinesisVideoSignalingClient client = new AWSKinesisVideoSignalingClient(
                getCredentialsProvider().getCredentials());
        client.setRegion(Region.getRegion(region));
        client.setSignerRegionOverride(region);
        client.setServiceNameIntern("kinesisvideo");
        client.setEndpoint(endpoint);
        return client;
    }
}
