"""Self-test of summarize-components.py on a tiny fake result: run with  python -I scripts/summarize-components-selftest.py

(`python -I` leaves the script's folder off sys.path and the module name has a dash, so the module is loaded by path.)
"""
import csv
import importlib.util
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))


def load():
    spec = importlib.util.spec_from_file_location("summarize_components", os.path.join(HERE, "summarize-components.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


S = load()

TABLE = [
    {"name": "Button", "group": "buttons", "fx": "Button", "alias_of": "", "note": ""},
    {"name": "Switch", "group": "material", "fx": "", "alias_of": "", "note": "no JavaFX equivalent"},
    {"name": "Box", "group": "containers", "fx": "Stack", "alias_of": "Stack", "note": "a pure rename of Stack: same tree"},
    {"name": "Zero", "group": "inputs", "fx": "Label", "alias_of": "", "note": ""},
]


def row(component, mode, build_warm, ready_warm=2e6, runs=5, ok=5, verified=5, **extra):
    r = {"component": component, "mode": mode, "runs": runs, "runs_ok": ok, "runs_verified": verified, "n": 300,
         "build_cold_ns": build_warm * 20, "build_warm_ns": build_warm, "ready_cold_ns": ready_warm * 10, "ready_warm_ns": ready_warm,
         "update_warm_ns": 1e6, "paint_warm_ns": 3e6, "build_warm_alloc_bytes": 2048, "ready_warm_alloc_bytes": 4096,
         "update_warm_alloc_bytes": 1024, "heap_live_with_bytes": 6000000, "heap_live_released_bytes": 5700000,
         "build_total_thread_cpu_ns": 1e6, "ready_total_thread_cpu_ns": 1e6, "update_total_thread_cpu_ns": 0,
         "paint_total_thread_cpu_ns": 0, "classes_loaded": 1200, "peak_working_set_bytes": 100 * 1024 * 1024}
    r.update(extra)
    return r


class SummaryTest(unittest.TestCase):
    def medians(self):
        rows = [row("Button", "javafx", 5e6, 40e6), row("Button", "interpreted", 2e6), row("Button", "template", 0.5e6),
                row("Button", "compiled", 0.2e6, 6e6),
                row("Switch", "interpreted", 2e6), row("Switch", "compiled", 0.1e6, verified=3),
                row("Zero", "javafx", 0, 0), row("Zero", "compiled", 1e6, 1e6),
                row("Box", "interpreted", 1e6)]
        return {"java17-x64": {(r["component"], r["mode"]): r for r in rows}}

    def test_ratio_against_javafx_and_bold_where_worse(self):
        text = S.report(self.medians(), TABLE)
        self.assertIn("### java17-x64", text)
        self.assertIn("#### Buttons and toggles, java17-x64", text)
        self.assertIn("| Button | javafx (ref) |", text)
        # compiled build warm 0.2 ms against 5 ms is 0.04x; the interpreted ready (2 ms against 40 ms) is better too
        self.assertIn("0.20 (0.04x)", text)
        # nothing is worse than JavaFX in this fake: no bold on the Button rows
        for line in text.splitlines():
            if line.startswith("| Button |"):
                self.assertNotIn("**", line)

    def test_a_mode_worse_than_javafx_is_bold(self):
        medians = self.medians()
        medians["java17-x64"][("Button", "compiled")] = row("Button", "compiled", 9e6, 6e6)
        text = S.report(medians, TABLE)
        self.assertIn("**9.00 (1.80x)**", text)

    def test_components_without_javafx_use_interpreted_as_reference(self):
        text = S.report(self.medians(), TABLE)
        self.assertIn("| Switch | interpreted (ref) |", text)
        self.assertIn("0.10 (0.05x)", text)  # compiled build warm, 0.1 ms against 2 ms
        self.assertNotIn("| Switch | javafx", text)
        self.assertIn("- Switch: no JavaFX equivalent", text)
        self.assertIn("- Box = Stack", text)

    def test_zero_and_missing_never_divide_and_are_shown_as_na(self):
        text = S.report(self.medians(), TABLE)
        # JavaFX measured 0 for Zero: the ratio is n/a, not a crash or inf
        zero = [line for line in text.splitlines() if line.startswith("| Zero | compiled")][0]
        self.assertIn("(n/a)", zero)
        self.assertNotIn("inf", text.lower().replace("information", ""))
        self.assertEqual(S.ratio(1.0, 0.0), None)
        self.assertEqual(S.ratio(None, 1.0), None)
        self.assertEqual(S.ratio(1.0, None), None)
        self.assertEqual(S.fmt(None), "n/a")
        # a metric missing from the CSV prints n/a
        missing = self.medians()
        del missing["java17-x64"][("Button", "compiled")]["paint_warm_ns"]
        self.assertIn("n/a", [l for l in S.report(missing, TABLE).splitlines() if l.startswith("| Button | compiled")][0])

    def test_ranking_orders_wins_and_losses_and_lists_unverified_runs(self):
        medians = self.medians()
        text = S.report(medians, TABLE)
        wins, losses = S.ranking(medians["java17-x64"], TABLE)
        self.assertEqual(wins[0][1], "Button")  # Zero has a zero JavaFX side: not ranked
        self.assertEqual(len(wins), 1)
        self.assertIn("| win | Button |", text)
        self.assertIn("Switch compiled: 2 of 5 runs have a fingerprint that differs from the reference", text)
        # the retained size per instance: (6000000 - 5700000) / 300
        self.assertIn("1000", text)

    def test_empty_directory(self):
        self.assertIn("No `components-<jvm>-median.csv` found.", S.report({}, TABLE))

    def test_command_line_with_a_bom_csv(self):
        with tempfile.TemporaryDirectory() as d:
            rows = [row("Button", "javafx", 5e6), row("Button", "compiled", 0.2e6)]
            columns = sorted({k for r in rows for k in r})
            with open(os.path.join(d, "components-java8-x86-median.csv"), "w", newline="", encoding="utf-8-sig") as f:
                w = csv.DictWriter(f, fieldnames=columns)
                w.writeheader()
                w.writerows(rows)
            table = os.path.join(d, "components.csv")
            with open(table, "w", newline="", encoding="utf-8") as f:
                w = csv.DictWriter(f, fieldnames=["name", "group", "fx", "alias_of", "note"])
                w.writeheader()
                w.writerow({"name": "Button", "group": "buttons", "fx": "Button", "alias_of": "", "note": ""})
            out = subprocess.run([sys.executable, "-I", os.path.join(HERE, "summarize-components.py"), "--dir", d, "--table", table],
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True, check=True)
            self.assertIn("### java8-x86", out.stdout)
            self.assertIn("| Button | compiled |", out.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
