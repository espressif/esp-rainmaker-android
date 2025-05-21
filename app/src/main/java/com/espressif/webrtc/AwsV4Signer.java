package com.espressif.webrtc;

import static com.google.common.hash.Hashing.sha256;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.DateUtils;
import com.google.common.collect.ImmutableMap;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@SuppressWarnings({"SpellCheckingInspection", "SameParameterValue"})
public class AwsV4Signer {

    // Guide - https://docs.aws.amazon.com/general/latest/gr/sigv4_signing.html
    // Implementation based on https://docs.aws.amazon.com/general/latest/gr/sigv4-signed-request-examples.html#sig-v4-examples-get-query-string

    /**
     * Constructs a WebRTC WebSocket connect URI with Query Parameters to connect to Kinesis Video Signaling.
     *
     * @param uri          The URL to sign.
     *                     <p>
     *                     <strong>Connect as Master URL</strong> - GetSignalingChannelEndpoint (master role) + Query Parameters: Channel ARN as X-Amz-ChannelARN
     *                     <a href="https://docs.aws.amazon.com/kinesisvideostreams-webrtc-dg/latest/devguide/kvswebrtc-websocket-apis-2.html">Additional info</a>
     *                     <p>
     *                     <strong>Connect as Viewer URL</strong> - GetSignalingChannelEndpoint (viewer role) + Query Parameters: Channel ARN as X-Amz-ChannelARN & Client Id as X-Amz-ClientId
     *                     <a href="https://docs.aws.amazon.com/kinesisvideostreams-webrtc-dg/latest/devguide/kvswebrtc-websocket-apis-1.html">Additional info</a>
     *                     <p>
     *                     Viewer URL example: {@literal wss://v-a1b2c3d4.kinesisvideo.us-west-2.amazonaws.com?X-Amz-ChannelARN=arn:aws:kinesisvideo:us-west-2:123456789012:channel/demo-channel/1234567890123&X-Amz-ClientId=d7d1c6e2-9cb0-4d61-bea9-ecb3d3816557}
     *                     <p>
     *                     <strong>Note</strong>: The Signaling Channel Endpoints are different depending on the role (master/viewer) specified in GetSignalingChannelEndpoint API call.
     *                     <a href="https://docs.aws.amazon.com/kinesisvideostreams/latest/dg/API_SingleMasterChannelEndpointConfiguration.html#KinesisVideo-Type-SingleMasterChannelEndpointConfiguration-Role">Additional info</a>
     * @param accessKey    AWS Access Key Id.
     * @param secretKey    AWS Secret Key.
     * @param sessionToken AWS Session Token, if applicable. Otherwise, can be {@code null} or an empty String ({@code ""}).
     * @param wssUri       Same as URL to sign, excluding query parameters.
     * @param region       AWS region. Example: us-west-2.
     * @param dateMilli    Date at which this request to be signed. Milliseconds since epoch.
     * @return Presigned WebSocket URL you can use to connect to Kinesis Video Signaling.
     * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams-webrtc-dg/latest/devguide/kvswebrtc-websocket-apis.html">Kinesis Video Streams WebRTC Websocket APIs</a>
     */
    public static URI sign(final URI uri, final String accessKey, final String secretKey,
                           final String sessionToken, final URI wssUri, final String region,
                           final long dateMilli) {
        // Step 1. Create canonical request.
        final String amzDate = getTimeStamp(dateMilli);
        final String datestamp = getDateStamp(dateMilli);
        final Map<String, String> queryParamsMap = buildQueryParamsMap(uri, accessKey, sessionToken, region, amzDate, datestamp);
        final String canonicalQuerystring = getCanonicalizedQueryString(queryParamsMap);
        final String canonicalRequest = getCanonicalRequest(uri, canonicalQuerystring);

        // Step 2. Construct StringToSign.
        final String stringToSign = signString(amzDate, createCredentialScope(region, datestamp), canonicalRequest);

        // Step 3. Calculate the signature.
        final byte[] signatureKey = getSignatureKey(secretKey, datestamp, region, AwsV4SignerConstants.SERVICE);
        final String signature = BinaryUtils.toHex(hmacSha256(stringToSign, signatureKey));

        // Step 4. Combine steps 1 and 3 to form the final URL.
        final String signedCanonicalQueryString = canonicalQuerystring + "&" + AwsV4SignerConstants.X_AMZ_SIGNATURE + "=" + signature;

        return URI.create(wssUri.getScheme() + "://" + wssUri.getHost() + "/?" + getCanonicalUri(uri).substring(1) + signedCanonicalQueryString);
    }

