package com.asciilive.wallpaper.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asciilive.wallpaper.data.SourceType
import com.asciilive.wallpaper.service.AsciiWallpaperService
import com.asciilive.wallpaper.ui.components.AsciiPreviewSurface
import com.asciilive.wallpaper.ui.components.FloatingAsciiToolbar
import com.asciilive.wallpaper.ui.components.LiveTelemetryPill
import com.asciilive.wallpaper.ui.sheet.SettingsBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }

    // Sync active Material You dynamic palette into DataStore for WallpaperService
    val dynamicFg = MaterialTheme.colorScheme.primary.toArgb()
    val dynamicBg = MaterialTheme.colorScheme.surfaceContainerLowest.toArgb()
    LaunchedEffect(dynamicFg, dynamicBg) {
        viewModel.syncMaterialYouPalette(dynamicFg, dynamicBg)
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onMediaPicked(uri)
        }
    }

    val launchPicker = {
        mediaPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fullscreen live OpenGL ES 3.0 ASCII wallpaper preview
        AsciiPreviewSurface(
            settings = settings,
            modifier = Modifier.fillMaxSize()
        )

        // Top live telemetry pill
        val sourceTag = when (settings.sourceType) {
            SourceType.DEMO -> settings.demoPreset.tag
            SourceType.IMAGE -> "PHOTO"
            SourceType.VIDEO -> "VIDEO"
        }
        LiveTelemetryPill(
            sourceTag = sourceTag,
            columns = settings.columns,
            fps = settings.targetFps,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp)
        )

        // MD3 Expressive Floating Toolbar
        FloatingAsciiToolbar(
            onPickMedia = launchPicker,
            onCycleDemo = viewModel::cycleDemoPreset,
            onOpenSettings = { showSettingsSheet = true },
            onApplyWallpaper = { openLiveWallpaperSetter(context) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 22.dp, start = 16.dp, end = 16.dp)
        )

        if (showSettingsSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            SettingsBottomSheet(
                settings = settings,
                sheetState = sheetState,
                onDismiss = { showSettingsSheet = false },
                onPickMedia = launchPicker,
                onUpdate = viewModel::updateSettings
            )
        }
    }
}

private fun openLiveWallpaperSetter(context: Context) {
    val changeIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(context, AsciiWallpaperService::class.java)
        )
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(changeIntent)
    }.onFailure {
        val fallback = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }
}
