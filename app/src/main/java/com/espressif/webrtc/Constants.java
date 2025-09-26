package com.espressif.webrtc;

public class Constants {
    /**
     * SDK identifier
     */
    public static final String APP_NAME = "aws-kvs-webrtc-android-client";
    /**
     * SDK version identifier
     */
    public static final String VERSION = "1.0.0";

    /**
     * Query parameter for Channel ARN. Used for calling Kinesis Video Websocket APIs.
     */
    public static final String CHANNEL_ARN_QUERY_PARAM = "X-Amz-ChannelARN";

    /**
     * Query parameter for Client Id. Only used for viewers. Used for calling Kinesis Video Websocket APIs.
     */
    public static final String CLIENT_ID_QUERY_PARAM = "X-Amz-ClientId";
}
