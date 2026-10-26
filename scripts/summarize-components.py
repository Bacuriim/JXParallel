"""Summarizes the per-component benchmark (scripts/measure-components.ps1) as the body of a Markdown report.

Reads components-<jvm>-median.csv from a results directory (and the components table the generator wrote, for the
groups and the JavaFX mapping) and prints, per JVM: one pair of tables per group of components (time; memory, CPU and
classes) with the median of every mode and its ratio against JavaFX when the component has a JavaFX counterpart, else
against the interpreted mode; the ratios that are worse than JavaFX in bold; a ranking of the biggest wins and losses;
the components without a JavaFX equivalent; and the runs whose fingerprint did not match.

    python -I scripts/summarize-components.py --dir docs/components-2026-10-22 [--out report.md] [--table components.csv]

Standard library only. A missing value prints n/a; a ratio with a zero or missing side prints n/a.
"""
import argparse
import csv
import glob
import os
import sys

GROUPS = [
    ("inputs", "Inputs and display"),
    ("buttons", "Buttons and toggles"),
    ("containers", "Containers and layout"),
    ("lists", "Lists, tables and trees"),
    ("overlays", "Overlays"),
    ("material", "Material controls"),
]
MODES = ["javafx", "interpreted", "template", "compiled", "live"]
NA = "n/a"
DEFAULT_TABLE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "jxparallel-examples-native", "src", "main",
                             "resources", "componentbench", "components.csv")


def ms(ns):
    return ns / 1e6


def kb(b):
    return b / 1024.0


def mb(b):
    return b / (1024.0 * 1024.0)


# (column title, function of one row -> number or None)
def _g(key):
    def get(row):
        return num(row.get(key))
    return get


def _sum(*keys):
    def get(row):
        values = [num(row.get(k)) for k in keys]
        return None if any(v is None for v in values) else sum(values)
    return get


def _retained(row):
    with_, released, n = num(row.get("heap_live_with_bytes")), num(row.get("heap_live_released_bytes")), num(row.get("n"))
    if with_ is None or released is None or not n:
        return None
    # the Java 8 client VM reports the live heap in steps of about 2.9 MB after System.gc(), which is more than what a
    # JXML component retains: those values (and any that come out zero or negative) are noise, not a measurement
    if str(row.get("jvm", "")).startswith("java8") or with_ - released <= 0:
        return None
    return (with_ - released) / n


def _scaled(getter, f):
    def get(row):
        v = getter(row)
        return None if v is None else f(v)
    return get


TIME_COLUMNS = [
    ("build cold ms", _scaled(_g("build_cold_ns"), ms)),
    ("build warm ms", _scaled(_g("build_warm_ns"), ms)),
    ("ready cold ms", _scaled(_g("ready_cold_ns"), ms)),
    ("ready warm ms", _scaled(_g("ready_warm_ns"), ms)),
    ("update warm ms", _scaled(_g("update_warm_ns"), ms)),
    ("paint warm ms (indicative)", _scaled(_g("paint_warm_ns"), ms)),
]
MEMORY_COLUMNS = [
    ("alloc build warm KB", _scaled(_g("build_warm_alloc_bytes"), kb)),
    ("alloc ready warm KB", _scaled(_g("ready_warm_alloc_bytes"), kb)),
    ("alloc update warm KB", _scaled(_g("update_warm_alloc_bytes"), kb)),
    ("retained B/instance", _retained),
    ("thread CPU ms (all stages)", _scaled(_sum("build_total_thread_cpu_ns", "ready_total_thread_cpu_ns",
                                                "update_total_thread_cpu_ns", "paint_total_thread_cpu_ns"), ms)),
    ("classes loaded", _g("classes_loaded")),
    ("peak working set MB", _scaled(_g("peak_working_set_bytes"), mb)),
]
# the headline ratio of the rankings: building and getting ready to paint, warm (paint is not like for like)
HEADLINE = ("build + ready warm", _scaled(_sum("build_warm_ns", "ready_warm_ns"), ms))


def num(text):
    """A number from a CSV cell, or None when it is empty or not a number."""
    if text is None:
        return None
    text = str(text).strip()
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def fmt(v):
    if v is None:
        return NA
    a = abs(v)
    if a >= 1000:
        return "%.0f" % v
    if a >= 100:
        return "%.0f" % v
    if a >= 10:
        return "%.1f" % v
    return "%.2f" % v


def ratio(v, ref):
    """v / ref, or None when either is missing or the reference is zero."""
    if v is None or ref is None or ref == 0:
        return None
    return v / ref


def fmt_ratio(r):
    if r is None:
        return NA
    if r < 0.01:
        return "<0.01x"
    return "%.2fx" % r


