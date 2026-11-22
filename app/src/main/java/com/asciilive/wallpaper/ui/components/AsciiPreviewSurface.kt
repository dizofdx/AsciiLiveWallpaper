package com.asciilive.wallpaper.ui.components

import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asciilive.wallpaper.data.AsciiSettings
import com.asciilive.wallpaper.engine.AsciiGlRenderer

/**
 * Fullscreen live GPU preview using the exact same OpenGL ES 3.0 AsciiGlRenderer
 * as AsciiWallpaperService.
 */
@Composable
fun AsciiPreviewSurface(
    settings: AsciiSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val renderer = remember { AsciiGlRenderer(context) }
    val glSurfaceView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    LaunchedEffect(settings) {
        glSurfaceView.queueEvent {
            renderer.updateSettings(settings)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    glSurfaceView.onResume()
                    glSurfaceView.queueEvent { renderer.setVisible(true) }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    glSurfaceView.queueEvent { renderer.setVisible(false) }
                    glSurfaceView.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceView.queueEvent { renderer.release() }
        }
    }

    AndroidView(
        factory = { glSurfaceView },
        modifier = modifier
    )
}
