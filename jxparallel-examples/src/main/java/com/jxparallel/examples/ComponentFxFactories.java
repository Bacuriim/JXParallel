package com.jxparallel.examples;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Pagination;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * The JavaFX side of the per-component benchmark: for each JXML component that has a JavaFX counterpart, a case that
 * builds the SAME N instances in code (no FXML), with the same texts, values and sizes the JXML sample writes, changes
 * the same prop in the update stage, and reads the same value for the fingerprint. The mapping tag to class is the
 * {@code fx} column of components.csv; the data formulas are those of {@code ComponentController} (JXML side).
 * JavaFX 8 API only (the runner also runs on JDK 8 with the bundled JavaFX 8).
 */
final class ComponentFxFactories {
    private static final int LIST_ROWS = 10000;
    private static final int FOLDERS = 100;
    private static final String[] CHOICES = {"A", "B", "C", "D"};
    private static WritableImage sharedImage;

    private ComponentFxFactories() {
    }

    // ---- data: the formulas of ComponentController.value ------------------------------------------------------

    static Object value(String kind, int ph, int k) {
        switch (kind) {
            case "string": return (ph == 0 ? "Item " : "Edited ") + k;
            case "digits": return String.valueOf((k + 37 * ph) % 100);
            case "dates": return LocalDate.of(2026, 1, 1).plusDays(k % 365 + ph).toString();
            case "colors": return String.format(Locale.ROOT, "#%06X", (k * 40503 + 0x1A2B3C + ph * 0x0F0F0F) & 0xFFFFFF);
            case "times": return String.format(Locale.ROOT, "%02d:%02d", (k + ph) % 24, (k % 4) * 15);
            case "initials": return "" + (char) ('A' + (k + ph) % 26) + (char) ('A' + (k / 26) % 26);
            case "numbers": return Double.valueOf((k * 7 + 31 * ph) % 100);
            case "fractions": return Double.valueOf(((k + 37 * ph) % 99 + 1) / 100.0);
            case "widths": return Double.valueOf(50 + (k % 20) * 10 + 5 * ph);
            case "sizes": return Double.valueOf(16 + (k % 12) * 2 + 2 * ph);
            case "ints": return Integer.valueOf((k + 7 * ph) % 120);
            case "steps": return Integer.valueOf((k + ph) % 4);
            case "pages": return Integer.valueOf((k + 3 * ph) % 10);
            case "flags": return Boolean.valueOf((k % 2 == 0) == (ph == 0));
            case "firsts": return Integer.valueOf(7 * ph);
            default: throw new IllegalArgumentException(kind);
        }
    }

    /** The same function as {@code ComponentBench.hash}: strings by their hash code, numbers by thousandths. */
    static long hs(String s) {
        return s == null ? 0 : s.hashCode();
    }

    static long hd(double d) {
        return Math.round(d * 1000.0);
    }

