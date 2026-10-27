# Развёртывание игрового сервера

Основной вариант — **VPS на Beget** (`159.194.210.112`, Ubuntu 24.04), где уже работают d-portret.ru и
налог-сервис.рф: см. раздел «Beget» ниже. Раздел «Любой другой VPS» — запасной вариант на чистом сервере.

## Beget: выкладка без SSH

Сервер сам забирает новые сборки — так же устроена выкладка налог-сервис.рф на этом же VPS.

```
CI (GitHub Actions): тесты зелёные → :server:installDist → ветка deploy/chess-server-dist (только сборка)
VPS: таймер systemd раз в 5 минут → chess-pull.sh → /opt/chess/current → перезапуск контейнера → /health
```

- **Безопасность.** Из git на сервер приходит только сборка (jar-файлы). Она работает в контейнере под
  непривилегированным пользователем, без записи на диск, с доступом только к своей базе. Скрипт выкладки,
  docker-compose и конфиг nginx ставятся один раз руками и из git не обновляются: коммит не может поменять
  настройки самого сервера или задеть соседние сайты.
- **Откат.** Если новая сборка не ответила на `/health` (или контейнер падает), скрипт за секунды возвращает
  предыдущую и больше не пытается ставить эту сборку — до следующего коммита.
- **Партии.** Перезапуск обрывает идущие партии (они в памяти), поэтому новая сборка ждёт, пока они
  закончатся, но не дольше 30 минут.
- **HTTPS.** Как только DNS домена начнёт указывать на сервер, скрипт сам выпустит сертификат (certbot) и
  включит перенаправление на https.
- **Статус** виден снаружи без входа на сервер: `https://chess.4vic.ru/deploy-status.json` — какая сборка
  работает, результат последней выкладки, `/health` (версия, игроков онлайн, идущих партий), история событий.
- Образы PostgreSQL и Java берутся с зеркала `mirror.gcr.io`: Docker Hub из России работает ненадёжно.

### Разовая настройка (под root, около 10 минут)

```bash
curl -fsSL https://raw.githubusercontent.com/nikolaylobanovrus/MobCheckMate/claude/busy-meitner-2gl6it/deploy/beget/setup.sh -o /tmp/chess-setup.sh
less /tmp/chess-setup.sh     # посмотреть, что он сделает
bash /tmp/chess-setup.sh
```

Скрипт ставит Docker из репозиториев Ubuntu (если его нет), создаёт `/opt/chess` со случайным паролем базы,
добавляет сайт `chess.4vic.ru` в nginx (соседние сайты не трогает, перед перезагрузкой — `nginx -t`), выкладывает
первую сборку, включает таймер, ежедневную копию базы (`/var/backups/chess`, 30 дней) и ротацию журнала.
Повторный запуск безопасен.

### DNS

У домена 4vic.ru сейчас (27.10.2026) не отвечают DNS-серверы: публичные резолверы Google и Cloudflare
возвращают ошибку «No Reachable Authority». Пока это так, `chess.4vic.ru` не откроется ни у кого. У регистратора
домена нужно указать рабочие DNS-серверы (например, DNS Beget: `ns1.beget.com`, `ns2.beget.com`,
`ns1.beget.pro`, `ns2.beget.pro`, если домен обслуживается там) и добавить A-запись `chess` → `159.194.210.112`.

### Полезное на сервере

```bash
systemctl list-timers | grep chess                 # таймер жив
tail -n 20 /var/log/chess-deploy.log               # журнал выкладок
docker compose -p chess --project-directory /opt/chess logs --tail 100 server   # журнал игрового сервера
cat /opt/chess/releases/*.failed                   # почему не поднялась сборка
ln -sfn /opt/chess/releases/<сборка> /opt/chess/current && docker compose -p chess --project-directory /opt/chess up -d --force-recreate server   # откат руками
```

Настройки — в `/opt/chess/.env`: `BOT_OPPONENTS=on|off`, `BOT_WAIT_SECONDS`, `CHESS_PORT` (по умолчанию 18080,
только для nginx на этом же сервере). После правки: `docker compose -p chess --project-directory /opt/chess up -d`.

Ветку, из которой CI публикует сборку, задаёт `.github/workflows/ci.yml` (задача `publish-server`): сейчас это
`claude/busy-meitner-2gl6it` и `main`.

## Любой другой VPS

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
Реквизиты оператора (ООО «СИТИОПТОМ», ОГРН, ИНН, адрес) уже вписаны. Остаётся завести ящик support@4vic.ru.

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
