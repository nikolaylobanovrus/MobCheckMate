#!/usr/bin/env bash
# One-time setup of the Chess Victory game server on the Beget VPS (Ubuntu 24.04, nginx already serving other
# sites). Run as root:
#
#   curl -fsSL https://raw.githubusercontent.com/nikolaylobanovrus/MobCheckMate/claude/busy-meitner-2gl6it/deploy/beget/setup.sh -o /tmp/chess-setup.sh
#   less /tmp/chess-setup.sh     # see what it does
#   bash /tmp/chess-setup.sh
#
# What it does, and nothing else:
#   - installs Docker from the Ubuntu repositories if it is missing;
#   - creates /opt/chess with the compose file, a generated database password and the pull script;
#   - adds the nginx site for chess.4vic.ru (other sites are not touched; nginx -t before reload);
#   - starts the first build and the five-minute timer that deploys new builds (see chess-pull.sh);
#   - adds a daily database backup and log rotation.
# Running it again is safe: existing settings and the database stay.
set -euo pipefail

DOMAIN=${DOMAIN:-chess.4vic.ru}
FILES_BRANCH=${FILES_BRANCH:-claude/busy-meitner-2gl6it}
RAW=https://raw.githubusercontent.com/nikolaylobanovrus/MobCheckMate/$FILES_BRANCH/deploy/beget
BASE=/opt/chess
PORT=18080

say() { printf '\n== %s\n' "$*"; }

[ "$(id -u)" = 0 ] || { echo "Run as root" >&2; exit 1; }

say "DNS on the server"
# Without name resolution neither git nor certbot work (seen on this server before).
getent hosts github.com >/dev/null || {
    mkdir -p /etc/systemd/resolved.conf.d
    printf '[Resolve]\nDNS=77.88.8.8 8.8.8.8\nFallbackDNS=1.1.1.1\n' >/etc/systemd/resolved.conf.d/dns.conf
    systemctl restart systemd-resolved
    getent hosts github.com >/dev/null || { echo "github.com does not resolve" >&2; exit 1; }
}

say "Port $PORT"
if ss -ltn "( sport = :$PORT )" | grep -q LISTEN && ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^chess-server'; then
    echo "Port $PORT is taken by another program; set another one in $BASE/.env (CHESS_PORT) and nginx" >&2
    exit 1
fi

say "Memory"
# PostgreSQL and the server are capped at 512 MB and 768 MB; the neighbours on this VPS need theirs too.
avail_mb=$(awk '/MemAvailable/ { print int($2 / 1024) }' /proc/meminfo)
if [ "$avail_mb" -lt 1300 ]; then
    echo "Warning: only ${avail_mb} MB of memory available; the chess server needs about 1.3 GB. Continuing."
fi

say "Docker"
if ! command -v docker >/dev/null; then
    apt-get update -q
    apt-get install -y -q docker.io docker-compose-v2
fi
docker compose version >/dev/null 2>&1 || apt-get install -y -q docker-compose-v2 || apt-get install -y -q docker-compose-plugin
systemctl enable --now docker
command -v certbot >/dev/null || apt-get install -y -q certbot python3-certbot-nginx

say "Files in $BASE"
mkdir -p "$BASE/releases" /var/www/chess-status /var/backups/chess
fetch() { curl -fsSL "$RAW/$1" -o "$2"; }
fetch docker-compose.yml "$BASE/docker-compose.yml"
fetch chess-pull.sh "$BASE/chess-pull.sh"
chmod 755 "$BASE/chess-pull.sh"
if [ ! -f "$BASE/.env" ]; then
    umask 077
    {
        echo "DB_PASSWORD=$(openssl rand -hex 24)"
        echo "BOT_OPPONENTS=on"
        echo "BOT_WAIT_SECONDS=8"
        echo "CHESS_PORT=$PORT"
    } >"$BASE/.env"
    umask 022
fi

say "nginx site $DOMAIN"
if [ ! -f /etc/nginx/sites-available/chess ]; then
    fetch nginx-chess.conf /etc/nginx/sites-available/chess
    sed -i "s/server_name chess.4vic.ru;/server_name $DOMAIN;/" /etc/nginx/sites-available/chess
fi
ln -sf /etc/nginx/sites-available/chess /etc/nginx/sites-enabled/chess
nginx -t
systemctl reload nginx

say "First deploy"
DOMAIN=$DOMAIN "$BASE/chess-pull.sh"

say "Timer, backups, log rotation"
fetch chess-pull.service /etc/systemd/system/chess-pull.service
fetch chess-pull.timer /etc/systemd/system/chess-pull.timer
if [ "$DOMAIN" != chess.4vic.ru ]; then
    mkdir -p /etc/systemd/system/chess-pull.service.d
    printf '[Service]\nEnvironment=DOMAIN=%s\n' "$DOMAIN" >/etc/systemd/system/chess-pull.service.d/domain.conf
fi
systemctl daemon-reload
systemctl enable --now chess-pull.timer
fetch chess-backup /etc/cron.daily/chess-backup
chmod 755 /etc/cron.daily/chess-backup
printf '/var/log/chess-deploy.log {\n    weekly\n    rotate 4\n    compress\n    missingok\n    notifempty\n}\n' >/etc/logrotate.d/chess-deploy

say "Done"
curl -fsS "http://127.0.0.1:$PORT/health" && echo
cat /var/www/chess-status/deploy-status.json
cat <<EOF

Next:
  1. DNS: an A record for $DOMAIN pointing to this server: $(hostname -I | awk '{print $1}')
  2. The certificate is issued by itself within five minutes after DNS works (or now: certbot --nginx -d $DOMAIN).
  3. Status from anywhere: https://$DOMAIN/deploy-status.json (http:// until the certificate is there).
EOF
