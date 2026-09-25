package com.jxparallel.fxml.template;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.DefaultProperty;
import javafx.beans.NamedArg;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;

/** Plain beans that exercise FxmlTemplate without starting the JavaFX toolkit. */
public final class TemplateBeans {
    private TemplateBeans() {
    }

    @DefaultProperty("children")
    public static class Box {
        private final List<Object> children = new ArrayList<Object>();
        private String title;
        private int size;
        private String color;
        private Object footer;
        private EventHandler<ActionEvent> onAction;
        private String id;

        public String getId() { return id; }
        public void setId(String value) { id = value; }

        public List<Object> getChildren() { return children; }
        public String getTitle() { return title; }
        public void setTitle(String value) { title = value; }
        public int getSize() { return size; }
        public void setSize(int value) { size = value; }
        public String getColor() { return color; }
        public void setColor(String value) { color = value; }
        public Object getFooter() { return footer; }
        public void setFooter(Object value) { footer = value; }
        public void setOnAction(EventHandler<ActionEvent> value) { onAction = value; }
        public void fire() { onAction.handle(new ActionEvent()); }
    }

    public static class Item {
        private final Number a;
        private final Number b;

        public Item(@NamedArg("a") int a, @NamedArg("b") int b) { this.a = a; this.b = b; }
        public Item(@NamedArg("a") double a, @NamedArg("b") double b) { this.a = a; this.b = b; }
        public Number getA() { return a; }
        public Number getB() { return b; }
    }

    public static final class Grid {
        private static final Map<Object, Integer> RANKS = new IdentityHashMap<Object, Integer>();

        private Grid() {
        }

        public static synchronized void setRank(Object node, Integer rank) { RANKS.put(node, rank); }
        public static synchronized Integer getRank(Object node) { return RANKS.get(node); }
    }

    public static class Controller {
        public static final List<Controller> CREATED = new ArrayList<Controller>();

        @FXML Box inner;
        @FXML Item first;
        Item notAnnotated;
        boolean initialized;
        int actions;

        public boolean isInitialized() { return initialized; }
        public int getActions() { return actions; }
        public Box getInner() { return inner; }
        public Item getNotAnnotated() { return notAnnotated; }

        @FXML
        private void initialize() {
            initialized = inner != null && first != null;
            synchronized (CREATED) {
                CREATED.add(this);
            }
        }

        @FXML
        private void onAction(ActionEvent event) {
            actions++;
        }
    }
}
