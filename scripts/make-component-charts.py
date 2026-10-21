"""Draws the charts of the JXForge benchmark reports as plain SVG (light and dark through CSS), from the CSVs.

    python -I scripts/make-component-charts.py [--dir docs/components-2026-10-21] [--out docs/assets/charts]

No plotting library: the SVG is written by hand (standard library only), following the data-viz method of the
reference palette: one axis, thin marks with a 4 px rounded data end, a 2 px surface ring on dots, hairline grid,
text in ink tokens (never in the series color), direct labels only where the story is, the reader's table in the
reports. Ratios are on a log axis anchored at 1x. Charts:

  headline-java17.svg        JavaFX / compiled JXML per metric, geometric mean over the components with a JavaFX equivalent
  by-jvm.svg                 the same on Java 8 x86, 17 and 21 (dot plot)
  update-by-component.svg    one prop of every instance changed, per component (diverging around 1x)
  paint-by-component.svg     paint, per component (diverging; indicative: different pipelines)
  alloc-update-by-component.svg  memory allocated by the update, per component (diverging)
  retained-per-instance.svg  memory kept alive per instance, JavaFX against compiled JXML (Java 17)
  forge-20-screens.svg       the 20 form screens: FXML, interpreted, template and compiled (Java 17)
"""
import argparse
import csv
import math
import os
import statistics as st
from xml.sax.saxutils import escape

FONT = 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif'
WIDTH = 900

CSS = """
.viz{color-scheme:light;--surface:#fcfcfb;--ink:#0b0b0b;--ink2:#52514e;--muted:#898781;--grid:#e1e0d9;--axis:#c3c2b7;
 --s1:#2a78d6;--s2:#eb6834;--s3:#1baf7a;--bad:#e34948;--gray:#b4b2aa;--r250:#86b6ef;--r400:#3987e5;--r550:#1c5cab;--ring:#fcfcfb}
@media (prefers-color-scheme: dark){.viz{color-scheme:dark;--surface:#1a1a19;--ink:#f0efec;--ink2:#c3c2b7;--muted:#898781;
 --grid:#2c2c2a;--axis:#383835;--s1:#3987e5;--s2:#d95926;--s3:#199e70;--bad:#e66767;--gray:#5d5c58;--r250:#6da7ec;--r400:#3987e5;
 --r550:#1c5cab;--ring:#1a1a19}}
.bg{fill:var(--surface)}.ink{fill:var(--ink)}.ink2{fill:var(--ink2)}.muted{fill:var(--muted)}
.grid{stroke:var(--grid);stroke-width:1;fill:none}.axis{stroke:var(--axis);stroke-width:1;fill:none}
.s1{fill:var(--s1)}.s2{fill:var(--s2)}.s3{fill:var(--s3)}.bad{fill:var(--bad)}.gray{fill:var(--gray)}
.r250{fill:var(--r250)}.r400{fill:var(--r400)}.r550{fill:var(--r550)}
.link{stroke:var(--axis);stroke-width:2;fill:none}.ring{stroke:var(--ring);stroke-width:2}
text{font-family:%s}
""" % FONT


# ---- tiny SVG builder --------------------------------------------------------------------------------------------

