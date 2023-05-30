package com.ihsan.android_webrtc.client

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.ihsan.android_webrtc.MainActivity
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class RTCClient(
    context: Context,
    observer: PeerConnection.Observer
) {
    companion object {
        private const val LOCAL_TRACK_ID = "local_track"
    }

    private val rootEglBase: EglBase = EglBase.create()
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    val TAG = "RTCClient"

    var remoteSessionDescription: SessionDescription? = null

    val db = Firebase.firestore

    init {
        initPeerConnectionFactory(context)
    }

    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302")
            .createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302")
            .createIceServer()
    )

    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val videoCapturer by lazy { getVideoCapturer(context) }

    private val audioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val peerConnection by lazy { buildPeerConnection(observer) }

    private fun initPeerConnectionFactory(context: Context) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory
            .builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    rootEglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true
                disableNetworkMonitor = true
            })
            .createPeerConnectionFactory()
    }

    private fun buildPeerConnection(observer: PeerConnection.Observer) =
        peerConnectionFactory.createPeerConnection(
            iceServer,
            observer
        )

    private fun getVideoCapturer(context: Context) =
        Camera2Enumerator(context).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw IllegalStateException()
        }

    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(true)
        setEnableHardwareScaler(true)
        init(rootEglBase.eglBaseContext, null)
    }

    fun startLocalVideoCapture(localVideoOutput: SurfaceViewRenderer) {
        try {
            val surfaceTextureHelper =
                SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)

            try {
                videoCapturer.initialize(
                    surfaceTextureHelper,
                    localVideoOutput.context,
                    localVideoSource.capturerObserver
                )
                videoCapturer.startCapture(320, 240, 30)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize VideoCapturer: $e")
            }

            localAudioTrack =
                peerConnectionFactory.createAudioTrack(LOCAL_TRACK_ID + "_audio", audioSource)
            localVideoTrack =
                peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)
            localVideoTrack?.addSink(localVideoOutput)
            /*val localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)
            Log.d(TAG, "startLocalVideoCapture: $peerConnection")
            peerConnection?.addStream(localStream)*/

            peerConnection?.addTrack(localAudioTrack)
            peerConnection?.addTrack(localVideoTrack)
        } catch (e: Exception) {
            Toast.makeText(MainActivity(), "$e", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "startLocalVideoCapture: $e")
        }
    }

    private fun PeerConnection.call(sdpObserver: SdpObserver, meetingID: String) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        createOffer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                        Log.e(TAG, "onSetFailure: $p0")
                    }

                    override fun onSetSuccess() {
                        val offer = hashMapOf(
                            "sdp" to desc?.description,
                            "type" to desc?.type
                        )
                        db.collection("calls").document(meetingID)
                            .set(offer)
                            .addOnSuccessListener {
                                Log.e(TAG, "DocumentSnapshot added")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Error adding document", e)
                            }
                        Log.e(TAG, "onSetSuccess")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                        Log.e(TAG, "onCreateSuccess: Description $p0")
                    }

                    override fun onCreateFailure(p0: String?) {
                        Log.e(TAG, "onCreateFailure: $p0")
                    }
                }, desc)
                sdpObserver.onCreateSuccess(desc)
            }

            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "onSetFailure: $p0")
            }

            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "onCreateFailure: $p0")
            }
        }, constraints)
    }

    private fun PeerConnection.answer(sdpObserver: SdpObserver, meetingID: String) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        createAnswer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                val answer = hashMapOf(
                    "sdp" to desc?.description,
                    "type" to desc?.type
                )
                db.collection("calls").document(meetingID)
                    .set(answer)
                    .addOnSuccessListener {
                        Log.e(TAG, "DocumentSnapshot added")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error adding document", e)
                    }
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                        Log.e(TAG, "onSetFailure: $p0")
                    }

                    override fun onSetSuccess() {
                        Log.e(TAG, "onSetSuccess")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                        Log.e(TAG, "onCreateSuccess: Description $p0")
                    }

                    override fun onCreateFailure(p0: String?) {
                        Log.e(TAG, "onCreateFailureLocal: $p0")
                    }
                }, desc)
                sdpObserver.onCreateSuccess(desc)
            }

            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "onCreateFailureRemote: $p0")
            }
        }, constraints)
    }

    fun call(sdpObserver: SdpObserver, meetingID: String) =
        peerConnection?.call(sdpObserver, meetingID)

    fun answer(sdpObserver: SdpObserver, meetingID: String) =
        peerConnection?.answer(sdpObserver, meetingID)

    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        remoteSessionDescription = sessionDescription
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "onSetFailure: $p0")
            }

            override fun onSetSuccess() {
                Log.e(TAG, "onSetSuccessRemoteSession")
            }

            override fun onCreateSuccess(p0: SessionDescription?) {
                Log.e(TAG, "onCreateSuccessRemoteSession: Description $p0")
            }

            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "onCreateFailure")
            }
        }, sessionDescription)

    }

    fun addIceCandidate(iceCandidate: IceCandidate?) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun endCall(meetingID: String) {
        db.collection("calls").document(meetingID).collection("candidates")
            .get().addOnSuccessListener {
                val iceCandidateArray: MutableList<IceCandidate> = mutableListOf()
                for (dataSnapshot in it) {
                    if (dataSnapshot.contains("type") && dataSnapshot["type"] == "offerCandidate") {
                        iceCandidateArray.add(
                            IceCandidate(
                                dataSnapshot["sdpMid"].toString(), Math.toIntExact(
                                    dataSnapshot["sdpMLineIndex"] as Long
                                ), dataSnapshot["sdp"].toString()
                            )
                        )
                    } else if (dataSnapshot.contains("type") && dataSnapshot["type"] == "answerCandidate") {
                        iceCandidateArray.add(
                            IceCandidate(
                                dataSnapshot["sdpMid"].toString(), Math.toIntExact(
                                    dataSnapshot["sdpMLineIndex"] as Long
                                ), dataSnapshot["sdp"].toString()
                            )
                        )
                    }
                }
                peerConnection?.removeIceCandidates(iceCandidateArray.toTypedArray())
            }
        val endCall = hashMapOf(
            "type" to "END_CALL"
        )
        db.collection("calls").document(meetingID)
            .set(endCall)
            .addOnSuccessListener {
                Log.e(TAG, "DocumentSnapshot added")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error adding document", e)
            }

        peerConnection?.close()
    }

    fun enableVideo(videoEnabled: Boolean) {
        if (localVideoTrack != null)
            localVideoTrack?.setEnabled(videoEnabled)
    }

    fun enableAudio(audioEnabled: Boolean) {
        if (localAudioTrack != null)
            localAudioTrack?.setEnabled(audioEnabled)
    }

    fun switchCamera() {
        videoCapturer.switchCamera(null)
    }
}