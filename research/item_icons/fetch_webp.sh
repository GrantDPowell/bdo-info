#!/usr/bin/env bash
# Download BDO item icon .webp files from bdocodex.com into webp_tmp/
set -u
DIR="C:/X-Files/Repos/~PERSONAL/bdo-boss-timer/research/item_icons"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36"
LOG="$DIR/fetch_log.tsv"
mkdir -p "$DIR/webp_tmp"
: > "$LOG"

slugify() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/_/g; s/^_+//; s/_+$//'
}

urlenc() { jq -rn --arg s "$1" '$s|@uri'; }

while IFS=$'\t' read -r exact query; do
  [ -z "$exact" ] && continue
  slug="$(slugify "$exact")"
  out="$DIR/webp_tmp/$slug.webp"

  json="$(curl -s -m 20 -A "$UA" "https://bdocodex.com/ac.php?l=us&term=$(urlenc "$query")")"
  pick="$(printf '%s' "$json" | jq -c --arg q "$query" '
    [.[]? | select(.link_type=="item")] as $items
    | ([$items[] | select((.name|ascii_downcase) == ($q|ascii_downcase))] + $items)
    | .[0] // empty' 2>/dev/null)"

  if [ -z "$pick" ]; then
    printf '%s\t%s\tMISS\tno_search_result\n' "$exact" "$slug" >> "$LOG"
    sleep 0.4; continue
  fi

  name="$(printf '%s' "$pick" | jq -r '.name')"
  icon="$(printf '%s' "$pick" | jq -r '.icon')"
  url="https://bdocodex.com/items/$icon"

  code="$(curl -s -m 20 -A "$UA" -o "$out" -w '%{http_code}' "$url")"
  magic="$(head -c 4 "$out" 2>/dev/null | od -An -c | tr -d ' \n')"
  size="$(stat -c %s "$out" 2>/dev/null || echo 0)"
  if [ "$code" != "200" ] || [ "$magic" != "RIFF" ] || [ "$size" -lt 500 ]; then
    printf '%s\t%s\tMISS\thttp=%s magic=%s size=%s url=%s\n' "$exact" "$slug" "$code" "$magic" "$size" "$url" >> "$LOG"
    rm -f "$out"; sleep 0.4; continue
  fi

  printf '%s\t%s\tOK\tmatched=%s\tsize=%s\turl=%s\n' "$exact" "$slug" "$name" "$size" "$url" >> "$LOG"
  sleep 0.4
done < "$DIR/items.tsv"

cat "$LOG"