class Svg:
    def __init__(self, width, height, title, desc):
        self.w, self.h, self.title, self.desc = width, height, title, desc
        self.items = []

    def add(self, s):
        self.items.append(s)

    def text(self, x, y, s, size=13, cls="ink", anchor="start", weight=400):
        self.add('<text x="%.1f" y="%.1f" font-size="%d" font-weight="%d" text-anchor="%s" class="%s">%s</text>'
                 % (x, y, size, weight, anchor, cls, escape(s)))

    def line(self, x1, y1, x2, y2, cls="grid"):
        self.add('<path d="M%.1f %.1fL%.1f %.1f" class="%s"/>' % (x1, y1, x2, y2, cls))

    def bar(self, x0, x1, y, h, cls, r=4):
        """Horizontal bar from the baseline x0 to the data end x1 (either side); the data end is rounded."""
        w = abs(x1 - x0)
        if w < 0.5:
            return
        r = min(r, w, h / 2)
        if x1 >= x0:
            d = "M%.1f %.1fH%.1fQ%.1f %.1f %.1f %.1fV%.1fQ%.1f %.1f %.1f %.1fH%.1fZ" % (
                x0, y, x1 - r, x1, y, x1, y + r, y + h - r, x1, y + h, x1 - r, y + h, x0)
        else:
            d = "M%.1f %.1fH%.1fQ%.1f %.1f %.1f %.1fV%.1fQ%.1f %.1f %.1f %.1fH%.1fZ" % (
                x0, y, x1 + r, x1, y, x1, y + r, y + h - r, x1, y + h, x1 + r, y + h, x0)
        self.add('<path d="%s" class="%s"/>' % (d, cls))

    def dot(self, x, y, cls, r=5):
        self.add('<circle cx="%.1f" cy="%.1f" r="%d" class="%s ring"/>' % (x, y, r, cls))

    def render(self):
        return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d" role="img" class="viz" '
                'aria-labelledby="t d"><title id="t">%s</title><desc id="d">%s</desc><style>%s</style>'
                '<rect class="bg" width="%d" height="%d" rx="12"/>%s</svg>\n'
                % (self.w, self.h, self.w, self.h, escape(self.title), escape(self.desc), CSS, self.w, self.h,
                   "".join(self.items)))


def header(svg, title, subtitle):
    svg.text(32, 40, title, 20, "ink", weight=600)
    svg.text(32, 62, subtitle, 13, "ink2")


class LogAxis:
    def __init__(self, vmin, vmax, x0, x1):
        self.vmin, self.vmax, self.x0, self.x1 = vmin, vmax, x0, x1
        self.lo, self.hi = math.log10(vmin), math.log10(vmax)

    def x(self, v):
        v = min(max(v, self.vmin), self.vmax)
        return self.x0 + (math.log10(v) - self.lo) / (self.hi - self.lo) * (self.x1 - self.x0)

    def ticks(self, steps=(1, 2, 5)):
        out = []
        e = math.floor(self.lo)
        while 10 ** e <= self.vmax * 1.0001:
            for s in steps:
                v = s * 10 ** e
                if self.vmin * 0.9999 <= v <= self.vmax * 1.0001:
                    out.append(v)
            e += 1
        return out


def ratio_label(v):
    if v >= 100:
        return "%.0fx" % v
    if v >= 10:
        return "%.0fx" % v
    if v >= 1:
        return "%.1fx" % v
    return "%.2fx" % v


def tick_label(v):
    return ("%gx" % v)


def draw_axis(svg, ax, top, bottom, label_y, fmt=tick_label, baseline=None):
    for v in ax.ticks():
        x = ax.x(v)
        svg.line(x, top, x, bottom, "grid")
        svg.text(x, label_y, fmt(v), 12, "muted", "middle")
    if baseline is not None:
        x = ax.x(baseline)
        svg.line(x, top, x, bottom, "axis")


# ---- data --------------------------------------------------------------------------------------------------------

def num(r, k):
    try:
        return float(r[k])
    except (KeyError, TypeError, ValueError):
        return None


def med(v):
    v = [x for x in v if x is not None]
    return st.median(v) if v else None


def gm(x):
    return math.exp(sum(math.log(v) for v in x) / len(x))


METRICS = [  # label, csv column (or "retained"), group
    ("Build, cold", "build_cold_ns"), ("Build, warm", "build_warm_ns"),
    ("Ready to paint, cold", "ready_cold_ns"), ("Ready to paint, warm", "ready_warm_ns"),
    ("Update one prop, warm", "update_warm_ns"), ("Paint, warm (indicative)", "paint_warm_ns"),
    ("Memory allocated: build", "build_warm_alloc_bytes"), ("Memory allocated: ready", "ready_warm_alloc_bytes"),
    ("Memory allocated: update", "update_warm_alloc_bytes"), ("Memory retained per instance", "retained"),
    ("Classes loaded", "classes_loaded"), ("Process CPU, whole run", "process_cpu_total_ns"),
]


