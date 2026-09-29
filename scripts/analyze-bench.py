"""Statistical comparison of FxBackendBench runs (scripts/measure-fx-backends.ps1 CSV output).

For each metric and each pair (baseline, candidate) of stacks it reports:
  - median and interquartile range (IQR) per stack;
  - two-sided Mann-Whitney U test, exact: the p-value comes from the permutation distribution of
    U over every split of the pooled sample (mid-ranks for ties), so no normal approximation;
  - Cliff's delta effect size, (#{x > y} - #{x < y}) / (n * m), in [-1, 1];
  - Holm-Bonferroni adjusted p-values over all tests of the run.

Pure Python (no SciPy). Usage:
  python scripts/analyze-bench.py runs.csv [--baseline purefx] [--metrics a,b,c] [--latex out.tex]
With n = m = 3 the smallest two-sided p-value possible is 2/C(6,3) = 0.1, so a 5% level needs
larger samples (n = m = 10 gives 2/C(20,10) = 1.1e-5).
"""
import argparse
import csv
import itertools
import math
import statistics
import sys


def quartiles(values):
    v = sorted(values)
    if len(v) < 2:
        return v[0], v[0]
    q = statistics.quantiles(v, n=4, method="inclusive")
    return q[0], q[2]


def midranks(pooled):
    order = sorted(range(len(pooled)), key=lambda i: pooled[i])
    ranks = [0.0] * len(pooled)
    i = 0
    while i < len(order):
        j = i
        while j + 1 < len(order) and pooled[order[j + 1]] == pooled[order[i]]:
            j += 1
        r = (i + j) / 2 + 1
        for k in range(i, j + 1):
            ranks[order[k]] = r
        i = j + 1
    return ranks


def mann_whitney_exact(x, y):
    """Two-sided exact permutation p-value of U (ties by mid-ranks)."""
    n, m = len(x), len(y)
    pooled = list(x) + list(y)
    ranks = midranks(pooled)
    u_obs = sum(ranks[:n]) - n * (n + 1) / 2
    mean_u = n * m / 2
    dev_obs = abs(u_obs - mean_u)
    total = 0
    extreme = 0
    for combo in itertools.combinations(range(n + m), n):
        u = sum(ranks[i] for i in combo) - n * (n + 1) / 2
        total += 1
        if abs(u - mean_u) >= dev_obs - 1e-9:
            extreme += 1
    return u_obs, extreme / total


def cliffs_delta(x, y):
    gt = sum(1 for a in x for b in y if a > b)
    lt = sum(1 for a in x for b in y if a < b)
    return (gt - lt) / (len(x) * len(y))


def holm(pvalues):
    order = sorted(range(len(pvalues)), key=lambda i: pvalues[i])
    adjusted = [0.0] * len(pvalues)
    running = 0.0
    k = len(pvalues)
    for rank, i in enumerate(order):
        running = max(running, min(1.0, (k - rank) * pvalues[i]))
        adjusted[i] = running
    return adjusted


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv")
    ap.add_argument("--baseline", default="purefx")
    ap.add_argument("--metrics", default="")
    ap.add_argument("--latex", default="")
    args = ap.parse_args()

    rows = list(csv.DictReader(open(args.csv, encoding="utf-8-sig")))
    stacks = []
    for r in rows:
        if r["backend"] not in stacks:
            stacks.append(r["backend"])
    metrics = [m for m in args.metrics.split(",") if m] or [
        k for k in rows[0].keys() if k not in ("backend", "run", "exit_code")]

    def values(stack, metric):
        out = []
        for r in rows:
            if r["backend"] == stack and r.get(metric) not in (None, ""):
                out.append(float(r[metric]))
        return out

    results = []
    for metric in metrics:
        base = values(args.baseline, metric)
        if not base:
            continue
        for stack in stacks:
            if stack == args.baseline:
                continue
            cand = values(stack, metric)
            if not cand:
                continue
            u, p = mann_whitney_exact(cand, base)
            results.append({
                "metric": metric, "stack": stack,
                "n": len(cand), "m": len(base),
                "med_base": statistics.median(base), "iqr_base": quartiles(base),
                "med_cand": statistics.median(cand), "iqr_cand": quartiles(cand),
                "u": u, "p": p, "delta": cliffs_delta(cand, base),
            })
    for r, adj in zip(results, holm([r["p"] for r in results])):
        r["p_holm"] = adj

    w = csv.writer(sys.stdout)
    w.writerow(["metric", "stack", "n", "m", "median_baseline", "q1_baseline", "q3_baseline",
                "median_stack", "q1_stack", "q3_stack", "U", "p_exact", "p_holm", "cliffs_delta"])
    for r in results:
        w.writerow([r["metric"], r["stack"], r["n"], r["m"], r["med_base"], *r["iqr_base"],
                    r["med_cand"], *r["iqr_cand"], r["u"], f"{r['p']:.3g}", f"{r['p_holm']:.3g}",
                    f"{r['delta']:.3f}"])

    if args.latex:
        with open(args.latex, "w", encoding="utf-8") as f:
            for r in results:
                f.write(f"% {r['metric']} {r['stack']} vs {args.baseline}: "
                        f"p={r['p']:.3g} holm={r['p_holm']:.3g} delta={r['delta']:.3f}\n")


if __name__ == "__main__":
    main()
