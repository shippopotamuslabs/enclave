#!/usr/bin/env bash
# One-shot rebrand: Enclave -> Enclave, Council mode -> Council mode
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

EXCLUDE_DIRS=(
  './third_party'
  './.git'
  './build'
  './plans'
  './docs/superpowers'
)

should_process() {
  local file="$1"
  for ex in "${EXCLUDE_DIRS[@]}"; do
    if [[ "$file" == $ex/* ]] || [[ "$file" == "$ex" ]]; then
      return 1
    fi
  done
  if [[ "$file" == *'/build/'* ]] || [[ "$file" == *'/.gradle/'* ]]; then
    return 1
  fi
  case "$file" in
    *.kt|*.kts|*.xml|*.cpp|*.pro|*.toml|*.ts|*.json|*.md|*.sh|*.yml|*.gradle|*.properties|*.html|*.svg)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

apply_replacements() {
  local file="$1"
  sed -i \
    -e 's/com\.browntowndev\.pocketcrew/com.browntowndev.enclave/g' \
    -e 's/Java_com_browntowndev_enclave/Java_com_browntowndev_enclave/g' \
    -e 's/Enclave/Enclave/g' \
    -e 's/enclave-tts/enclave-tts/g' \
    -e 's/pocketcrew:\/\//enclave:\/\//g' \
    -e 's/pocketcrew\.db/enclave.db/g' \
    -e 's/enclave/enclave/g' \
    -e 's/Enclave/Enclave/g' \
    -e 's/rootProject\.name = "Enclave"/rootProject.name = "Enclave"/g' \
    -e 's/rootProject\.name = "Enclave"/rootProject.name = "Enclave"/g' \
    -e 's/getIncompleteCouncilMessages/getIncompleteCouncilMessages/g' \
    -e 's/IncompleteCouncil/IncompleteCouncil/g' \
    -e 's/UseTheCouncilPopup/UseTheCouncilPopup/g' \
    -e 's/onUseTheCouncil/onUseTheCouncil/g' \
    -e 's/UseTheCouncil/UseTheCouncil/g' \
    -e 's/use_the_council_subtitle/use_the_council_subtitle/g' \
    -e 's/use_the_council/use_the_council/g' \
    -e 's/mode_council/mode_council/g' \
    -e 's/ChatModeUi\.COUNCIL/ChatModeUi.COUNCIL/g' \
    -e 's/Mode\.COUNCIL/Mode.COUNCIL/g' \
    -e 's/\bCREW mode/COUNCIL mode/g' \
    -e 's/\bCREW pipeline/COUNCIL pipeline/g' \
    -e 's/\bCREW ChatModeUi/COUNCIL ChatModeUi/g' \
    -e 's/\bCREW conversation/COUNCIL conversation/g' \
    -e 's/\bCREW image/COUNCIL image/g' \
    -e 's/for COUNCIL/for COUNCIL/g' \
    -e 's/testing COUNCIL/testing COUNCIL/g' \
    -e 's/,\s*COUNCIL)/, COUNCIL)/g' \
    -e 's/COUNCIL(R\.string\.mode_council/COUNCIL(R.string.mode_council/g' \
    -e 's/\bCREW\b/COUNCIL/g' \
    -e 's/Council mode/Council mode/g' \
    -e 's/Council pipeline/Council pipeline/g' \
    -e 's/Council ChatModeUi/Council ChatModeUi/g' \
    -e 's/the Council pipeline/the Council pipeline/g' \
    -e 's/Use the Council/Use the Council/g' \
    -e 's/Prompt Enclave/Prompt Enclave/g' \
    -e 's/Enclave Document schema/Enclave Document schema/g' \
    "$file"
}

echo "Applying text replacements..."
while IFS= read -r -d '' file; do
  if should_process "$file"; then
    apply_replacements "$file"
  fi
done < <(find . -type f -print0)

echo "Moving package directories pocketcrew -> enclave..."
while IFS= read -r -d '' dir; do
  parent="$(dirname "$dir")"
  target="$parent/enclave"
  if [[ -e "$target" ]]; then
    echo "Skip existing: $target"
  else
    mv "$dir" "$target"
    echo "Moved: $dir -> $target"
  fi
done < <(find . -type d -path '*/com/browntowndev/pocketcrew' -print0)

echo "Renaming Enclave/UseTheCouncil files..."
while IFS= read -r -d '' file; do
  new="${file//Enclave/Enclave}"
  new="${new//UseTheCouncil/UseTheCouncil}"
  if [[ "$file" != "$new" ]]; then
    mv "$file" "$new"
    echo "Renamed: $file -> $new"
  fi
done < <(find . -type f \( -name '*Enclave*' -o -name '*UseTheCouncil*' \) -print0)

echo "Renaming root SVG assets..."
for svg in enclave_icon.svg enclave_icon_material_you.svg enclave_icon_no_background.svg; do
  if [[ -f "$svg" ]]; then
    mv "$svg" "${svg/enclave/enclave}"
  fi
done

echo "Done."
