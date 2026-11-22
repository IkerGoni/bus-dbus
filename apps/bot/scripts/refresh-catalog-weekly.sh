#!/bin/sh
# Regeneración semanal del índice de descubrimiento.
# No toca BOT_TOKEN ni reinicia el bot: los cambios del catálogo los lee el
# proceso en su próximo arranque (regla 9 de AGENTS.md).
#
# Instalación en RS (Linux, cron del usuario; NO instalado en este Mac):
#   0 3 * * 0 /bin/sh /ruta/a/bus-dbus/apps/bot/scripts/refresh-catalog-weekly.sh
# En macOS hay además una plantilla launchd (com.dbus.catalog-weekly.plist).
set -eu

ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
CATALOG=apps/bot/data/dbus-catalog.json
LOG=${TMPDIR:-/tmp}/dbus-catalog-refresh.log

cd "$ROOT"
echo "=== $(date -u +%FT%TZ) refresh catálogo ===" >>"$LOG"

pnpm --filter @dbus/bot build:catalog >>"$LOG" 2>&1

if git diff --quiet -- "$CATALOG"; then
  echo "sin cambios" >>"$LOG"
  exit 0
fi

# generatedAt cambia siempre: solo publicamos si cambiaron los DATOS reales.
if node --input-type=module -e '
  import { execFileSync } from "node:child_process";
  const [file] = process.argv.slice(1);
  const head = JSON.parse(execFileSync("git", ["show", `HEAD:${file}`], { encoding: "utf8" }));
  const now = JSON.parse((await import("node:fs")).readFileSync(file, "utf8"));
  const strip = ({ generatedAt, coverage, ...rest }) => rest;
  process.stdout.write(JSON.stringify(strip(head)) === JSON.stringify(strip(now)) ? "same" : "changed");
' "$CATALOG" | grep -q '^same$'; then
  git checkout -- "$CATALOG"
  echo "solo generatedAt: descartado" >>"$LOG"
  exit 0
fi

git add "$CATALOG"
git commit -m "chore(bot): catálogo semanal regenerado desde la API de Dbus" >>"$LOG" 2>&1
git push origin main >>"$LOG" 2>&1
echo "publicado" >>"$LOG"
