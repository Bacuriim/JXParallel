package com.jxparallel.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.collections.ObservableList;
import com.jxparallel.fx.scene.control.ComboBox;
import com.jxparallel.fx.scene.control.Label;
import com.jxparallel.fx.scene.layout.VBox;
import com.jxparallel.fx.util.StringConverter;
import com.sun.javafx.application.PlatformImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** What crosses between JX and JavaFX comes back as the same JX object, and JavaFX calls JX overrides. */
class FxBoundaryTest {

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            PlatformImpl.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await();
    }

    @Test
    void childrenComeBackAsTheSameJxObjects() {
        VBox box = new VBox();
        Label label = new Label("a");
        box.getChildren().add(label);

        assertSame(label, box.getChildren().get(0));
        assertSame(box, label.getParent());
        assertSame(javafx.scene.control.Label.class, ((javafx.scene.Node) Fx.fx(label)).getClass().getSuperclass());
    }

    @Test
    void javaFxCallsTheJxOverride() {
        List<String> calls = new ArrayList<>();
        ComboBox<Integer> combo = new ComboBox<>();
        combo.setConverter(new StringConverter<Integer>() {
            @Override
            public String toString(Integer value) {
                calls.add("toString " + value);
                return "#" + value;
            }

            @Override
            public Integer fromString(String text) {
                return Integer.valueOf(text.substring(1));
            }
        });

        String shown = ((javafx.scene.control.ComboBox<Integer>) Fx.fx(combo)).getConverter().toString(7);

        assertEquals("#7", shown);
        assertEquals("[toString 7]", calls.toString());
    }

    public static final class Row {
        private final com.jxparallel.fx.beans.property.SimpleStringProperty name =
                new com.jxparallel.fx.beans.property.SimpleStringProperty("ana");

        public com.jxparallel.fx.beans.property.SimpleStringProperty nameProperty() {
            return name;
        }

        public int getAge() {
            return 30;
        }
    }

    /** JavaFX's PropertyValueFactory would cast the model's JX property to its own and fail. */
    @Test
    void propertyValueFactoryReadsJxModels() {
        com.jxparallel.fx.scene.control.TableView<Row> table = new com.jxparallel.fx.scene.control.TableView<>();
        com.jxparallel.fx.scene.control.TableColumn<Row, String> column = new com.jxparallel.fx.scene.control.TableColumn<>();
        Row row = new Row();
        com.jxparallel.fx.scene.control.TableColumn.CellDataFeatures<Row, String> cell =
                new com.jxparallel.fx.scene.control.TableColumn.CellDataFeatures<>(table, column, row);

        assertSame(row.nameProperty(), new com.jxparallel.fx.scene.control.cell.PropertyValueFactory<Row, String>("name").call(cell));
        assertEquals(30, new com.jxparallel.fx.scene.control.cell.PropertyValueFactory<Row, Object>("age")
                .call((com.jxparallel.fx.scene.control.TableColumn.CellDataFeatures) cell).getValue());
    }

    @Test
    void listListenersSeeJxElements() {
        ObservableList<Label> labels = FXCollections.observableArrayList();
        List<Object> added = new ArrayList<>();
        labels.addListener((com.jxparallel.fx.collections.ListChangeListener<Label>) change -> {
            while (change.next()) {
                added.addAll(change.getAddedSubList());
            }
        });
        Label label = new Label("x");
        labels.add(label);

        assertEquals(1, added.size());
        assertSame(label, added.get(0));
        assertTrue(labels.contains(label));
    }
}
