#!/usr/bin/env bash
# Prints JaCoCo coverage as Markdown: totals against the minimums in pom.xml, then each package.
set -euo pipefail

csv=${1:-target/site/jacoco/jacoco.csv}
pom=${2:-pom.xml}

minimum() { sed -n "s:.*<jacoco\.minimum\.$1>\(.*\)</jacoco\.minimum\.$1>.*:\1:p" "$pom"; }
line_min=$(minimum line)
branch_min=$(minimum branch)
: "${line_min:?jacoco.minimum.line not found in $pom}" "${branch_min:?jacoco.minimum.branch not found in $pom}"

# CSV columns: 2 package, 6/7 branches missed/covered, 8/9 lines missed/covered
awk -F, -v line_min="$line_min" -v branch_min="$branch_min" '
  # rounded down, so 94.99% never shows as a passing 95.0%
  function pct(c, m) { return c + m ? sprintf("%.1f%%", int(1000 * c / (c + m)) / 10) : "n/a" }
  function status(c, m, min) { return c + m == 0 || c / (c + m) >= min ? "✅" : "❌" }
  NR > 1 { bm += $6; bc += $7; lm += $8; lc += $9 }
  END {
    print "## Test coverage\n"
    print "| | Coverage | Covered | Minimum | |"
    print "|---|---:|---:|---:|---|"
    printf "| Lines | %s | %d / %d | %g%% | %s |\n", pct(lc, lm), lc, lc + lm, line_min * 100, status(lc, lm, line_min)
    printf "| Branches | %s | %d / %d | %g%% | %s |\n", pct(bc, bm), bc, bc + bm, branch_min * 100, status(bc, bm, branch_min)
  }' "$csv"

printf '\n<details><summary>By package</summary>\n\n| Package | Lines | Branches |\n|---|---:|---:|\n'
awk -F, '
  function pct(c, m) { return c + m ? sprintf("%.1f%%", int(1000 * c / (c + m)) / 10) : "n/a" }
  NR > 1 { seen[$2]; bm[$2] += $6; bc[$2] += $7; lm[$2] += $8; lc[$2] += $9 }
  END { for (p in seen) printf "| `%s` | %s | %s |\n", p, pct(lc[p], lm[p]), pct(bc[p], bm[p]) }' "$csv" | sort
printf '\n</details>\n'
