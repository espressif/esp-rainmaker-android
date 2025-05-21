package com.espressif.webrtc;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;

public class IoTCredentialsProvider implements AWSCredentialsProvider {
    private AWSCredentials credentials;

    public IoTCredentialsProvider(String accessKeyId, String secretKey, String sessionToken) {
        this.credentials = new BasicSessionCredentials(accessKeyId, secretKey, sessionToken);
    }

    @Override
    public AWSCredentials getCredentials() {
        return credentials;
    }

    @Override
    public void refresh() {
        // Not implemented for this simple case
    }
}
