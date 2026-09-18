#!/usr/bin/env bash
# Dev lifecycle helper. Usage:
#   ./scripts/dev.sh up      start both services (Neon if .env exists, else H2)
#   ./scripts/dev.sh down    stop both services
#   ./scripts/dev.sh status  quick health check
#
# .env handling: credentials embedded in DB_URL (owner role) take precedence
# over separate DB_USER/DB_PASSWORD lines - avoids the PG15 "permission denied
# for schema public" trap when those point at a limited role.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

KILL_JVMS='Get-CimInstance Win32_Process -Filter "name=''java.exe''" | Where-Object {$_.CommandLine -like ''*event-service*'' -or $_.CommandLine -like ''*booking-service*''} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }'

case "${1:-up}" in
  up)
    if [ -f .env ]; then
      set -a; source <(tr -d '\r' < .env); set +a
      unset DB_USER DB_PASSWORD   # prefer creds embedded in the pasted URI
      DB_USER=$(printf '%s' "$DB_URL" | sed -E 's#.*//([^:]+):.*#\1#')
      DB_PASSWORD=$(printf '%s' "$DB_URL" | sed -E 's#.*:([^@]+)@.*#\1#')
      HOST=$(printf '%s' "$DB_URL" | sed -E 's#.*@([^/?"]+).*#\1#')
      export DB_USER DB_PASSWORD DB_DRIVER=org.postgresql.Driver
      echo "Postgres: $DB_USER @ $HOST (Neon)"
      DB_URL="jdbc:postgresql://$HOST/eventdb?sslmode=require" \
        java -jar event-service/target/event-service-0.0.1-SNAPSHOT.jar > event-service.log 2>&1 &
      DB_URL="jdbc:postgresql://$HOST/bookingdb?sslmode=require" \
        java -jar booking-service/target/booking-service-0.0.1-SNAPSHOT.jar > booking-service.log 2>&1 &
      echo "services starting -> logs: event-service.log, booking-service.log"
    else
      echo "no .env found -> starting on in-memory H2"
      (cd event-service && mvn spring-boot:run) > event-service.log 2>&1 &
      (cd booking-service && mvn spring-boot:run) > booking-service.log 2>&1 &
    fi
    echo "frontend: cd frontend && pnpm dev   |   stop everything: ./scripts/dev.sh down"
    ;;
  down)
    powershell -NoProfile -Command "$KILL_JVMS" > /dev/null 2>&1 || true
    echo "services stopped"
    ;;
  status)
    E=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost:8081/api/events || echo 000)
    B=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -X POST http://localhost:8082/api/auth/login || echo 000)
    F=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost:5173/ || echo 000)
    echo "event-service(8081): $E   booking-service(8082): $B   frontend(5173): $F   (000 = down)"
    ;;
  *)
    echo "usage: $0 up|down|status"
    ;;
esac
