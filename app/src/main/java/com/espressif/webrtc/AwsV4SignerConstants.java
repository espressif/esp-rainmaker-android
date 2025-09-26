package com.espressif.webrtc;

public final class AwsV4SignerConstants {
    static final String ALGORITHM_AWS4_HMAC_SHA_256 = "AWS4-HMAC-SHA256";
    static final String AWS4_REQUEST_TYPE = "aws4_request";
    static final String SERVICE = "kinesisvideo";
    static final String X_AMZ_ALGORITHM = "X-Amz-Algorithm";
    static final String X_AMZ_CREDENTIAL = "X-Amz-Credential";
    static final String X_AMZ_DATE = "X-Amz-Date";
    static final String X_AMZ_EXPIRES = "X-Amz-Expires";
    static final String X_AMZ_SECURITY_TOKEN = "X-Amz-Security-Token";
    static final String X_AMZ_SIGNATURE = "X-Amz-Signature";
    static final String X_AMZ_SIGNED_HEADERS = "X-Amz-SignedHeaders";
    static final String NEW_LINE_DELIMITER = "\n";
    static final String DATE_PATTERN = "yyyyMMdd";
    static final String TIME_PATTERN = "yyyyMMdd'T'HHmmss'Z'";
    static final String METHOD = "GET";
    static final String SIGNED_HEADERS = "host";
}