    /**
     * Same as {@link #sign(URI, String, String, String, URI, String, long)}, except the {@code wssUri}
     * parameter is extracted from the {@code uri} parameter and not passed in.
     */
    public static URI sign(final URI uri, final String accessKey, final String secretKey,
                           final String sessionToken, final String region, final long dateMillis) {
        final URI wssUri = URI.create("wss://" + uri.getHost());
        return sign(uri, accessKey, secretKey, sessionToken, wssUri, region, dateMillis);
    }

    /**
     * Return a map of all of the query parameters as key-value pairs. The values will be
     * {@link #urlEncode(String)}'d. Note: The query parameters in this map may not be in
     * sorted order.
     * <p>
     * The query parameters that are included in this map are:
     * <ol>
     *     <li>{@value AwsV4SignerConstants#X_AMZ_ALGORITHM}</li>
     *     <li>{@value AwsV4SignerConstants#X_AMZ_CREDENTIAL}</li>
     *     <li>{@value AwsV4SignerConstants#X_AMZ_DATE}</li>
     *     <li>{@value AwsV4SignerConstants#X_AMZ_EXPIRES}</li>
     *     <li>{@value AwsV4SignerConstants#X_AMZ_SIGNED_HEADERS}</li>
     *     <li>{@value AwsV4SignerConstants#X_AMZ_SECURITY_TOKEN}, if the AWS Session Token is specified.</li>
     *     <li>And, the query parameters passed through {@code uri}.</li>
     * </ol>
     *
     * @param uri          URL to sign.
     * @param accessKey    AWS Access Key Id.
     * @param sessionToken AWS Session Token. Can be null or an empty string if non-temporary credentials are used.
     * @param region       AWS region. Example: us-west-2.
     * @param amzDate      The result of {@link #getTimeStamp(long)}.
     * @param datestamp    The result of {@link #getDateStamp(long)}.
     * @return Map of the query parameters to be included.
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-query-string-auth.html">Authenticating Requests: Using Query Parameters (AWS Signature Version 4)</a>
     */
    static Map<String, String> buildQueryParamsMap(final URI uri,
                                                   final String accessKey,
                                                   final String sessionToken,
                                                   final String region,
                                                   final String amzDate,
                                                   final String datestamp) {
        final ImmutableMap.Builder<String, String> queryParamsBuilder = ImmutableMap.<String, String>builder()
                .put(AwsV4SignerConstants.X_AMZ_ALGORITHM, AwsV4SignerConstants.ALGORITHM_AWS4_HMAC_SHA_256)
                .put(AwsV4SignerConstants.X_AMZ_CREDENTIAL, urlEncode(accessKey + "/" + createCredentialScope(region, datestamp)))
                .put(AwsV4SignerConstants.X_AMZ_DATE, amzDate)

                // The SigV4 signer has a maximum time limit of five minutes.
                // Once a connection is established, peers exchange signaling messages,
                // and the P2P connection is successful, the media P2P session
                // can continue for longer period of time.
                .put(AwsV4SignerConstants.X_AMZ_EXPIRES, "299")
                .put(AwsV4SignerConstants.X_AMZ_SIGNED_HEADERS, AwsV4SignerConstants.SIGNED_HEADERS);

        if (isNotEmpty(sessionToken)) {
            queryParamsBuilder.put(AwsV4SignerConstants.X_AMZ_SECURITY_TOKEN, urlEncode(sessionToken));
        }

        // Add the query parameters included in the uri.
        // Note: query parameters follow the format: key1=val1&key2=val2&key3=val3
        if (isNotEmpty(uri.getQuery())) {
            final String[] params = uri.getQuery().split("&");
            for (final String param : params) {
                final int index = param.indexOf('=');
                if (index > 0) {
                    final String paramKey = param.substring(0, index);
                    final String paramValue = urlEncode(param.substring(index + 1));
                    queryParamsBuilder.put(paramKey, paramValue);
                }
            }
        }
        return queryParamsBuilder.build();
    }

    static String getCanonicalizedQueryString(final Map<String, String> queryParamsMap) {
        final List<String> queryKeys = new ArrayList<>(queryParamsMap.keySet());
        Collections.sort(queryKeys);

        final StringBuilder builder = new StringBuilder();

        for (int i = 0; i < queryKeys.size(); i++) {
            builder.append(queryKeys.get(i)).append("=").append(queryParamsMap.get(queryKeys.get(i)));
            if (queryKeys.size() - 1 > i) {
                builder.append("&");
            }
        }

        return builder.toString();
    }

