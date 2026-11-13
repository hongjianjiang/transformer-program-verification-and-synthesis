#!/bin/sh
# Wrapper for a solver binary: runs it unchanged, appends exit status and
# /usr/bin/time -l resource usage (peak RSS etc.) to $MEASURE_OUT.
REAL="$1"; shift
/usr/bin/time -l sh -c 'exec "$0" "$@" 2>&1' "$REAL" "$@" 2>>"$MEASURE_OUT"
code=$?
echo "exit=$code" >> "$MEASURE_OUT"
exit $code
