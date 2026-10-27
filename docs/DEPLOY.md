# Развёртывание игрового сервера

Сервер — один Docker-контейнер с Ktor, рядом PostgreSQL и Caddy, который сам получает и продлевает HTTPS-сертификат.
Всё описано в `deploy/docker-compose.yml`. Стек проверен целиком: сборка образа, старт с PostgreSQL, HTTPS и
WebSocket через Caddy, партия настоящего клиента приложения с сохранением аккаунтов и партий в базе.

## 1. Сервер

- VPS **в России**: персональные данные россиян по закону № 242-ФЗ хранятся в РФ, это же указано в политике
  конфиденциальности. Подойдут Selectel, Timeweb Cloud, Yandex Cloud, VK Cloud.
- Ubuntu 24.04, 2 vCPU, 2–4 ГБ памяти, 20 ГБ диска. Этого хватает на тысячи одновременных партий.
- Открытые порты: 22, 80, 443.

```bash
sudo ufw allow 22,80,443/tcp && sudo ufw allow 443/udp && sudo ufw enable
curl -fsSL https://get.docker.com | sh
```

## 2. Домен

В DNS-зоне 4vic.ru добавьте A-запись `chess` → IP сервера. Проверка: `dig +short chess.4vic.ru`.
Адреса `wss://chess.4vic.ru/ws` (игра) и `https://chess.4vic.ru` (документы) уже зашиты в релизную сборку.
Если домен будет другим, поменяйте `SERVER_URL` и `SITE_URL` в `androidApp/build.gradle.kts`.

## 3. Запуск

```bash
git clone https://github.com/nikolaylobanovrus/MobCheckMate.git && cd MobCheckMate
cp deploy/.env.example deploy/.env
sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=')|" deploy/.env
docker compose -f deploy/docker-compose.yml up -d --build
```

Если репозиторий закрытый, для клонирования нужен токен GitHub с правом чтения или deploy key.
Первая сборка занимает 5–10 минут. Проверка:

```bash
curl https://chess.4vic.ru/health          # {"status":"ok","version":"0.4.0","protocol":1}
docker compose -f deploy/docker-compose.yml logs -f server
```

Страницы для магазинов: `https://chess.4vic.ru/privacy.html`, `/terms.html`, `/delete-account.html`.
Перед публикацией замените в них жёлтые заглушки `[ФИО …]`, `[ИНН …]`, `[адрес]` в
`server/src/main/resources/site/` и заведите ящик support@4vic.ru.

## 4. Резервные копии

Ежедневный дамп базы, хранение 30 дней (срок указан в политике конфиденциальности):

```bash
sudo mkdir -p /var/backups/chess
sudo tee /etc/cron.daily/chess-backup > /dev/null <<'CRON'
#!/bin/sh
cd /root/MobCheckMate
docker compose -f deploy/docker-compose.yml exec -T db pg_dump -U chess chess | gzip > /var/backups/chess/chess-$(date +%F).sql.gz
find /var/backups/chess -name '*.sql.gz' -mtime +30 -delete
CRON
sudo chmod +x /etc/cron.daily/chess-backup
```

Восстановление: `gunzip -c файл.sql.gz | docker compose -f deploy/docker-compose.yml exec -T db psql -U chess chess`.
Копии стоит раз в неделю забирать на другой сервер или в объектное хранилище.

## 5. Обновление

```bash
git pull && docker compose -f deploy/docker-compose.yml up -d --build
```

Идущие партии и таблицы идущих арен хранятся в памяти и при перезапуске теряются; аккаунты, рейтинги и сыгранные
партии остаются в базе. Обновляйте ночью, когда игроков меньше всего. Протокол версионирован: если когда-нибудь
поменяется `PROTOCOL_VERSION`, старые версии приложения увидят просьбу обновиться.

## 6. Без Docker и для разработки

```bash
./gradlew :server:run                                  # в памяти, http://localhost:8080
DATABASE_URL=jdbc:postgresql://localhost:5432/chess DATABASE_USER=chess DATABASE_PASSWORD=… ./gradlew :server:run
```

Эмулятор Android видит компьютер по адресу 10.0.2.2: отладочная сборка подключается к `ws://10.0.2.2:8080/ws`.
Для телефона в той же сети адрес меняется в отладочной сборке: «Настройки» → «Адрес сервера», например
`ws://192.168.1.10:8080/ws`.

## Переменные окружения сервера

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `PORT` | 8080 | Порт HTTP и WebSocket |
| `DATABASE_URL` | — | JDBC-адрес PostgreSQL; без него данные хранятся в памяти |
| `DATABASE_USER`, `DATABASE_PASSWORD` | — | Учётные данные базы |
| `JAVA_OPTS` | в образе: `-XX:MaxRAMPercentage=75` | Параметры JVM |
| `BOT_OPPONENTS` | `on` | Компьютерный соперник, если за время ожидания человек не нашёлся; `off` отключает |
| `BOT_WAIT_SECONDS` | 8 | Сколько ждать человека (к этому добавляется случайно до 6 секунд) |
