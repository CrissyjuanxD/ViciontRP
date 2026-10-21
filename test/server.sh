#!/usr/bin/env bash
set -euo pipefail

MC="$1"
MODE="$2"
TARGET="${3:-}"
ROOT="$(pwd)"
DIR="$(mktemp -d)"
JAR="$(ls "$ROOT"/plugin/target/ViciontRP-*.jar | head -1)"

url="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$MC/builds/latest" | jq -r '.downloads["server:default"].url // empty' || true)"
if [ -z "$url" ]; then
  build="$(curl -fsSL "https://api.papermc.io/v2/projects/paper/versions/$MC/builds" | jq -r '.builds[-1].build')"
  url="https://api.papermc.io/v2/projects/paper/versions/$MC/builds/$build/downloads/paper-$MC-$build.jar"
fi
echo "Paper $MC: $url"
curl -fsSL -o "$DIR/paper.jar" "$url"

mkdir -p "$DIR/plugins/ViciontRP"
cp "$JAR" "$DIR/plugins/"
sed -e 's|^key: ""|key: "vrp1.prueba.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"|' \
    -e 's|^api: .*|api: "http://127.0.0.1:8787"|' \
    -e 's|^refresh-seconds: .*|refresh-seconds: 15|' \
    -e 's|^join-delay-ticks: .*|join-delay-ticks: 10|' \
    -e 's|^warning-seconds: .*|warning-seconds: 3|' \
    "$ROOT/plugin/src/main/resources/config.yml" > "$DIR/plugins/ViciontRP/config.yml"
grep -q 'vrp1.prueba' "$DIR/plugins/ViciontRP/config.yml"
echo "eula=true" > "$DIR/eula.txt"
cat > "$DIR/server.properties" <<'EOF'
online-mode=false
server-port=25565
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
view-distance=3
simulation-distance=3
enforce-secure-profile=false
EOF

node "$ROOT/test/mock-api.mjs" "$DIR/calls.json" &
API=$!
mkfifo "$DIR/console"
(cd "$DIR" && tail -f console | java -Xmx1536M -jar paper.jar --nogui > server.log 2>&1) &
SERVER=$!
cleanup() { cp "$DIR/server.log" "$ROOT/server-$MC.log" 2>/dev/null || true; kill "$API" 2>/dev/null || true; }
trap cleanup EXIT

for _ in $(seq 1 360); do
  if grep -q 'Done (' "$DIR/server.log" 2>/dev/null; then break; fi
  if ! kill -0 "$SERVER" 2>/dev/null; then echo "El servidor se cerró al arrancar"; tail -50 "$DIR/server.log"; exit 1; fi
  sleep 1
done
grep -q 'Done (' "$DIR/server.log" || { echo "El servidor no arrancó a tiempo"; tail -50 "$DIR/server.log"; exit 1; }
for _ in $(seq 1 30); do
  grep -q 'Packs de Croissants de Prueba' "$DIR/server.log" && break
  sleep 1
done
grep -i 'viciontrp' "$DIR/server.log" || true
grep -q 'Packs de Croissants de Prueba' "$DIR/server.log" || { echo "ViciontRP no recibió los packs de la API"; exit 1; }

status=0
if [ "$MODE" = "bot" ]; then
  (cd "$ROOT/test" && npm install --no-audit --no-fund --loglevel=error)
  node "$ROOT/test/bot.mjs" "$DIR/calls.json" "$DIR" || status=$?
elif [ "$MODE" = "client" ]; then
  node "$ROOT/test/client.mjs" "$DIR" "$TARGET" || status=$?
fi

echo "stop" > "$DIR/console"
for _ in $(seq 1 60); do kill -0 "$SERVER" 2>/dev/null || break; sleep 1; done
if grep -E 'Error occurred while enabling ViciontRP|Could not pass event .* to ViciontRP|com\.viciontstudios\.rp\.[A-Za-z]+\.[a-zA-Z]+\(' "$DIR/server.log"; then
  echo "ViciontRP dejó errores en el registro del servidor"
  status=1
fi
exit "$status"
