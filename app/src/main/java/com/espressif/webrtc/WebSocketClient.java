package com.espressif.webrtc;

import static org.awaitility.Awaitility.await;

import android.util.Log;

import androidx.annotation.NonNull;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;

/**
 * A JSR356 based websocket client.
 */
class WebSocketClient {

    private static final String TAG = "WebSocketClient";

    private Session session;

    private final ExecutorService executorService;

    WebSocketClient(final String uri, final ClientManager clientManager,
                    final SignalingListener signalingListener,
                    final ExecutorService executorService) {

        this.executorService = executorService;
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create()
                .configurator(new ClientEndpointConfig.Configurator() {
                    @Override
                    public void beforeRequest(final Map<String, List<String>> headers) {
                        super.beforeRequest(headers);

                        final String userAgent = Constants.APP_NAME + "/" + Constants.VERSION + " " + System.getProperty("http.agent");

                        headers.put("User-Agent", Collections.singletonList(userAgent.trim()));
                    }

                    @Override
                    public void afterResponse(final HandshakeResponse hr) {
                        super.afterResponse(hr);

                        hr.getHeaders().forEach((key, values) -> Log.d(TAG, "header - " + key + ": " + values));
                    }
                })
                .build();

        clientManager.getProperties().put(ClientProperties.LOG_HTTP_UPGRADE, true);

        final Endpoint endpoint = new Endpoint() {

            @Override
            public void onOpen(final Session session, final EndpointConfig endpointConfig) {
                Log.d(TAG, "Registering message handler");
                session.addMessageHandler(signalingListener.getMessageHandler());
            }

            @Override
            public void onClose(final Session session, final CloseReason closeReason) {
                super.onClose(session, closeReason);
                Log.d(TAG, "Session " + session.getRequestURI() + " closed with reason " +
                        closeReason.getReasonPhrase());
            }

            @Override
            public void onError(final Session session, final Throwable thr) {
                super.onError(session, thr);
                Log.w(TAG, thr);
            }

        };

        executorService.submit(() -> {
            try {
                session = clientManager.connectToServer(endpoint, cec, new URI(uri));
            } catch (final DeploymentException | IOException | URISyntaxException e) {
                signalingListener.onException(e);
            }
        });

        await().atMost(10, TimeUnit.SECONDS).until(WebSocketClient.this::isOpen);
    }

    boolean isOpen() {
        if (session == null) {
            Log.d(TAG, "isOpen: false");
            return false;
        }
        Log.d(TAG, "isOpen: " + session.isOpen());
        return session.isOpen();
    }

    void send(@NonNull final String message) {
        if (!this.isOpen()) {
            Log.e(TAG, "Connection isn't open!");
            return;
        }

        try {
            session.getBasicRemote().sendText(message);
        } catch (final IOException e) {
            Log.e(TAG, "Exception sending message: " + e.getMessage());
        }
    }

    void disconnect() {
        if (session == null) {
            Log.e(TAG, "Connection hasn't opened yet!");
            return;
        }

        if (!session.isOpen()) {
            Log.w(TAG, "Connection already closed for " + session.getRequestURI());
            return;
        }

        try {
            session.close();
            executorService.shutdownNow();
            Log.i(TAG, "Disconnected from " + session.getRequestURI() + " successfully!");
        } catch (final IOException e) {
            Log.e(TAG, "Exception closing: " + e.getMessage());
        }
    }
}
