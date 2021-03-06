package com.example.webrtctest;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.EglBase10;
import org.webrtc.EglRenderer;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class JoinActivity extends AppCompatActivity implements SignalingClient.Callback {
    MediaConstraints audioConstraints;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    SurfaceViewRenderer remoteView;
    String roomName;
    private String TAG = "JOIN_ACTIVITY";
    EglBase.Context eglBaseContext;
    PeerConnectionFactory peerConnectionFactory;
    PeerConnection peerConnection;
    MediaStream mediaStream;
    List<PeerConnection.IceServer> iceServers;
    VideoTrack remoteVideoTrack;

    HashMap<String, PeerConnection> peerConnectionMap;
    SurfaceViewRenderer[] remoteViews;
    SurfaceViewRenderer localView;

    DefaultVideoEncoderFactory defaultVideoEncoderFactory;
    DefaultVideoDecoderFactory defaultVideoDecoderFactory;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join);

        Intent getintent = getIntent();
        roomName = getintent.getStringExtra("roomName");

        peerConnectionMap = new HashMap<>();
        iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());


        eglBaseContext = EglBase.create().getEglBaseContext();
        // create PeerConnectionFactory
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(this)
                .createInitializationOptions());

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();

         defaultVideoEncoderFactory =
                new DefaultVideoEncoderFactory(eglBaseContext, true, true);

         defaultVideoDecoderFactory =
                new DefaultVideoDecoderFactory(eglBaseContext);


        //????????? ????????? ??????????????? .
        AudioDeviceModule audioDeviceModule = JavaAudioDeviceModule.builder(getApplicationContext())
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .createAudioDeviceModule();

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();


        //????????? ?????? ????????? ??????
//        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext);
//        VideoCapturer videoCapturer = createCameraCapturer(true);
//        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
//        videoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
//        videoCapturer.startCapture(480, 640, 30);
//        VideoTrack videoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);


        //????????? ?????? ????????? ??????
        audioConstraints = new MediaConstraints();
        //        audioConstraints.mandatory.add(
//                new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
//        audioConstraints.mandatory.add(
//                new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
//        audioConstraints.mandatory.add(
//                new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
//        audioConstraints.mandatory.add(
//                new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"));
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);
        localAudioTrack.setVolume(10);


        mediaStream = peerConnectionFactory.createLocalMediaStream("mediaStream");
        //????????? ???????????? ??????????????? ??????
//        mediaStream.addTrack(videoTrack);
//        //????????? ???????????? ????????? ????????? ??????
//        mediaStream.addTrack(localAudioTrack);

        remoteView = findViewById(R.id.remoteView);
        remoteView.setMirror(true);
        remoteView.init(eglBaseContext, null);

        SignalingClient.get().init(this, roomName);
    }

    private synchronized PeerConnection getOrCreatePeerConnection(String socketId) {

        Log.d(TAG, "getOrCreatePeerConnection");
        peerConnection = peerConnectionMap.get(socketId);
        if (peerConnection != null) {
            return peerConnection;
        }
        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, new PeerConnectionAdapter("PC :" + socketId) {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                SignalingClient.get().sendIceCandidate(iceCandidate, socketId);
            }

//            @Override
//            public void onRemoveStream(MediaStream mediaStream) {
//                super.onRemoveStream(mediaStream);
//                System.out.println("????????? ?????? ");
//            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);

                remoteVideoTrack = mediaStream.videoTracks.get(0);
                Log.d("onAddStreamRemote", "" + mediaStream.videoTracks.get(0).toString());
                Log.d("onAddStreamRemote", "" + remoteVideoTrack);
                runOnUiThread(() -> {
                    remoteVideoTrack.addSink(remoteView);
                });
            }
        });
        peerConnection.addStream(mediaStream);
        peerConnectionMap.put(socketId, peerConnection);
        return peerConnection;
    }


    @Override
    public void onCreateRoom() {
        Log.d(TAG, "onCreateRoom");
    }

    @Override
    public void onPeerJoined(String socketId) {
        Log.d(TAG, "onPeerJoined");
        PeerConnection peerConnection = getOrCreatePeerConnection(socketId);
        peerConnection.createOffer(new SdpAdapter("createOfferSdp:" + socketId) {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                peerConnection.setLocalDescription(new SdpAdapter("setLocalSdp:" + socketId), sessionDescription);
                SignalingClient.get().sendSessionDescription(sessionDescription, socketId);
            }
        }, new MediaConstraints());
    }

    @Override
    public void onSelfJoined() {
        Log.d(TAG, "onSelfJoined");
    }

    @Override
    public void onPeerLeave(String msg) {
        System.out.println("?????? ?????? : " + msg);


    }

    @Override
    public void onOfferReceived(JSONObject data) {

        Log.d(TAG, "onOfferReceived" + data.toString());
        runOnUiThread(() -> {
            final String socketId = data.optString("from");
            PeerConnection peerConnection = getOrCreatePeerConnection(socketId);
            peerConnection.setRemoteDescription(new SdpAdapter("setRemoteSdp:" + socketId),
                    new SessionDescription(SessionDescription.Type.OFFER, data.optString("sdp")));
            peerConnection.createAnswer(new SdpAdapter("localAnswerSdp") {
                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                    super.onCreateSuccess(sdp);
                    peerConnectionMap.get(socketId).setLocalDescription(new SdpAdapter("setLocalSdp:" + socketId), sdp);
                    SignalingClient.get().sendSessionDescription(sdp, socketId);
                }
            }, new MediaConstraints());

        });
    }

    @Override
    public void onAnswerReceived(JSONObject data) {

        Log.d(TAG, "onAnswerReceived" + data.toString());
        String socketId = data.optString("from");
        PeerConnection peerConnection = getOrCreatePeerConnection(socketId);
        peerConnection.setRemoteDescription(new SdpAdapter("setRemoteSdp:" + socketId),
                new SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp")));
    }

    @Override
    public void onIceCandidateReceived(JSONObject data) {
        Log.d(TAG, "onIceCandidateReceived" + data.toString());
        String socketId = data.optString("from");
        PeerConnection peerConnection = getOrCreatePeerConnection(socketId);
        peerConnection.addIceCandidate(new IceCandidate(
                data.optString("id"),
                data.optInt("label"),
                data.optString("candidate")
        ));
    }

    @Override
    protected void onDestroy() { //??? ?????? ????????? ?????????
        Log.d(TAG, "onDestroy");
        super.onDestroy();

        SignalingClient.get().destroy(); // ???????????? ?????? ????????? ??????
        peerConnection.dispose(); //peer ?????? ??????
//        defaultVideoDecoderFactory =null ;
//        defaultVideoEncoderFactory = null ;
        remoteView.release(); /// ??? ?????? eglRender ??? ???????????? ????????? release ????????? .
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        System.out.println("???????????? ?????? ?????? ");
//        PackageManager packageManager = getApplicationContext().getPackageManager();
//        Intent intent = packageManager.getLaunchIntentForPackage(getApplicationContext().getPackageName());
//        getApplicationContext().startActivity(intent);
//        Runtime.getRuntime().exit(0);
    }


}