    static String hex(Color c) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", Math.round((float) (c.getRed() * 255)), Math.round((float) (c.getGreen() * 255)),
                Math.round((float) (c.getBlue() * 255)));
    }

    // ---- the cases --------------------------------------------------------------------------------------------

    /** One instance of the component: its root node, how to give it the next value, and the nodes the fingerprint reads. */
    static final class Inst {
        final Node root;
        final Consumer<Object> set;
        final Object[] probes;

        Inst(Node root, Consumer<Object> set, Object... probes) {
            this.root = root;
            this.set = set;
            this.probes = probes;
        }
    }

    interface Maker {
        Inst make(int k, Object first);
    }

    /** N instances of one component in a VBox, as the JXML sample's Column. */
    static class FxCase {
        final ComponentCsv.Entry entry;
        final int n;
        final Maker maker;
        final Function<Object, Long> hash;
        final List<Inst> insts = new ArrayList<Inst>();
        long sum;
        long count;
        FxCase(ComponentCsv.Entry entry, Maker maker, Function<Object, Long> hash) {
            this.entry = entry;
            this.n = entry.n;
            this.maker = maker;
            this.hash = hash;
        }

        Object v(int ph, int k) {
            return value(entry.kind, ph, k);
        }

        /** The screen at the first value set. */
        Parent build() {
            VBox root = new VBox(2);
            root.setPadding(new Insets(4));
            root.setAlignment(Pos.TOP_LEFT);
            root.setFillWidth(true);
            insts.clear();
            for (int k = 0; k < n; k++) {
                Inst inst = maker.make(k, v(0, k));
                insts.add(inst);
                root.getChildren().add(inst.root);
            }
            return root;
        }

        void update(int phase) {
            for (int k = 0; k < n; k++) {
                insts.get(k).set.accept(v(phase, k));
            }
        }

        /** The fingerprint's sum and count over the probed nodes, in the order of the JXML tree. */
        void probe() {
            sum = 0;
            count = 0;
            for (Inst inst : insts) {
                for (Object p : inst.probes) {
                    count++;
                    sum += hash.apply(p).longValue() * count;
                }
            }
        }
    }

    static FxCase create(ComponentCsv.Entry e) {
        switch (e.fx) {
            // inputs and display
            case "Label": return leaf(e, (v, k) -> new Label((String) v), (t, v) -> ((Label) t).setText((String) v), o -> hs(((Label) o).getText()));
            case "Hyperlink": return leaf(e, (v, k) -> new Hyperlink((String) v), (t, v) -> ((Hyperlink) t).setText((String) v), o -> hs(((Hyperlink) o).getText()));
            case "Button": return leaf(e, (v, k) -> new Button((String) v), (t, v) -> ((Button) t).setText((String) v), o -> hs(((Button) o).getText()));
            case "ToggleButton": return leaf(e, (v, k) -> new ToggleButton((String) v), (t, v) -> ((ToggleButton) t).setText((String) v), o -> hs(((ToggleButton) o).getText()));
            case "RadioButton": return leaf(e, (v, k) -> new RadioButton((String) v), (t, v) -> ((RadioButton) t).setText((String) v), o -> hs(((RadioButton) o).getText()));
            case "CheckBox": return leaf(e, (v, k) -> new CheckBox((String) v), (t, v) -> ((CheckBox) t).setText((String) v), o -> hs(((CheckBox) o).getText()));
            case "TextField": return leaf(e, (v, k) -> {
                TextField t = new TextField((String) v);
                t.setPromptText("Type here");
                t.setPrefColumnCount(12);
                return t;
            }, (t, v) -> ((TextField) t).setText((String) v), o -> hs(((TextField) o).getText()));
            case "PasswordField": return leaf(e, (v, k) -> {
                PasswordField t = new PasswordField();
                t.setText((String) v);
                t.setPromptText("Secret");
                t.setPrefColumnCount(12);
                return t;
            }, (t, v) -> ((PasswordField) t).setText((String) v), o -> hs(((PasswordField) o).getText()));
            case "TextArea": return leaf(e, (v, k) -> {
                TextArea t = new TextArea((String) v);
                t.setPromptText("Notes");
                t.setPrefColumnCount(20);
                t.setPrefRowCount(3);
                return t;
            }, (t, v) -> ((TextArea) t).setText((String) v), o -> hs(((TextArea) o).getText()));
            case "ComboBox": {
                final ObservableList<String> choices = FXCollections.observableArrayList(CHOICES);
                return leaf(e, (v, k) -> {
                    ComboBox<String> t = new ComboBox<String>(choices);
                    t.setPromptText("Choose");
                    t.setValue((String) v);
                    return t;
                }, (t, v) -> ((ComboBox<String>) t).setValue((String) v), o -> hs((String) ((ComboBox<?>) o).getValue()));
            }
            case "ChoiceBox": {
                final ObservableList<String> choices = FXCollections.observableArrayList(CHOICES);
                return leaf(e, (v, k) -> {
                    ChoiceBox<String> t = new ChoiceBox<String>(choices);
                    t.setValue((String) v);
                    return t;
                }, (t, v) -> ((ChoiceBox<String>) t).setValue((String) v), o -> hs((String) ((ChoiceBox<?>) o).getValue()));
            }
            case "Spinner": return leaf(e, (v, k) -> new Spinner<Integer>(0, 100, Integer.parseInt((String) v)),
                    (t, v) -> ((Spinner<Integer>) t).getValueFactory().setValue(Integer.valueOf((String) v)),
                    o -> hs(String.valueOf(((Spinner<?>) o).getValue())));
            case "DatePicker": return leaf(e, (v, k) -> {
                DatePicker t = new DatePicker(LocalDate.parse((String) v));
                t.setPromptText("Date");
                return t;
            }, (t, v) -> ((DatePicker) t).setValue(LocalDate.parse((String) v)), o -> hs(String.valueOf(((DatePicker) o).getValue())));
            case "ColorPicker": return leaf(e, (v, k) -> {
                ColorPicker t = new ColorPicker(Color.web((String) v));
                t.setPromptText("#RRGGBB");
                return t;
            }, (t, v) -> ((ColorPicker) t).setValue(Color.web((String) v)), o -> hs(hex(((ColorPicker) o).getValue())));
            case "Slider": return leaf(e, (v, k) -> new Slider(0, 100, (Double) v), (t, v) -> ((Slider) t).setValue((Double) v),
                    o -> hd(((Slider) o).getValue()));
            case "ScrollBar": return leaf(e, (v, k) -> {
                ScrollBar t = new ScrollBar();
                t.setMin(0);
                t.setMax(100);
                t.setValue((Double) v);
                t.setVisibleAmount(10);
                t.setOrientation(Orientation.HORIZONTAL);
                return t;
            }, (t, v) -> ((ScrollBar) t).setValue((Double) v), o -> hd(((ScrollBar) o).getValue()));
            case "ProgressBar": return leaf(e, (v, k) -> {
                ProgressBar t = new ProgressBar((Double) v);
                t.setPrefWidth(200);
                return t;
            }, (t, v) -> ((ProgressBar) t).setProgress((Double) v), o -> hd(((ProgressBar) o).getProgress()));
            case "ProgressIndicator": return leaf(e, (v, k) -> new ProgressIndicator((Double) v), (t, v) -> ((ProgressIndicator) t).setProgress((Double) v),
                    o -> hd(((ProgressIndicator) o).getProgress()));
            case "Separator": return leaf(e, (v, k) -> {
                Separator t = new Separator(Orientation.HORIZONTAL);
                t.setPrefWidth((Double) v);
                return t;
            }, (t, v) -> ((Separator) t).setPrefWidth((Double) v), o -> hd(((Separator) o).getPrefWidth()));
            case "ImageView": return leaf(e, (v, k) -> {
                ImageView t = new ImageView(image());
                t.setFitWidth((Double) v);
                t.setFitHeight(32);
                t.setPreserveRatio(false);
                return t;
            }, (t, v) -> ((ImageView) t).setFitWidth((Double) v), o -> hd(((ImageView) o).getFitWidth()));

            // containers: three labels (or buttons), the first one bound
            case "Column": return labels3(e, 3, nodes -> new VBox(4, nodes));
            case "Row": return labels3(e, 3, nodes -> new HBox(4, nodes));
            case "Stack": return labels3(e, 3, nodes -> new StackPane(nodes));
            case "Grid": return labels3(e, 3, nodes -> {
                GridPane g = new GridPane();
                g.setHgap(4);
                g.setVgap(4);
                g.add(nodes[0], 0, 0);
                g.add(nodes[1], 1, 0);
                g.add(nodes[2], 0, 1);
                return g;
            });
            case "Border": return labels3(e, 3, nodes -> {
                BorderPane b = new BorderPane();
                b.setTop(nodes[0]);
                b.setCenter(nodes[1]);
                b.setBottom(nodes[2]);
                return b;
            });
            case "Scroll": return labels3(e, 1, nodes -> {
                ScrollPane s = new ScrollPane(nodes[0]);
                s.setFitToWidth(true);
                s.setPrefHeight(40);
                return s;
            });
            case "Flow": return labels3(e, 3, nodes -> {
                FlowPane f = new FlowPane(4, 4, nodes);
                f.setPrefWrapLength(200);
                return f;
            });
            case "Tile": return labels3(e, 3, nodes -> {
                TilePane t = new TilePane(4, 4, nodes);
                t.setPrefColumns(3);
                t.setPrefTileWidth(60);
                t.setPrefTileHeight(24);
                return t;
            });
            case "Anchor": return labels3(e, 3, nodes -> {
                AnchorPane a = new AnchorPane(nodes);
                a.setPrefSize(200, 60);
                AnchorPane.setTopAnchor(nodes[0], 4.0);
                AnchorPane.setLeftAnchor(nodes[0], 4.0);
                AnchorPane.setRightAnchor(nodes[1], 4.0);
                AnchorPane.setBottomAnchor(nodes[1], 4.0);
                AnchorPane.setTopAnchor(nodes[2], 30.0);
                AnchorPane.setLeftAnchor(nodes[2], 50.0);
                return a;
            });
            case "Pane": return labels3(e, 3, nodes -> {
                Pane p = new Pane(nodes);
                p.setPrefSize(200, 60);
                double[][] at = {{10, 4}, {60, 20}, {110, 36}};
                for (int i = 0; i < 3; i++) {
                    nodes[i].setLayoutX(at[i][0]);
                    nodes[i].setLayoutY(at[i][1]);
                }
                return p;
            });
            case "TextFlow": return textFlow(e);
            case "ButtonBar": return buttons3(e, nodes -> {
                ButtonBar b = new ButtonBar();
                b.getButtons().addAll(nodes);
                return b;
            });
            case "ToolBar": return buttons3(e, nodes -> new ToolBar(nodes));
            case "SplitPane": return labels3(e, 3, nodes -> {
                SplitPane s = new SplitPane(nodes);
                s.setOrientation(Orientation.HORIZONTAL);
                s.setDividerPositions(0.3, 0.7);
                s.setPrefHeight(40);
                return s;
            });
            case "Tabs": return tabs(e);
            case "Accordion": return accordion(e);
            case "TitledPane": return leaf(e, (v, k) -> {
                TitledPane t = new TitledPane((String) v, new Label("a"));
                t.setExpanded(true);
                t.setCollapsible(true);
                return t;
            }, (t, v) -> ((TitledPane) t).setText((String) v), o -> hs(((TitledPane) o).getText()));
            case "Pagination": return leaf(e, (v, k) -> {
                Pagination p = new Pagination(10, (Integer) v);
                p.setPageFactory(i -> new Label("page"));
                return p;
            }, (t, v) -> ((Pagination) t).setCurrentPageIndex((Integer) v), o -> (long) ((Pagination) o).getCurrentPageIndex());
            case "MenuBar": return menuBar(e);
            case "MenuButton": return leaf(e, (v, k) -> new MenuButton((String) v, null, new MenuItem("a"), new MenuItem("b"), new MenuItem("c")),
                    (t, v) -> ((MenuButton) t).setText((String) v), o -> hs(((MenuButton) o).getText()));

            // virtualized: a model of 10000 rows, a window of 30 rows (fixed cell size 24), the update scrolls by 7
            case "ListView": return listView(e);
            case "TableView": return tableView(e);
            case "TreeView": return treeView(e);
            case "TreeTableView": return treeTableView(e);
            default: throw new IllegalArgumentException("no JavaFX case " + e.fx);
        }
    }

    interface Set1 {
        void set(Node node, Object value);
    }

    interface Make1 {
        Node make(Object first, int k);
    }

    private static FxCase leaf(ComponentCsv.Entry e, Make1 make, Set1 set, Function<Object, Long> hash) {
        return new FxCase(e, (k, first) -> {
            Node node = make.make(first, k);
            return new Inst(node, v -> set.set(node, v), node);
        }, hash);
    }

    private static final Function<Object, Long> LABEL_HASH = o -> o instanceof Label ? hs(((Label) o).getText())
            : o instanceof Text ? hs(((Text) o).getText()) : o instanceof Button ? hs(((Button) o).getText()) : 0L;

    interface Assemble {
        Node assemble(Node[] parts);
    }

    /** {@code count} Labels "value", "b", "c" (the first is bound) in a container; every Label is probed. */
    private static FxCase labels3(ComponentCsv.Entry e, int count, Assemble assemble) {
        return new FxCase(e, (k, first) -> {
            Node[] parts = new Node[count];
            Label[] labels = new Label[count];
            String[] texts = {(String) first, "b", "c"};
            for (int i = 0; i < count; i++) {
                labels[i] = new Label(texts[i]);
                parts[i] = labels[i];
            }
            Object[] probes = new Object[count];
            System.arraycopy(labels, 0, probes, 0, count);
            return new Inst(assemble.assemble(parts), v -> labels[0].setText((String) v), probes);
        }, LABEL_HASH);
    }

    /** Three Buttons "value", "b", "c" (the first is bound) in a container; every Button is probed. */
    private static FxCase buttons3(ComponentCsv.Entry e, Assemble assemble) {
        return new FxCase(e, (k, first) -> {
            Button[] buttons = {new Button((String) first), new Button("b"), new Button("c")};
            return new Inst(assemble.assemble(buttons), v -> buttons[0].setText((String) v), (Object[]) buttons);
        }, LABEL_HASH);
    }

    private static FxCase textFlow(ComponentCsv.Entry e) {
        return new FxCase(e, (k, first) -> {
            Text t0 = new Text((String) first);
            Text t1 = new Text("b");
            return new Inst(new TextFlow(t0, t1, new Hyperlink("c")), v -> t0.setText((String) v), t0, t1);
        }, LABEL_HASH);
    }

    private static FxCase tabs(ComponentCsv.Entry e) {
        return new FxCase(e, (k, first) -> {
            Label l0 = new Label((String) first);
            TabPane pane = new TabPane();
            pane.setPrefHeight(60);
            String[] titles = {"One", "Two", "Three"};
            Node[] contents = {l0, new Label("b"), new Label("c")};
            for (int i = 0; i < 3; i++) {
                Tab tab = new Tab(titles[i], contents[i]);
                tab.setClosable(false);
                pane.getTabs().add(tab);
            }
            // JXML mounts only the selected tab's content in the node tree: the fingerprint reads that one
            return new Inst(pane, v -> l0.setText((String) v), l0);
        }, LABEL_HASH);
    }

    private static FxCase accordion(ComponentCsv.Entry e) {
        return new FxCase(e, (k, first) -> {
            TitledPane t0 = new TitledPane((String) first, new Label("a"));
            TitledPane t1 = new TitledPane("b", new Label("c"));
            Accordion accordion = new Accordion();
            accordion.getPanes().addAll(t0, t1);
            t0.setExpanded(true);
            t1.setExpanded(false);
            return new Inst(accordion, v -> t0.setText((String) v), t0, t1);
        }, o -> hs(((TitledPane) o).getText()));
    }

    private static FxCase menuBar(ComponentCsv.Entry e) {
        return new FxCase(e, (k, first) -> {
            Menu m0 = new Menu((String) first, null, new MenuItem("a"), new MenuItem("b"));
            Menu m1 = new Menu("Edit", null, new MenuItem("c"), new MenuItem("d"));
            return new Inst(new MenuBar(m0, m1), v -> m0.setText((String) v), m0, m1);
        }, o -> hs(((Menu) o).getText()));
    }

    // ---- virtualized components ---------------------------------------------------------------------------------

    private static final Function<Object, Long> FIRST_HASH = o -> (long) ((int[]) o)[0];

    private static ObservableList<String> model;
    private static ObservableList<FxRow> rows;

    /** A row of the table model: the strings the JXML side's BenchRow holds. */
    static final class FxRow {
        final String id;
        final String name;
        final String total;

        FxRow(int k) {
            id = String.valueOf(k);
            name = "Name " + k;
            total = String.valueOf((k * 37) % 1000);
        }
    }

    /** A tree item's data: the name and the Size column. */
    static final class FxNode {
        final String name;
        final String size;

        FxNode(String name, String size) {
            this.name = name;
            this.size = size;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static FxCase listView(ComponentCsv.Entry e) {
        if (model == null) {
            List<String> list = new ArrayList<String>(LIST_ROWS);
            for (int k = 0; k < LIST_ROWS; k++) {
                list.add("Row " + k);
            }
            model = FXCollections.observableArrayList(list);
        }
        return new FxCase(e, (k, first) -> {
            ListView<String> list = new ListView<String>(model);
            list.setFixedCellSize(24);
            list.setPrefHeight(720);
            int[] at = {(Integer) first};
            return new Inst(list, v -> {
                at[0] = (Integer) v;
                list.scrollTo(at[0]);
            }, (Object) at);
        }, FIRST_HASH);
    }

    private static FxCase tableView(ComponentCsv.Entry e) {
        if (rows == null) {
            List<FxRow> list = new ArrayList<FxRow>(LIST_ROWS);
            for (int k = 0; k < LIST_ROWS; k++) {
                list.add(new FxRow(k));
            }
            rows = FXCollections.observableArrayList(list);
        }
        return new FxCase(e, (k, first) -> {
            TableView<FxRow> table = new TableView<FxRow>(rows);
            table.getColumns().add(column("Id", 60, r -> r.id));
            table.getColumns().add(column("Name", 160, r -> r.name));
            table.getColumns().add(column("Total", 80, r -> r.total));
            table.setFixedCellSize(24);
            table.setPrefHeight(750);
            int[] at = {(Integer) first};
            return new Inst(table, v -> {
                at[0] = (Integer) v;
                table.scrollTo(at[0]);
            }, (Object) at);
        }, FIRST_HASH);
    }

    private static TableColumn<FxRow, String> column(String title, double width, Function<FxRow, String> text) {
        TableColumn<FxRow, String> c = new TableColumn<FxRow, String>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
        return c;
    }

    /** 100 folders of 99 children, every folder expanded: 10000 visible rows. The items are made on every build. */
    private static FxCase treeView(ComponentCsv.Entry e) {
        return new FxCase(e, (k, first) -> {
            TreeItem<String> root = new TreeItem<String>("root");
            root.setExpanded(true);
            for (int f = 0; f < FOLDERS; f++) {
                TreeItem<String> folder = new TreeItem<String>("Folder " + f);
                folder.setExpanded(true);
                for (int c = 0; c < 99; c++) {
                    folder.getChildren().add(new TreeItem<String>("File " + f + "." + c));
                }
                root.getChildren().add(folder);
            }
            TreeView<String> tree = new TreeView<String>(root);
            tree.setShowRoot(false);
            tree.setFixedCellSize(24);
            tree.setPrefHeight(720);
            int[] at = {(Integer) first};
            return new Inst(tree, v -> {
                at[0] = (Integer) v;
                tree.scrollTo(at[0]);
            }, (Object) at);
        }, FIRST_HASH);
    }

    private static FxCase treeTableView(ComponentCsv.Entry e) {
        return new FxCase(e, (k, first) -> {
            TreeItem<FxNode> root = new TreeItem<FxNode>(new FxNode("root", ""));
            root.setExpanded(true);
            for (int f = 0; f < FOLDERS; f++) {
                String size = (f + 1) + " KB";
                TreeItem<FxNode> folder = new TreeItem<FxNode>(new FxNode("Folder " + f, size));
                folder.setExpanded(true);
                for (int c = 0; c < 99; c++) {
                    folder.getChildren().add(new TreeItem<FxNode>(new FxNode("File " + f + "." + c, size)));
                }
                root.getChildren().add(folder);
            }
            TreeTableView<FxNode> table = new TreeTableView<FxNode>(root);
            table.setShowRoot(false);
            TreeTableColumn<FxNode, String> name = new TreeTableColumn<FxNode, String>("Name");
            name.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getValue().name));
            TreeTableColumn<FxNode, String> size = new TreeTableColumn<FxNode, String>("Size");
            size.setPrefWidth(80);
            size.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getValue().size));
            table.getColumns().add(name);
            table.getColumns().add(size);
            table.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
            table.setFixedCellSize(24);
            table.setPrefHeight(750);
            int[] at = {(Integer) first};
            return new Inst(table, v -> {
                at[0] = (Integer) v;
                table.scrollTo(at[0]);
            }, (Object) at);
        }, FIRST_HASH);
    }

    private static WritableImage image() {
        if (sharedImage == null) {
            WritableImage image = new WritableImage(16, 16);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    image.getPixelWriter().setArgb(x, y, 0xFF000000 | (x * 16) << 16 | (y * 16) << 8 | 128);
                }
            }
            sharedImage = image;
        }
        return sharedImage;
    }

    // ---- the fingerprint's node count -------------------------------------------------------------------------

    /**
     * The logical nodes before any CSS pass, counted the way the JXML elements are: a control is one node, its
     * content (tab contents, scroll pane content, items of a tool bar, split pane, button bar, menus) is counted, the
     * skin's internals are not (they do not exist yet), and a Tab itself is not (JXML has no element for it).
     */
    static long nodes(Object o) {
        if (o == null) {
            return 0;
        }
        long c = 1;
        if (o instanceof ScrollPane) {
            c += nodes(((ScrollPane) o).getContent());
        } else if (o instanceof TabPane) {
            for (Tab t : ((TabPane) o).getTabs()) {
                c += nodes(t.getContent());
            }
        } else if (o instanceof SplitPane) {
            for (Node n : ((SplitPane) o).getItems()) {
                c += nodes(n);
            }
        } else if (o instanceof ToolBar) {
            for (Node n : ((ToolBar) o).getItems()) {
                c += nodes(n);
            }
        } else if (o instanceof ButtonBar) {
            for (Node n : ((ButtonBar) o).getButtons()) {
                c += nodes(n);
            }
        } else if (o instanceof TitledPane) {
            c += nodes(((TitledPane) o).getContent());
        } else if (o instanceof Accordion) {
            for (TitledPane p : ((Accordion) o).getPanes()) {
                c += nodes(p);
            }
        } else if (o instanceof MenuBar) {
            for (Menu m : ((MenuBar) o).getMenus()) {
                c += items(m);
            }
        } else if (o instanceof MenuButton) {
            c += ((MenuButton) o).getItems().size();
        } else if (o instanceof Parent) {
            for (Node n : ((Parent) o).getChildrenUnmodifiable()) {
                c += nodes(n);
            }
        }
        return c;
    }

    private static long items(MenuItem item) {
        long c = 1;
        if (item instanceof Menu) {
            for (MenuItem i : ((Menu) item).getItems()) {
                c += items(i);
            }
        }
        return c;
    }
}
