package com.asciilive.wallpaper.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages Media3 ExoPlayer -> SurfaceTexture zero-copy GPU video decoding
 * and EXIF-aware downsampled Bitmap loading for photos.
 */
class MediaSourceController(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var exoPlayer: ExoPlayer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var videoSurface: Surface? = null

    private val frameAvailable = AtomicBoolean(false)
    private val pendingPhotoBitmap = AtomicReference<Bitmap?>(null)

    @Volatile
    var mediaWidth: Int = 1080
        private set

    @Volatile
    var mediaHeight: Int = 1920
        private set

    private var currentVideoUri: String? = null
    private var currentPhotoUri: String? = null
    private var currentSpeed: Float = 1.0f
    private var currentLoop: Boolean = true
    private var isVisible: Boolean = true

    fun attachVideoTexture(oesTextureId: Int) {
        detachVideoSurface()
        val st = SurfaceTexture(oesTextureId).apply {
            setOnFrameAvailableListener {
                frameAvailable.set(true)
            }
        }
        surfaceTexture = st
        videoSurface = Surface(st)
        mainHandler.post {
            exoPlayer?.setVideoSurface(videoSurface)
        }
    }

    fun prepareVideo(uriString: String, speed: Float, loop: Boolean) {
        val uriChanged = uriString != currentVideoUri
        val speedChanged = speed != currentSpeed
        val loopChanged = loop != currentLoop

        currentVideoUri = uriString
        currentSpeed = speed
        currentLoop = loop

        mainHandler.post {
            val player = exoPlayer ?: ExoPlayer.Builder(context).build().also { created ->
                created.volume = 0f
                created.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            mediaWidth = videoSize.width
                            mediaHeight = videoSize.height
                        }
                    }
                })
                videoSurface?.let { created.setVideoSurface(it) }
                exoPlayer = created
            }

            if (uriChanged) {
                player.setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
                player.prepare()
            }
            if (uriChanged || loopChanged) {
                player.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            }
            if (uriChanged || speedChanged) {
                player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 2.5f))
            }
            player.playWhenReady = isVisible
            if (isVisible) player.play() else player.pause()
        }
    }

    fun pauseVideo() {
        isVisible = false
        mainHandler.post {
            exoPlayer?.playWhenReady = false
            exoPlayer?.pause()
        }
    }

    fun resumeVideo() {
        isVisible = true
        mainHandler.post {
            if (currentVideoUri != null) {
                exoPlayer?.playWhenReady = true
                exoPlayer?.play()
            }
        }
    }

    fun stopVideo() {
        currentVideoUri = null
        mainHandler.post {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        }
    }

    fun updateVideoTexImage(transformMatrix: FloatArray): Boolean {
        val st = surfaceTexture ?: return false
        return if (frameAvailable.compareAndSet(true, false)) {
            runCatching {
                st.updateTexImage()
                st.getTransformMatrix(transformMatrix)
                true
            }.getOrDefault(false)
        } else {
            false
        }
    }

    fun loadPhotoIfNeeded(uriString: String) {
        if (uriString == currentPhotoUri) return
        currentPhotoUri = uriString
        val bitmap = decodeScaledBitmap(Uri.parse(uriString), maxDimension = 1440)
        if (bitmap != null) {
            mediaWidth = bitmap.width
            mediaHeight = bitmap.height
            pendingPhotoBitmap.getAndSet(bitmap)?.recycle()
        }
    }

    fun consumePendingPhoto(): Bitmap? = pendingPhotoBitmap.getAndSet(null)

    private fun decodeScaledBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val rotation = readExifRotation(uri)
            if (rotation == 0f) {
                rawBitmap
            } else {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                    .also { if (it != rawBitmap) rawBitmap.recycle() }
            }
        }.getOrNull()
    }

    private fun readExifRotation(uri: Uri): Float {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
    }

    private fun detachVideoSurface() {
        videoSurface?.release()
        videoSurface = null
        surfaceTexture?.release()
        surfaceTexture = null
    }

    fun release() {
        currentVideoUri = null
        currentPhotoUri = null
        pendingPhotoBitmap.getAndSet(null)?.recycle()
        mainHandler.post {
            exoPlayer?.release()
            exoPlayer = null
        }
        detachVideoSurface()
    }
}
