package com.jxparallel.fx.nativeimpl;

import java.util.ArrayList;
import java.util.List;

import com.jxparallel.fx.Fx;

import javafx.animation.Animation;
import javafx.animation.ParallelTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Transition;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.util.Duration;

/**
 * Transitions of native nodes. FadeTransition and its siblings take a node, so in native mode they
 * are native models; the methods they inherit from Animation (play, stop, cycleCount, onFinished)
 * run on a real JavaFX animation built here, which the JavaFX pulse drives as usual and which
 * writes the node's properties. Only opacity is drawn by the native renderer; translate, scale and
 * rotate transitions change the values (listeners and bindings see them) without moving pixels.
 */
final class NativeAnimations {
    private static final String KEY = "animation";

    private NativeAnimations() {
    }

    /** The JavaFX animation behind a native transition, created once per model. */
    static Animation of(NativeModel model) {
        Object existing = model.state.get(KEY);
        if (existing instanceof Animation) {
            return (Animation) existing;
        }
        Animation animation;
        if (model.is(ParallelTransition.class) || model.is(SequentialTransition.class)) {
            animation = group(model);
        } else if (model.is(javafx.animation.FadeTransition.class) || model.is(javafx.animation.TranslateTransition.class)
                || model.is(javafx.animation.ScaleTransition.class) || model.is(javafx.animation.RotateTransition.class)) {
            animation = new PropertyTransition(model);
        } else {
            throw new UnsupportedOperationException("JX native: " + model.type
                    + " animates shapes, which the native renderer does not draw");
        }
        model.state.put(KEY, animation);
        return animation;
    }

    /** Parallel and sequential transitions: a JavaFX one whose children follow the model's children. */
    private static Animation group(NativeModel model) {
        boolean parallel = model.is(ParallelTransition.class);
        Animation group = parallel ? new ParallelTransition() : new SequentialTransition();
        ObservableList<Animation> target = parallel ? ((ParallelTransition) group).getChildren()
                : ((SequentialTransition) group).getChildren();
        ObservableList<Object> children = model.children; // what getChildren() and the constructor fill
        Runnable sync = () -> {
            List<Animation> adapted = new ArrayList<>();
            for (Object child : children) {
                adapted.add((Animation) Fx.peerAs(Fx.fx(child), Animation.class));
            }
            target.setAll(adapted);
        };
        children.addListener((ListChangeListener<Object>) change -> sync.run());
        sync.run();
        return group;
    }

    /** Fade, translate, scale and rotate: from, to and by values of the model applied to its node. */
    static final class PropertyTransition extends Transition {
        private final NativeModel model;
        private final String[][] axes;
        private double[] start;
        private double[] end;

        PropertyTransition(NativeModel model) {
            this.model = model;
            if (model.is(javafx.animation.FadeTransition.class)) {
                axes = new String[][] {{"opacity", "fromValue", "toValue", "byValue"}};
            } else if (model.is(javafx.animation.RotateTransition.class)) {
                axes = new String[][] {{"rotate", "fromAngle", "toAngle", "byAngle"}};
            } else {
                String prefix = model.is(javafx.animation.ScaleTransition.class) ? "scale" : "translate";
                axes = new String[][] {
                    {prefix + "X", "fromX", "toX", "byX"}, {prefix + "Y", "fromY", "toY", "byY"}, {prefix + "Z", "fromZ", "toZ", "byZ"}};
            }
            setCycleDuration(duration());
        }

        private Duration duration() {
            Object d = Native.value(model, "duration");
            return d instanceof Duration ? (Duration) d : Duration.millis(400);
        }

        private NativeModel node() {
            Object node = Fx.fx(Native.value(model, "node"));
            return node instanceof NativeModel ? (NativeModel) node : null;
        }

        private static double number(NativeModel m, String name, double fallback) {
            Object v = Native.value(m, name);
            return v instanceof Number ? ((Number) v).doubleValue() : fallback;
        }

        /** Captures where each axis starts and ends, as JavaFX does when the transition starts. */
        private void begin() {
            setCycleDuration(duration());
            NativeModel node = node();
            start = new double[axes.length];
            end = new double[axes.length];
            for (int i = 0; i < axes.length; i++) {
                double current = node == null ? 0 : number(node, axes[i][0], 0);
                double from = number(model, axes[i][1], Double.NaN);
                double to = number(model, axes[i][2], Double.NaN);
                double by = number(model, axes[i][3], 0);
                start[i] = Double.isNaN(from) ? current : from;
                end[i] = !Double.isNaN(to) ? to : start[i] + by;
            }
        }

        @Override
        protected void interpolate(double frac) {
            NativeModel node = node();
            if (node == null) {
                return;
            }
            if (start == null) {
                begin(); // jumpTo before any play
            }
            for (int i = 0; i < axes.length; i++) {
                if (start[i] == end[i] && !node.values.containsKey(axes[i][0])) {
                    continue; // an axis the transition does not move stays unset
                }
                double value = start[i] + (end[i] - start[i]) * frac;
                if ("opacity".equals(axes[i][0])) {
                    value = Math.max(0, Math.min(1, value));
                }
                Native.property(node, axes[i][0], double.class).setValue(value);
            }
        }

        @Override
        public void play() {
            if (getStatus() == Status.STOPPED) {
                begin();
            }
            super.play();
        }

        @Override
        public void playFromStart() {
            begin();
            super.playFromStart();
        }
    }
}