class Series:
    def __init__(self, directory):
        self.dir = directory
        self.cache = {}

    def load(self, jvm):
        if jvm in self.cache:
            return self.cache[jvm]
        g, info = {}, {}
        with open(os.path.join(self.dir, "components-%s.csv" % jvm), encoding="utf-8-sig") as f:
            for r in csv.DictReader(f):
                if r["status"] == "ok" and r["mode"] in ("javafx", "interpreted", "template", "compiled"):
                    g.setdefault((r["component"], r["mode"]), []).append(r)
                    info[r["component"]] = int(float(r["n"]))
        self.cache[jvm] = (g, info)
        return g, info

    @staticmethod
    def value(rows, key, jvm):
        if key == "retained":
            if jvm.startswith("java8"):
                return None  # the client VM's heap reading moves in 2.9 MB steps: not a measurement
            v = [((num(r, "heap_live_with_bytes") or 0) - (num(r, "heap_live_released_bytes") or 0)) / max(1, num(r, "n"))
                 for r in rows]
            m = med(v)
            return m if m and m > 0 else None
        return med([num(r, key) for r in rows])

    def ratios(self, jvm, key, mode="compiled"):
        """{component: JavaFX / mode} for the components that have a JavaFX equivalent."""
        g, _ = self.load(jvm)
        out = {}
        for (c, m) in g:
            if m != "javafx":
                continue
            a, b = self.value(g[(c, "javafx")], key, jvm), self.value(g[(c, mode)], key, jvm)
            if a and b and a > 0 and b > 0:
                out[c] = a / b
        return out


# ---- charts ------------------------------------------------------------------------------------------------------

def headline(data):
    rows = []
    for label, key in METRICS:
        r = data.ratios("java17-x64", key)
        if r:
            rows.append((label, gm(list(r.values())), len(r)))
    pitch, top = 32, 96
    h = top + pitch * len(rows) + 64
    svg = Svg(WIDTH, h, "Compiled JXML against JavaFX on Java 17",
              "For each metric, how many times better compiled JXML is than the JavaFX control, as a geometric mean over the "
              "components that have a JavaFX equivalent. " + "; ".join("%s %s" % (l, ratio_label(v)) for l, v, _ in rows))
    header(svg, "Compiled JXML against JavaFX, Java 17 x64",
           "How many times better JXML is (geometric mean over %d components, log scale; 1x = same as JavaFX)" % rows[0][2])
    ax = LogAxis(1, 100, 250, WIDTH - 90)
    draw_axis(svg, ax, top - 14, top + pitch * len(rows) - 6, top + pitch * len(rows) + 14, baseline=1)
    for i, (label, v, _) in enumerate(rows):
        y = top + i * pitch
        svg.text(32, y + 14, label, 13, "ink2")
        svg.bar(ax.x(1), ax.x(v), y, 16, "s1")
        svg.text(ax.x(v) + 8, y + 13, ratio_label(v), 13, "ink", weight=600)
    svg.text(32, h - 18, "Ready to paint compares native nodes with a JavaFX Scene, CSS and skins: different work by design. "
             "Paint compares different pipelines.", 11, "muted")
    return svg


JVMS = [("java8-x86", "Java 8 x86", "s1"), ("java17-x64", "Java 17 x64", "s2"), ("java21-x64", "Java 21 x64", "s3")]


