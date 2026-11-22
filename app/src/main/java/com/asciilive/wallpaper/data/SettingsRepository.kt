package com.asciilive.wallpaper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.asciiDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ascii_live_wallpaper_prefs"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SOURCE_TYPE = stringPreferencesKey("source_type")
        val DEMO_PRESET = stringPreferencesKey("demo_preset")
        val MEDIA_URI = stringPreferencesKey("media_uri")
        val CHARSET_PRESET = stringPreferencesKey("charset_preset")
        val CUSTOM_CHARSET = stringPreferencesKey("custom_charset")
        val COLUMNS = intPreferencesKey("columns")
        val COLOR_MODE = stringPreferencesKey("color_mode")
        val FG_COLOR = intPreferencesKey("fg_color")
        val BG_COLOR = intPreferencesKey("bg_color")
        val DYNAMIC_FG_COLOR = intPreferencesKey("dynamic_fg_color")
        val DYNAMIC_BG_COLOR = intPreferencesKey("dynamic_bg_color")
        val INVERT = booleanPreferencesKey("invert")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val CONTRAST = floatPreferencesKey("contrast")
        val GLOW = floatPreferencesKey("glow")
        val SCANLINES = floatPreferencesKey("scanlines")
        val VIDEO_SCALE_MODE = stringPreferencesKey("video_scale_mode")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val LOOP_VIDEO = booleanPreferencesKey("loop_video")
        val TARGET_FPS = intPreferencesKey("target_fps")
    }

    val settingsFlow: Flow<AsciiSettings> = context.asciiDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            AsciiSettings(
                sourceType = prefs[Keys.SOURCE_TYPE]
                    ?.let { runCatching { SourceType.valueOf(it) }.getOrNull() }
                    ?: SourceType.DEMO,
                demoPreset = prefs[Keys.DEMO_PRESET]
                    ?.let { runCatching { DemoPreset.valueOf(it) }.getOrNull() }
                    ?: DemoPreset.DNA_HELIX,
                mediaUri = prefs[Keys.MEDIA_URI]?.takeIf { it.isNotBlank() },
                charsetPreset = prefs[Keys.CHARSET_PRESET]
                    ?.let { runCatching { CharsetPreset.valueOf(it) }.getOrNull() }
                    ?: CharsetPreset.CLASSIC,
                customCharset = prefs[Keys.CUSTOM_CHARSET] ?: CharsetPreset.CLASSIC.chars,
                columns = (prefs[Keys.COLUMNS] ?: 96).coerceIn(32, 200),
                colorMode = prefs[Keys.COLOR_MODE]
                    ?.let { runCatching { ColorMode.valueOf(it) }.getOrNull() }
                    ?: ColorMode.MONOCHROME,
                foregroundColor = prefs[Keys.FG_COLOR] ?: 0xFFFFFFFF.toInt(),
                backgroundColor = prefs[Keys.BG_COLOR] ?: 0xFF000000.toInt(),
                dynamicForegroundColor = prefs[Keys.DYNAMIC_FG_COLOR] ?: 0xFFD9F99D.toInt(),
                dynamicBackgroundColor = prefs[Keys.DYNAMIC_BG_COLOR] ?: 0xFF0A0D0A.toInt(),
                invertLuminance = prefs[Keys.INVERT] ?: false,
                brightness = (prefs[Keys.BRIGHTNESS] ?: 0.0f).coerceIn(-0.5f, 0.5f),
                contrast = (prefs[Keys.CONTRAST] ?: 1.15f).coerceIn(0.4f, 2.5f),
                glowIntensity = (prefs[Keys.GLOW] ?: 0.25f).coerceIn(0f, 1f),
                scanlinesIntensity = (prefs[Keys.SCANLINES] ?: 0.08f).coerceIn(0f, 1f),
                videoScaleMode = prefs[Keys.VIDEO_SCALE_MODE]
                    ?.let { runCatching { VideoScaleMode.valueOf(it) }.getOrNull() }
                    ?: VideoScaleMode.FILL,
                playbackSpeed = (prefs[Keys.PLAYBACK_SPEED] ?: 1.0f).coerceIn(0.25f, 2.5f),
                loopVideo = prefs[Keys.LOOP_VIDEO] ?: true,
                targetFps = (prefs[Keys.TARGET_FPS] ?: 60).coerceIn(30, 120)
            )
        }

    suspend fun update(transform: (AsciiSettings) -> AsciiSettings) {
        context.asciiDataStore.edit { prefs ->
            val current = AsciiSettings(
                sourceType = prefs[Keys.SOURCE_TYPE]?.let { runCatching { SourceType.valueOf(it) }.getOrNull() } ?: SourceType.DEMO,
                demoPreset = prefs[Keys.DEMO_PRESET]?.let { runCatching { DemoPreset.valueOf(it) }.getOrNull() } ?: DemoPreset.DNA_HELIX,
                mediaUri = prefs[Keys.MEDIA_URI]?.takeIf { it.isNotBlank() },
                charsetPreset = prefs[Keys.CHARSET_PRESET]?.let { runCatching { CharsetPreset.valueOf(it) }.getOrNull() } ?: CharsetPreset.CLASSIC,
                customCharset = prefs[Keys.CUSTOM_CHARSET] ?: CharsetPreset.CLASSIC.chars,
                columns = prefs[Keys.COLUMNS] ?: 96,
                colorMode = prefs[Keys.COLOR_MODE]?.let { runCatching { ColorMode.valueOf(it) }.getOrNull() } ?: ColorMode.MONOCHROME,
                foregroundColor = prefs[Keys.FG_COLOR] ?: 0xFFFFFFFF.toInt(),
                backgroundColor = prefs[Keys.BG_COLOR] ?: 0xFF000000.toInt(),
                dynamicForegroundColor = prefs[Keys.DYNAMIC_FG_COLOR] ?: 0xFFD9F99D.toInt(),
                dynamicBackgroundColor = prefs[Keys.DYNAMIC_BG_COLOR] ?: 0xFF0A0D0A.toInt(),
                invertLuminance = prefs[Keys.INVERT] ?: false,
                brightness = prefs[Keys.BRIGHTNESS] ?: 0.0f,
                contrast = prefs[Keys.CONTRAST] ?: 1.15f,
                glowIntensity = prefs[Keys.GLOW] ?: 0.25f,
                scanlinesIntensity = prefs[Keys.SCANLINES] ?: 0.08f,
                videoScaleMode = prefs[Keys.VIDEO_SCALE_MODE]?.let { runCatching { VideoScaleMode.valueOf(it) }.getOrNull() } ?: VideoScaleMode.FILL,
                playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
                loopVideo = prefs[Keys.LOOP_VIDEO] ?: true,
                targetFps = prefs[Keys.TARGET_FPS] ?: 60
            )
            val updated = transform(current)
            prefs[Keys.SOURCE_TYPE] = updated.sourceType.name
            prefs[Keys.DEMO_PRESET] = updated.demoPreset.name
            if (updated.mediaUri != null) {
                prefs[Keys.MEDIA_URI] = updated.mediaUri
            } else {
                prefs.remove(Keys.MEDIA_URI)
            }
            prefs[Keys.CHARSET_PRESET] = updated.charsetPreset.name
            prefs[Keys.CUSTOM_CHARSET] = updated.customCharset
            prefs[Keys.COLUMNS] = updated.columns.coerceIn(32, 200)
            prefs[Keys.COLOR_MODE] = updated.colorMode.name
            prefs[Keys.FG_COLOR] = updated.foregroundColor
            prefs[Keys.BG_COLOR] = updated.backgroundColor
            prefs[Keys.DYNAMIC_FG_COLOR] = updated.dynamicForegroundColor
            prefs[Keys.DYNAMIC_BG_COLOR] = updated.dynamicBackgroundColor
            prefs[Keys.INVERT] = updated.invertLuminance
            prefs[Keys.BRIGHTNESS] = updated.brightness.coerceIn(-0.5f, 0.5f)
            prefs[Keys.CONTRAST] = updated.contrast.coerceIn(0.4f, 2.5f)
            prefs[Keys.GLOW] = updated.glowIntensity.coerceIn(0f, 1f)
            prefs[Keys.SCANLINES] = updated.scanlinesIntensity.coerceIn(0f, 1f)
            prefs[Keys.VIDEO_SCALE_MODE] = updated.videoScaleMode.name
            prefs[Keys.PLAYBACK_SPEED] = updated.playbackSpeed.coerceIn(0.25f, 2.5f)
            prefs[Keys.LOOP_VIDEO] = updated.loopVideo
            prefs[Keys.TARGET_FPS] = updated.targetFps.coerceIn(30, 120)
        }
    }
}
