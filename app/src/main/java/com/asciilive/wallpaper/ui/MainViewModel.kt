package com.asciilive.wallpaper.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asciilive.wallpaper.AsciiWallpaperApp
import com.asciilive.wallpaper.data.AsciiSettings
import com.asciilive.wallpaper.data.DemoPreset
import com.asciilive.wallpaper.data.SourceType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as AsciiWallpaperApp).settingsRepository

    val settings: StateFlow<AsciiSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AsciiSettings()
    )

    fun updateSettings(transform: (AsciiSettings) -> AsciiSettings) {
        viewModelScope.launch {
            repository.update(transform)
        }
    }

    fun syncMaterialYouPalette(foregroundArgb: Int, backgroundArgb: Int) {
        val current = settings.value
        if (current.dynamicForegroundColor == foregroundArgb &&
            current.dynamicBackgroundColor == backgroundArgb
        ) {
            return
        }
        viewModelScope.launch {
            repository.update {
                it.copy(
                    dynamicForegroundColor = foregroundArgb,
                    dynamicBackgroundColor = backgroundArgb
                )
            }
        }
    }

    fun onMediaPicked(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val sourceType = if (isVideoUri(resolver, uri)) SourceType.VIDEO else SourceType.IMAGE
        viewModelScope.launch {
            repository.update {
                it.copy(
                    sourceType = sourceType,
                    mediaUri = uri.toString()
                )
            }
        }
    }

    /**
     * Some devices return a null MIME type for picked media (MOV/MKV and certain
     * picker providers), which would silently turn a video into a frozen "photo".
     * Fall back to the file extension before assuming it is an image.
     */
    private fun isVideoUri(resolver: android.content.ContentResolver, uri: Uri): Boolean {
        val mimeType = runCatching { resolver.getType(uri) }.getOrNull()
        if (mimeType?.startsWith("video/") == true) return true
        val path = runCatching { uri.path?.lowercase() }.getOrNull().orEmpty()
        return VIDEO_EXTENSIONS.any(path::endsWith)
    }

    private companion object {
        val VIDEO_EXTENSIONS = listOf(".mp4", ".mov", ".webm", ".mkv", ".3gp", ".m4v", ".avi")
    }

    fun cycleDemoPreset() {
        val current = settings.value
        val presets = DemoPreset.entries
        val next = if (current.sourceType != SourceType.DEMO) {
            presets.first()
        } else {
            presets[(current.demoPreset.ordinal + 1) % presets.size]
        }
        viewModelScope.launch {
            repository.update {
                it.copy(
                    sourceType = SourceType.DEMO,
                    demoPreset = next
                )
            }
        }
    }
}
