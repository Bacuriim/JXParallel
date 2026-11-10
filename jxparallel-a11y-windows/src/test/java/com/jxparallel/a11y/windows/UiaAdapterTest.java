package com.jxparallel.a11y.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.accessibility.JXAccessibility;
import com.jxparallel.ui.accessibility.JXAccessibilityAdapter;
import com.jxparallel.ui.accessibility.JXAccessibilityEvent;
import com.jxparallel.ui.accessibility.JXAccessibilityHost;
import com.jxparallel.ui.accessibility.JXAccessibilityRole;
import com.jxparallel.ui.accessibility.JXAccessibleNode;
import com.jxparallel.ui.native2d.JXWindow;

/**
 * Opens a small window, installs the UI Automation adapter and reads the window back through the UI Automation CLIENT API
 * (.NET's System.Windows.Automation in a PowerShell child process): the element tree, control types, names, patterns,
 * then invokes a button, toggles a check box and sets a text value through the patterns. It sends no mouse or keyboard
 * input. Skipped off Windows, on 32-bit, and when no window can be opened (no desktop session).
 */
class UiaAdapterTest {
    private static final String CLIENT = String.join("\n",
        "param([long]$Hwnd, [string]$Act = '')",
        "Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes",
        "$ErrorActionPreference = 'Stop'",
        "$root = [System.Windows.Automation.AutomationElement]::FromHandle([IntPtr]$Hwnd)",
        "$walker = [System.Windows.Automation.TreeWalker]::RawViewWalker",
        "function Walk($el, $depth) {",
        "  $c = $el.Current",
        "  $pats = ($el.GetSupportedPatterns() | ForEach-Object { $_.ProgrammaticName -replace 'Identifiers.Pattern','' }) -join ','",
        "  $r = $c.BoundingRectangle",
        "  Write-Output ('NODE|{0}|{1}|{2}|{3}|{4}|{5}|{6}|{7},{8},{9},{10}' -f $depth, $c.ControlType.ProgrammaticName, $c.Name, $c.IsEnabled, $pats, $c.IsKeyboardFocusable, $c.IsPassword, [int]$r.X, [int]$r.Y, [int]$r.Width, [int]$r.Height)",
        "  $ch = $walker.GetFirstChild($el)",
        "  while ($ch -ne $null) { Walk $ch ($depth + 1); $ch = $walker.GetNextSibling($ch) }",
        "}",
        "Walk $root 0",
        "function Find($name) {",
        "  $cond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, $name)",
        "  $root.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $cond)",
        "}",
        "if ($Act -eq 'act') {",
        "  (Find 'Increment').GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke()",
        "  Write-Output 'DID|invoke'",
        "  (Find 'Agree').GetCurrentPattern([System.Windows.Automation.TogglePattern]::Pattern).Toggle()",
        "  Write-Output 'DID|toggle'",
        "  (Find 'Name').GetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern).SetValue('Typed by UIA')",
        "  Write-Output 'DID|setvalue'",
        "  $pin = Find 'PIN'",
        "  $pinText = ''",
        "  try { $pinText = $pin.GetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern).Current.Value } catch { $pinText = 'DENIED' }",
        "  Write-Output ('DID|pin=' + $pinText)",
        "}");

    @Test
    void theAdapterIsFoundByTheNameOfDashDJxAccessibility() {
        System.setProperty(JXAccessibility.PROPERTY, "uia");
        try {
            JXAccessibility.uninstall();
            // uninstall marks the choice made; a fresh JVM reads the property, which ServiceLoader resolves by name
            boolean found = false;
            for (JXAccessibilityAdapter a : java.util.ServiceLoader.load(JXAccessibilityAdapter.class)) {
                found |= a instanceof UiaAdapter && "uia".equals(a.getName());
            }
            assertTrue(found, "META-INF/services lists the UI Automation adapter");
        } finally {
            System.clearProperty(JXAccessibility.PROPERTY);
        }
    }