def by_jvm(data):
    metrics = [m for m in METRICS if m[1] != "retained"]
    pitch, top = 46, 118
    h = top + pitch * len(metrics) + 36
    svg = Svg(WIDTH, h, "Compiled JXML against JavaFX on three JVMs",
              "Grouped bars of the geometric-mean ratio JavaFX / compiled JXML for each metric on Java 8 x86, Java 17 x64 and Java 21 x64.")
    header(svg, "The same comparison on three JVMs",
           "JavaFX divided by compiled JXML, geometric mean over the components with a JavaFX equivalent (log scale)")
    ax = LogAxis(1, 300, 250, WIDTH - 210)
    lx = 32
    for jvm, name, cls in JVMS:  # a legend is always present for three series
        svg.add('<rect x="%d" y="82" width="12" height="12" rx="3" class="%s"/>' % (lx, cls))
        svg.text(lx + 18, 93, name, 13, "ink2")
        lx += 150
    draw_axis(svg, ax, top - 12, top + pitch * len(metrics) - 10, top + pitch * len(metrics) + 10, baseline=1)
    svg.text(WIDTH - 196, top - 20, "Java 8 | 17 | 21", 12, "muted")
    for i, (label, key) in enumerate(metrics):
        y = top + i * pitch
        svg.text(32, y + 20, label, 13, "ink2")
        vals = [gm(list(data.ratios(j, key).values())) for j, _, _ in JVMS]
        for k, ((jvm, name, cls), v) in enumerate(zip(JVMS, vals)):
            svg.bar(ax.x(1), ax.x(v), y + k * 10, 8, cls, r=3)
        # the reader's table: the aqua series sits below 3:1 on the surface, so the values are also written out
        svg.text(WIDTH - 196, y + 20, " | ".join(ratio_label(v).replace("x", "") for v in vals) + "x", 12, "ink2")
    return svg

def diverging(data, key, title, subtitle, name, lo, hi, notes, extreme=3):
    r = data.ratios("java17-x64", key)
    items = sorted(r.items(), key=lambda kv: -kv[1])
    pitch, top, namew = 19, 100, 150
    h = top + pitch * len(items) + 70
    worse = [(c, v) for c, v in items if v < 1]
    svg = Svg(WIDTH, h, title,
              "%s Components where JXML is worse: %s." % (subtitle, ", ".join("%s %s" % (c, ratio_label(v)) for c, v in worse) or "none"))
    header(svg, title, subtitle)
    ax = LogAxis(lo, hi, 32 + namew, WIDTH - 60)
    draw_axis(svg, ax, top - 14, top + pitch * len(items) - 4, top + pitch * len(items) + 14, baseline=1)
    svg.text(ax.x(1) - 8, top - 22, "JXML allocates more" if "alloc" in name else "JXML slower", 12, "muted", "end")
    svg.text(ax.x(1) + 8, top - 22, "JXML allocates less" if "alloc" in name else "JXML faster", 12, "muted")
    best = {c for c, _ in items[:extreme]}
    for i, (c, v) in enumerate(items):
        y = top + i * pitch
        svg.text(32, y + 11, c, 12, "ink2")
        svg.bar(ax.x(1), ax.x(v), y, 12, "s1" if v >= 1 else "bad")
        if v < 1 or c in best:  # label the story, not every bar: the losses and the best few
            right = v >= 1
            svg.text(ax.x(v) + (7 if right else -7), y + 10, ratio_label(v), 12, "ink", "start" if right else "end", 600)
    for i, line in enumerate(notes):
        svg.text(32, h - 36 + i * 15, line, 11, "muted")
    return svg


