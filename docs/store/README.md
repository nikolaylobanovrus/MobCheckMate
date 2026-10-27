# Карточки в Google Play и RuStore

Всё, что нужно вставить в консоли магазинов. Картинки в этой папке готовы к загрузке и пересобираются командой
`./gradlew :androidApp:recordRoborazziPlayDebug -PstoreAssets --tests '*StoreAssets*'`.

| Файл | Куда |
|---|---|
| `icon_512.png` | Google Play: значок 512×512; RuStore: иконка 512×512 |
| `ru/feature_graphic.png`, `en/feature_graphic.png` | Google Play: графический баннер 1024×500 |
| `ru/screenshots/*.png`, `en/screenshots/*.png` | Скриншоты телефона 1080×1920, по порядку номеров |

## Название

**Chess Victory** — и в русской, и в английской карточке, и под иконкой на телефоне. Поиск по Google Play
(27.10.2026) приложений с таким названием не нашёл; RuStore на автоматический запрос не ответил — проверьте поиском
в приложении RuStore перед публикацией. Если планируется регистрация товарного знака, стоит посмотреть базы ФИПС и WIPO.

## Русская карточка

**Название (до 30 знаков):** Chess Victory: шахматы онлайн

**Краткое описание (до 80):** Играйте с людьми и ботом, решайте задачи, побеждайте в бесплатных турнирах

**Полное описание:**

> Шахматы для тех, кто любит живую игру. Партия с соперником начинается через несколько секунд, а каждый час стартует
> новый турнир.
>
> ИГРА ОНЛАЙН
> • Подбор соперника по силе: пуля, блиц и рапид — от 1+0 до 15+10.
> • Рейтинговые и товарищеские партии, отдельный рейтинг для каждой скорости.
> • Честные часы: время считает сервер, а задержку сети мы учитываем.
> • Связь пропала? Приложение переподключится, и партия продолжится с того же места.
> • Предход, ничья, реванш и быстрые фразы для общения без грубостей.
>
> ТУРНИРЫ
> • Арены каждый час: заходите в любой момент, играйте сколько хотите, уходите на паузу.
> • Две победы подряд включают «огонь» — очки удваиваются.
> • Берсерк: рискните половиной времени ради дополнительного очка.
> • Приватные турниры для друзей, клуба или класса — по коду из шести символов.
> • Участие во всех турнирах бесплатное.
>
> ИГРА С КОМПЬЮТЕРОМ
> • Пять уровней — от новичка до сильного любителя (около 2000).
> • Работает без интернета.
>
> ЗАДАЧИ
> • 29 505 задач с рейтингом: мат в 1–3 хода, вилки, связки, эндшпиль.
> • Задача дня и серия дней, подсказка и решение.
>
> А ЕЩЁ
> • Игра вдвоём на одном телефоне.
> • Четыре цвета доски, тёмная тема, звуки ходов.
> • Без регистрации: аккаунт создаётся сам, имя можно сменить.
> • Маленький размер — около 4 МБ.

**Что нового (первый релиз):** Первая версия: онлайн-партии, турниры каждый час, приватные турниры, бот на пяти
уровнях и 29 505 задач.

**Категория:** Игры → Настольные игры. **Теги / ключевые слова (RuStore):** шахматы, шахматы онлайн, турниры, блиц,
задачи, шахматы с компьютером, шахматы вдвоём.

## English listing

**Title (30):** Chess Victory: Online Chess

**Short description (80):** Play people and a 5-level bot, solve puzzles, win free hourly tournaments

**Full description:**

> Chess for people who love a live game. An opponent is found in seconds, and a new tournament starts every hour.
>
> PLAY ONLINE
> • Matched by strength: bullet, blitz and rapid, from 1+0 to 15+10.
> • Rated and casual games, with a separate rating for each speed.
> • Fair clocks: the server keeps time and allows for network lag.
> • Lost connection? The app reconnects and the game goes on.
> • Premoves, draw offers, rematches and quick phrases instead of an open chat.
>
> TOURNAMENTS
> • Hourly arenas: join any time, play as much as you like, pause when you need to.
> • Two wins in a row put you on fire and double your points.
> • Berserk: give up half your clock for an extra point.
> • Private tournaments for friends, clubs or classes, joined with a six-character code.
> • Every tournament is free to enter.
>
> PLAY THE COMPUTER
> • Five levels, from beginner to strong club player (about 2000).
> • Works offline.
>
> PUZZLES
> • 29,505 rated puzzles: mates in 1–3, forks, pins, endgames.
> • Daily puzzle and streaks, hints and solutions.
>
> ALSO
> • Two players on one phone.
> • Four board colours, dark theme, move sounds.
> • No sign-up: an account is created for you, and you can change your name.
> • Small download, about 4 MB.

**Category:** Games → Board.

## Google Play: раздел App content

**Data safety** (соответствует политике конфиденциальности и реальному трафику приложения 1.0):

| Вопрос | Ответ |
|---|---|
| Собирает или передаёт данные пользователей? | Да |
| Данные шифруются при передаче? | Да (wss/https) |
| Можно запросить удаление данных? | Да: в приложении и по ссылке `https://chess.4vic.ru/delete-account.html` |
| Personal info → Name | Собирается (ник игрока), не передаётся третьим лицам, обязательно для онлайн-игры. Цели: App functionality, Account management |
| Personal info → User IDs | Собирается (идентификатор аккаунта), не передаётся. Цели: App functionality, Account management, Fraud prevention, security, and compliance |
| App activity → Other actions | Собирается (сыгранные партии, рейтинги, турниры), не передаётся. Цели: App functionality |
| Остальные типы (местоположение, контакты, финансы, ID устройства, диагностика и др.) | Не собираются |

Если позже подключить рекламу, аналитику или отчёты о сбоях, Data safety и политику нужно обновить до выпуска этой версии.

**Ads:** No (в версии 1.0 рекламы нет). **App access:** All functionality available without special access
(вход не нужен). **Target audience:** 13–15, 16–17, 18+ — без отметки детских возрастов, чтобы не попасть под
программу Families; играть детям при этом не запрещено. **News app:** No. **Government app:** No.
**Financial features:** None. **Health:** None.

**Content rating (IARC):** категория «Game». Насилие, страх, сексуальный контент, ненормативная лексика, наркотики,
азартные игры — нет. «Users can interact or exchange information» — **да** (соперники видят имена друг друга и
выбранные фразы из готового списка; свободного чата нет). «Shares user location» — нет. «Digital purchases» — нет.
Ожидаемый результат: PEGI 3, ESRB Everyone, с пометкой «Users Interact».

**Privacy policy URL:** `https://chess.4vic.ru/privacy.html`

## RuStore

- Возрастное ограничение: **0+**.
- Категория: Игры → Настольные.
- Политика конфиденциальности: `https://chess.4vic.ru/privacy.html`.
- Сборка: `./gradlew :androidApp:assembleRustoreRelease` (APK подписывается ключом из `keystore.properties`).
- Скриншоты и иконка — те же, что для Google Play.

## Перед публикацией

1. Завести ящик support@4vic.ru.
2. Развернуть сервер (`docs/DEPLOY.md`) и проверить ссылки выше.
