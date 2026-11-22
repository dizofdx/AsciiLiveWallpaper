package com.asciilive.wallpaper.ui.sheet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asciilive.wallpaper.data.AsciiSettings
import com.asciilive.wallpaper.data.CharsetPreset
import com.asciilive.wallpaper.data.ColorMode
import com.asciilive.wallpaper.data.DemoPreset
import com.asciilive.wallpaper.data.SourceType
import com.asciilive.wallpaper.data.VideoScaleMode
import com.asciilive.wallpaper.ui.components.ColorSwatchRow
import com.asciilive.wallpaper.ui.components.DemoPresetRadioCard
import com.asciilive.wallpaper.ui.components.ExpressiveFpsSelector
import com.asciilive.wallpaper.ui.components.ExpressiveHapticSlider
import com.asciilive.wallpaper.ui.components.ExpressiveSectionHeader
import com.asciilive.wallpaper.ui.components.ExpressiveToggleRow
import com.asciilive.wallpaper.ui.components.MorphingSelectionChip
import com.asciilive.wallpaper.ui.theme.ExpressiveMotion
import java.util.Locale
import kotlin.math.roundToInt

private enum class SettingsTab(val title: String) {
    SOURCE("Источник"),
    ASCII("Символы"),
    COLOR("Цвет"),
    MOTION("Видео")
}

private val FG_SWATCHES = listOf(
    0xFFFFFFFF.toInt(),
    0xFFC8F582.toInt(),
    0xFF00FF66.toInt(),
    0xFF7DD3FC.toInt(),
    0xFFFDE047.toInt(),
    0xFFFF7A90.toInt(),
    0xFFD8B4FE.toInt()
)

