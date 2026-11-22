package com.asciilive.wallpaper.service

import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.asciilive.wallpaper.AsciiWallpaperApp
import com.asciilive.wallpaper.data.AsciiSettings
import com.asciilive.wallpaper.data.SourceType
import com.asciilive.wallpaper.engine.AsciiGlRenderer
import com.asciilive.wallpaper.engine.EglSurfaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Battery-efficient Live Wallpaper service using the shared OpenGL ES 3.0 AsciiGlRenderer.
 * Automatically reacts to DataStore setting changes without restarting the service,
 * and immediately halts video decoding & GPU rendering when the wallpaper is not visible.
 */
class AsciiWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = AsciiEngine()

    private inner class AsciiEngine : Engine() {

        private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var renderThread: HandlerThread? = null
        private var renderHandler: Handler? = null

        private val eglManager = EglSurfaceManager()
        private val renderer = AsciiGlRenderer(applicationContext)

        @Volatile
        private var isVisible = false

        @Volatile
        private var currentSettings = AsciiSettings()

        private val frameRunnable = object : Runnable {
            override fun run() {
                if (!isVisible || !eglManager.isReady) return

                val frameStartMs = System.currentTimeMillis()
                if (eglManager.makeCurrent()) {
                    renderer.renderFrame()
                    eglManager.swapBuffers()
                }

                // Static photo doesn't need continuous 60 FPS polling once drawn,
                // unless settings change (which explicitly triggers requestRender()).
                if (currentSettings.sourceType == SourceType.IMAGE && currentSettings.mediaUri != null) {
                    return
                }

                val targetFrameMs = (1000L / currentSettings.targetFps.coerceIn(30, 120)).coerceAtLeast(8L)
                val elapsedMs = System.currentTimeMillis() - frameStartMs
                val delayMs = (targetFrameMs - elapsedMs).coerceAtLeast(1L)
                renderHandler?.postDelayed(this, delayMs)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val thread = HandlerThread("AsciiWallpaperGL").apply { start() }
            renderThread = thread
            renderHandler = Handler(thread.looper)

            val repo = (application as AsciiWallpaperApp).settingsRepository
            serviceScope.launch {
                repo.settingsFlow.collect { updated ->
                    currentSettings = updated
                    renderHandler?.post {
                        renderer.updateSettings(updated)
                        if (isVisible) {
                            scheduleNextFrame()
                        }
                    }
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            renderHandler?.post {
                if (eglManager.init(holder)) {
                    renderer.onGlContextCreated()
                    renderer.updateSettings(currentSettings)
                    renderer.setVisible(isVisible)
                    if (isVisible) {
                        scheduleNextFrame()
                    }
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderHandler?.post {
                if (eglManager.makeCurrent()) {
                    renderer.onGlViewportChanged(width, height)
                    if (isVisible) {
                        scheduleNextFrame()
                    }
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisible = visible
            renderHandler?.post {
                renderHandler?.removeCallbacks(frameRunnable)
                renderer.setVisible(visible)
                if (visible && eglManager.isReady) {
                    scheduleNextFrame()
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            isVisible = false
            val latch = java.util.concurrent.CountDownLatch(1)
            renderHandler?.post {
                renderHandler?.removeCallbacks(frameRunnable)
                renderer.setVisible(false)
                if (eglManager.makeCurrent()) {
                    renderer.release()
                }
                eglManager.release()
                latch.countDown()
            }
            runCatching { latch.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS) }
        }

        override fun onDestroy() {
            super.onDestroy()
            serviceScope.cancel()
            renderHandler?.removeCallbacksAndMessages(null)
            renderThread?.quitSafely()
            renderThread = null
            renderHandler = null
        }

        private fun scheduleNextFrame() {
            renderHandler?.removeCallbacks(frameRunnable)
            renderHandler?.post(frameRunnable)
        }
    }
}
