# Сторонние компоненты и лицензии

Apache 2.0 требует передавать копию лицензии и сохранять уведомления об авторских правах. В приложении это сделано:
«Настройки» → «О приложении» → «Лицензии открытых компонентов» показывает `androidApp/src/main/assets/licenses.txt`
(перечень ниже и полный текст Apache 2.0). При добавлении компонента обновляйте оба файла.

## Попадают в приложение

| Компонент | Где используется | Лицензия | Примечание |
|---|---|---|---|
| [kchesslib](https://github.com/cvb941/kchesslib) 1.0.5 | `shared` (клиент и сервер) | Apache-2.0 | Мультиплатформенный порт chesslib |
| [chesslib](https://github.com/bhlangonijr/chesslib) | исходный код kchesslib | Apache-2.0 | © Ben-Hur Carlos Vieira Langoni Junior |
| [Chessnut pieces](https://github.com/LexLuengas/chessnut-pieces) | `androidApp/src/main/res/drawable/piece_*.xml` | Apache-2.0 | © 2015 Alexis Luengas. Взято из коммита `2b8eaf14a31edad7e9deb53b1473e1d4857868a9`, SVG сконвертированы в VectorDrawable скриптом `tools/svg_to_vector_drawable.py` без изменения формы. Копия лицензии: `third_party/chessnut-pieces/` |
| [Lichess puzzle database](https://database.lichess.org/#puzzles) | `androidApp/src/main/assets/puzzles.tsv` | CC0 1.0 | 29 505 задач, отобраны скриптом `tools/extract_puzzles.py`. Ссылка на источник не обязательна, но указываем её из уважения |
| Material Symbols «arrow_back» | `androidApp/src/main/res/drawable/ic_arrow_back.xml` | Apache-2.0 | Google |
| Chessnut knight в иконке приложения | `androidApp/src/main/res/drawable/ic_launcher_foreground.xml`, `docs/store/icon_512.png` | Apache-2.0 | Тот же конь из набора Chessnut, перекрашен |
| kotlinx.serialization, kotlinx.coroutines | `shared`, `androidApp` | Apache-2.0 | |
| Ktor client, OkHttp, Okio | `androidApp` (соединение с сервером) | Apache-2.0 | |
| AndroidX, Jetpack Compose | `androidApp` | Apache-2.0 | |

## Только сервер (не распространяется пользователям)

| Компонент | Лицензия |
|---|---|
| Ktor | Apache-2.0 |
| Logback | EPL-1.0 или LGPL-2.1 |
| PostgreSQL JDBC Driver | BSD-2-Clause |
| HikariCP | Apache-2.0 |
| PostgreSQL (образ в `deploy/`) | PostgreSQL License |
| Caddy (образ в `deploy/`) | Apache-2.0 |
| Eclipse Temurin (базовый образ сервера) | GPL-2.0 with Classpath Exception |

Звуки (`androidApp/src/main/res/raw`) синтезированы скриптом `tools/generate_sounds.py`, сторонних лицензий нет.

## Только тесты и сборка

| Компонент | Лицензия |
|---|---|
| Robolectric | MIT |
| Roborazzi | Apache-2.0 |
| JUnit 4 | EPL-1.0 |
| H2 Database (тесты хранилища) | MPL-2.0 или EPL-1.0 |
| Stockfish (локальная калибровка уровней бота, не входит в репозиторий и приложение) | GPL-3.0 |

## Что сознательно не используем

- Код lichess (lila) и его ассеты — AGPL-3.0 и CC BY-NC-SA.
- Набор фигур cburnett — у lichess указан как GPLv2+.
- dartchess, chessground — GPL-3.0.
- Stockfish в клиенте — GPL-3.0 (на сервере допустимо, см. ADR 0002).
