# ASCII Live Wallpaper (Android / OpenGL ES 3.0 / Material 3 Expressive)

Полноценное Android-приложение живых обоев, превращающее выбранное пользователем фото, видео или встроенные 3D-сцены в поток ASCII-символов на GPU в реальном времени.

## Стек
- **Kotlin + Jetpack Compose + Material 3 Expressive** (Dynamic Color, Spring Motion, Shape Morphing, Floating Toolbar, Expressive Bottom Sheet, Haptic Feedback)
- **OpenGL ES 3.0 + GLSL** (`AsciiShaders`, `AsciiGlRenderer`, `AsciiGlyphAtlas`) — единый шейдер для превью и `AsciiWallpaperService`
- **AndroidX Media3 (ExoPlayer)** -> `SurfaceTexture` (`GL_OES_EGL_image_external_essl3`) без копирования Bitmap на CPU
- **Jetpack DataStore (Preferences)** + `StateFlow` — мгновенное применение настроек в сервисе обоев без перезапуска
- **minSdk 26 / targetSdk 35**

## Сборка
1. Откройте папку `AsciiLiveWallpaper` в **Android Studio (Meerkat / Ladybug или новее)** с JDK 17.
2. Дождитесь синхронизации Gradle.
3. Запустите конфигурацию `app` на физическом устройстве Android 8.0+ (рекомендуется реальный смартфон с поддержкой OpenGL ES 3.0 для оценки 60–120 FPS и тактильного отклика).
