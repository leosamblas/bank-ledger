#!/usr/bin/env sh
DIR="$(cd "$(dirname "$0")" && pwd)"
ARGS=""
for arg in "$@"; do
    CLEAN_ARG=$(echo "$arg" | sed 's/:services:/:/g')
    ARGS="$ARGS $CLEAN_ARG"
done
exec "$DIR/services/gradlew" -p "$DIR/services" $ARGS
