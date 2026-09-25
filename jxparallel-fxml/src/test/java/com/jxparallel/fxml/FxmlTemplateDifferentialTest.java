package com.jxparallel.fxml;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javafx.fxml.FXMLLoader;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

import com.jxparallel.fxml.template.TemplateBeans.Box;
import com.jxparallel.fxml.template.TemplateBeans.Grid;
import com.jxparallel.fxml.template.TemplateBeans.Item;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Differential test: random FXML documents are loaded by JavaFX's FXMLLoader and by FxmlTemplate,
 * and both object graphs must be the same. FXMLLoader is the specification.
 */
class FxmlTemplateDifferentialTest {
    private static final String HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<?import com.jxparallel.fxml.template.TemplateBeans.Box?>\n"
            + "<?import com.jxparallel.fxml.template.TemplateBeans.Item?>\n"
            + "<?import com.jxparallel.fxml.template.TemplateBeans.Grid?>\n";

    @Property(tries = 300)
    void templateBuildsWhatFxmlLoaderBuilds(@ForAll("documents") String body) throws Exception {
        String fxml = HEADER + body.replaceFirst("<TemplateBeans.Box", "<TemplateBeans.Box xmlns:fx=\"http://javafx.com/fxml\"");
        byte[] bytes = fxml.getBytes(StandardCharsets.UTF_8);

        FXMLLoader loader = new FXMLLoader();
        loader.setClassLoader(getClass().getClassLoader());
        Object expected = loader.load(new ByteArrayInputStream(bytes));
        Object actual = FxmlTemplate.parse(bytes, null, getClass().getClassLoader()).instantiate();

        assertEquals(describe(expected), describe(actual), fxml);
    }

    @Provide
    Arbitrary<String> documents() {
        return box(3);
    }

    private static Arbitrary<String> box(int depth) {
        Arbitrary<String> child = depth == 0 ? item() : Arbitraries.frequencyOf(Tuple.of(2, item()), Tuple.of(1, box(depth - 1)));
        Arbitrary<String> footer = depth == 0 ? Arbitraries.just("")
                : Arbitraries.oneOf(Arbitraries.just(""), child.map(c -> "<footer>" + c + "</footer>"));
        return Combinators.combine(
                        attribute("title", Arbitraries.strings().alpha().numeric().ofMaxLength(5)),
                        attribute("size", Arbitraries.integers().between(-5, 500).map(String::valueOf)),
                        attribute("color", Arbitraries.of("#ff0000", "red", "#0a0b0c")),
                        attribute("fx:id", Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(4)),
                        child.list().ofMaxSize(3),
                        footer)
                .as((title, size, color, id, children, foot) ->
                        "<TemplateBeans.Box" + title + size + color + id + ">" + String.join("", children) + foot + "</TemplateBeans.Box>");
    }

    private static Arbitrary<String> item() {
        Arbitrary<String> number = Arbitraries.oneOf(
                Arbitraries.integers().between(-100, 100).map(String::valueOf),
                Arbitraries.doubles().between(-100, 100).ofScale(2).map(String::valueOf));
        return Combinators.combine(number, number, attribute("TemplateBeans.Grid.rank", Arbitraries.integers().between(0, 9).map(String::valueOf)))
                .as((a, b, rank) -> "<TemplateBeans.Item a=\"" + a + "\" b=\"" + b + "\"" + rank + "/>");
    }

    /** The attribute or nothing, so optional attributes are covered too. */
    private static Arbitrary<String> attribute(String name, Arbitrary<String> value) {
        return Arbitraries.oneOf(Arbitraries.just(""), value.map(v -> " " + name + "=\"" + v + "\""));
    }

    private static String describe(Object node) {
        if (node instanceof Item) {
            Item item = (Item) node;
            return "Item(" + typed(item.getA()) + "," + typed(item.getB()) + ",rank=" + Grid.getRank(item) + ")";
        }
        if (node instanceof Box) {
            Box box = (Box) node;
            StringBuilder out = new StringBuilder("Box(").append(box.getTitle()).append(',').append(box.getSize())
                    .append(',').append(box.getColor()).append(",id=").append(box.getId()).append(")[");
            for (Object child : box.getChildren()) {
                out.append(describe(child)).append(' ');
            }
            return out.append("] footer=").append(box.getFooter() == null ? null : describe(box.getFooter())).toString();
        }
        return String.valueOf(node);
    }

    private static String typed(Number value) {
        return value.getClass().getSimpleName() + ":" + value;
    }
}
