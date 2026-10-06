import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.geometry.Insets;
import com.jxparallel.fx.scene.Scene;
import com.jxparallel.fx.scene.control.*;
import com.jxparallel.fx.scene.image.Image;
import com.jxparallel.fx.scene.layout.*;
import com.jxparallel.fx.stage.Stage;
import com.sun.javafx.application.PlatformImpl;

/**
 * The same screen (every kind of common JavaFX control) painted by NanoVG and by the software
 * renderer, as Node.snapshot paints it in native mode. Writes both PNGs and the difference image,
 * and prints how many pixels differ by more than a tolerance.
 *
 *   java -Djx.backend=native -cp <jx>;<this> RendererDiff <out dir>
 */
public class RendererDiff {
    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "target/renderer-diff");
        out.mkdirs();
        CountDownLatch started = new CountDownLatch(1);
        PlatformImpl.startup(started::countDown);
        started.await();
        CompletableFuture<int[][]> shots = new CompletableFuture<>();
        javafx.application.Platform.runLater(() -> {
            try {
                VBox root = screen();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 900, 640));
                stage.show();
                System.clearProperty("jx.snapshot");
                Image nanovg = root.snapshot(null, null);
                System.setProperty("jx.snapshot", "software");
                Image software = root.snapshot(null, null);
                shots.complete(new int[][] {pixels(nanovg), pixels(software),
                        {(int) nanovg.getWidth(), (int) nanovg.getHeight()}});
                stage.hide();
            } catch (Throwable e) {
                shots.completeExceptionally(e);
            }
        });
        int[][] r = shots.get(60, TimeUnit.SECONDS);
        int w = r[2][0];
        int h = r[2][1];
        int[] diff = new int[w * h];
        long differing = 0;
        long sum = 0;
        for (int i = 0; i < w * h; i++) {
            int a = r[0][i];
            int b = r[1][i];
            int d = Math.max(Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)),
                    Math.max(Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)), Math.abs((a & 0xFF) - (b & 0xFF))));
            sum += d;
            if (d > 48) {
                differing++;
            }
            int v = 255 - Math.min(255, d * 3);
            diff[i] = 0xFF000000 | v << 16 | v << 8 | v;
        }
        write(r[0], w, h, new File(out, "nanovg.png"));
        write(r[1], w, h, new File(out, "software.png"));
        write(diff, w, h, new File(out, "diff.png"));
        System.out.printf("pixels=%d differing(>48)=%d (%.2f%%) mean_abs_diff=%.2f%n",
                (long) w * h, differing, 100.0 * differing / (w * h), (double) sum / (w * h));
        System.exit(0);
    }

    private static VBox screen() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(8));
        String[] labels = {"Texto", "Combinação", "Marcação", "Número", "Data", "Senha", "Rádio", "Deslizante"};
        Control[] fields = {
                new TextField("valor 12"),
                new ComboBox<>(FXCollections.observableArrayList("A", "B", "C")),
                new CheckBox("Habilitado"),
                new Spinner<Integer>(0, 1000, 42),
                new DatePicker(java.time.LocalDate.of(2026, 1, 15)),
                new PasswordField(),
                new RadioButton("Opção"),
                new Slider(0, 100, 30)};
        ((ComboBox<String>) fields[1]).setValue("B");
        ((CheckBox) fields[2]).setSelected(true);
        ((PasswordField) fields[5]).setText("segredo");
        ((RadioButton) fields[6]).setSelected(true);
        for (int i = 0; i < labels.length; i++) {
            grid.add(new Label(labels[i]), 0, i);
            grid.add(fields[i], 1, i);
        }
        TitledPane pane = new TitledPane("Parâmetros do Mostrador", grid);
        TableView<String[]> table = new TableView<>();
        for (int c = 0; c < 3; c++) {
            final int col = c;
            TableColumn<String[], String> column = new TableColumn<>("Coluna " + (c + 1));
            column.setCellValueFactory(cell -> new com.jxparallel.fx.beans.property.SimpleStringProperty(cell.getValue()[col]));
            table.getColumns().add(column);
        }
        for (int i = 0; i < 20; i++) {
            table.getItems().add(new String[] {"Linha " + i, "Fortaleza", String.valueOf(i * 37 % 1000)});
        }
        table.getSelectionModel().select(3);
        TabPane tabs = new TabPane(new Tab("Cadastro", pane), new Tab("Registros", table));
        tabs.setPrefHeight(420);
        ProgressBar progress = new ProgressBar(0.6);
        HBox bar = new HBox(8, new Button("Novo"), new Button("Salvar"), new Label("Pronto"), progress);
        bar.setPadding(new Insets(8));
        Label bold = new Label("Situação: ativo");
        bold.setStyle("-fx-font-weight: bold;");
        return new VBox(8, bar, bold, tabs);
    }

    private static int[] pixels(Image image) {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        int[] p = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                p[y * w + x] = image.getPixelReader().getArgb(x, y);
            }
        }
        return p;
    }

    private static void write(int[] argb, int w, int h, File file) throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        javax.imageio.ImageIO.write(img, "png", file);
    }
}
