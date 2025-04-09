package com.keshav.drone.vyorius

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.unit.sp


class MainActivity : ComponentActivity() {
    var player: ExoPlayer? = null
    private var recorder: MediaRecorderWrapper? = null
    private var isRecording by mutableStateOf(false)
    private var isPlayerReady by mutableStateOf(false)

    private val currentRtspUrl = mutableStateOf("rtsp://10.51.34.105:5570/ch0")
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, "Keshav Raj", Toast.LENGTH_SHORT).show()

        requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
                initializePlayer(currentRtspUrl.value)
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_LONG).show()
                Toast.makeText(this, "Please grant all required permissions", Toast.LENGTH_LONG).show()
                initializePlayer(currentRtspUrl.value)
                finish()
            }
        }

        if (!hasRequiredPermissions()) {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
                )
            )
        } else {
            initializePlayer(currentRtspUrl.value)
        }

        setContent {
            RTSPPlayerApp(
                mainActivity = this,
                isPlayerReady = isPlayerReady,
                onRtspUrlChanged = {
                    currentRtspUrl.value = it
                    initializePlayer(it)
                },
                currentUrl = currentRtspUrl.value
            )
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
    }



    @OptIn(UnstableApi::class)
    private fun initializePlayer(rtspUrl: String) {
        if (rtspUrl.isEmpty()) return

        player?.release()

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player = ExoPlayer.Builder(this, renderersFactory).build().apply {
            val mediaItem = MediaItem.fromUri(rtspUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isPlayerReady = state == Player.STATE_READY
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Toast.makeText(this@MainActivity, "Playback error: ${error.message}", Toast.LENGTH_SHORT).show()
                    isPlayerReady = false
                }
            })
        }
    }


    fun startRecording() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            return
        }

        if (isRecording || player == null || !isPlayerReady) {
            Toast.makeText(this, "Cannot start recording", Toast.LENGTH_SHORT).show()
            return
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(getExternalFilesDir(null), "ASS_VID_$timeStamp.mp4")




        recorder = MediaRecorderWrapper(this, file.absolutePath) {
            isRecording = false
            Toast.makeText(this, "Saved: ${file.name}", Toast.LENGTH_SHORT).show()
        }

        recorder?.start()
        isRecording = true
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
    }

    fun stopRecording() {
        if (isRecording) {
            recorder?.stop()
            isRecording = false
        } else {
            Toast.makeText(this, "Not recording", Toast.LENGTH_SHORT).show()
        }
    }

    fun enablePictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(pipParams)
            Toast.makeText(this, "Zoom if not visible", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "PIP not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        recorder?.release()
    }
}

@Composable
fun RTSPPlayerApp(
    mainActivity: MainActivity,
    isPlayerReady: Boolean,
    onRtspUrlChanged: (String) -> Unit,
    currentUrl: String
) {
    val context = LocalContext.current
    var rtspUrl by remember { mutableStateOf(currentUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = rtspUrl,
            onValueChange = { rtspUrl = it },
            label = { Text("RTSP URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    mainActivity.player?.let { this.player = it }
                    useController = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StyledButton(text = "Record") {
                mainActivity.startRecording()
            }
            StyledButton(text = "Stop") {
                mainActivity.stopRecording()
            }
            StyledButton(text = "Exit") {
                Toast.makeText(context, "App Exited", Toast.LENGTH_SHORT).show()
                (context as? Activity)?.finishAffinity()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                StyledButton(text = "PIP") {
                    mainActivity.enablePictureInPicture()
                }
            }
        }
    }
}

@Composable
fun StyledButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 80.dp)
    ) {
        Text(text = text, fontSize = 16.sp, color = Color.White)
    }
}


class MediaRecorderWrapper(
    private val context: Context,
    private val outputPath: String,
    private val onFinished: () -> Unit
) {
    private var mediaRecorder: MediaRecorder? = null

    fun start() {
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputPath)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncodingBitRate(1_000_000)
                setVideoFrameRate(30)
                setVideoSize(1280, 720)
                setOrientationHint(90)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("MediaRecorderWrapper", "Start failed", e)
            release()
        }
    }

    fun stop() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            onFinished()
        } catch (e: Exception) {
            Log.e("MediaRecorderWrapper", "Stop failed", e)
        } finally {
            release()
        }
    }

    fun release() {
        mediaRecorder?.release()
        mediaRecorder = null
    }
}