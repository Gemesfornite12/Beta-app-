package com.example.data.webrtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.model.CallSession
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.CopyOnWriteArrayList

/** Peer-to-peer media client for the isolated .test call namespace. */
class WebRtcCallClient(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    @Volatile private var eglBase: EglBase? = null
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()
    private val _connectionState = MutableStateFlow("Preparando llamada")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var callReference: DatabaseReference? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var currentRole: String = "caller"
    private var remoteDescriptionSet = false
    private var speakerEnabled = true
    private var originalAudioMode = AudioManager.MODE_NORMAL
    private var originalSpeakerphone = false
    private var audioRoutingActive = false
    private var offerAnswerListener: ValueEventListener? = null
    private var remoteCandidatesListener: ChildEventListener? = null
    private val pendingCandidates = CopyOnWriteArrayList<IceCandidate>()
    private var currentCall: CallSession? = null

    val eglContext: EglBase.Context get() = ensureEglBase().eglBaseContext

    @Synchronized
    private fun ensureEglBase(): EglBase {
        return eglBase ?: EglBase.create().also { eglBase = it }
    }

    @Synchronized
    fun startOutgoingCall(call: CallSession) {
        if (!isTestBuild()) return fail("Las llamadas reales están habilitadas solo en la versión de prueba.")
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) return fail("Falta permiso para usar el micrófono.")
        if (call.isVideo && !hasPermission(Manifest.permission.CAMERA)) return fail("Falta permiso para usar la cámara.")
        if (call.groupName != null || !call.channelId.startsWith("direct")) return fail("Esta prueba admite llamadas individuales; las grupales vienen después.")
        try {
            resetConnection()
            currentCall = call
            currentRole = "caller"
            callReference = FirebaseDatabase.getInstance(DATABASE_URL).reference.child("calls_test").child(call.callId)
            prepareMedia(call.isVideo)
            createPeerConnection(call)
            listenForRemoteCandidates("callee")
            createOffer()
            _connectionState.value = "Llamando a ${call.peerName}"
        } catch (error: Exception) {
            Log.e(TAG, "No se pudo iniciar la llamada WebRTC", error)
            resetConnection()
            fail("No se pudo abrir el micrófono o la cámara: ${error.localizedMessage ?: "revisa los permisos"}")
        }
    }

    @Synchronized
    fun answerIncomingCall(call: CallSession) {
        if (!isTestBuild()) return fail("Las llamadas reales están habilitadas solo en la versión de prueba.")
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) return fail("Falta permiso para usar el micrófono.")
        if (call.isVideo && !hasPermission(Manifest.permission.CAMERA)) return fail("Falta permiso para usar la cámara.")
        if (call.groupName != null || !call.channelId.startsWith("direct")) return fail("Esta prueba admite llamadas individuales; las grupales vienen después.")
        try {
            resetConnection()
            currentCall = call
            currentRole = "callee"
            callReference = FirebaseDatabase.getInstance(DATABASE_URL).reference.child("calls_test").child(call.callId)
            prepareMedia(call.isVideo)
            createPeerConnection(call)
            listenForRemoteCandidates("caller")
            listenForOffer()
            _connectionState.value = "Conectando con ${call.callerName.ifBlank { call.peerName }}"
        } catch (error: Exception) {
            Log.e(TAG, "No se pudo responder la llamada WebRTC", error)
            resetConnection()
            fail("No se pudo abrir el micrófono o la cámara: ${error.localizedMessage ?: "revisa los permisos"}")
        }
    }

    fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        _localVideoTrack.value?.setEnabled(enabled)
    }

    fun setSpeakerphone(enabled: Boolean) {
        speakerEnabled = enabled
        applyAudioRoute(enabled)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontFacing: Boolean) = Unit
            override fun onCameraSwitchError(errorDescription: String) {
                fail("No se pudo cambiar de cámara: $errorDescription")
            }
        })
    }

    @Synchronized
    fun endCall() {
        resetConnection()
        _connectionState.value = "Llamada finalizada"
    }

    @Synchronized
    fun release() {
        resetConnection()
        factory?.dispose()
        factory = null
        eglBase?.release()
        eglBase = null
    }

    private fun isTestBuild() = BuildConfig.APPLICATION_ID.endsWith(".test")

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun prepareMedia(withVideo: Boolean) {
        originalAudioMode = audioManager.mode
        @Suppress("DEPRECATION")
        run { originalSpeakerphone = audioManager.isSpeakerphoneOn }
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioRoutingActive = true
        applyAudioRoute(speakerEnabled)

        ensureFactory()
        val mediaFactory = factory ?: error("WebRTC no se inicializó")
        val createdAudioSource = mediaFactory.createAudioSource(MediaConstraints())
        audioSource = createdAudioSource
        audioTrack = mediaFactory.createAudioTrack("omni_audio", createdAudioSource).apply { setEnabled(true) }

        if (withVideo) {
            val enumerator = Camera2Enumerator(appContext)
            val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
                ?: error("No se encontró una cámara disponible")
            val capturer = enumerator.createCapturer(cameraName, null) as? CameraVideoCapturer
                ?: error("No se pudo iniciar la cámara")
            videoCapturer = capturer
            val createdVideoSource = mediaFactory.createVideoSource(false)
            videoSource = createdVideoSource
            val textureHelper = SurfaceTextureHelper.create("OmniCallCamera", ensureEglBase().eglBaseContext)
            surfaceTextureHelper = textureHelper
            capturer.initialize(textureHelper, appContext, createdVideoSource.capturerObserver)
            capturer.startCapture(640, 480, 24)
            _localVideoTrack.value = mediaFactory.createVideoTrack("omni_video", createdVideoSource).apply { setEnabled(true) }
        }
    }

    @Synchronized
    private fun ensureFactory() {
        if (factory != null) return
        synchronized(FACTORY_LOCK) {
            if (!factoryInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()
                )
                factoryInitialized = true
            }
            factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(ensureEglBase().eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(ensureEglBase().eglBaseContext))
                .createPeerConnectionFactory()
        }
    }

    private fun createPeerConnection(call: CallSession) {
        val mediaFactory = factory ?: error("Fábrica WebRTC no disponible")
        val iceServers = listOf(
            PeerConnection.IceServer.builder(STUN_SERVER).createIceServer()
        )
        val configuration = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val connection = mediaFactory.createPeerConnection(configuration, peerObserver())
            ?: error("No se pudo crear la conexión entre dispositivos")
        peerConnection = connection
        audioTrack?.let { connection.addTrack(it, listOf(call.callId)) }
        _localVideoTrack.value?.let { connection.addTrack(it, listOf(call.callId)) }
    }

    private fun peerObserver() = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> _connectionState.value = if (currentCall?.isVideo == true) "Audio y video conectados" else "Audio conectado"
                PeerConnection.IceConnectionState.FAILED -> fail("No se pudo conectar entre estas redes. La prueba usa conexión directa.")
                PeerConnection.IceConnectionState.DISCONNECTED -> _connectionState.value = "Reconectando…"
                PeerConnection.IceConnectionState.CHECKING -> _connectionState.value = "Conectando…"
                else -> Unit
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidate(candidate: IceCandidate) {
            val candidatesPath = if (currentRole == "caller") "caller" else "callee"
            callReference?.child("rtc")?.child("candidates")?.child(candidatesPath)?.push()?.setValue(
                mapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "candidate" to candidate.sdp
                )
            )?.addOnFailureListener { Log.w(TAG, "No se pudo enviar candidato ICE", it) }
        }
        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
        override fun onAddStream(stream: org.webrtc.MediaStream) {
            stream.videoTracks.firstOrNull()?.let { _remoteVideoTrack.value = it }
        }
        override fun onRemoveStream(stream: org.webrtc.MediaStream) {
            _remoteVideoTrack.value = null
        }
        override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<org.webrtc.MediaStream>) {
            (receiver.track() as? VideoTrack)?.let { _remoteVideoTrack.value = it }
        }
        override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {
            (transceiver.receiver.track() as? VideoTrack)?.let { _remoteVideoTrack.value = it }
        }
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            if (newState == PeerConnection.PeerConnectionState.CONNECTED) _connectionState.value = if (currentCall?.isVideo == true) "Audio y video conectados" else "Audio conectado"
            if (newState == PeerConnection.PeerConnectionState.FAILED) fail("Falló la conexión de medios.")
        }
    }

    private fun createOffer() {
        val connection = peerConnection ?: return fail("No hay conexión para llamar")
        connection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                connection.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        callReference?.child("rtc")?.child("offer")?.setValue(descriptionMap(description))
                            ?.addOnSuccessListener { listenForAnswer() }
                            ?.addOnFailureListener { fail("No se pudo iniciar la conexión de llamada.") }
                    }
                    override fun onSetFailure(error: String) = fail("No se pudo preparar la llamada: $error")
                    override fun onCreateSuccess(description: SessionDescription) = Unit
                    override fun onCreateFailure(error: String) = Unit
                }, description)
            }
            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String) = fail("No se pudo iniciar la llamada: $error")
            override fun onSetFailure(error: String) = Unit
        }, MediaConstraints())
    }

    private fun listenForOffer() = listenForDescription("offer") { description ->
        val connection = peerConnection ?: return@listenForDescription
        connection.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushPendingCandidates()
                createAnswer()
            }
            override fun onSetFailure(error: String) = fail("No se pudo recibir la llamada: $error")
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onCreateFailure(error: String) = Unit
        }, description)
    }

    private fun createAnswer() {
        val connection = peerConnection ?: return fail("No hay conexión para responder")
        connection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                connection.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        callReference?.child("rtc")?.child("answer")?.setValue(descriptionMap(description))
                            ?.addOnFailureListener { fail("No se pudo responder la llamada.") }
                    }
                    override fun onSetFailure(error: String) = fail("No se pudo preparar la respuesta: $error")
                    override fun onCreateSuccess(description: SessionDescription) = Unit
                    override fun onCreateFailure(error: String) = Unit
                }, description)
            }
            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String) = fail("No se pudo responder la llamada: $error")
            override fun onSetFailure(error: String) = Unit
        }, MediaConstraints())
    }

    private fun listenForAnswer() = listenForDescription("answer") { description ->
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushPendingCandidates()
            }
            override fun onSetFailure(error: String) = fail("No se pudo completar la llamada: $error")
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onCreateFailure(error: String) = Unit
        }, description)
    }

    private fun listenForDescription(field: String, onDescription: (SessionDescription) -> Unit) {
        val ref = callReference?.child("rtc")?.child(field) ?: return fail("No hay señal de llamada")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val type = snapshot.child("type").getValue(String::class.java) ?: return
                val sdp = snapshot.child("sdp").getValue(String::class.java) ?: return
                ref.removeEventListener(this)
                if (offerAnswerListener === this) offerAnswerListener = null
                onDescription(SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp))
            }
            override fun onCancelled(error: DatabaseError) = fail("Se perdió la señalización de llamada: ${error.message}")
        }
        offerAnswerListener = listener
        ref.addValueEventListener(listener)
    }

    private fun listenForRemoteCandidates(remoteRole: String) {
        val ref = callReference?.child("rtc")?.child("candidates")?.child(remoteRole) ?: return
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val sdpMid = snapshot.child("sdpMid").getValue(String::class.java)
                val index = snapshot.child("sdpMLineIndex").getValue(Int::class.java) ?: return
                val candidate = snapshot.child("candidate").getValue(String::class.java) ?: return
                val ice = IceCandidate(sdpMid, index, candidate)
                if (remoteDescriptionSet) peerConnection?.addIceCandidate(ice) else pendingCandidates.add(ice)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) { fail("Se perdió la señalización ICE: ${error.message}") }
        }
        remoteCandidatesListener = listener
        ref.addChildEventListener(listener)
    }

    private fun flushPendingCandidates() {
        val connection = peerConnection ?: return
        pendingCandidates.forEach { connection.addIceCandidate(it) }
        pendingCandidates.clear()
    }

    private fun descriptionMap(description: SessionDescription) = mapOf(
        "type" to description.type.canonicalForm(),
        "sdp" to description.description
    )

    private fun applyAudioRoute(useSpeaker: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wantedType = if (useSpeaker) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == wantedType }
            if (device != null) audioManager.setCommunicationDevice(device)
        } else {
            @Suppress("DEPRECATION")
            run { audioManager.isSpeakerphoneOn = useSpeaker }
        }
    }

    @Synchronized
    private fun resetConnection() {
        offerAnswerListener?.let { listener ->
            callReference?.child("rtc")?.child("offer")?.removeEventListener(listener)
            callReference?.child("rtc")?.child("answer")?.removeEventListener(listener)
        }
        offerAnswerListener = null
        remoteCandidatesListener?.let { listener ->
            val remoteRole = if (currentRole == "caller") "callee" else "caller"
            callReference?.child("rtc")?.child("candidates")?.child(remoteRole)?.removeEventListener(listener)
        }
        remoteCandidatesListener = null
        pendingCandidates.clear()
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        try { videoCapturer?.stopCapture() } catch (error: InterruptedException) { Thread.currentThread().interrupt() }
        videoCapturer?.dispose()
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        _localVideoTrack.value?.dispose()
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        videoSource?.dispose()
        videoSource = null
        remoteDescriptionSet = false
        if (audioRoutingActive) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice()
                audioManager.mode = originalAudioMode
                @Suppress("DEPRECATION")
                run { audioManager.isSpeakerphoneOn = originalSpeakerphone }
            } catch (_: Exception) { }
            audioRoutingActive = false
        }
        currentCall = null
        callReference = null
    }

    private fun fail(message: String) {
        Log.w(TAG, message)
        _connectionState.value = message
    }

    companion object {
        private const val TAG = "WebRtcCallClient"
        private const val DATABASE_URL = "https://omnistudio-caaf5-default-rtdb.firebaseio.com"
        private const val STUN_SERVER = "stun:stun.l.google.com:19302"
        private const val FACTORY_LOCK = "WebRtcCallClient.Factory"
        @Volatile private var factoryInitialized = false
    }
}
