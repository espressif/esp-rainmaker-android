package com.espressif.webrtc;

import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;

import javax.websocket.MessageHandler;

public abstract class SignalingListener implements Signaling {

    private final static String TAG = "CustomMessageHandler";

    private final Gson gson = new Gson();

    private final MessageHandler messageHandler = new MessageHandler.Whole<String>() {

        @Override
        public void onMessage(final String message) {
            if (message.isEmpty()) {
                return;
            }

            Log.d(TAG, "Received message: " + message);

            if (!message.contains("messagePayload")) {
                return;
            }

            final Event evt = gson.fromJson(message, Event.class);

            if (evt == null || evt.getMessageType() == null || evt.getMessagePayload().isEmpty()) {
                return;
            }

            switch (evt.getMessageType().toUpperCase()) {
                case "SDP_OFFER":
                    Log.d(TAG, "Offer received: SenderClientId=" + evt.getSenderClientId());
                    Log.d(TAG, new String(Base64.decode(evt.getMessagePayload(), 0)));

                    onSdpOffer(evt);
                    break;
                case "SDP_ANSWER":
                    Log.d(TAG, "Answer received: SenderClientId=" + evt.getSenderClientId());

                    onSdpAnswer(evt);
                    break;
                case "ICE_CANDIDATE":
                    Log.d(TAG, "Ice Candidate received: SenderClientId=" + evt.getSenderClientId());
                    Log.d(TAG, new String(Base64.decode(evt.getMessagePayload(), 0)));

                    onIceCandidate(evt);
                    break;
                default:
                    break;
            }
        }
    };

    public MessageHandler getMessageHandler() {
        return messageHandler;
    }
}