def retained(data):
    comps = ["Label", "Button", "CheckBox", "TextField", "ComboBox", "DatePicker", "Slider", "Row", "Grid", "Tabs", "TitledPane", "Image"]
    g, _ = data.load("java17-x64")
    rows = []
    for c in comps:
        a = Series.value(g[(c, "javafx")], "retained", "java17-x64")
        b = Series.value(g[(c, "compiled")], "retained", "java17-x64")
        rows.append((c, a, b))
    pitch, top = 40, 118
    h = top + pitch * len(rows) + 40
    svg = Svg(WIDTH, h, "Memory kept alive per instance, JavaFX against compiled JXML",
              "Bytes retained per instance on Java 17 x64 (live heap after a full collection, over the instances). "
              + "; ".join("%s: JavaFX %d, JXML %d" % (c, a, b) for c, a, b in rows))
    header(svg, "Memory kept alive per instance, Java 17 x64",
           "Live heap after a full collection, divided by the instances held (log scale)")
    ax = LogAxis(100, 1000000, 150, WIDTH - 110)

    def fmt(v):
        return "%d B" % round(v) if v < 1000 else ("%.1f KB" % (v / 1024) if v < 1024 * 1024 else "%.1f MB" % (v / 1048576))

    svg.add('<rect x="32" y="82" width="12" height="12" rx="3" class="gray"/>')
    svg.text(50, 93, "JavaFX control", 13, "ink2")
    svg.add('<rect x="170" y="82" width="12" height="12" rx="3" class="s1"/>')
    svg.text(188, 93, "Compiled JXML", 13, "ink2")
    for v in ax.ticks((1,)):
        x = ax.x(v)
        svg.line(x, top - 8, x, top + pitch * len(rows) - 8, "grid")
        svg.text(x, top + pitch * len(rows) + 12, "%s" % ("%d B" % v if v < 1000 else ("%d KB" % (v / 1000) if v < 1e6 else "1 MB")), 12, "muted", "middle")
    svg.line(ax.x(100), top - 8, ax.x(100), top + pitch * len(rows) - 8, "axis")
    for i, (c, a, b) in enumerate(rows):
        y = top + i * pitch
        svg.text(32, y + 20, c, 13, "ink2")
        svg.bar(ax.x(100), ax.x(a), y, 14, "gray")
        svg.text(ax.x(a) + 8, y + 12, fmt(a), 12, "ink2")
        svg.bar(ax.x(100), ax.x(b), y + 18, 14, "s1")
        svg.text(ax.x(b) + 8, y + 30, fmt(b), 12, "ink", weight=600)
    return svg


def ms_label(v):
    if v >= 1000:
        return "%.1f s" % (v / 1000)
    if v >= 100:
        return "%.0f ms" % v
    if v >= 10:
        return "%.1f ms" % v
    return "%.2f ms" % v