    private static JXElement el(String type, JXProps.Builder props, JXElement... kids) {
        return JXElement.of(type, props.build(), kids);
    }

    private static List<String> run(Path script, long hwnd, String act) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("powershell.exe", "-MTA", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
                script.toString(), "-Hwnd", String.valueOf(hwnd), "-Act", act).redirectErrorStream(true).start();
        List<String> lines = new ArrayList<String>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                lines.add(line);
            }
        }
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
        }
        return lines;
    }

    private static boolean has(List<String> lines, String controlType, String name) {
        for (String l : lines) {
            String[] f = l.split("\\|", -1);
            if (f.length > 3 && "NODE".equals(f[0]) && f[2].equals("ControlType." + controlType) && f[3].equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String patternsOf(List<String> lines, String name) {
        for (String l : lines) {
            String[] f = l.split("\\|", -1);
            if (f.length > 5 && "NODE".equals(f[0]) && f[3].equals(name)) {
                return f[5];
            }
        }
        return null;
    }

    @Test
    void aUiAutomationClientReadsTheWindowAndInvokesItsControls() throws Exception {
        assumeTrue(UiaAdapter.supported(), "UI Automation needs 64-bit Windows");
        System.setProperty("jx.renderer", "software"); // no OpenGL context needed
        AtomicInteger clicks = new AtomicInteger();
        List<Object> changes = new ArrayList<Object>();
        List<Object> pinChanges = new ArrayList<Object>();
        Consumer<Object> record = v -> {
            synchronized (changes) {
                changes.add(v);
            }
        };
        AtomicReference<JXAccessibilityHost> hostRef = new AtomicReference<JXAccessibilityHost>();
        List<JXAccessibilityEvent> events = new ArrayList<JXAccessibilityEvent>();
        UiaAdapter uia = new UiaAdapter();
        JXAccessibility.install(new JXAccessibilityAdapter() {
            public String getName() { return "uia-test"; }
            public void attach(JXAccessibilityHost h) { hostRef.set(h); uia.attach(h); }
            public void detach(JXAccessibilityHost h) { uia.detach(h); }
            public void deliver(JXAccessibilityHost h, List<JXAccessibilityEvent> e) {
                synchronized (events) {
                    events.addAll(e);
                }
                uia.deliver(h, e);
            }
        });
        JXWindow window = new JXWindow("JX UIA test");
        try {
            window.setSize(420, 300);
            window.setFocusOnShow(false);
            window.setContent(el("column", JXProps.builder().set("gap", 6.0).set("padding", "10"),
                    el("#text", JXProps.builder().set("value", "Hello JX")),
                    el("button", JXProps.builder().set("label", "Increment").set("onAction", (Runnable) clicks::incrementAndGet)),
                    el("checkbox", JXProps.builder().set("label", "Agree").set("checked", false).set("onChange", record)),
                    el("input", JXProps.builder().set("value", "").set("accessibleName", "Name").set("onChange", record)),
                    el("password", JXProps.builder().set("value", "hunter2").set("accessibleName", "PIN")
                            .set("onChange", (Consumer<Object>) pinChanges::add))));
            try {
                window.open();
            } catch (Throwable t) {
                assumeTrue(false, "no window can be opened here: " + t);
            }
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline && (hostRef.get() == null || hostRef.get().getRoot() == null
                    || hostRef.get().getNativeHandle() == 0 || hostRef.get().getRoot().find(JXAccessibilityRole.BUTTON, "Increment") == null)) {
                Thread.sleep(50);
            }
            JXAccessibilityHost host = hostRef.get();
            assumeTrue(host != null && host.getNativeHandle() != 0, "the window got no native handle");
            JXAccessibleNode snap = host.getRoot();
            assertTrue(snap.find(JXAccessibilityRole.BUTTON, "Increment") != null, snap.dump());

            Path script = Files.createTempFile("jx-uia-client", ".ps1");
            Files.write(script, CLIENT.getBytes(StandardCharsets.UTF_8));
            try {
                List<String> out = run(script, host.getNativeHandle(), "act");
                String all = String.join("\n", out);
                assertTrue(has(out, "Button", "Increment"), all);
                assertTrue(has(out, "CheckBox", "Agree"), all);
                assertTrue(has(out, "Edit", "Name"), all);
                assertTrue(has(out, "Edit", "PIN"), all);
                assertTrue(has(out, "Text", "Hello JX"), all);
                assertTrue(patternsOf(out, "Increment").contains("Invoke"), all);
                assertTrue(patternsOf(out, "Agree").contains("Toggle"), all);
                assertTrue(patternsOf(out, "Name").contains("Value"), all);
                assertTrue(out.contains("DID|invoke"), all);
                assertTrue(out.contains("DID|toggle"), all);
                assertTrue(out.contains("DID|setvalue"), all);
                assertTrue(!all.contains("hunter2"), "a password must not be readable: " + all);

                long until = System.currentTimeMillis() + 10000;
                while (System.currentTimeMillis() < until && (clicks.get() == 0 || changes.size() < 2)) {
                    Thread.sleep(50);
                }
                assertEquals(1, clicks.get(), "Invoke reached the button's onAction");
                synchronized (changes) {
                    assertTrue(changes.contains(Boolean.TRUE), "Toggle reached the check box's onChange: " + changes);
                    assertTrue(changes.contains("Typed by UIA"), "SetValue reached the field's onChange: " + changes);
                }
            } finally {
                Files.deleteIfExists(script);
            }
        } finally {
            window.dispose();
            window.awaitClosed();
            JXAccessibility.uninstall();
        }
    }

    private static final String LISTENER = String.join("\n",
        "param([long]$Hwnd)",
        "Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes",
        "Add-Type -ReferencedAssemblies UIAutomationClient,UIAutomationTypes -TypeDefinition @'",
        "using System; using System.Collections.Generic; using System.Windows.Automation;",
        "public class FocusLog { public static List<string> Names = new List<string>(); public static List<string> Props = new List<string>();",
        "  public static void On(object s, AutomationFocusChangedEventArgs e) { try { var el = s as AutomationElement;",
        "    lock (Names) { Names.Add(el.Current.Name); } } catch (Exception ex) { lock (Props) { Props.Add(\"ERR \" + ex.Message); } } }",
        "  public static void OnProp(object s, AutomationPropertyChangedEventArgs e) { try { var el = s as AutomationElement;",
        "    lock (Props) { Props.Add(el.Current.Name + \"=\" + e.NewValue); } } catch (Exception ex) { lock (Props) { Props.Add(\"ERR \" + ex.Message); } } } }",
        "'@",
        "$root = [System.Windows.Automation.AutomationElement]::FromHandle([IntPtr]$Hwnd)",
        "$h = [Delegate]::CreateDelegate([System.Windows.Automation.AutomationFocusChangedEventHandler], [FocusLog].GetMethod('On'))",
        "[System.Windows.Automation.Automation]::AddAutomationFocusChangedEventHandler($h)",
        "$h2 = [Delegate]::CreateDelegate([System.Windows.Automation.AutomationPropertyChangedEventHandler], [FocusLog].GetMethod('OnProp'))",
        "[System.Windows.Automation.Automation]::AddAutomationPropertyChangedEventHandler($root, [System.Windows.Automation.TreeScope]::Subtree, $h2, [System.Windows.Automation.ValuePattern]::ValueProperty)",
        "Write-Output 'READY'",
        "[Console]::Out.Flush()",
        "$until = (Get-Date).AddSeconds(12)",
        "while ((Get-Date) -lt $until) { Start-Sleep -Milliseconds 100; if ([FocusLog]::Props.Count -gt 0) { break } }",
        "foreach ($n in [FocusLog]::Names) { Write-Output ('EVENT|focus|' + $n) }",
        "foreach ($n in [FocusLog]::Props) { Write-Output ('EVENT|value|' + $n) }",
        "[System.Windows.Automation.Automation]::RemoveAllEventHandlers()");

    @Test
    void focusAndValueChangesReachAUiAutomationClientAsEvents() throws Exception {
        assumeTrue(UiaAdapter.supported(), "UI Automation needs 64-bit Windows");
        System.setProperty("jx.renderer", "software");
        AtomicReference<JXAccessibilityHost> hostRef = new AtomicReference<JXAccessibilityHost>();
        List<JXAccessibilityEvent> events = new ArrayList<JXAccessibilityEvent>();
        UiaAdapter uia = new UiaAdapter();
        JXAccessibility.install(new JXAccessibilityAdapter() {
            public String getName() { return "uia-test"; }
            public void attach(JXAccessibilityHost h) { hostRef.set(h); uia.attach(h); }
            public void detach(JXAccessibilityHost h) { uia.detach(h); }
            public void deliver(JXAccessibilityHost h, List<JXAccessibilityEvent> e) {
                synchronized (events) {
                    events.addAll(e);
                }
                uia.deliver(h, e);
            }
        });
        JXWindow window = new JXWindow("JX UIA events");
        try {
            window.setSize(420, 200);
            window.setFocusOnShow(true);
            window.setContent(el("column", JXProps.builder().set("gap", 6.0).set("padding", "10"),
                    el("button", JXProps.builder().set("label", "First").set("onAction", (Runnable) () -> { })),
                    el("input", JXProps.builder().set("value", "").set("accessibleName", "Name"))));
            try {
                window.open();
            } catch (Throwable t) {
                assumeTrue(false, "no window can be opened here: " + t);
            }
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline && (hostRef.get() == null || hostRef.get().getRoot() == null
                    || hostRef.get().getNativeHandle() == 0 || hostRef.get().getRoot().find(JXAccessibilityRole.TEXT_FIELD, "Name") == null)) {
                Thread.sleep(50);
            }
            JXAccessibilityHost host = hostRef.get();
            assumeTrue(host != null && host.getNativeHandle() != 0, "the window got no native handle");
            long fieldId = host.getRoot().find(JXAccessibilityRole.TEXT_FIELD, "Name").getId();
            Path script = Files.createTempFile("jx-uia-listener", ".ps1");
            Files.write(script, LISTENER.getBytes(StandardCharsets.UTF_8));
            Process p = new ProcessBuilder("powershell.exe", "-MTA", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
                    script.toString(), "-Hwnd", String.valueOf(host.getNativeHandle())).redirectErrorStream(true).start();
            List<String> lines = new ArrayList<String>();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean asked = false;
                while ((line = in.readLine()) != null) {
                    lines.add(line);
                    if (!asked && line.equals("READY")) {
                        asked = true;
                        Thread.sleep(500);
                        assertTrue(host.perform(fieldId, com.jxparallel.ui.accessibility.JXAccessibleAction.FOCUS, null));
                        Thread.sleep(300);
                        window.setContent(el("column", JXProps.builder().set("gap", 6.0).set("padding", "10"),
                                el("button", JXProps.builder().set("label", "First").set("onAction", (Runnable) () -> { })),
                                el("input", JXProps.builder().set("value", "changed text").set("accessibleName", "Name"))));
                    }
                }
            } finally {
                p.destroyForcibly();
                Files.deleteIfExists(script);
            }
            String all = String.join("\n", lines);
            synchronized (events) {
                boolean focusEvent = false;
                for (JXAccessibilityEvent e : events) {
                    focusEvent |= e.getKind() == JXAccessibilityEvent.Kind.FOCUS_CHANGED && e.getNodeId() == fieldId;
                }
                assertTrue(focusEvent, "the window found the focus change: " + events);
            }
            assertTrue(lines.contains("EVENT|focus|Name"), "the client heard the focus-changed event: " + all);
            assertTrue(lines.contains("EVENT|value|Name=changed text"), "the client heard the value-changed event: " + all);
            System.out.println("UIA client output: " + all);
        } finally {
            window.dispose();
            window.awaitClosed();
            JXAccessibility.uninstall();
        }
    }
}
