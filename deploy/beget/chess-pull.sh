#!/usr/bin/env bash
# Server half of the Chess Victory deploy channel. A systemd timer runs it every five minutes:
#   1. fetch the server build that CI publishes to the git branch deploy/chess-server-dist;
#   2. if it is new, switch /opt/chess/current to it and restart the server container next to PostgreSQL;
#   3. check /health and go back to the previous build if the new one does not answer;
#   4. once DNS points here, get the HTTPS certificate (certbot, at most one attempt an hour);
#   5. write the outcome to /var/www/chess-status/deploy-status.json, served at https://<domain>/deploy-status.json,
#      so the result can be checked from outside without logging in.
#
# Why pull, not push: the development environment reaches the outside only over HTTPS and cannot SSH here.
# The same scheme already deploys налог-сервис.рф on this server.
#
# What comes from git is only the published build (jars and the start script). It runs in a container as an
# unprivileged user with the build mounted read-only. This script, the compose file and the nginx config are installed
# once by setup.sh and never replaced from git, so a commit cannot change how the host itself is set up.
set -euo pipefail

REPO=${REPO:-https://github.com/nikolaylobanovrus/MobCheckMate.git}
BRANCH=${DEPLOY_BRANCH:-deploy/chess-server-dist}
BASE=${CHESS_BASE:-/opt/chess}
STATUS_FILE=${STATUS_FILE:-/var/www/chess-status/deploy-status.json}
PORT=${CHESS_PORT:-18080}
DOMAIN=${DOMAIN:-chess.4vic.ru}
KEEP_RELEASES=5
# A restart ends the games in progress (they live in memory), so a new build waits for them, but not forever.
MAX_WAIT_FOR_GAMES=1800

SRC=$BASE/dist-git
RELEASES=$BASE/releases
CURRENT=$BASE/current
COMPOSE=(docker compose --project-name chess --project-directory "$BASE" -f "$BASE/docker-compose.yml" --env-file "$BASE/.env")

mkdir -p "$RELEASES" "$(dirname "$STATUS_FILE")"
exec 9>"$BASE/.pull.lock"
flock -n 9 || exit 0 # the previous run is still busy

log() { echo "$(date -u +%FT%TZ) $*"; }

healthy() { # wait up to $1 seconds for /health; give up early if the container keeps crashing
    local deadline=$((SECONDS + $1)) id restarts
    while ((SECONDS < deadline)); do
        curl -fsS --max-time 3 "http://127.0.0.1:$PORT/health" >/dev/null 2>&1 && return 0
        id=$("${COMPOSE[@]}" ps -q server 2>/dev/null || true)
        restarts=$([ -n "$id" ] && docker inspect -f '{{.RestartCount}}' "$id" 2>/dev/null || echo 0)
        [ "${restarts:-0}" -gt 1 ] && return 1
        sleep 2
    done
    return 1
}

running_games() {
    curl -fsS --max-time 3 "http://127.0.0.1:$PORT/health" 2>/dev/null |
        python3 -c 'import json, sys; print(json.load(sys.stdin).get("games", 0))' 2>/dev/null || echo 0
}

restart_server() {
    "${COMPOSE[@]}" up -d --force-recreate server >/dev/null 2>&1
}

# --- 1. The build -----------------------------------------------------------------------------------------------
if [ ! -d "$SRC/.git" ]; then
    rm -rf "$SRC"
    git clone -q --depth 1 -b "$BRANCH" "$REPO" "$SRC"
else
    git -C "$SRC" fetch -q --depth 1 origin "$BRANCH"
    git -C "$SRC" reset -q --hard "origin/$BRANCH"
fi
HEAD=$(git -C "$SRC" rev-parse --short=10 HEAD)
SOURCE=$(tr -d '\n' <"$SRC/SOURCE" 2>/dev/null || echo unknown)
RELEASE=$RELEASES/$HEAD
PREVIOUS=$(readlink "$CURRENT" 2>/dev/null || true)
EVENT=false

# --- 2-3. Switch, restart, check, roll back -------------------------------------------------------------------
if [ "$PREVIOUS" = "$RELEASE" ]; then
    if healthy 5; then
        RESULT="работает"
    else
        restart_server
        if healthy 120; then RESULT="перезапущен: не отвечал на /health"; else RESULT="не отвечает на /health"; fi
        EVENT=true
    fi
elif [ -f "$RELEASE.failed" ]; then
    # Retrying a build that did not start would cost a restart every five minutes; the next commit gets a new try.
    RESULT="сборка $HEAD не поднялась, работает предыдущая"
elif games=$(running_games) && [ "$games" -gt 0 ] && [ -n "$PREVIOUS" ] &&
    [ $(($(date +%s) - $(stat -c %Y "$BASE/.waiting-$HEAD" 2>/dev/null || { touch "$BASE/.waiting-$HEAD"; date +%s; }))) -lt "$MAX_WAIT_FOR_GAMES" ]; then
    RESULT="сборка $HEAD ждёт окончания партий: идёт $games"
else
    rm -f "$BASE"/.waiting-*
    EVENT=true
    rm -rf "$RELEASE" && mkdir -p "$RELEASE"
    git -C "$SRC" archive HEAD | tar -x -C "$RELEASE"
    chmod -R a+rX "$RELEASE"
    ln -sfn "$RELEASE" "$CURRENT"
    restart_server
    if healthy 120; then
        RESULT="выложена сборка $HEAD"
    else
        # The marker keeps the server's last words for whoever looks into it.
        "${COMPOSE[@]}" logs --tail 40 server >"$RELEASE.failed" 2>&1 || touch "$RELEASE.failed"
        if [ -n "$PREVIOUS" ] && [ -d "$PREVIOUS" ]; then
            ln -sfn "$PREVIOUS" "$CURRENT"
            restart_server
            healthy 120 || true
            RESULT="откат: сборка $HEAD не ответила на /health, вернул $(basename "$PREVIOUS")"
        else
            RESULT="ошибка: сборка $HEAD не ответила на /health"
        fi
    fi
    # Keep a few previous builds to go back to by hand: ln -sfn /opt/chess/releases/<build> /opt/chess/current.
    find "$RELEASES" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' | sort -rn | tail -n +$((KEEP_RELEASES + 1)) |
        cut -d' ' -f2- | while read -r old; do [ "$old" = "$(readlink "$CURRENT")" ] || rm -rf "$old" "$old.failed"; done
fi
HEALTH=$(curl -fsS --max-time 3 "http://127.0.0.1:$PORT/health" 2>/dev/null || echo '{}')

# --- 4. HTTPS ---------------------------------------------------------------------------------------------------
TLS="есть"
if [ ! -d "/etc/letsencrypt/live/$DOMAIN" ]; then
    TLS="нет: домен $DOMAIN ещё не указывает на этот сервер"
    target=$(getent ahostsv4 "$DOMAIN" 2>/dev/null | awk 'NR == 1 { print $1 }' || true) # empty while DNS does not resolve
    if [ -n "$target" ] && hostname -I | tr ' ' '\n' | grep -qx "$target"; then
        stamp=$BASE/.certbot-attempt
        if ! command -v certbot >/dev/null; then
            TLS="нет: не установлен certbot"
        elif [ -f "$stamp" ] && [ $(($(date +%s) - $(stat -c %Y "$stamp"))) -lt 3600 ]; then
            TLS="нет: certbot не смог, следующая попытка в течение часа"
        else
            touch "$stamp"
            EVENT=true
            if certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos --redirect --register-unsafely-without-email >/dev/null 2>&1; then
                TLS="выпущен"
                rm -f "$stamp"
            else
                TLS="нет: certbot не смог, следующая попытка в течение часа"
            fi
        fi
    fi
fi

# --- 5. Status --------------------------------------------------------------------------------------------------
python3 - "$STATUS_FILE" "$HEAD" "$SOURCE" "$RESULT" "$TLS" "$HEALTH" "$EVENT" <<'PY'
import datetime, json, pathlib, sys
path, build, source, result, tls, health, event = sys.argv[1:8]
now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
f = pathlib.Path(path)
try:
    prev = json.loads(f.read_text())
except Exception:
    prev = {}
try:
    health = json.loads(health)
except Exception:
    health = {}
# Only events go to the history; otherwise 288 identical "running" lines a day would push everything useful out.
history = prev.get("history", [])
if event == "true":
    history = ([{"at": now, "build": build, "result": result, "tls": tls}] + history)[:10]
f.write_text(json.dumps({
    "build": build,
    "source": source,
    "checked_at": now,
    "result": result,
    "health": health,
    "tls": tls,
    "history": history,
}, ensure_ascii=False, indent=2) + "\n")
f.chmod(0o644)
PY

log "$HEAD: $RESULT; TLS: $TLS"
