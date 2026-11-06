# Сторонние компоненты и лицензии

Apache 2.0 требует передавать копию лицензии и сохранять уведомления об авторских правах. В приложении это сделано:
«Настройки» → «О приложении» → «Лицензии открытых компонентов» показывает `androidApp/src/main/assets/licenses.txt`
(перечень ниже и полные тексты BSD, SIL OFL и Apache 2.0). При добавлении компонента обновляйте оба файла.

## Попадают в приложение

| Компонент | Где используется | Лицензия | Примечание |
|---|---|---|---|
| [kchesslib](https://github.com/cvb941/kchesslib) 1.0.5 | `shared` (клиент и сервер) | Apache-2.0 | Мультиплатформенный порт chesslib |
| [chesslib](https://github.com/bhlangonijr/chesslib) | исходный код kchesslib | Apache-2.0 | © Ben-Hur Carlos Vieira Langoni Junior |
| [Фигуры Cburnett](https://commons.wikimedia.org/wiki/Category:SVG_chess_pieces/Standard_transparent) | `androidApp/src/main/res/drawable/piece_*.xml` | BSD-3-Clause (автор выпустил под GFDL, BSD и GPL; берём BSD) | © 2006 Colin M.L. Burnett. Классический набор, как в Википедии и lichess. SVG с Wikimedia Commons сконвертированы в VectorDrawable скриптом `tools/svg_to_vector.py` без изменения формы. Текст лицензии: `third_party/cburnett-pieces/LICENSE.txt` |
| [Manrope](https://github.com/googlefonts/manrope) | `androidApp/src/main/res/font/manrope.ttf` | SIL OFL 1.1 | Шрифт интерфейса (переменный, латиница и кириллица). Китайский текст рисуется системным шрифтом. Текст лицензии: `third_party/manrope/OFL.txt` |
| [Lichess puzzle database](https://database.lichess.org/#puzzles) | `androidApp/src/main/assets/puzzles.tsv` | CC0 1.0 | 29 505 задач, отобраны скриптом `tools/extract_puzzles.py`. Ссылка на источник не обязательна, но указываем её из уважения |
| Material Symbols Rounded | `androidApp/src/main/res/drawable/ic_*.xml` (кроме `ic_launcher_*`) | Apache-2.0 | Google. Иконки интерфейса, сконвертированы `tools/svg_to_vector.py` |
| Конь Cburnett в иконке приложения | `androidApp/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `docs/store/icon_512.png` | BSD-3-Clause | Тот же конь из набора Cburnett, залит золотым градиентом |
| kotlinx.serialization, kotlinx.coroutines | `shared`, `androidApp` | Apache-2.0 | |
| Ktor client, OkHttp, Okio | `androidApp` (соединение с сервером) | Apache-2.0 | |
| AndroidX (AppCompat: язык приложения и тёмная тема), Jetpack Compose | `androidApp` | Apache-2.0 | |

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
- Копия фигур cburnett из репозитория lichess — там они под GPLv2+. Мы берём оригиналы автора с Wikimedia Commons,
  где он выпустил их и под BSD; используем именно BSD.
- dartchess, chessground — GPL-3.0.
- Stockfish в клиенте — GPL-3.0 (на сервере допустимо, см. ADR 0002).
