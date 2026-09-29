package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.collections.FXCollections;
import com.jxparallel.fx.scene.control.ListCell;
import com.jxparallel.fx.scene.control.ListView;
import com.jxparallel.fx.scene.input.ClipboardContent;
import com.jxparallel.fx.scene.input.DataFormat;
import com.jxparallel.fx.scene.input.Dragboard;
import com.jxparallel.fx.scene.input.TransferMode;
import com.jxparallel.fx.scene.layout.HBox;
import com.jxparallel.ui.native2d.JXNativeNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Drag and drop between the rows of two lists, the way DeviceConfig's parameter screens do it. */
class NativeDragAndDropTest extends NativeTestSupport {
    private static final DataFormat INDEX = new DataFormat("application/x-jx-test-index");

    @Test
    void dragARowFromOneListAndDropItOnTheOther() throws Exception {
        List<String> log = new ArrayList<>();
        ListView<String>[] lists = new ListView[2];
        NativeScene s = show(fx(() -> {
            lists[0] = new ListView<>(FXCollections.observableArrayList("a", "b", "c"));
            lists[1] = new ListView<>(FXCollections.observableArrayList("x"));
            lists[0].setCellFactory(v -> {
                ListCell<String> row = new ListCell<String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty ? null : item);
                    }
                };
                row.setOnDragDetected(e -> {
                    Dragboard db = row.startDragAndDrop(TransferMode.COPY_OR_MOVE);
                    db.setDragView(row.snapshot(null, null));
                    ClipboardContent content = new ClipboardContent();
                    content.put(INDEX, row.getIndex());
                    db.setContent(content);
                    log.add("detected " + row.getIndex());
                    e.consume();
                });
                row.setOnDragDone(e -> log.add("done " + e.getTransferMode()));
                return row;
            });
            lists[1].setOnDragOver(e -> {
                if (e.getGestureSource() != lists[1] && e.getDragboard().hasContent(INDEX)) {
                    e.acceptTransferModes(TransferMode.MOVE);
                }
                e.consume();
            });
            lists[1].setOnDragDropped(e -> {
                int index = (Integer) e.getDragboard().getContent(INDEX);
                lists[1].getItems().add(lists[0].getItems().remove(index));
                e.setDropCompleted(true);
                e.consume();
            });
            return new HBox(20, lists[0], lists[1]);
        }), 600, 300);
        JXNativeNode from = node(s, lists[0]);
        JXNativeNode to = node(s, lists[1]);
        double fromX = from.getX() + 20;
        double rowB = from.getY() + 1 + 23 + 10;

        press(s, fromX, rowB, 1, 0);
        move(s, fromX + 10, rowB + 2, true);
        move(s, to.getX() + 30, to.getY() + 50, true);
        release(s, to.getX() + 30, to.getY() + 50, 1, 0);
        settle();

        assertEquals(java.util.Arrays.asList("a", "c"), fx(() -> new ArrayList<>(lists[0].getItems())));
        assertEquals(java.util.Arrays.asList("x", "b"), fx(() -> new ArrayList<>(lists[1].getItems())));
        assertEquals(java.util.Arrays.asList("detected 1", "done MOVE"), log);
        close(s);
    }

    @Test
    void droppingWhereNothingAcceptsEndsWithNoTransfer() throws Exception {
        List<String> log = new ArrayList<>();
        ListView<String>[] list = new ListView[1];
        NativeScene s = show(fx(() -> {
            list[0] = new ListView<>(FXCollections.observableArrayList("a"));
            list[0].setOnDragDetected(e -> {
                Dragboard db = list[0].startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString("a");
                db.setContent(content);
            });
            list[0].setOnDragDone(e -> log.add("done " + e.getTransferMode()));
            return new HBox(list[0]);
        }), 600, 300);
        JXNativeNode n = node(s, list[0]);

        press(s, n.getX() + 10, n.getY() + 10, 1, 0);
        move(s, n.getX() + 40, n.getY() + 40, true);
        release(s, n.getX() + 40, n.getY() + 40, 1, 0);
        settle();

        assertEquals(java.util.Arrays.asList("done null"), log);
        close(s);
    }

    @Test
    void startDragAndDropOutsideDragDetectedFails() throws Exception {
        ListView<String> list = fx(() -> new ListView<String>());
        NativeScene s = show(fx(() -> new HBox(list)), 100, 100);
        assertThrows(IllegalStateException.class, () -> fx(() -> list.startDragAndDrop(TransferMode.ANY)));
        close(s);
    }
    @Test
    void theDragboardKeepsContentFormatsModesAndItsView() throws Exception {
        fx(() -> {
            javafx.scene.input.TransferMode[] modes = {javafx.scene.input.TransferMode.COPY, javafx.scene.input.TransferMode.MOVE};
            javafx.scene.input.Dragboard board = NativeDragAndDrop.createDragboard(modes);
            assertTrue(board.getContentTypes().isEmpty());
            assertFalse(board.hasString());
            javafx.scene.input.DataFormat custom = javafx.scene.input.DataFormat.lookupMimeType("application/x-jx-test") != null
                    ? javafx.scene.input.DataFormat.lookupMimeType("application/x-jx-test")
                    : new javafx.scene.input.DataFormat("application/x-jx-test");
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString("linha 3");
            content.put(custom, 42);
            assertTrue(board.setContent(content));
            assertEquals("linha 3", board.getString());
            assertEquals(42, board.getContent(custom));
            assertTrue(board.hasContent(custom));
            assertEquals(2, board.getContentTypes().size());
            assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(modes)), board.getTransferModes());
            assertEquals(0.0, board.getDragViewOffsetX());
            javafx.scene.image.WritableImage image = new javafx.scene.image.WritableImage(2, 2);
            board.setDragView(image, 3, 4);
            assertTrue(board.getDragView() == image);
            assertEquals(3.0, board.getDragViewOffsetX());
            assertEquals(4.0, board.getDragViewOffsetY());
            javafx.scene.input.ClipboardContent other = new javafx.scene.input.ClipboardContent();
            other.putString("outra");
            board.setContent(other);
            assertFalse(board.hasContent(custom), "new content replaces the old");
            return null;
        });
    }
}
