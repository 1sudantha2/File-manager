#!/usr/bin/env bash
# Keep compiler/build failures available as check annotations, even without log downloads.
set -uo pipefail
log_file="$(mktemp)"
"$@" 2>&1 | tee "$log_file"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
  python3 - "$log_file" <<'PY'
import pathlib, re, sys
lines = pathlib.Path(sys.argv[1]).read_text(errors='replace').splitlines()
important = [line for line in lines if re.search(r'(^e:|error:|Execution failed|What went wrong|Could not|Caused by:|FAILURE:)', line, re.I)]
text = '\n'.join(important[:100] + ['--- final output ---'] + lines[-35:])
for offset in range(0, len(text), 6000):
    chunk = text[offset:offset + 6000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print('::error title=Build diagnostics::' + chunk)
PY
fi
rm -f "$log_file"
exit "$status"
