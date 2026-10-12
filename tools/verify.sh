#!/usr/bin/env bash
# Full local verification: unit tests, catalog validation, certificate replay, lint and debug APK.
# Uses JAVA_HOME (JDK 17+) and ANDROID_HOME from the environment (or local.properties). Each step keeps its
# log in logs/ and reports its real exit code; the Gradle daemons are stopped at the end.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
# Fall back to the author's machine layout only when nothing is set and those paths exist.
if [[ -z "${JAVA_HOME:-}" && -d /opt/android-studio/jbr ]]; then export JAVA_HOME=/opt/android-studio/jbr; fi
if [[ -z "${ANDROID_HOME:-}" && -d /opt/android-sdk ]]; then export ANDROID_HOME=/opt/android-sdk; fi
mkdir -p logs
cleanup() {
    result=$?
    trap - EXIT
    if ! ./gradlew --stop >logs/gradle-stop.log 2>&1; then
        printf '%s\n' 'Failed to stop Gradle; see logs/gradle-stop.log' >&2
        if (( result == 0 )); then result=1; fi
    fi
    exit "$result"
}
trap cleanup EXIT
run_check() {
    local name=$1
    shift
    printf 'Running %s…\n' "$name"
    local result=0
    "$@" >"logs/$name.log" 2>&1 || result=$?
    if (( result == 0 )); then
        printf '%s: PASS (exit 0)\n' "$name"
    else
        tail -70 "logs/$name.log" >&2
        printf '%s: FAIL (exit %s)\n' "$name" "$result" >&2
        return "$result"
    fi
}
run_check tests ./gradlew :core:test :leveltool:test :app:testDebugUnitTest --console=plain --max-workers=2
run_check content ./gradlew :leveltool:validateCatalog --console=plain --max-workers=2
run_check certificates ./gradlew :leveltool:verifyCertificates --console=plain --max-workers=2
run_check apk ./gradlew :app:lintDebug :app:assembleDebug --console=plain --max-workers=2
printf '%s\n' 'All checks passed. APK at app/build/outputs/apk/debug/app-debug.apk'