private val BG_SWATCHES = listOf(
    0xFF000000.toInt(),
    0xFF0A0D0B.toInt(),
    0xFF0B1120.toInt(),
    0xFF140D1B.toInt(),
    0xFF1A1410.toInt(),
    0xFFF4F6F0.toInt()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsBottomSheet(
    settings: AsciiSettings,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onPickMedia: () -> Unit,
    onUpdate: ((AsciiSettings) -> AsciiSettings) -> Unit
) {
    var activeTab by rememberSaveable { mutableStateOf(SettingsTab.SOURCE) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "ASCII-обои",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Spring-morphing category tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsTab.entries.forEach { tab ->
                    MorphingSelectionChip(
                        label = tab.title,
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val enter = slideInHorizontally(ExpressiveMotion.spatialDefault()) {
                        if (forward) it / 4 else -it / 4
                    } + fadeIn(ExpressiveMotion.effectsDefault())
                    val exit = slideOutHorizontally(ExpressiveMotion.spatialFast()) {
                        if (forward) -it / 4 else it / 4
                    } + fadeOut(ExpressiveMotion.effectsDefault())
                    enter togetherWith exit
                },
                label = "settingsTabTransition"
            ) { tab ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (tab) {
                        SettingsTab.SOURCE -> SourceTabContent(
                            settings = settings,
                            onPickMedia = onPickMedia,
                            onUpdate = onUpdate
                        )
                        SettingsTab.ASCII -> AsciiTabContent(
                            settings = settings,
                            onUpdate = onUpdate
                        )
                        SettingsTab.COLOR -> ColorTabContent(
                            settings = settings,
                            onUpdate = onUpdate
                        )
                        SettingsTab.MOTION -> MotionTabContent(
                            settings = settings,
                            onUpdate = onUpdate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceTabContent(
    settings: AsciiSettings,
    onPickMedia: () -> Unit,
    onUpdate: ((AsciiSettings) -> AsciiSettings) -> Unit
) {
    ExpressiveSectionHeader(
        index = "01",
        title = "Медиафайл",
        trailingText = settings.sourceType.label.uppercase()
    )

    FilledTonalButton(
        onClick = onPickMedia,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Icon(imageVector = Icons.Rounded.PhotoLibrary, contentDescription = null)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (settings.mediaUri != null && settings.sourceType != SourceType.DEMO) {
                "Заменить фото или видео"
            } else {
                "Выбрать фото / видео из галереи"
            },
            style = MaterialTheme.typography.labelLarge
        )
    }

    ExpressiveSectionHeader(
        index = "02",
        title = "Встроенные 3D-демо"
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DemoPreset.entries.forEach { preset ->
            DemoPresetRadioCard(
                title = preset.title,
                subtitle = preset.subtitle,
                tag = preset.tag,
                selected = settings.sourceType == SourceType.DEMO && settings.demoPreset == preset,
                onClick = {
                    onUpdate {
                        it.copy(
                            sourceType = SourceType.DEMO,
                            demoPreset = preset
                        )
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AsciiTabContent(
    settings: AsciiSettings,
    onUpdate: ((AsciiSettings) -> AsciiSettings) -> Unit
) {
    ExpressiveSectionHeader(
        index = "01",
        title = "Набор символов",
        trailingText = settings.charsetPreset.title.uppercase()
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CharsetPreset.entries.forEach { preset ->
            MorphingSelectionChip(
                label = preset.title,
                sublabel = if (preset == CharsetPreset.CUSTOM) "abc" else preset.chars.trim().take(6),
                selected = settings.charsetPreset == preset,
                onClick = {
                    onUpdate { it.copy(charsetPreset = preset) }
                }
            )
        }
    }

    if (settings.charsetPreset == CharsetPreset.CUSTOM) {
        OutlinedTextField(
            value = settings.customCharset,
            onValueChange = { text ->
                onUpdate { it.copy(customCharset = text) }
            },
            label = { Text("От тёмного к светлому (минимум 2 символа)") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }

    ExpressiveSectionHeader(
        index = "02",
        title = "Разрешение сетки",
        trailingText = "${settings.columns} COLS"
    )

    ExpressiveHapticSlider(
        title = "Плотность символов",
        valueText = "${settings.columns} по ширине",
        value = settings.columns.toFloat(),
        valueRange = 32f..200f,
        hapticSteps = 24,
        caption = "Меньше столбцов — крупнее терминальные глифы. Больше — выше детализация.",
        onValueChange = { value ->
            onUpdate { it.copy(columns = value.roundToInt()) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorTabContent(
    settings: AsciiSettings,
    onUpdate: ((AsciiSettings) -> AsciiSettings) -> Unit
) {
    ExpressiveSectionHeader(
        index = "01",
        title = "Цветовая модель",
        trailingText = settings.colorMode.title.uppercase()
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ColorMode.entries.forEach { mode ->
            MorphingSelectionChip(
                label = mode.title,
                selected = settings.colorMode == mode,
                onClick = { onUpdate { it.copy(colorMode = mode) } }
            )
        }
    }

    if (settings.colorMode == ColorMode.MONOCHROME || settings.colorMode == ColorMode.DUOTONE) {
        ColorSwatchRow(
            title = "Цвет символов",
            selectedColor = settings.foregroundColor,
            swatches = FG_SWATCHES,
            onSelect = { color -> onUpdate { it.copy(foregroundColor = color) } }
        )
        ColorSwatchRow(
            title = "Цвет фона",
            selectedColor = settings.backgroundColor,
            swatches = BG_SWATCHES,
            onSelect = { color -> onUpdate { it.copy(backgroundColor = color) } }
        )
    }

    ExpressiveToggleRow(
        title = "Инверсия яркости",
        subtitle = "Меняет местами плотные и пустые символы",
        checked = settings.invertLuminance,
        onCheckedChange = { inv -> onUpdate { it.copy(invertLuminance = inv) } }
    )

    ExpressiveSectionHeader(index = "02", title = "Коррекция и эффекты")

    ExpressiveHapticSlider(
        title = "Яркость",
        valueText = String.format(Locale.US, "%+.2f", settings.brightness),
        value = settings.brightness,
        valueRange = -0.45f..0.45f,
        onValueChange = { v -> onUpdate { it.copy(brightness = v) } }
    )

    ExpressiveHapticSlider(
        title = "Контрастность",
        valueText = String.format(Locale.US, "%.2fx", settings.contrast),
        value = settings.contrast,
        valueRange = 0.5f..2.2f,
        onValueChange = { v -> onUpdate { it.copy(contrast = v) } }
    )

    ExpressiveHapticSlider(
        title = "Мягкое свечение (Glow)",
        valueText = "${(settings.glowIntensity * 100).roundToInt()}%",
        value = settings.glowIntensity,
        valueRange = 0f..1f,
        onValueChange = { v -> onUpdate { it.copy(glowIntensity = v) } }
    )

    ExpressiveHapticSlider(
        title = "Эффект сканлайнов (CRT)",
        valueText = "${(settings.scanlinesIntensity * 100).roundToInt()}%",
        value = settings.scanlinesIntensity,
        valueRange = 0f..1f,
        onValueChange = { v -> onUpdate { it.copy(scanlinesIntensity = v) } }
    )
}

@Composable
private fun MotionTabContent(
    settings: AsciiSettings,
    onUpdate: ((AsciiSettings) -> AsciiSettings) -> Unit
) {
    ExpressiveSectionHeader(
        index = "01",
        title = "Масштабирование медиа"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VideoScaleMode.entries.forEach { mode ->
            MorphingSelectionChip(
                label = mode.title,
                selected = settings.videoScaleMode == mode,
                onClick = { onUpdate { it.copy(videoScaleMode = mode) } },
                modifier = Modifier.weight(1f)
            )
        }
    }

    ExpressiveSectionHeader(
        index = "02",
        title = "Скорость и частота кадров",
        trailingText = "${settings.targetFps} FPS"
    )

    ExpressiveHapticSlider(
        title = "Скорость анимации / видео",
        valueText = String.format(Locale.US, "%.2fx", settings.playbackSpeed),
        value = settings.playbackSpeed,
        valueRange = 0.25f..2.5f,
        onValueChange = { v -> onUpdate { it.copy(playbackSpeed = v) } }
    )

    ExpressiveFpsSelector(
        selectedFps = settings.targetFps,
        onSelect = { fps -> onUpdate { it.copy(targetFps = fps) } }
    )

    Text(
        text = "60 FPS — оптимальный баланс плавности и автономности. 120 FPS для LTPO-экранов.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    ExpressiveToggleRow(
        title = "Зацикливать видео",
        subtitle = "Бесшовное повторение выбранного ролика",
        checked = settings.loopVideo,
        onCheckedChange = { loop -> onUpdate { it.copy(loopVideo = loop) } }
    )
}
