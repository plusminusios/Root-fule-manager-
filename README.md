# Root File Manager

Файловый менеджер для Android. Работает и без root — с доступом к обычному хранилищу. С root можно лазить по системным разделам, менять права, ставить APK тихо, перезагружать в recovery и т.д.

Стек: Kotlin, Jetpack Compose, Material 3.

## Что умеет

**Файлы** — просмотр, поиск, создание папок/файлов, переименование, удаление. Быстрые переходы в хранилище, «Загрузки» и корень `/`. Долгий тап по элементу — контекстное меню.

**Редактор** — открывает текстовые файлы прямо в приложении. Подсветка комментариев, настраиваемый размер шрифта.

**Приложения** — список установленных пакетов, информация, удаление (где доступно).

**ИИ** — анализ logcat/dmesg и простой чат через Gemini API. Вход через Google аккаунт; ключ API вводится вручную и хранится только на устройстве.

**Настройки** — тема, цвет акцента, сортировка, скрытые файлы, стартовая папка. Для Pixel есть блокировка/разблокировка OTA (нужен root). Там же reboot, recovery, soft reboot.

Root не обязателен. Без него приложение работает как обычный проводник по `/storage/emulated/0`, но для полного доступа к файлам на Android 11+ нужно выдать разрешение «Доступ ко всем файлам».

## Требования

- Android 8.0+ (API 26)
- Android Studio Hedgehog или новее для сборки
- JDK 17

## Сборка

```bash
git clone https://github.com/YOUR_USERNAME/root-file-manager.git
cd root-file-manager
```

Открыть проект в Android Studio → **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

Debug APK:

```
app/build/outputs/apk/debug/app-debug.apk
```

Gradle wrapper в репозитории может отсутствовать — Studio подтянет его сама при первом открытии.

## Первый запуск

1. Открой вкладку **Файлы**.
2. Нажми **Разрешить** и выдай доступ к хранилищу (на Android 11+ — «Разрешить доступ ко всем файлам»).
3. По умолчанию открывается внутреннее хранилище, не корень системы.

## Google Sign-In и Gemini

Для вкладки **ИИ** нужно:

1. Создать проект в [Google Cloud Console](https://console.cloud.google.com/).
2. **Обязательно:** OAuth client типа **Android** — package `com.example` + SHA-1 от keystore.
   - SHA-1: `.\gradlew.bat signingReport` или смотрите подсказку в приложении при ошибке входа (код 10).
3. **Опционально:** Web client ID в `local.properties` (скопируйте из `local.properties.example`):

```properties
GOOGLE_WEB_CLIENT_ID=XXXX.apps.googleusercontent.com
```

4. Ключ Gemini взять на [aistudio.google.com](https://aistudio.google.com) и ввести в приложении после входа через Google.

`firebase-applet-config.json` в git не коммитим — там ключи.

## Структура

```
app/src/main/java/com/example/
  MainActivity.kt      — UI, проводник, редактор, настройки
  AITab.kt             — Google Sign-In, Gemini
  GeminiApi.kt         — запросы к API
  AppPreferences.kt    — настройки пользователя
  StorageHelper.kt     — разрешения и пути
```

## Контакты

Telegram: [@PMFileManager](https://t.me/PMFileManager)

GitHub: [plusminusios/Root-file-manager-](https://github.com/plusminusios/Root-file-manager-)

## Лицензия

Без лицензии — бери и делай что хочешь, но сам отвечаешь за последствия.