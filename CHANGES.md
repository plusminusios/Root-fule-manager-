# Root File Manager — Changelog v2.0

## Исправленные баги

### GeminiApi.kt
- **[КРИТИЧНО] Неправильная модель**: `gemini-3.1-pro-preview` → `gemini-1.5-flash`
  (старая модель не существует — все запросы к ИИ молча падали с 404)
- **[БАГ] Мёртвый код + ошибка переменной**: функция вычисляла `finalKey`, но
  в сетевой вызов передавала оригинальный `apiKey`. Исправлено — используется
  напрямую переданный ключ.
- **[NEW]** Добавлена функция `chatWithGemini()` для свободного чата.
- **[NEW]** Детальная обработка HTTP-ошибок: 401/403 (неверный ключ), 429 (лимит),
  500/503 (сервер), UnknownHostException (нет интернета), SocketTimeoutException.
- Добавлен разбор поля `error` в ответе Gemini API.

### AITab.kt
- **[КРИТИЧНО] Фейковый Google Sign-In**: кнопка "Войти через Google" просто
  записывала строку `AIStudio_Google_Logged_In_Token` как API-ключ. ИИ не работал
  никогда — строка-заглушка отклоняется сервером.
  → **Реализован настоящий Google Sign-In** через `GoogleSignInClient` из
    `play-services-auth`. Получает реальные email и displayName.
- Добавлен диалог ввода Gemini API ключа с кнопкой показать/скрыть.
- Предупреждение-баннер если ключ не введён.
- Кнопка выхода из аккаунта Google (с реальным `googleSignInClient.signOut()`).
- Добавлена вкладка **AI Чат** для свободных вопросов об Android/Linux.
- Кнопка очистки ответа.

### build.gradle.kts / libs.versions.toml
- **[КРИТИЧНО] Build failure**: плагин `secrets-gradle-plugin` с AGP 8.4.1 + Gradle 9.x
  вызывал `Cannot mutate dependencies of configuration after resolved`.
  → Плагин удалён. API ключ теперь вводится пользователем в приложении и хранится
    в `SharedPreferences` (не встраивается в APK).
- AGP обновлён: `8.4.1` → `8.5.2` (полная совместимость с Gradle 9.x).
- compileSdk / targetSdk: `34` → `35`.
- Добавлена зависимость `play-services-auth:21.2.0`.

### AndroidManifest.xml
- Добавлен `android:windowSoftInputMode="adjustResize"` (клавиатура не перекрывает UI).
- Добавлен `android:usesCleartextTraffic="false"` (только HTTPS, безопаснее).

## Как получить Gemini API ключ

1. Откройте https://aistudio.google.com
2. Нажмите **Get API key** → **Create API key**
3. Скопируйте ключ
4. В приложении откройте вкладку **ИИ** → войдите через Google → введите ключ

Ключ хранится только локально на устройстве.
