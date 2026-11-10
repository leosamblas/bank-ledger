#!/usr/bin/env sh
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/services/gradlew" -p "$DIR/services" "$@"