    /**
     * Create and return the credential scope, which belongs in the X-Amz-Credential query parameter,
     * except for the access-key-id, and the slash that follows.
     * <p>
     * The format of the full scope is as follows:
     * {@code <access-key-id>/<datestamp>/<region>/kinesisvideo/aws4_request}
     *
     * @param region    AWS Region. For example, us-west-2.
     * @param datestamp The datestamp in "yyyyMMdd" format. For example, "20160801".
     * @return The scope, except for the access-key-id and the slash that follows.
     */
    static String createCredentialScope(final String region, final String datestamp) {
        return new StringJoiner("/")
                .add(datestamp)
                .add(region)
                .add(AwsV4SignerConstants.SERVICE)
                .add(AwsV4SignerConstants.AWS4_REQUEST_TYPE)
                .toString();
    }

    /**
     * Constructs and returns the canonical request.
     * <p>
     * An example canonical request looks like the following:
     * <pre>
     * GET
     * /
     * X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-ChannelARN=arn%3Aaws%3Akinesisvideo%3Aus-west-2%3A123456789012%3Achannel%2Fdemo-channel%2F1234567890123&X-Amz-ClientId=d7d1c6e2-9cb0-4d61-bea9-ecb3d3816557&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20230718%2Fus-west-2%2Fkinesisvideo%2Faws4_request&X-Amz-Date=20230718T191301Z&X-Amz-Expires=299&X-Amz-SignedHeaders=host
     * host:v-1a2b3c4d.kinesisvideo.us-west-2.amazonaws.com
     *
     * host
     * e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
     * </pre>
     * <p>
     * The format of the <strong>canonical request</strong> are as follows:
     * <ol>
     *     <li>{@code HTTP method + "\n"} - With presigned URL's, it's always {@value AwsV4SignerConstants#METHOD}.</li>
     *     <li>{@code Canonical URI + "\n"} - Resource. In our case, it's always "/".</li>
     *     <li>{@code Canonical Query String + "\n"} - Sorted list of query parameters (and their values), excluding X-Amz-Signature. In our case: X-Amz-Algorithm, X-Amz-ChannelARN, X-Amz-ClientId (if viewer), X-Amz-Credential, X-Amz-Date, X-Amz-Expires.</li>
     *     <li>{@code Canonical Headers + "\n"} - In our case, we only have the required HTTP {@code host} header.</li>
     *     <li>{@code Signed Headers + "\n"} - Which headers, from the canonical headers, in alphabetical order.</li>
     *     <li>{@code Hashed Payload} - In our case, it's always the SHA-256 checksum of an empty string.</li>
     * </ol>
     *
     * @param uri                  The URL to sign. For example, {@code wss://v-a1b2c3d4.kinesisvideo.us-west-2.amazonaws.com?
     *                             X-Amz-ChannelARN=arn:aws:kinesisvideo:us-west-2:123456789012:channel/demo-channel/1234567890123&X-Amz-ClientId=d7d1c6e2-9cb0-4d61-bea9-ecb3d3816557}.
     * @param canonicalQuerystring The Canonical Query String to use in the Canonical Request. Sorted list of query
     *                             parameters (and their values), excluding X-Amz-Signature, already URL-encoded.
     * @return The fully-constructed canonical request.
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-query-string-auth.html#w28981aab9c27c27c11.UriEncode%28%29:~:text=or%20trailing%20whitespace.-,UriEncode(),-URI%20encode%20every">URL encoding</a>
     * @see <a href="https://docs.aws.amazon.com/IAM/latest/UserGuide/create-signed-request.html#create-canonical-request">Canonical request specification</a>
     */
    static String getCanonicalRequest(final URI uri, final String canonicalQuerystring) {
        final String payloadHash = sha256().hashString(EMPTY, UTF_8).toString();
        final String canonicalUri = getCanonicalUri(uri);
        final String canonicalHeaders = "host:" + uri.getHost() + AwsV4SignerConstants.NEW_LINE_DELIMITER;

        return new StringJoiner(AwsV4SignerConstants.NEW_LINE_DELIMITER)
                .add(AwsV4SignerConstants.METHOD)
                .add(canonicalUri)
                .add(canonicalQuerystring)
                .add(canonicalHeaders)
                .add(AwsV4SignerConstants.SIGNED_HEADERS)
                .add(payloadHash)
                .toString();
    }

    static String getCanonicalUri(final URI uri) {
        return Optional.of(uri.getPath())
                .filter(s -> !isEmpty(s))
                .orElse("/");
    }

