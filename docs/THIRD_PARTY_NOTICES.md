# Сторонние компоненты и лицензии

Перед первым релизом в приложении нужен экран «Лицензии» с текстами лицензий компонентов, которые попадают в APK.
Apache 2.0 требует передавать копию лицензии и сохранять уведомления об авторских правах.

## Попадают в приложение

| Компонент | Где используется | Лицензия | Примечание |
|---|---|---|---|
| [kchesslib](https://github.com/cvb941/kchesslib) 1.0.5 | `shared` (клиент и сервер) | Apache-2.0 | Мультиплатформенный порт chesslib |
| [chesslib](https://github.com/bhlangonijr/chesslib) | исходный код kchesslib | Apache-2.0 | © Ben-Hur Carlos Vieira Langoni Junior |
| [Chessnut pieces](https://github.com/LexLuengas/chessnut-pieces) | `androidApp/src/main/res/drawable/piece_*.xml` | Apache-2.0 | © 2015 Alexis Luengas. Взято из коммита `2b8eaf14a31edad7e9deb53b1473e1d4857868a9`, SVG сконвертированы в VectorDrawable скриптом `tools/svg_to_vector_drawable.py` без изменения формы. Копия лицензии: `third_party/chessnut-pieces/` |
| kotlinx.serialization | `shared` | Apache-2.0 | |
| AndroidX, Jetpack Compose | `androidApp` | Apache-2.0 | |

## Только сервер (не распространяется пользователям)

| Компонент | Лицензия |
|---|---|
| Ktor | Apache-2.0 |
| Logback | EPL-1.0 или LGPL-2.1 |

## Только тесты и сборка

| Компонент | Лицензия |
|---|---|
| Robolectric | MIT |
| Roborazzi | Apache-2.0 |
| JUnit 4 | EPL-1.0 |

## Что сознательно не используем

- Код lichess (lila) и его ассеты — AGPL-3.0 и CC BY-NC-SA.
- Набор фигур cburnett — у lichess указан как GPLv2+.
- dartchess, chessground — GPL-3.0.
- Stockfish в клиенте — GPL-3.0 (на сервере допустимо, см. ADR 0002).
