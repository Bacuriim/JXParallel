"""Fails when a JMH ratio drops below its floor: python scripts/check-jmh.py result.json

Ratios between benchmarks of the same run do not depend on how fast the CI machine is, so they
can gate a build where absolute times cannot. Floors sit well below the measured ratios
(2026-09-25, JDK 17, i7-1255U: 57x and about 20000x) to absorb runner noise.
"""
import json
import sys

FLOORS = [
    ("ReconcileBenchmark.mountFresh", "ReconcileBenchmark.reconcileOneLabel", 10),
    ("ReconcileBenchmark.mountFresh", "ReconcileBenchmark.reconcileUnchanged", 1000),
]

scores = {}
for entry in json.load(open(sys.argv[1], encoding="utf-8")):
    name = entry["benchmark"].rsplit(".", 2)
    scores[name[-2] + "." + name[-1]] = entry["primaryMetric"]["score"]

failed = False
for slow, fast, floor in FLOORS:
    ratio = scores[slow] / scores[fast]
    ok = ratio >= floor
    failed |= not ok
    print(f"{'ok  ' if ok else 'FAIL'} {slow} / {fast} = {ratio:.1f}x (floor {floor}x)")
sys.exit(1 if failed else 0)
