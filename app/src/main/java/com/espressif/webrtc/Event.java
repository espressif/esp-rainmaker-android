package com.espressif.webrtc;

import android.util.Base64;
import android.util.Log;

import com.google.common.base.Charsets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.webrtc.IceCandidate;

import java.util.Optional;

/**
 * A class representing the Event object. All response messages are asynchronously delivered
 * to the recipient as events (for example, an SDP offer or SDP answer delivery).
 *
 * @see <a href="https://docs.aws.amazon.com/kinesisvideostreams-webrtc-dg/latest/devguide/kvswebrtc-websocket-apis-7.html">Event</a>
 */
public class Event {

    private static final String TAG = "Event";

    private final String senderClientId;

    private final String messageType;

    private final String messagePayload;

    private String statusCode;

    private String body;

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getSenderClientId() {
        return senderClientId;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getMessagePayload() {
        return messagePayload;
    }


    public Event(final String senderClientId, final String messageType, final String messagePayload) {
        this.senderClientId = senderClientId;
        this.messageType = messageType;
        this.messagePayload = messagePayload;
    }

    /**
     * Attempts to convert an {@code ICE_CANDIDATE} {@link Event} into an {@link IceCandidate}.
     *
     * @param event the ICE_CANDIDATE event to convert.
     * @return an {@link IceCandidate} from the {@link Event}. {@code null} if the IceCandidate wasn't
     * able to be constructed.
     */
    public static IceCandidate parseIceCandidate(final Event event) {
        if (event == null || !"ICE_CANDIDATE".equalsIgnoreCase(event.getMessageType())) {
            Log.e(TAG, event + " is not an ICE_CANDIDATE type!");
            return null;
        }

        final byte[] decode = Base64.decode(event.getMessagePayload(), Base64.DEFAULT);
        final String candidateString = new String(decode, Charsets.UTF_8);

        if (candidateString.equals("null")) {
            Log.w(TAG, "Received null IceCandidate!");
            return null;
        }

        final JsonObject jsonObject = JsonParser.parseString(candidateString).getAsJsonObject();

        final String sdpMid = Optional.ofNullable(jsonObject.get("sdpMid"))
                .map(Object::toString)
                // Remove quotes
                .map(sdpMidStr -> sdpMidStr.length() > 2 ? sdpMidStr.substring(1, sdpMidStr.length() - 1) : sdpMidStr)
                .orElse("");

        int sdpMLineIndex = -1;
        try {
            sdpMLineIndex = Integer.parseInt(jsonObject.get("sdpMLineIndex").toString());
        } catch (final NumberFormatException e) {
            Log.e(TAG, "Invalid sdpMLineIndex");
        }

        // Ice Candidate needs one of these two to be present
        if (sdpMid.isEmpty() && sdpMLineIndex == -1) {
            return null;
        }

        final String candidate = Optional.ofNullable(jsonObject.get("candidate"))
                .map(Object::toString)
                // Remove quotes
                .map(candidateStr -> candidateStr.length() > 2 ? candidateStr.substring(1, candidateStr.length() - 1) : candidateStr)
                .orElse("");

        return new IceCandidate(sdpMid, sdpMLineIndex == -1 ? 0 : sdpMLineIndex, candidate);
    }

    public static String parseSdpEvent(final Event answerEvent) {

        final String message = new String(Base64.decode(answerEvent.getMessagePayload().getBytes(), Base64.DEFAULT));
        final JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();
        final String type = jsonObject.get("type").toString();

        if (!type.equalsIgnoreCase("\"answer\"")) {
            Log.e(TAG, "Error in answer message");
        }

        final String sdp = jsonObject.get("sdp").getAsString();
        Log.d(TAG, "SDP answer received from master: " + sdp);
        return sdp;
    }

    public static String parseOfferEvent(Event offerEvent) {
        final String s = new String(Base64.decode(offerEvent.getMessagePayload(), Base64.DEFAULT));

        return Optional.of(JsonParser.parseString(s))
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map(jsonObject -> jsonObject.get("sdp"))
                .map(JsonElement::getAsString)
                .orElse("");
    }

    @Override
    public String toString() {
        return "Event(" +
                "senderClientId='" + senderClientId + '\'' +
                ", messageType='" + messageType + '\'' +
                ", messagePayload='" + messagePayload + '\'' +
                ", statusCode='" + statusCode + '\'' +
                ", body='" + body + '\'' +
                ')';
    }
}
