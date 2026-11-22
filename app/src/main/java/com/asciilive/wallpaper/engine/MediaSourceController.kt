package com.asciilive.wallpaper.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages Media3 ExoPlayer -> SurfaceTexture zero-copy GPU video decoding
 * and EXIF-aware downsampled Bitmap loading for photos.
 *
 * All ExoPlayer and Surface ownership mutations happen on the main thread.
 * The GL thread only touches the frame-available flag and [updateTexImage].
 */
class MediaSourceController(private val context: Context) {

    private companion object {
        const val TAG = "AsciiMediaSource"
        const val MAX_RETRIES = 2
        const val RETRY_DELAY_MS = 400L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var exoPlayer: ExoPlayer? = null

    @Volatile
    private var surfaceTexture: SurfaceTexture? = null

    @Volatile
    private var videoSurface: Surface? = null

    /** The Surface instance currently attached to ExoPlayer (main thread only). */
    @Volatile
    private var attachedSurface: Surface? = null

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
    private var retryCount: Int = 0

    @Volatile
    private var isVisible: Boolean = true

    fun attachVideoTexture(oesTextureId: Int) {
        detachVideoSurface()
        attachedSurface = null

        val st = SurfaceTexture(oesTextureId).apply {
            // Sane placeholder size until the codec publishes the real video size;
            // avoids 64x64 default buffers before the first frame lands.
            setDefaultBufferSize(720, 1280)
            setOnFrameAvailableListener {
                frameAvailable.set(true)
            }
        }
        surfaceTexture = st
        videoSurface = Surface(st)
        mainHandler.post {
            val player = exoPlayer ?: return@post
            val surface = videoSurface ?: return@post
            player.setVideoSurface(surface)
            attachedSurface = surface
        }
    }

    fun prepareVideo(uriString: String, speed: Float, loop: Boolean) {
        val uriChanged = uriString != currentVideoUri
        val speedChanged = speed != currentSpeed
        val loopChanged = loop != currentLoop

        currentVideoUri = uriString
        currentSpeed = speed
        currentLoop = loop
        if (uriChanged) retryCount = 0

        mainHandler.post {
            val player = exoPlayer ?: buildPlayer().also { exoPlayer = it }

            // The player may have been created before the SurfaceTexture existed
            // (settings arrive earlier than the GL surface). Attach the decoder
            // output whenever it is missing or stale — this is what guarantees
            // the video actually reaches the ASCII shader.
            val surface = videoSurface
            if (surface != null && surface !== attachedSurface) {
                player.setVideoSurface(surface)
                attachedSurface = surface
            }

            // A freshly rebuilt player has no media even when the URI did not
            // change (e.g. after wallpaper surface recreation), so re-prepare it.
            val needsPrepare = uriChanged || player.currentMediaItem == null
            if (needsPrepare) {
                player.setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
                player.prepare()
            }
            if (uriChanged || loopChanged) {
                player.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            }
            if (uriChanged || speedChanged) {
                player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 2.5f))
            }
            if (isVisible) player.play() else player.pause()
        }
    }

    fun pauseVideo() {
        isVisible = false
        mainHandler.post {
            exoPlayer?.pause()
        }
    }

    fun resumeVideo() {
        isVisible = true
        mainHandler.post {
            val player = exoPlayer ?: return@post
            // Self-heal: if playback was never prepared (hidden wallpaper, lost
            // surface race), prepare the last known media now and start it.
            if (player.currentMediaItem == null) {
                val uri = currentVideoUri ?: return@post
                player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
                player.prepare()
            }
            player.play()
        }
    }

    fun stopVideo() {
        currentVideoUri = null
        retryCount = 0
        mainHandler.post {
            exoPlayer?.run {
                stop()
                clearMediaItems()
            }
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

    private fun buildPlayer(): ExoPlayer =
        ExoPlayer.Builder(context).build().also { created ->
            created.volume = 0f
            created.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        mediaWidth = videoSize.width
                        mediaHeight = videoSize.height
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // Playback finishes preparing while the wallpaper was
                    // momentarily hidden or the surface was mid-recreate:
                    // start as soon as it is READY and visible again.
                    if (playbackState == Player.STATE_READY && isVisible) {
                        exoPlayer?.play()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "Video playback error: ${error.errorCodeName}", error)
                    retryVideo()
                }
            })
            videoSurface?.let {
                created.setVideoSurface(it)
                attachedSurface = it
            }
        }

    /** Bounded silent recovery for transient decoder/IO failures. */
    private fun retryVideo() {
        if (retryCount >= MAX_RETRIES) return
        retryCount++
        val uri = currentVideoUri ?: return
        mainHandler.postDelayed({
            val player = exoPlayer ?: return@postDelayed
            player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            player.prepare()
            if (isVisible) player.play()
        }, RETRY_DELAY_MS)
    }

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
        retryCount = 0
        pendingPhotoBitmap.getAndSet(null)?.recycle()

        // Tear the player down synchronously BEFORE releasing the SurfaceTexture:
        // the decoder may still be writing frames into the released BufferQueue
        // otherwise. release() runs on the GL thread, so a bounded wait is safe.
        val latch = CountDownLatch(1)
        mainHandler.post {
            exoPlayer?.run {
                clearVideoSurface()
                release()
            }
            exoPlayer = null
            attachedSurface = null
            latch.countDown()
        }
        runCatching { latch.await(600, TimeUnit.MILLISECONDS) }

        detachVideoSurface()
    }
}
