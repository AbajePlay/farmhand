#!/usr/bin/env bash
# Smoke test: boots a dedicated server for every build in the matrix and checks that the mod
# loaded and every mixin injection applied.
#
# farmhand.mixins.json sets defaultRequire: 1, so Mixin must apply each injection or refuse to
# start. "The server reached its verdict" therefore means all three injection points resolved
# against this Minecraft version.
#
# The server does not stop on its own and outlives the gradlew wrapper (it is a child of the
# Gradle daemon), so it is stopped precisely: by the PID listening on the port.

set -u
cd "$(dirname "$0")/.." || exit 1
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Eclipse Adoptium/jdk-21.0.8.9-hotspot}"

# A dedicated port rather than the default 25565, so the test can never collide with - or kill -
# a developer's own local server.
PORT=25599
TIMEOUT=${TIMEOUT:-240}
OUT=smoke
mkdir -p "$OUT"
RESULTS="$OUT/results.tsv"
: > "$RESULTS"

kill_server() {
	local pid
	pid=$(netstat -ano 2>/dev/null | grep ":$PORT " | grep LISTENING | awk '{print $5}' | head -1)
	if [ -n "${pid:-}" ]; then
		taskkill //F //PID "$pid" >/dev/null 2>&1
		sleep 2
	fi
}

FILTER="${1:-}"   # optional filter: bash scripts/smoke-test.sh 26.2-fabric

printf '%-20s %-8s %-7s %-6s %-7s %s\n' "BUILD" "STATUS" "MIXIN" "INIT" "CHECKS" "TIME"

for proj in $(ls versions); do
	if [ -n "$FILTER" ] && [ "$proj" != "$FILTER" ]; then continue; fi
	log="$OUT/$proj.log"
	mkdir -p "versions/$proj/run"
	printf 'eula=true\n' > "versions/$proj/run/eula.txt"
	printf 'server-port=%s\nonline-mode=false\n' "$PORT" > "versions/$proj/run/server.properties"

	kill_server
	started=$(date +%s)
	./gradlew ":$proj:runServer" -Pfarmhand.selftest=true --console=plain > "$log" 2>&1 &
	wrapper=$!

	waited=0
	status="TIMEOUT"
	while [ "$waited" -lt "$TIMEOUT" ]; do
		if grep -q 'SELFTEST PASS - self-test verdict' "$log" 2>/dev/null; then status="PASS"; break; fi
		if grep -q 'SELFTEST FAIL' "$log" 2>/dev/null; then status="FAIL"; break; fi
		if grep -qE 'MixinApply|InjectionError|Critical injection|FAILURE: Build' "$log" 2>/dev/null; then
			status="FAIL"; break
		fi
		sleep 3
		waited=$((waited + 3))
	done
	elapsed=$(( $(date +%s) - started ))

	kill_server
	kill "$wrapper" 2>/dev/null

	# grep -c prints 0 AND returns 1, so "|| echo 0" would append a second line.
	mixerr=$(grep -cE 'MixinApply|InjectionError|Critical injection' "$log" 2>/dev/null) || mixerr=0
	init=$(grep -cE 'Farmhand [0-9][^ ]* on ' "$log" 2>/dev/null) || init=0
	checks=$(grep -cE 'SELFTEST PASS' "$log" 2>/dev/null) || checks=0

	printf '%-20s %-8s %-7s %-6s %-7s %ss\n' "$proj" "$status" "$mixerr" "$init" "$checks" "$elapsed"
	printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$proj" "$status" "$mixerr" "$init" "$checks" "$elapsed" >> "$RESULTS"
done

echo
echo "===== SUMMARY ====="
# A build passes only with a PASS verdict, zero mixin errors, the mod initialised, and all eight
# self-test checks: three crops x two scenarios, the live villager, and the final verdict.
awk -F'\t' '{ tot++; if ($2=="PASS" && $3==0 && $4>0 && $5>=8) ok++ } END { printf "passed %d of %d\n", ok, tot }' "$RESULTS"
awk -F'\t' '$2!="PASS" || $3!=0 || $4==0 || $5<8 { print "  PROBLEM: " $1 " status=" $2 " mixin=" $3 " init=" $4 " checks=" $5 }' "$RESULTS"
