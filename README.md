# Chess Online (MobCheckMate)

Мобильные онлайн-шахматы для Android: игра с людьми по сети, с компьютером, шахматные задачи и турниры.
Ориентир по продукту — «Chess Online – Duel friends!» (com.hagstrom.henrik.chess), но с надёжным сетевым
кодом (серверные часы, реконнект), русской локализацией, турнирами и лигами.

Публикация: Google Play (весь мир) и RuStore (Россия), затем Huawei AppGallery и Xiaomi GetApps.

## Документация

| Документ | О чём |
|---|---|
| [docs/ROADMAP.md](docs/ROADMAP.md) | Полный план: анализ референса, стек, этапы разработки, публикация, маркетинг, метрики |
| [docs/TOURNAMENTS.md](docs/TOURNAMENTS.md) | Спецификация турниров и лиг, платный вход, fair-play, модель данных |
| [docs/CHECKLIST_GOOGLE_PLAY.md](docs/CHECKLIST_GOOGLE_PLAY.md) | Чек-лист публикации в Google Play |
| [docs/CHECKLIST_RUSTORE.md](docs/CHECKLIST_RUSTORE.md) | Чек-лист публикации в RuStore |
| [docs/adr/](docs/adr/) | Архитектурные решения (ADR) |

## Структура репозитория

```
MobCheckMate/
├── shared/       # Kotlin Multiplatform: правила шахмат (kchesslib), рейтинг, правила турниров
├── androidApp/   # Android-клиент на Jetpack Compose, flavors play / rustore
├── server/       # Игровой сервер на Ktor (WebSocket)
└── docs/         # Roadmap, спецификации, чек-листы, ADR
```

Позже появятся `engine-worker/` (серверный Stockfish), `pairing/` (bbpPairings для швейцарок) и `tools/`
(импорт базы задач lichess).

## Сборка

Нужны JDK 21 и Android SDK с платформой 36. Путь к SDK задаётся в `local.properties`
(`sdk.dir=/path/to/android-sdk`) или переменной `ANDROID_HOME`.

```bash
./gradlew :shared:jvmTest            # тесты правил и турнирной логики
./gradlew :server:test               # тесты сервера
./gradlew :server:run                # сервер на http://localhost:8080
./gradlew :androidApp:assemblePlayDebug      # APK для Google Play
./gradlew :androidApp:assembleRustoreDebug   # APK для RuStore
```