def read_csv(path):
    with open(path, newline="", encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def load_medians(directory):
    """{jvm: {(component, mode): row}} from the components-<jvm>-median.csv files of a directory."""
    out = {}
    for path in sorted(glob.glob(os.path.join(directory, "components-*-median.csv"))):
        jvm = os.path.basename(path)[len("components-"):-len("-median.csv")]
        out[jvm] = {(r["component"], r["mode"]): r for r in read_csv(path)}
    return out


def cell(row, ref_row, getter, vs_javafx):
    """'value (ratio)' for a row against its reference row; bold when the mode is worse than JavaFX."""
    if row is None:
        return NA
    v = getter(row)
    if ref_row is None or ref_row is row:
        return fmt(v)
    r = ratio(v, getter(ref_row))
    text = "%s (%s)" % (fmt(v), fmt_ratio(r))
    if vs_javafx and r is not None and r > 1.0:
        text = "**%s**" % text
    return text


def group_table(jvm_rows, components, columns):
    """Markdown lines: a row per component and mode, a column per metric."""
    header = "| component | mode | n | " + " | ".join(c for c, _ in columns) + " |"
    lines = [header, "|---|---|---:|" + "---:|" * len(columns)]
    for comp in components:
        has_fx = bool(comp["fx"]) and (comp["name"], "javafx") in jvm_rows
        ref_mode = "javafx" if has_fx else "interpreted"
        ref_row = jvm_rows.get((comp["name"], ref_mode))
        for mode in MODES:
            row = jvm_rows.get((comp["name"], mode))
            if row is None:
                if mode == "javafx" and not comp["fx"]:
                    continue  # no JavaFX equivalent: no such row
                if mode == "javafx":
                    row = None
                else:
                    continue
            n = fmt(num(row.get("n"))) if row else NA
            cells = [cell(row, ref_row, getter, has_fx) if row is not None else NA for _, getter in columns]
            label = mode + (" (ref)" if mode == ref_mode else "")
            lines.append("| %s | %s | %s | %s |" % (comp["name"], label, n, " | ".join(cells)))
    return lines


def ranking(jvm_rows, components, top=10):
    """(wins, losses): compiled against JavaFX on the headline, the components that have both."""
    scored = []
    for comp in components:
        if not comp["fx"]:
            continue
        c = jvm_rows.get((comp["name"], "compiled"))
        j = jvm_rows.get((comp["name"], "javafx"))
        if c is None or j is None:
            continue
        r = ratio(HEADLINE[1](c), HEADLINE[1](j))
        if r is not None:
            scored.append((r, comp["name"], HEADLINE[1](c), HEADLINE[1](j)))
    scored.sort()
    return scored[:top], list(reversed(scored))[:top]


def _pick(rows, name, mode, getter):
    row = rows.get((name, mode))
    return None if row is None else getter(row)


def live_section(jvm, rows, components):
    """Live bindings (research): compiled against live against JavaFX, where the components have a live row."""
    names = [c for c in components if (c["name"], "live") in rows]
    if not names:
        return []
    lines = ["#### Live bindings against compiled JXML and JavaFX, %s" % jvm, "",
             "Update of one prop of every instance, warm pass. Ratios are JavaFX / mode (above 1x the mode is better than JavaFX) and "
             "live / compiled (below 1x live is better than compiled). Build and ready include the recording and its pairing with "
             "the mounted nodes.", ""]
    update_ms = _scaled(_g("update_warm_ns"), ms)
    update_kb = _scaled(_g("update_warm_alloc_bytes"), kb)
    lines.append("| component | n | update ms compiled | live | JavaFX | JavaFX/compiled | JavaFX/live | live/compiled |"
                 " alloc KB compiled | live | JavaFX | JavaFX/compiled | JavaFX/live | live/compiled | fallbacks | opaque builds |")
    lines.append("|---|---:|" + "---:|" * 14)
    for comp in names:
        n = comp["name"]
        tc, tl, tj = (_pick(rows, n, m, update_ms) for m in ("compiled", "live", "javafx"))
        ac, al, aj = (_pick(rows, n, m, update_kb) for m in ("compiled", "live", "javafx"))
        live = rows[(n, "live")]
        lines.append("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |" % (
            n, fmt(num(live.get("n"))), fmt(tc), fmt(tl), fmt(tj), fmt_ratio(ratio(tj, tc)), fmt_ratio(ratio(tj, tl)),
            fmt_ratio(ratio(tl, tc)), fmt(ac), fmt(al), fmt(aj), fmt_ratio(ratio(aj, ac)), fmt_ratio(ratio(aj, al)),
            fmt_ratio(ratio(al, ac)), fmt(num(live.get("live_fallbacks"))), fmt(num(live.get("live_opaque_builds")))))
    lines.append("")
    lines.append("Build, ready, retained (compiled and live), warm:")
    lines.append("")
    lines.append("| component | build ms compiled | live | ready ms compiled | live | build KB compiled | live | ready KB compiled | live |"
                 " retained B/instance compiled | live | live/compiled |")
    lines.append("|---|" + "---:|" * 11)
    for comp in names:
        n = comp["name"]
        cells = []
        for getter in (_scaled(_g("build_warm_ns"), ms), _scaled(_g("ready_warm_ns"), ms), _scaled(_g("build_warm_alloc_bytes"), kb),
                       _scaled(_g("ready_warm_alloc_bytes"), kb)):
            cells.extend([fmt(_pick(rows, n, "compiled", getter)), fmt(_pick(rows, n, "live", getter))])
        rc, rl = (_pick(rows, n, m, _retained) for m in ("compiled", "live"))
        cells.extend([fmt(rc), fmt(rl), fmt_ratio(ratio(rl, rc))])
        lines.append("| %s | %s |" % (n, " | ".join(cells)))
    lines.append("")
    return lines


def report(medians, components, source=""):
    lines = []
    lines.append("## Per-component results" + (" (%s)" % source if source else ""))
    lines.append("")
    lines.append("Each cell is the median of the runs of that mode: the value, then in parentheses its ratio against the reference "
                 "row of the component (JavaFX when the component has a JavaFX counterpart, else the interpreted mode; the "
                 "reference row itself has no ratio). A ratio above 1 is more cost than the reference; **bold** marks the JXML "
                 "modes that cost more than JavaFX. `n/a` is a value that was not measured or a ratio with a zero or missing side. "
                 "Paint compares the software painter of JXML with the Prism pipeline of JavaFX: indicative, not equivalent.")
    lines.append("")
    if not medians:
        lines.append("No `components-<jvm>-median.csv` found.")
        return "\n".join(lines) + "\n"
    for jvm, rows in medians.items():
        lines.append("### %s" % jvm)
        lines.append("")
        for key, title in GROUPS:
            comps = [c for c in components if c["group"] == key and any((c["name"], m) in rows for m in MODES)]
            if not comps:
                continue
            lines.append("#### %s, %s" % (title, jvm))
            lines.append("")
            lines.append("Time (cold is pass 1, warm the last pass):")
            lines.append("")
            lines.extend(group_table(rows, comps, TIME_COLUMNS))
            lines.append("")
            lines.append("Memory, CPU and classes:")
            lines.append("")
            lines.extend(group_table(rows, comps, MEMORY_COLUMNS))
            lines.append("")
        lines.extend(live_section(jvm, rows, components))
        wins, losses = ranking(rows, components)
        lines.append("#### Biggest wins and losses of compiled JXML against JavaFX, %s (%s)" % (jvm, HEADLINE[0]))
        lines.append("")
        lines.append("| | component | compiled ms | JavaFX ms | ratio |")
        lines.append("|---|---|---:|---:|---:|")
        for tag, items in (("win", wins), ("loss", losses)):
            if not items:
                lines.append("| %s | %s | %s | %s | %s |" % (tag, NA, NA, NA, NA))
            for r, name, c, j in items:
                lines.append("| %s | %s | %s | %s | %s |" % (tag, name, fmt(c), fmt(j), fmt_ratio(r)))
        lines.append("")
        bad = []
        for comp in components:
            for mode in MODES:
                r = rows.get((comp["name"], mode))
                if r is not None:
                    ok, verified = num(r.get("runs_ok")), num(r.get("runs_verified"))
                    runs = num(r.get("runs"))
                    if runs is not None and ok is not None and ok < runs:
                        bad.append("%s %s: %d of %d runs finished" % (comp["name"], mode, ok, runs))
                    if ok is not None and verified is not None and verified < ok:
                        bad.append("%s %s: %d of %d runs have a fingerprint that differs from the reference" % (comp["name"], mode, ok - verified, ok))
        lines.append("#### Runs that failed or did not verify, %s" % jvm)
        lines.append("")
        lines.extend(["- " + b for b in bad] if bad else ["None."])
        lines.append("")
    lines.append("### Components without a JavaFX equivalent")
    lines.append("")
    lines.append("Reported as absolute numbers (no ratio against JavaFX):")
    lines.append("")
    none = [c for c in components if not c["fx"]]
    for c in none:
        lines.append("- %s: %s" % (c["name"], c.get("note") or "no JavaFX equivalent"))
    if not none:
        lines.append("None.")
    aliases = [c for c in components if c.get("alias_of")]
    if aliases:
        lines.append("")
        lines.append("### Pure renames")
        lines.append("")
        lines.append("These tags build the same tree as the tag they rename (checked by the tests); they are not independent results:")
        lines.append("")
        for c in aliases:
            lines.append("- %s = %s" % (c["name"], c["alias_of"]))
    return "\n".join(lines) + "\n"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--dir", required=True, help="results directory of measure-components.ps1")
    parser.add_argument("--table", default=DEFAULT_TABLE, help="components.csv written by generate-component-bench.py")
    parser.add_argument("--out", help="write the report here instead of stdout")
    args = parser.parse_args(argv)
    components = read_csv(args.table)
    text = report(load_medians(args.dir), components, os.path.basename(os.path.normpath(args.dir)))
    if args.out:
        with open(args.out, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
