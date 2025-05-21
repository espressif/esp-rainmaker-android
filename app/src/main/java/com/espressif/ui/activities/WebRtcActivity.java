package com.espressif.ui.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.services.kinesisvideowebrtcstorage.AWSKinesisVideoWebRTCStorageClient;
import com.amazonaws.services.kinesisvideowebrtcstorage.model.JoinStorageSessionRequest;
import com.espressif.rainmaker.R;
import com.espressif.webrtc.AwsV4Signer;
import com.espressif.webrtc.Constants;
import com.espressif.webrtc.Event;
import com.espressif.webrtc.KinesisVideoPeerConnection;
import com.espressif.webrtc.KinesisVideoSdpObserver;
import com.espressif.webrtc.Message;
import com.espressif.webrtc.SignalingListener;
import com.espressif.webrtc.SignalingServiceWebSocketClient;
import com.espressif.webrtc.WebRtcConstants;
import com.google.common.base.Strings;

import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStats;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCodecInfo;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WebRtcActivity extends AppCompatActivity {
    private static final String TAG = "KVSWebRtcActivity";
    private static final String AudioTrackID = "KvsAudioTrack";
    private static final String VideoTrackID = "KvsVideoTrack";
    private static final String LOCAL_MEDIA_STREAM_LABEL = "KvsLocalMediaStream";
    private static final int VIDEO_SIZE_WIDTH = 400;
    private static final int VIDEO_SIZE_HEIGHT = 300;
    private static final int VIDEO_FPS = 30;
    private static final String CHANNEL_ID = "WebRtcDataChannel";
    private static final boolean ENABLE_INTEL_VP8_ENCODER = true;
    private static final boolean ENABLE_H264_HIGH_PROFILE = true;

    private static volatile SignalingServiceWebSocketClient client;
    private PeerConnectionFactory peerConnectionFactory;

    private VideoSource videoSource;
    private VideoTrack localVideoTrack;

    private AudioManager audioManager;
    private int originalAudioMode;
    private boolean originalSpeakerphoneOn;

    private AudioTrack localAudioTrack;

    private SurfaceViewRenderer localView;
    private SurfaceViewRenderer remoteView;

    private PeerConnection localPeer;

    private EglBase rootEglBase = null;
    private VideoCapturer videoCapturer;

    private final List<IceServer> peerIceServers = new ArrayList<>();

    private boolean gotException = false;

    private String recipientClientId;

    private int mNotificationId = 0;

    private boolean master = true;
    private EditText dataChannelText = null;
    private Button sendDataChannelButton = null;

    private String mChannelArn;
    private String mClientId;

    private String webrtcEndpoint;
    private String mStreamArn;

    private String mWssEndpoint;
    private String mRegion;

    private boolean mCameraFacingFront = true;

    private AWSCredentials mCreds = null;

    private ProgressBar progressLoader;
    private TextView tvLoading;

    /**
     * Prints WebRTC stats to the debug console every so often.
     */
    private final ScheduledExecutorService printStatsExecutor = Executors.newSingleThreadScheduledExecutor();

    /**
     * Mapping of established peer connections to the peer's sender id. In other words, if an SDP
     * offer/answer for a peer connection has been received and sent, the PeerConnection is added
     * to this map.
     */
    private final HashMap<String, PeerConnection> peerConnectionFoundMap = new HashMap<String, PeerConnection>();

    /**
     * Only used when we are master. Mapping of the peer's sender id to its received ICE candidates.
     * Since we can receive ICE Candidates before we have sent the answer, we hold ICE candidates in
     * this queue until after we send the answer and the peer connection is established.
     */
    private final HashMap<String, Queue<IceCandidate>> pendingIceCandidatesMap = new HashMap<String, Queue<IceCandidate>>();

    private void initWsConnection() {

        // See https://docs.aws.amazon.com/kinesisvideostreams-webrtc-dg/latest/devguide/kvswebrtc-websocket-apis-2.html
        final String masterEndpoint = mWssEndpoint + "?" + Constants.CHANNEL_ARN_QUERY_PARAM + "=" + mChannelArn;

        // See https://docs.aws.amazon.com/kinesisvideostreams-webrtc-dg/latest/devguide/kvswebrtc-websocket-apis-1.html
        final String viewerEndpoint = mWssEndpoint + "?" + Constants.CHANNEL_ARN_QUERY_PARAM + "=" + mChannelArn + "&" + Constants.CLIENT_ID_QUERY_PARAM + "=" + mClientId;

        runOnUiThread(() -> mCreds = WebRtcConstants.getCredentialsProvider().getCredentials());

        final URI signedUri;
        if (master) {
            signedUri = getSignedUri(masterEndpoint);
        } else {
            signedUri = getSignedUri(viewerEndpoint);
        }

        if (signedUri == null) {
            gotException = true;
            return;
        }

        if (master) {
            createLocalPeerConnection();
        }

        final String wsHost = signedUri.toString();

        // Step 10. Create Signaling Client Event Listeners.
        //          When we receive messages, we need to take the appropriate action.
        final SignalingListener signalingListener = new SignalingListener() {

            @Override
            public void onSdpOffer(final Event offerEvent) {
                Log.d(TAG, "Received SDP Offer: Setting Remote Description ");

                runOnUiThread(() -> {
                    progressLoader.setVisibility(View.GONE);
                    tvLoading.setVisibility(View.GONE);
                });

                final String sdp = Event.parseOfferEvent(offerEvent);

                localPeer.setRemoteDescription(new KinesisVideoSdpObserver(), new SessionDescription(SessionDescription.Type.OFFER, sdp));
                recipientClientId = offerEvent.getSenderClientId();
                Log.d(TAG, "Received SDP offer for client ID: " + recipientClientId + ". Creating answer");

                createSdpAnswer();

                if (master && webrtcEndpoint != null) {
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Media is being recorded to " + mStreamArn, Toast.LENGTH_LONG).show());
                    Log.i(TAG, "Media is being recorded to " + mStreamArn);
                }
            }

            @Override
            public void onSdpAnswer(final Event answerEvent) {

                Log.d(TAG, "SDP answer received from signaling");

                final String sdp = Event.parseSdpEvent(answerEvent);

                final SessionDescription sdpAnswer = new SessionDescription(SessionDescription.Type.ANSWER, sdp);

                localPeer.setRemoteDescription(new KinesisVideoSdpObserver() {
                    @Override
                    public void onCreateFailure(final String error) {
                        super.onCreateFailure(error);
                    }
                }, sdpAnswer);
                Log.d(TAG, "Answer Client ID: " + answerEvent.getSenderClientId());
                peerConnectionFoundMap.put(answerEvent.getSenderClientId(), localPeer);
                // Check if ICE candidates are available in the queue and add the candidate
                handlePendingIceCandidates(answerEvent.getSenderClientId());

            }

            @Override
            public void onIceCandidate(final Event message) {
                Log.d(TAG, "Received ICE candidate from remote");
                final IceCandidate iceCandidate = Event.parseIceCandidate(message);
                if (iceCandidate != null) {
                    checkAndAddIceCandidate(message, iceCandidate);
                } else {
                    Log.e(TAG, "Invalid ICE candidate: " + message);
                }
            }

            @Override
            public void onError(final Event errorMessage) {
                Log.e(TAG, "Received error message: " + errorMessage);
            }

            @Override
            public void onException(final Exception e) {
                Log.e(TAG, "Signaling client returned exception: " + e.getMessage());
                gotException = true;
            }
        };


        // Step 11. Create SignalingServiceWebSocketClient.
        //          This is the actual client that is used to send messages over the signaling channel.
        //          SignalingServiceWebSocketClient will attempt to open the connection in its constructor.
        if (wsHost != null) {
            try {
                client = new SignalingServiceWebSocketClient(wsHost, signalingListener, Executors.newFixedThreadPool(10));

                Log.d(TAG, "Client connection " + (client.isOpen() ? "Successful" : "Failed"));
            } catch (final Exception e) {
                Log.e(TAG, "Exception with websocket client: " + e);
                gotException = true;
                return;
            }

            if (isValidClient()) {

                Log.d(TAG, "Client connected to Signaling service " + client.isOpen());

                if (master) {

                    // If webrtc endpoint is non-null ==> Ingest media was checked
                    if (webrtcEndpoint != null) {
                        new Thread(() -> {
                            try {
                                final AWSKinesisVideoWebRTCStorageClient storageClient =
                                        new AWSKinesisVideoWebRTCStorageClient(
                                                WebRtcConstants.getCredentialsProvider().getCredentials());
                                storageClient.setRegion(Region.getRegion(mRegion));
                                storageClient.setSignerRegionOverride(mRegion);
                                storageClient.setServiceNameIntern("kinesisvideo");
                                storageClient.setEndpoint(webrtcEndpoint);

                                Log.i(TAG, "Channel ARN is: " + mChannelArn);
                                storageClient.joinStorageSession(new JoinStorageSessionRequest()
                                        .withChannelArn(mChannelArn));
                                Log.i(TAG, "Join storage session request sent!");
                            } catch (Exception ex) {
                                Log.e(TAG, "Error sending join storage session request!", ex);
                            }
                        }).start();
                    }
                } else {
                    Log.d(TAG, "Signaling service is connected: " +
                            "Sending offer as viewer to remote peer"); // Viewer

                    createSdpOffer();
                }
            } else {
                Log.e(TAG, "Error in connecting to signaling service");
                gotException = true;
            }
        }
    }

    private boolean isValidClient() {
        return client != null && client.isOpen();
    }

    /**
     * Called once the peer connection is established. Checks the pending ICE candidate queue to see
     * if we have received any before we finished sending the SDP answer. If so, add those ICE
     * candidates to the peer connection belonging to this clientId.
     *
     * @param clientId The sender client id of the peer whose peer connection was just established.
     * @see #pendingIceCandidatesMap
     */
    private void handlePendingIceCandidates(final String clientId) {
        // Add any pending ICE candidates from the queue for the client ID
        Log.d(TAG, "Pending ice candidates found? " + pendingIceCandidatesMap.get(clientId));
        final Queue<IceCandidate> pendingIceCandidatesQueueByClientId = pendingIceCandidatesMap.get(clientId);
        while (pendingIceCandidatesQueueByClientId != null && !pendingIceCandidatesQueueByClientId.isEmpty()) {
            final IceCandidate iceCandidate = pendingIceCandidatesQueueByClientId.peek();
            final PeerConnection peer = peerConnectionFoundMap.get(clientId);
            final boolean addIce = peer.addIceCandidate(iceCandidate);
            Log.d(TAG, "Added ice candidate after SDP exchange " + iceCandidate + " " + (addIce ? "Successfully" : "Failed"));
            pendingIceCandidatesQueueByClientId.remove();
        }
        // After sending pending ICE candidates, the client ID's peer connection need not be tracked
        pendingIceCandidatesMap.remove(clientId);
    }

    private void checkAndAddIceCandidate(final Event message, final IceCandidate iceCandidate) {
        // If answer/offer is not received, it means peer connection is not found. Hold the received ICE candidates in the map.
        // Once the peer connection is found, add them directly instead of adding it to the queue.

        if (!peerConnectionFoundMap.containsKey(message.getSenderClientId())) {
            Log.d(TAG, "SDP exchange is not complete. Ice candidate " + iceCandidate + " + added to pending queue");

            // If the entry for the client ID already exists (in case of subsequent ICE candidates), update the queue
            if (pendingIceCandidatesMap.containsKey(message.getSenderClientId())) {
                final Queue<IceCandidate> pendingIceCandidatesQueueByClientId = pendingIceCandidatesMap.get(message.getSenderClientId());
                pendingIceCandidatesQueueByClientId.add(iceCandidate);
                pendingIceCandidatesMap.put(message.getSenderClientId(), pendingIceCandidatesQueueByClientId);
            }

            // If the first ICE candidate before peer connection is received, add entry to map and ICE candidate to a queue
            else {
                final Queue<IceCandidate> pendingIceCandidatesQueueByClientId = new LinkedList<>();
                pendingIceCandidatesQueueByClientId.add(iceCandidate);
                pendingIceCandidatesMap.put(message.getSenderClientId(), pendingIceCandidatesQueueByClientId);
            }
        }

        // This is the case where peer connection is established and ICE candidates are received for the established
        // connection
        else {
            Log.d(TAG, "Peer connection found already");
            // Remote sent us ICE candidates, add to local peer connection
            final PeerConnection peer = peerConnectionFoundMap.get(message.getSenderClientId());
            final boolean addIce = peer.addIceCandidate(iceCandidate);

            Log.d(TAG, "Added ice candidate " + iceCandidate + " " + (addIce ? "Successfully" : "Failed"));
        }
    }

    @Override
    protected void onDestroy() {
        Thread.setDefaultUncaughtExceptionHandler(null);
        printStatsExecutor.shutdownNow();

        audioManager.setMode(originalAudioMode);
        audioManager.setSpeakerphoneOn(originalSpeakerphoneOn);

        if (rootEglBase != null) {
            rootEglBase.release();
            rootEglBase = null;
        }

        if (remoteView != null) {
            remoteView.release();
            remoteView = null;
        }

        if (localPeer != null) {
            localPeer.dispose();
            localPeer = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to stop webrtc video capture. ", e);
            }
            videoCapturer = null;
        }

        if (localView != null) {
            localView.release();
            localView = null;
        }

        if (client != null) {
            client.disconnect();
            client = null;
        }
        peerConnectionFoundMap.clear();
        pendingIceCandidatesMap.clear();

        finish();

        super.onDestroy();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Start websocket after adding local audio/video tracks
        initWsConnection();

        if (!gotException && isValidClient()) {
            Toast.makeText(this, "Signaling Connected", Toast.LENGTH_LONG).show();
        } else {
            notifySignalingConnectionFailed();
        }
    }

    private void notifySignalingConnectionFailed() {
        runOnUiThread(() -> {
            progressLoader.setVisibility(View.GONE);
            tvLoading.setText("Connection error to signaling");
        });
        finish();
        Toast.makeText(this, "Connection error to signaling", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final Intent intent = getIntent();
        mChannelArn = intent.getStringExtra(WebRtcConstants.KEY_CHANNEL_ARN);
        mStreamArn = intent.getStringExtra(WebRtcConstants.KEY_STREAM_ARN);
        mWssEndpoint = intent.getStringExtra(WebRtcConstants.KEY_WSS_ENDPOINT);
        webrtcEndpoint = intent.getStringExtra(WebRtcConstants.KEY_WEBRTC_ENDPOINT);

        mClientId = intent.getStringExtra(WebRtcConstants.KEY_CLIENT_ID);
        // If no client identifier is present, a random one will be created.
        if (Strings.isNullOrEmpty(mClientId)) {
            mClientId = UUID.randomUUID().toString();
        }
        master = intent.getBooleanExtra(WebRtcConstants.KEY_IS_MASTER, true);
        ArrayList<String> mUserNames = intent.getStringArrayListExtra(WebRtcConstants.KEY_ICE_SERVER_USER_NAME);
        ArrayList<String> mPasswords = intent.getStringArrayListExtra(WebRtcConstants.KEY_ICE_SERVER_PASSWORD);
        ArrayList<Integer> mTTLs = intent.getIntegerArrayListExtra(WebRtcConstants.KEY_ICE_SERVER_TTL);
        ArrayList<List<String>> mUrisList = (ArrayList<List<String>>) intent.getSerializableExtra(WebRtcConstants.KEY_ICE_SERVER_URI);
        mRegion = intent.getStringExtra(WebRtcConstants.KEY_REGION);
        mCameraFacingFront = intent.getBooleanExtra(WebRtcConstants.KEY_CAMERA_FRONT_FACING, true);

        rootEglBase = EglBase.create();


        //TODO: add ui to control TURN only option

        final IceServer stun = IceServer
                .builder(String.format("stun:stun.kinesisvideo.%s.amazonaws.com:443", mRegion))
                .createIceServer();

        peerIceServers.add(stun);

        if (mUrisList != null) {
            for (int i = 0; i < mUrisList.size(); i++) {
                final String turnServer = mUrisList.get(i).toString();
                if (turnServer != null) {
                    final IceServer iceServer = IceServer.builder(turnServer.replace("[", "").replace("]", ""))
                            .setUsername(mUserNames.get(i))
                            .setPassword(mPasswords.get(i))
                            .createIceServer();

                    Log.d(TAG, "IceServer details (TURN) = " + iceServer.toString());
                    peerIceServers.add(iceServer);
                }
            }
        }

        setContentView(R.layout.activity_webrtc_main);

        progressLoader = findViewById(R.id.progress_loader);
        tvLoading = findViewById(R.id.tv_loading);

        // Show the loader initially
        progressLoader.setVisibility(View.VISIBLE);
        tvLoading.setVisibility(View.VISIBLE);

        PeerConnectionFactory.initialize(PeerConnectionFactory
                .InitializationOptions
                .builder(this)
                .createInitializationOptions());

        final VideoDecoderFactory vdf = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        Log.d(TAG, "Available decoders on this device:");
        for (final VideoCodecInfo videoCodecInfo : vdf.getSupportedCodecs()) {
            Log.d(TAG, videoCodecInfo.name);
        }
        final VideoEncoderFactory vef = new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(),
                ENABLE_INTEL_VP8_ENCODER, ENABLE_H264_HIGH_PROFILE);
        Log.d(TAG, "Available encoders on this device:");
        for (final VideoCodecInfo videoCodecInfo : vef.getSupportedCodecs()) {
            Log.d(TAG, videoCodecInfo.name);
        }
        peerConnectionFactory =
                PeerConnectionFactory.builder()
                        .setVideoDecoderFactory(vdf)
                        .setVideoEncoderFactory(vef)
                        .setAudioDeviceModule(JavaAudioDeviceModule.builder(getApplicationContext())
                                .createAudioDeviceModule())
                        .createPeerConnectionFactory();

        // Enable Google WebRTC debug logs
        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);

        videoCapturer = createVideoCapturer();

        // Local video view
        localView = findViewById(R.id.local_view);
        localView.init(rootEglBase.getEglBaseContext(), null);
        localView.setEnableHardwareScaler(true);


        videoSource = peerConnectionFactory.createVideoSource(false);
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().getName(), rootEglBase.getEglBaseContext());
        videoCapturer.initialize(surfaceTextureHelper, this.getApplicationContext(), videoSource.getCapturerObserver());

        localVideoTrack = peerConnectionFactory.createVideoTrack(VideoTrackID, videoSource);
        localVideoTrack.addSink(localView);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        originalAudioMode = audioManager.getMode();
        originalSpeakerphoneOn = audioManager.isSpeakerphoneOn();

        // Start capturing video
        videoCapturer.startCapture(VIDEO_SIZE_WIDTH, VIDEO_SIZE_HEIGHT, VIDEO_FPS);
        localVideoTrack.setEnabled(true);

        remoteView = findViewById(R.id.remote_view);
        remoteView.init(rootEglBase.getEglBaseContext(), null);
    }

    private VideoCapturer createVideoCapturer() {

        final VideoCapturer videoCapturer;

        Logging.d(TAG, "Create camera");
        videoCapturer = createCameraCapturer(new Camera1Enumerator(false));

        return videoCapturer;
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {

        final String[] deviceNames = enumerator.getDeviceNames();

        Logging.d(TAG, "Enumerating cameras");

        for (String deviceName : deviceNames) {

            if (mCameraFacingFront ? enumerator.isFrontFacing(deviceName) : enumerator.isBackFacing(deviceName)) {

                Logging.d(TAG, "Camera created");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private void createLocalPeerConnection() {

        final PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(peerIceServers);

        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;

        // Step 8. Create RTCPeerConnection.
        //         The RTCPeerConnection is the primary interface for WebRTC communications in the Web.
        //         We also configure the Add Peer Connection Event Listeners here.
        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, new KinesisVideoPeerConnection() {

            @Override
            public void onIceCandidate(final IceCandidate iceCandidate) {

                super.onIceCandidate(iceCandidate);

                final Message message = createIceCandidateMessage(iceCandidate);
                Log.d(TAG, "Sending IceCandidate to remote peer " + iceCandidate);
                client.sendIceCandidate(message);  /* Send to Peer */
            }

            @Override
            public void onAddStream(final MediaStream mediaStream) {

                super.onAddStream(mediaStream);

                Log.d(TAG, "Adding remote video stream (and audio) to the view");

                addRemoteStreamToVideoView(mediaStream);
            }

            @Override
            public void onIceConnectionChange(final PeerConnection.IceConnectionState iceConnectionState) {
                super.onIceConnectionChange(iceConnectionState);
                if (iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Connection to peer failed!", Toast.LENGTH_LONG).show());
                } else if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Connected to peer!", Toast.LENGTH_LONG).show());
                    runOnUiThread(() -> {
                        progressLoader.setVisibility(View.GONE);
                    });
                }
            }

            @Override
            public void onDataChannel(final DataChannel dataChannel) {
                super.onDataChannel(dataChannel);

                dataChannel.registerObserver(new DataChannel.Observer() {
                    @Override
                    public void onBufferedAmountChange(long l) {
                        // no op on receiver side
                    }

                    @Override
                    public void onStateChange() {
                        Log.d(TAG, "Remote Data Channel onStateChange: state: " + dataChannel.state().toString());
                    }

                    @SuppressLint("MissingPermission")
                    @Override
                    public void onMessage(DataChannel.Buffer buffer) {
                        runOnUiThread(() -> {
                            final byte[] bytes;
                            if (buffer.data.hasArray()) {
                                bytes = buffer.data.array();
                            } else {
                                bytes = new byte[buffer.data.remaining()];
                                buffer.data.get(bytes);
                            }
                            Toast.makeText(getApplicationContext(), "New message from peer, check notification.", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        });

        String frameWidth = "1920";
        String frameHeight = "1080";
        String framesPerSecond = "0";

        if (localPeer != null) {
            printStatsExecutor.scheduleWithFixedDelay(() -> {
                localPeer.getStats(rtcStatsReport -> {
                    final Map<String, RTCStats> statsMap = rtcStatsReport.getStatsMap();
                    for (final Map.Entry<String, RTCStats> entry : statsMap.entrySet()) {

                        if (entry.getValue().getType().equals("inbound-rtp")) {

                            Map<String, Object> objectMap = entry.getValue().getMembers();

                            for (Map.Entry<String, Object> object : objectMap.entrySet()) {
                                Log.d(TAG, "object: Key : " + object.getKey());
                                Log.d(TAG, "object: Value : " + object.getValue());
                            }

//                            Log.i(TAG, "fps: " + objectMap.get("framesPerSecond"));
                            Object tmp = objectMap.get("framesPerSecond");
                            Object width = objectMap.get("frameWidth");
                            Object height = objectMap.get("frameHeight");

                            if (tmp != null) {
                                final String fps = tmp.toString();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        TextView my_view = findViewById(R.id.tv_fps);
                                        String to_display = "fps: " + fps;
                                        my_view.setText(to_display);

                                        TextView resolution = findViewById(R.id.tv_resolution);
                                        String resolutionStr = width + " x " + height;
                                        resolution.setText(resolutionStr);
                                    }
                                });

                            }
                        }
                    }
                });
            }, 0, 1, TimeUnit.SECONDS);
        }

        addDataChannelToLocalPeer();
        addStreamToLocalPeer();
    }

    private Message createIceCandidateMessage(final IceCandidate iceCandidate) {
        final String sdpMid = iceCandidate.sdpMid;
        final int sdpMLineIndex = iceCandidate.sdpMLineIndex;
        final String sdp = iceCandidate.sdp;

        final String messagePayload =
                "{\"candidate\":\""
                        + sdp
                        + "\",\"sdpMid\":\""
                        + sdpMid
                        + "\",\"sdpMLineIndex\":"
                        + sdpMLineIndex
                        + "}";

        final String senderClientId = (master) ? "" : mClientId;

        return new Message("ICE_CANDIDATE", recipientClientId, senderClientId,
                new String(Base64.encode(messagePayload.getBytes(),
                        Base64.URL_SAFE | Base64.NO_WRAP)));
    }

    private void addStreamToLocalPeer() {

        final MediaStream stream = peerConnectionFactory.createLocalMediaStream(LOCAL_MEDIA_STREAM_LABEL);

        if (!stream.addTrack(localVideoTrack)) {
            Log.e(TAG, "Add video track failed");
        }

        localPeer.addTrack(stream.videoTracks.get(0), Collections.singletonList(stream.getId()));
    }

    private void addDataChannelToLocalPeer() {
        Log.d(TAG, "Data channel addDataChannelToLocalPeer");
        final DataChannel localDataChannel = localPeer.createDataChannel("data-channel-of-" + mClientId, new DataChannel.Init());
        localDataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {
                Log.d(TAG, "Local Data Channel onBufferedAmountChange called with amount " + l);
            }

            @Override
            public void onStateChange() {
                Log.d(TAG, "Local Data Channel onStateChange: state: " + localDataChannel.state().toString());

                if (sendDataChannelButton != null) {
                    runOnUiThread(() -> {
                        if (localDataChannel.state() == DataChannel.State.OPEN) {
                            sendDataChannelButton.setEnabled(true);
                        } else {
                            sendDataChannelButton.setEnabled(false);
                        }
                    });
                }
            }

            @Override
            public void onMessage(final DataChannel.Buffer buffer) {
                // Send out data, no op on sender side
            }
        });
    }

    // when mobile sdk is viewer
    private void createSdpOffer() {

        MediaConstraints sdpMediaConstraints = new MediaConstraints();

        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
//        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));

        if (localPeer == null) {

            createLocalPeerConnection();
        }

        localPeer.createOffer(new KinesisVideoSdpObserver() {

            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

                super.onCreateSuccess(sessionDescription);

                localPeer.setLocalDescription(new KinesisVideoSdpObserver(), sessionDescription);

                final Message sdpOfferMessage = Message.createOfferMessage(sessionDescription, mClientId);

                if (isValidClient()) {
                    client.sendSdpOffer(sdpOfferMessage);
                } else {
                    notifySignalingConnectionFailed();
                }
            }
        }, sdpMediaConstraints);
    }


    // when local is set to be the master
    private void createSdpAnswer() {

        final MediaConstraints sdpMediaConstraints = new MediaConstraints();

        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
//        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));

        localPeer.createAnswer(new KinesisVideoSdpObserver() {

            @Override
            public void onCreateSuccess(final SessionDescription sessionDescription) {
                Log.d(TAG, "Creating answer: success");
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new KinesisVideoSdpObserver(), sessionDescription);
                final Message answer = Message.createAnswerMessage(sessionDescription, master, recipientClientId);
                client.sendSdpAnswer(answer);

                peerConnectionFoundMap.put(recipientClientId, localPeer);
                handlePendingIceCandidates(recipientClientId);
            }

            @Override
            public void onCreateFailure(final String error) {
                super.onCreateFailure(error);

                // Device is unable to support the requested media format
                if (error.contains("ERROR_CONTENT")) {
                    Log.e(TAG, "No supported codec is present in the offer!");
                }
                gotException = true;
            }
        }, sdpMediaConstraints);
    }

    private void addRemoteStreamToVideoView(MediaStream stream) {

        final VideoTrack remoteVideoTrack = stream.videoTracks != null && stream.videoTracks.size() > 0 ? stream.videoTracks.get(0) : null;

        AudioTrack remoteAudioTrack = stream.audioTracks != null && stream.audioTracks.size() > 0 ? stream.audioTracks.get(0) : null;

        if (remoteAudioTrack != null) {
            remoteAudioTrack.setEnabled(true);
            Log.d(TAG, "remoteAudioTrack received: State=" + remoteAudioTrack.state().name());
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);
        }

        if (remoteVideoTrack != null) {
            runOnUiThread(() -> {
                try {
                    Log.d(TAG, "remoteVideoTrackId=" + remoteVideoTrack.id() + " videoTrackState=" + remoteVideoTrack.state());
                    resizeLocalView();
                    remoteVideoTrack.addSink(remoteView);
                    resizeRemoteView();
                } catch (Exception e) {
                    Log.e(TAG, "Error in setting remote video view" + e);
                }
            });
        } else {
            Log.e(TAG, "Error in setting remote track");
        }

    }

    /**
     * Constructs and returns signed URL for the specified endpoint.
     *
     * @param endpoint The websocket endpoint (master or viewer endpoint)
     * @return A signed URL. {@code null} if there was an issue fetching credentials.
     */
    private URI getSignedUri(final String endpoint) {
        final String accessKey = mCreds.getAWSAccessKeyId();
        final String secretKey = mCreds.getAWSSecretKey();
        final String sessionToken = Optional.of(mCreds)
                .filter(creds -> creds instanceof AWSSessionCredentials)
                .map(awsCredentials -> (AWSSessionCredentials) awsCredentials)
                .map(AWSSessionCredentials::getSessionToken)
                .orElse("");

        if (accessKey.isEmpty() || secretKey.isEmpty()) {
            Toast.makeText(this, "Failed to fetch credentials!", Toast.LENGTH_LONG).show();
            return null;
        }

        return AwsV4Signer.sign(
                URI.create(endpoint),
                accessKey,
                secretKey,
                sessionToken,
                URI.create(mWssEndpoint),
                mRegion,
                new Date().getTime());
    }

    @SuppressLint("ClickableViewAccessibility")
    private void resizeLocalView() {
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        final ViewGroup.LayoutParams lp = localView.getLayoutParams();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        lp.height = (int) (displayMetrics.heightPixels * 0.25);
        lp.width = (int) (displayMetrics.widthPixels * 0.25);
        localView.setLayoutParams(lp);
        localView.setOnTouchListener(new View.OnTouchListener() {
            private final int mMarginRight = displayMetrics.widthPixels;
            private final int mMarginBottom = displayMetrics.heightPixels;
            private int deltaOfDownXAndMargin, deltaOfDownYAndMargin;

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                final int X = (int) motionEvent.getRawX();
                final int Y = (int) motionEvent.getRawY();
                switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        FrameLayout.LayoutParams lParams = (FrameLayout.LayoutParams) lp;

                        deltaOfDownXAndMargin = X + lParams.rightMargin;
                        deltaOfDownYAndMargin = Y + lParams.bottomMargin;

                        return true;
                    case MotionEvent.ACTION_MOVE:
                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) lp;

                        layoutParams.rightMargin = deltaOfDownXAndMargin - X;
                        layoutParams.bottomMargin = deltaOfDownYAndMargin - Y;

                        // shouldn't be out of screen
                        if (layoutParams.rightMargin >= mMarginRight - lp.width) {
                            layoutParams.rightMargin = mMarginRight - lp.width;
                        }

                        if (layoutParams.bottomMargin >= mMarginBottom - lp.height) {
                            layoutParams.bottomMargin = mMarginBottom - lp.height;
                        }

                        if (layoutParams.rightMargin <= 0) {
                            layoutParams.rightMargin = 0;
                        }

                        if (layoutParams.bottomMargin <= 0) {
                            layoutParams.bottomMargin = 0;
                        }

                        localView.setLayoutParams(layoutParams);
                }
                return false;
            }
        });
    }

    private void resizeRemoteView() {
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        final ViewGroup.LayoutParams lp = remoteView.getLayoutParams();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        double fhd_ratio = 1920.0 / 1080; // 1.8
        double display_ratio = (double) displayMetrics.widthPixels / displayMetrics.heightPixels;
        double scale = 1.0;

        if (fhd_ratio > display_ratio) {
            scale = fhd_ratio / display_ratio;
            lp.height = (int) (displayMetrics.heightPixels * .9 / scale);
            lp.width = (int) (displayMetrics.widthPixels * .9);
        } else {
            scale = display_ratio / fhd_ratio;
            lp.height = (int) (displayMetrics.heightPixels * .9);
            lp.width = (int) (displayMetrics.widthPixels * .9 / scale);
        }
        Log.i(TAG, "final, [wd: " + lp.width + ", ht: " + lp.height + "]");
        Log.i(TAG, "display, [wd: " + displayMetrics.widthPixels + ", ht: " + displayMetrics.heightPixels + "]");
        remoteView.setLayoutParams(lp);
        localView.bringToFront();
    }
}
