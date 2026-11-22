package com.asciilive.wallpaper.data

enum class SourceType(val shaderValue: Int, val label: String) {
    DEMO(0, "3D-Демо"),
    IMAGE(1, "Фото"),
    VIDEO(2, "Видео")
}

enum class DemoPreset(val shaderValue: Int, val title: String, val subtitle: String, val tag: String) {
    DNA_HELIX(0, "ДНК-спираль", "Двойная спираль — бесконечная диагональ", "DNA HELIX"),
    EVENT_HORIZON(1, "Чёрная дыра", "Аккреционный диск + фотонное кольцо", "EVENT HORIZON"),
    WIREFRAME_CUBE(2, "Большой куб", "3D-каркас с перспективным вращением", "BIG CUBE")
}

enum class CharsetPreset(val title: String, val chars: String) {
    CLASSIC("Классика", " .:-=+*#%@"),
    MINIMAL("Минимал", " .:+*#"),
    BLOCKS("Блочный", " ░▒▓█"),
    MATRIX("Матрица", " .:;10X#$@"),
    BRAILLE("Брайль", " ⠀⠁⠃⠇⠏⠟⠿⣿"),
    CUSTOM("Свой", " .:-=+*#%@")
}

enum class ColorMode(val shaderValue: Int, val title: String) {
    MONOCHROME(0, "Монохром"),
    ORIGINAL(1, "Оригинал"),
    DUOTONE(2, "Дуотон"),
    MATERIAL_YOU(3, "Material You")
}

enum class VideoScaleMode(val shaderValue: Int, val title: String) {
    FILL(0, "Заполнить (Fill)"),
    FIT(1, "Вместить (Fit)")
}

data class AsciiSettings(
    val sourceType: SourceType = SourceType.DEMO,
    val demoPreset: DemoPreset = DemoPreset.DNA_HELIX,
    val mediaUri: String? = null,
    val charsetPreset: CharsetPreset = CharsetPreset.CLASSIC,
    val customCharset: String = " .:-=+*#%@",
    val columns: Int = 96,
    val colorMode: ColorMode = ColorMode.MONOCHROME,
    val foregroundColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundColor: Int = 0xFF000000.toInt(),
    val dynamicForegroundColor: Int = 0xFFD9F99D.toInt(),
    val dynamicBackgroundColor: Int = 0xFF0A0D0A.toInt(),
    val invertLuminance: Boolean = false,
    val brightness: Float = 0.0f,
    val contrast: Float = 1.15f,
    val glowIntensity: Float = 0.25f,
    val scanlinesIntensity: Float = 0.08f,
    val videoScaleMode: VideoScaleMode = VideoScaleMode.FILL,
    val playbackSpeed: Float = 1.0f,
    val loopVideo: Boolean = true,
    val targetFps: Int = 60
) {
    val activeCharset: String
        get() {
            val raw = if (charsetPreset == CharsetPreset.CUSTOM) customCharset else charsetPreset.chars
            return if (raw.length >= 2) raw else CharsetPreset.CLASSIC.chars
        }

    val effectiveForegroundColor: Int
        get() = when (colorMode) {
            ColorMode.MATERIAL_YOU -> dynamicForegroundColor
            else -> foregroundColor
        }

    val effectiveBackgroundColor: Int
        get() = when (colorMode) {
            ColorMode.MATERIAL_YOU -> dynamicBackgroundColor
            else -> backgroundColor
        }
}
