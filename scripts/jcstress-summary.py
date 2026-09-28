"""Sums the jcstress HTML reports per test and outcome: python scripts/jcstress-summary.py <results dir>."""
import glob
import os
import re
import sys

results = sys.argv[1] if len(sys.argv) > 1 else "results"
for path in sorted(glob.glob(os.path.join(results, "com.jxparallel*.html"))):
    text = open(path, encoding="utf-8", errors="replace").read()
    table = text[text.index("Observed States"):]
    states = re.findall(r"<th nowrap align='center'>([^<]*)</th>", table)
    header_end = table.index("</tr>", table.index("<td colspan=4></td>"))
    expects = re.findall(r"<td>([A-Za-z_ ]+)</td>", table[:header_end])[:len(states)]
    totals = [0] * len(states)
    runs = failed = 0
    for row in re.findall(r"bgColor='[a-z ]+'>(\w+)</td>((?:\s*<td align='right'[^>]*>\d+</td>)+)", table):
        runs += 1
        failed += row[0] == "FAILED"
        for i, count in enumerate(re.findall(r">(\d+)</td>", row[1])):
            totals[i] += int(count)
    print(f"{os.path.basename(path)[:-5]}  ({runs} configurations, {failed} failed)")
    for state, expect, total in zip(states, expects, totals):
        print(f"    {state:14} {expect:12} {total:>14,}")
