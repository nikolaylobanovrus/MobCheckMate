# ADR 0001. Клиентский стек: Kotlin, Jetpack Compose, Kotlin Multiplatform

Статус: принято, 26.10.2026.

## Контекст
Нужен Android-клиент онлайн-шахмат с маленьким APK, быстрым UI доски и возможностью позже выпустить iOS-версию.
Команда — 1–2 разработчика в России. Приложение закрытое (не open source).

## Рассмотренные варианты
- **Kotlin + Jetpack Compose (+ KMP для общей логики).** Самый маленький APK, лучший пул разработчиков в России,
  доска на Canvas, iOS через Compose Multiplatform (стабилен для iOS с версии 1.8.0, май 2025).
- **Flutter.** Быстрая разработка и iOS сразу, но лучшие шахматные Dart-библиотеки (dartchess, chessground от
  lichess) под GPL-3.0 и не подходят для закрытого приложения. Пакеты под MIT (bishop, squares) не обновлялись
  2–3 года.
- **Unity, Godot.** 30–60 МБ к размеру APK ради физики и 3D, которых в шахматах нет.

## Решение
- Клиент на Kotlin и Jetpack Compose, доска рисуется на Canvas.
- Общая логика (правила, рейтинг, правила турниров, протокол) — в модуле `shared` на Kotlin Multiplatform.
  Сейчас собирается только JVM-таргет; iOS-таргеты добавляются, когда начнётся iOS-версия.
- Правила шахмат — **kchesslib** (`io.github.cvb941:kchesslib`, Apache-2.0 по POM на Maven Central). Это
  мультиплатформенный порт chesslib (Apache-2.0) с тем же API (`com.github.bhlangonijr.chesslib`), собранный под
  JVM, iOS, JS и Wasm.
- Сборка: AGP 9.4 со встроенной поддержкой Kotlin, Gradle 9.8, Kotlin 2.4, Java 17 bytecode.
- `compileSdk` 37: текущие AndroidX (Compose 1.12, core 1.19) требуют API 37 и AGP 9.1+.
  `targetSdk` 36 — минимум Google Play с 31.08.2026; переход на 37 — отдельной задачей после проверки
  изменений поведения Android 17. `minSdk` 26.
- Идентификатор приложения `ru.fourvic.chess` построен от домена владельца 4vic.ru. Часть «4vic» записана как
  «fourvic»: Android требует, чтобы каждая часть идентификатора начиналась с буквы. Пакеты Kotlin используют тот же
  префикс: `ru.fourvic.chess` (Android), `ru.fourvic.chess.shared`, `ru.fourvic.chess.server`.
- Варианты сборки (flavors): `play` и `rustore`; позже `appgallery`. Отличаются платежами, push-транспортом и
  ссылками на магазин.

## Последствия
- Любая логика, которая нужна и клиенту, и серверу, пишется в `shared/commonMain` без JVM-специфичных API.
- Плагин `org.jetbrains.kotlin.android` в Android-модуле не подключается: в AGP 9 Kotlin встроен.
- Нативный код уже есть транзитивно (`libandroidx.graphics.path.so` из Compose). Каждая релизная сборка
  проверяется на выравнивание под страницы памяти 16 КБ (`zipalign -c -P 16`).
- Если kchesslib перестанет поддерживаться, у него тот же API, что у chesslib, и на JVM можно переключиться на
  оригинал без изменения кода.