    /**
     * Returns the following string. Each line (except the last one) is followed by a newline character.
     * <pre>
     * Algorithm
     * RequestDateTime
     * CredentialScope
     * HashedCanonicalRequest
     * </pre>
     * <p>
     * Here is an explanation of each line.
     * <ol>
     *     <li>Algorithm - For SHA-256, we use AWS4-HMAC-SHA256.</li>
     *     <li>RequestDateTime - Timestamp from {@link #getTimeStamp(long)}.</li>
     *     <li>CredentialScope - Scope from {@link #createCredentialScope(String, String)}.</li>
     *     <li>HashedCanonicalRequest - Hash of {@link #getCanonicalRequest(URI, String)}.
     *     Since we use SHA-256, this is the SHA-256 digest of the canonical request.</li>
     * </ol>
     * <p>
     * An example of a string to sign looks like the following:
     * <pre>
     * AWS4-HMAC-SHA256
     * 20150830T123600Z
     * AKIDEXAMPLE/20150830/us-west-2/kinesisvideo/aws4_request
     * 816cd5b414d056048ba4f7c5386d6e0533120fb1fcfa93762cf0fc39e2cf19e0
     * </pre>
     *
     * @param amzDate          The result of {@link #getTimeStamp(long)}.
     * @param credentialScope  the result of {@link #createCredentialScope(String, String)}.
     * @param canonicalRequest the result of {@link #getCanonicalRequest(URI, String)}.
     * @return The string to sign.
     * @see <a href="https://docs.aws.amazon.com/IAM/latest/UserGuide/create-signed-request.html#create-string-to-sign">String to sign</a>
     */
    static String signString(final String amzDate, final String credentialScope, final String canonicalRequest) {
        return new StringJoiner(AwsV4SignerConstants.NEW_LINE_DELIMITER)
                .add(AwsV4SignerConstants.ALGORITHM_AWS4_HMAC_SHA_256)
                .add(amzDate)
                .add(credentialScope)
                .add(sha256().hashString(canonicalRequest, UTF_8).toString())
                .toString();
    }

    static String urlEncode(final String str) {
        try {
            return URLEncoder.encode(str, UTF_8.name());
        } catch (final Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    //  https://docs.aws.amazon.com/general/latest/gr/signature-v4-examples.html#signature-v4-examples-java
    static byte[] hmacSha256(final String data, final byte[] key) {
        final String algorithm = "HmacSHA256";
        final Mac mac;
        try {
            mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (final Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Calculate and return the signature key. Note: the returned signature must be converted from
     * binary to hexadecimal representation (lowercase letters).
     * <p>
     * The formula is as follows:
     * <pre>
     * kDate = hash("AWS4" + Key, Date)
     * kRegion = hash(kDate, Region)
     * kService = hash(kRegion, ServiceName)
     * kSigning = hash(kService, "aws4_request")
     * ksignature = hash(kSigning, string-to-sign)
     * </pre>
     *
     * @param key         AWS secret access key.
     * @param dateStamp   Date used in the credential scope. Format: yyyyMMdd.
     * @param regionName  AWS region. Example: us-west-2.
     * @param serviceName The name of the service. Should be {@code kinesisvideo}.
     * @return {@code ksignature}, as specified above.
     * @see <a href="https://docs.aws.amazon.com/IAM/latest/UserGuide/create-signed-request.html#calculate-signature">Calculate signature</a>
     */
    static byte[] getSignatureKey(
            final String key,
            final String dateStamp,
            final String regionName,
            final String serviceName) {
        final byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        final byte[] kDate = hmacSha256(dateStamp, kSecret);
        final byte[] kRegion = hmacSha256(regionName, kDate);
        final byte[] kService = hmacSha256(serviceName, kRegion);
        return hmacSha256(AwsV4SignerConstants.AWS4_REQUEST_TYPE, kService);
    }

    /**
     * Returns the date and time, formatted to follow the ISO 8601 standard,
     * which is the "yyyyMMddTHHmmssZ" format.
     * <p>
     * For example if the date and time was "08/01/2016 15:32:41.982-700"
     * then it must first be converted to UTC (Coordinated Universal Time)
     * and then submitted as "20160801T223241Z".
     *
     * @param dateMilli The milliseconds since epoch at which this request is to be signed at.
     *                  Must be the same as the one passed to {@link #getDateStamp(long)} to avoid
     *                  signing issues at midnight UTC.
     * @return The date string, formatted to the ISO 8601 standard.
     */
    static String getTimeStamp(final long dateMilli) {
        return DateUtils.format(AwsV4SignerConstants.TIME_PATTERN, new Date(dateMilli));
    }

    /**
     * Returns the date in yyyyMMdd format.
     * <p>
     * For example if the date and time was "08/01/2016 15:32:41.982-700"
     * then it must first be converted to UTC (Coordinated Universal Time)
     * and "20160801" will be returned.
     *
     * @param dateMilli The milliseconds since epoch at which this request is to be signed at.
     *                  Must be the same as the one passed to {@link #getTimeStamp(long)} to avoid
     *                  signing issues at midnight UTC.
     * @return The date string, without the current time.
     */
    static String getDateStamp(final long dateMilli) {
        return DateUtils.format(AwsV4SignerConstants.DATE_PATTERN, new Date(dateMilli));
    }
}