def forge_screens(directory):
    def table(stage):
        with open(os.path.join(directory, "..", "jxforge-results-2026-10-21-java17-x64-%s-median.csv" % stage), encoding="utf-8-sig") as f:
            return {r["metric"]: r for r in csv.DictReader(f)}
    load, ready = table("load"), table("ready")
    groups = [("Load, cold", load["pass1_total_ns"]), ("Load, warm", load["pass10_total_ns"]),
              ("Ready to paint, cold", ready["pass1_total_ns"]), ("Ready to paint, warm", ready["pass10_total_ns"])]
    modes = [("javafx", "FXML on JavaFX", "gray"), ("interpreted", "JXML interpreted", "r250"),
             ("template", "JXML cached template", "r400"), ("compiled", "JXML compiled", "r550")]
    pitch, bar, top = 100, 15, 118
    h = top + pitch * len(groups) + 36
    svg = Svg(WIDTH, h, "Twenty form screens: FXML against JXML, Java 17",
              "Time to build or get ready 20 form screens on Java 17 x64, median of 10 runs: "
              + "; ".join("%s: %s" % (gname, ", ".join("%s %.2f ms" % (mn, float(row[m]) / 1e6) for m, mn, _ in modes)) for gname, row in groups))
    header(svg, "Twenty form screens, Java 17 x64", "Time for 20 screens, median of 10 runs (log scale, milliseconds)")
    ax = LogAxis(0.1, 5000, 210, WIDTH - 100)
    lx = 32
    for m, name, cls in modes:
        svg.add('<rect x="%d" y="82" width="12" height="12" rx="3" class="%s"/>' % (lx, cls))
        svg.text(lx + 18, 93, name, 13, "ink2")
        lx += 210 if m == "template" else 175
    for v in ax.ticks((1,)):
        x = ax.x(v)
        svg.line(x, top - 12, x, top + pitch * len(groups) - 16, "grid")
        svg.text(x, top + pitch * len(groups) + 4, ("%g ms" % v) if v < 1000 else ("%g s" % (v / 1000)), 12, "muted", "middle")
    svg.line(ax.x(0.1), top - 12, ax.x(0.1), top + pitch * len(groups) - 16, "axis")
    for gi, (gname, row) in enumerate(groups):
        y0 = top + gi * pitch
        svg.text(32, y0 + 38, gname, 13, "ink2", weight=600)
        for mi, (m, mname, cls) in enumerate(modes):
            v = float(row[m]) / 1e6
            y = y0 + mi * (bar + 4)
            svg.bar(ax.x(0.1), ax.x(v), y, bar, cls)
            svg.text(ax.x(v) + 8, y + 12, ms_label(v), 12, "ink", weight=600 if m == "compiled" else 400)
    return svg


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default="docs/components-2026-10-21")
    ap.add_argument("--out", default="docs/assets/charts")
    ap.add_argument("--after", action="store_true",
                    help="the series measured after the optimizations: other notes, files named -after, no 20-screens chart")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    data = Series(a.dir)
    if a.after:
        charts = {
            "headline-java17-after": headline(data),
            "by-jvm-after": by_jvm(data),
            "update-by-component-after": diverging(
                data, "update_warm_ns", "Changing one prop of every instance, per component",
                "JavaFX divided by compiled JXML on Java 17 x64 (log scale; blue = JXML faster, red = slower)", "update", 0.2, 50,
                ["JXML builds a new element and reconciles it; JavaFX sets the property. Only the losses and the three best are labeled.",
                 "Tree and TreeTable keep their 10,000 items with <For memo>."]),
            "paint-by-component-after": diverging(
                data, "paint_warm_ns", "Painting, per component (indicative)",
                "JavaFX divided by JXML on Java 17 x64 (log scale; blue = JXML faster, red = slower)", "paint", 0.3, 20,
                ["JXML paints with its software painter, JavaFX with Prism: different pipelines. Only the losses and the three best are labeled.",
                 "Nodes outside the window are skipped; each frame is the last of 8 passes of a fresh JVM, so the loops are not fully compiled."]),
            "alloc-update-by-component-after": diverging(
                data, "update_warm_alloc_bytes", "Memory allocated by the update, per component",
                "JavaFX divided by compiled JXML on Java 17 x64 (log scale; blue = JXML allocates less, red = more)", "alloc update", 0.005, 100,
                ["Rebuilding an immutable element allocates more than setting a property when the change is tiny.",
                 "Only the losses and the three best are labeled."]),
            "retained-per-instance-after": retained(data),
        }
        for name, svg in charts.items():
            path = os.path.join(a.out, name + ".svg")
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(svg.render())
            print("wrote", path)
        return
    charts = {
        "headline-java17": headline(data),
        "by-jvm": by_jvm(data),
        "update-by-component": diverging(
            data, "update_warm_ns", "Changing one prop of every instance, per component",
            "JavaFX divided by compiled JXML on Java 17 x64 (log scale; blue = JXML faster, red = slower)", "update", 0.2, 50,
            ["JXML builds a new element and reconciles it; JavaFX sets the property. Only the losses and the three best are labeled.",
             "Tree and TreeTable flatten the whole 10,000-row model on every render."]),
        "paint-by-component": diverging(
            data, "paint_warm_ns", "Painting, per component (indicative)",
            "JavaFX divided by JXML on Java 17 x64 (log scale; blue = JXML faster, red = slower)", "paint", 0.3, 20,
            ["JXML paints with its software painter, JavaFX with Prism: different pipelines. Only the losses and the three best are labeled.",
             "The software painter does not skip nodes outside the window (cause read in the source, not isolated)."]),
        "alloc-update-by-component": diverging(
            data, "update_warm_alloc_bytes", "Memory allocated by the update, per component",
            "JavaFX divided by compiled JXML on Java 17 x64 (log scale; blue = JXML allocates less, red = more)", "alloc update", 0.005, 100,
            ["Rebuilding an immutable element allocates more than setting a property when the change is tiny.",
             "Only the losses and the three best are labeled."]),
        "retained-per-instance": retained(data),
        "forge-20-screens": forge_screens(a.dir),
    }
    for name, svg in charts.items():
        path = os.path.join(a.out, name + ".svg")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(svg.render())
        print("wrote", path)


if __name__ == "__main__":
    main()
