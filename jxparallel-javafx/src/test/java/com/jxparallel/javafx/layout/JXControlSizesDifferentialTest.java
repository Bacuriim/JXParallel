package com.jxparallel.javafx.layout;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeContainer;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Min, pref and max sizes of native controls against real JavaFX 21 with Modena, for random
 * labels: the native layout places controls where JavaFX would only if they are as big.
 * Sizes are compared as JavaFX layouts use them, snapped up to whole pixels.
 */
class JXControlSizesDifferentialTest {
    @BeforeContainer
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // started by an earlier test class
        }
        // what jxparallel-fx's native runtime installs: JavaFX's own line metrics
        com.jxparallel.ui.text.JXTextEngine.setLineMetrics(new com.jxparallel.ui.text.JXTextEngine.LineMetrics() {
            @Override
            public float[] measure(float size, boolean bold) {
                Font font = Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, size);
                Label label = new Label("Hg");
                label.setFont(font);
                javafx.scene.text.Text text = new javafx.scene.text.Text("Hg");
                text.setFont(font);
                StackPane root = new StackPane(label, text);
                new Scene(root);
                root.applyCss();
                root.layout();
                return new float[] {(float) text.getBaselineOffset(), (float) Math.ceil(label.prefHeight(-1))};
            }

            @Override
            public float width(String value, float size, boolean bold) {
                javafx.scene.text.Text text = new javafx.scene.text.Text(value);
                text.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
                new Scene(new javafx.scene.Group(text));
                return (float) text.getLayoutBounds().getWidth();
            }
        });
    }

    @net.jqwik.api.lifecycle.AfterContainer
    static void removeLineMetrics() {
        com.jxparallel.ui.text.JXTextEngine.setLineMetrics(null);
    }

    @Provide
    Arbitrary<String> labels() {
        return Arbitraries.of("OK", "Save", "Salvar alterações", "Ficha 7816", "Zeus NG", "ação", "W", "iiii", "Customers", "Endereço IP");
    }

    private static void assertSameSizes(String what, Region fx, JXElement element) {
        StackPane root = new StackPane(fx);
        new Scene(root);
        root.applyCss();
        root.layout();
        JXNativeNode jx = JXNativeNode.createBackendNode(element);
        String expected = sizes(Math.ceil(fx.minWidth(-1)), Math.ceil(fx.prefWidth(-1)), cap(fx.maxWidth(-1)),
                Math.ceil(fx.minHeight(-1)), Math.ceil(fx.prefHeight(-1)), cap(fx.maxHeight(-1)));
        String actual = sizes(Math.ceil(jx.getMinWidth()), Math.ceil(jx.getPrefWidth()), cap(jx.getMaxWidth()),
                Math.ceil(jx.getMinHeight()), Math.ceil(jx.getPrefHeight()), cap(jx.getMaxHeight()));
        assertEquals(expected, actual, what);
    }

    private static double cap(double v) {
        return v >= 1e6 ? -1 : Math.ceil(v);
    }

    private static String sizes(double... v) {
        return String.format("min %.0fx%.0f pref %.0fx%.0f max %.0fx%.0f", v[0], v[3], v[1], v[4], v[2], v[5]);
    }

    private static JXElement labeled(String type, String label) {
        return JXElement.of(type, JXProps.builder().set("label", label).build());
    }

    @Property(tries = 40)
    void buttonsAndToggles(@ForAll("labels") String text) {
        assertSameSizes("button " + text, new Button(text), labeled("button", text));
        assertSameSizes("toggle " + text, new ToggleButton(text), labeled("toggle", text));
    }

    @Property(tries = 40)
    void checkBoxesRadioButtonsAndHyperlinks(@ForAll("labels") String text) {
        assertSameSizes("checkbox " + text, new CheckBox(text), labeled("checkbox", text));
        assertSameSizes("radio " + text, new RadioButton(text), labeled("radio", text));
        assertSameSizes("hyperlink " + text, new Hyperlink(text), labeled("hyperlink", text));
    }

    @Property(tries = 40)
    void labelsInRegularAndBoldAtSeveralSizes(@ForAll("labels") String text, @ForAll @IntRange(min = 10, max = 18) int size,
                                              @ForAll boolean bold) {
        Label fx = new Label(text);
        fx.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        JXProps.Builder p = JXProps.builder().set("value", text).set("fontSize", (double) size);
        if (bold) {
            p.set("bold", true);
        }
        assertSameSizes("label " + text + " " + size + (bold ? " bold" : ""), fx, JXElement.of("#text", p.build()));
    }

    @Property(tries = 10)
    void textFieldsAndSpinnersAreSizedByColumns(@ForAll @IntRange(min = 1, max = 30) int columns) {
        TextField field = new TextField();
        field.setPrefColumnCount(columns);
        assertSameSizes("field " + columns, field, JXElement.of("input", JXProps.builder().set("prefColumnCount", (double) columns).build()));
    }

    @Property(tries = 1)
    void fixedSizeControls() {
        assertSameSizes("spinner", new Spinner<Integer>(0, 10, 5), JXElement.of("spinner", JXProps.empty()));
        assertSameSizes("progress", new ProgressBar(0.5), JXElement.of("progress", JXProps.builder().set("progress", 0.5).build()));
        assertSameSizes("slider", new Slider(), JXElement.of("slider", JXProps.empty()));
    }

    @Property(tries = 30)
    void scrollPaneAroundContent(@ForAll @IntRange(min = 0, max = 400) int w, @ForAll @IntRange(min = 0, max = 400) int h) {
        Region content = new Region();
        content.setPrefSize(w, h);
        ScrollPane fx = new ScrollPane(content);
        JXElement jx = JXElement.of("scroll", JXProps.empty(),
                JXElement.of("pane", JXProps.builder().set("prefWidth", (double) w).set("prefHeight", (double) h).build()));
        assertSameSizes("scroll " + w + "x" + h, fx, jx);
    }

    @Property(tries = 40)
    void wrappedLabelsGrowByLinesAtTheirWidth(@ForAll("sentences") String text, @ForAll @IntRange(min = 40, max = 260) int width) {
        Label fx = new Label(text);
        fx.setWrapText(true);
        fx.setPrefWidth(width);
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(fx);
        new Scene(box, 400, 400);
        box.applyCss();
        box.layout();
        JXNativeNode jx = JXNativeNode.createBackendNode(JXElement.of("column", JXProps.empty(),
                JXElement.of("#text", JXProps.builder().set("value", text).set("wrapText", true).set("prefWidth", (double) width).build())));
        jx.layoutForBackend(400, 400);
        JXNativeNode label = jx.getChildren().get(0);
        assertEquals(Math.round(fx.getWidth()) + "x" + Math.round(fx.getHeight()), label.getWidth() + "x" + label.getHeight(),
                "wrapped label '" + text + "' at " + width);
    }

    @Provide
    Arbitrary<String> sentences() {
        return Arbitraries.of("Não foi possível conectar ao banco de dados", "Deseja salvar as alterações antes de sair?",
                "OK", "Ficha 7816 do equipamento Zeus NG", "Endereço IP inválido para a interface de rede selecionada");
    }

    @SuppressWarnings("unused")
    private static Control unused() {
        return null;
    }
}
