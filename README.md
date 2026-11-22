<div align="center">

# `ASCII // LIVE WALLPAPER`

**GPU-ускоренные живые обои для Android, превращающие 3D-сцены, фото и видео в поток ASCII-символов в реальном времени**

[![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![OpenGL ES](https://img.shields.io/badge/OpenGL%20ES-3.0%20GLSL-5586A4?style=for-the-badge&logo=opengl&logoColor=white)](https://www.khronos.org/opengles/)
[![Latest Release](https://img.shields.io/github/v/release/dizofdx/AsciiLiveWallpaper?style=for-the-badge&color=A6E3A1&label=Release)](https://github.com/dizofdx/AsciiLiveWallpaper/releases/latest)

<br />

<!-- Раскомментируй блок ниже, когда загрузишь скриншоты или GIF в папку docs/ -->
<!--
<p align="center">
  <img src="docs/preview_demo.gif" width="260" alt="Live ASCII Preview" />
  <img src="docs/screen_settings.png" width="260" alt="Settings UI" />
</p>
-->

</div>

---

## ✨ Возможности

* **Чистый GPU-рендеринг (OpenGL ES 3.0):** весь расчёт 3D-геометрии, выбор символов из атласа глифов, glow и CRT-сканлайны выполняются в едином фрагментном GLSL-шейдере без нагрузки на CPU.
* **Фото и видео в реальном времени:** выбор любых изображений и видеороликов через системный Android Photo Picker (`PickVisualMedia`). Видео декодируется аппаратно через **Media3 (ExoPlayer)** напрямую в `SurfaceTexture` (`samplerExternalOES`) — без копирования кадров в память CPU.
* **Энергоэффективность:** при выключении экрана или переключении на другое приложение рендер-поток EGL и видеодекодер мгновенно засыпают (`0%` нагрузки). Для статичных фото кадр перерисовывается только при изменении настроек.
* **Терминальный AMOLED-интерфейс + Material You:** тёмная палитра с пружинными анимациями, морфингом форм, тактильным откликом (`Haptic Feedback`) и опциональным переключением на системные динамические цвета.

---

## 🧊 Встроенные 3D-сцены

Все семь сцен рассчитываются аналитически прямо в шейдере на GPU:

| Сцена | Тег в превью | Описание |
| :--- | :--- | :--- |
| **Макароны** | `FUSILLI ×2` | Две объёмные спиральные фигуры, вращающиеся в противоположных направлениях |
| **Макароны XXL** | `FUSILLI MAX` | Гигантские спирали на весь экран — крупный объём и винтовые рёбра |
| **Кубы** | `CUBES ×2` | Два 3D-каркаса с перспективной проекцией и глубиной рёбер |
| **Кубы XXL** | `CUBES MAX` | Два крупных каркаса на всю высоту экрана с глубиной рёбер |
| **Большой куб** | `BIG CUBE ×1` | Крупный центральный 3D-куб на фиксированной дистанции |
| **ДНК** | `DNA HELIX` | Бесконечная диагональная двойная спираль с анимированными перекладинами |
| **Чёрная дыра** | `EVENT HORIZON` | Горизонт событий, фотонное кольцо, вращающийся аккреционный диск и мерцающие звёзды |

---

## 🎛️ Настройки в реальном времени

Все изменения мгновенно сохраняются в **Jetpack DataStore** и применяются к активным обоям без перезапуска сервиса:

* **01 / Форма:** переключение между 7 встроенными 3D-сценами и пользовательским фото/видео.
* **02 / Разрешение:** плотность сетки от **40 до 200 символов** по ширине + 6 наборов символов (`Классика`, `Минимал`, `Блоки`, `Матрица`, `Брайль` и `Свой набор`).
* **03 / Движение:** скорость вращения 3D-фигур (`0–45°/с`) или воспроизведения видео (`0.25x–2.0x`), режимы `Заполнить / Вместить`, зацикливание и ограничитель частоты кадров (**30 / 45 / 60 / 90 / 120 FPS**).
* **04 / Штрихи и эффекты:** толщина штрихов 3D-сцен (`0.5×–2.0×`), контраст, яркость, фосфорное свечение (**Glow**) и **CRT-сканлайны**.
* **05 / Цвет:** режимы `Монохром`, `Оригинал`, `Дуотон`, готовые цветовые пары (`Белый`, `Терминал`, `Material You`, `Янтарь`, `Кибер`, `Инверт`), инверсия яркости и переключатель динамической темы интерфейса.

---

## 📲 Установка APK

1. Перейдите на страницу **[Releases](https://github.com/dizofdx/AsciiLiveWallpaper/releases/latest)** и скачайте последний файл `AsciiLiveWallpaper-v*.apk`.
2. Откройте скачанный файл на смартфоне (Android 8.0 или выше) и при запросе разрешите установку из неизвестных источников.
3. Запустите **ASCII-обои**, настройте сцену или выберите фото/видео из галереи.
4. Нажмите кнопку **«Установить обои»** в нижней панели и подтвердите установку на рабочий стол (и экран блокировки).

> **Примечание:** если ранее была установлена тестовая Debug-версия, удалите её перед установкой подписанного Release APK.

---

## 🛠️ Сборка из исходников

### Требования
* **Android Studio** Ladybug / Meerkat или новее
* **JDK 17** или **JDK 21** (Gradle 8.x не поддерживает JDK 25 — убедитесь, что в *Settings → Build Tools → Gradle → Gradle JDK* выбран JDK 17/21)
* **Android SDK 35**

### Шаги
```bash
git clone [https://github.com/dizofdx/AsciiLiveWallpaper.git](https://github.com/dizofdx/AsciiLiveWallpaper.git)
cd AsciiLiveWallpaper
./gradlew assembleDebug
```

Готовый APK появится в `app/build/outputs/apk/debug/app-debug.apk`. Также в репозитории настроен workflow **GitHub Actions** (`.github/workflows/build-apk.yml`) для автоматической облачной сборки.

---

## 🏗️ Стек технологий

* **Язык:** Kotlin, Coroutines, StateFlow
* **UI:** Jetpack Compose, Material 3, Edge-to-Edge
* **Графика:** OpenGL ES 3.0 (`GLSL ES 3.00`), кастомный `EglRenderThread` в `WallpaperService`
* **Видео:** AndroidX Media3 (`ExoPlayer`) → `SurfaceTexture` (`GL_OES_EGL_image_external_essl3`)
* **Хранилище:** Jetpack DataStore Preferences
