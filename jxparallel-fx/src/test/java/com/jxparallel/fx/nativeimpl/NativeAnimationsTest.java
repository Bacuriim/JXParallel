package com.jxparallel.fx.nativeimpl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.jxparallel.fx.animation.Animation;
import com.jxparallel.fx.animation.FadeTransition;
import com.jxparallel.fx.animation.FillTransition;
import com.jxparallel.fx.animation.Interpolator;
import com.jxparallel.fx.animation.ParallelTransition;
import com.jxparallel.fx.animation.PauseTransition;
import com.jxparallel.fx.animation.RotateTransition;
import com.jxparallel.fx.animation.ScaleTransition;
import com.jxparallel.fx.animation.SequentialTransition;
import com.jxparallel.fx.animation.TranslateTransition;
import com.jxparallel.fx.scene.control.Label;
import com.jxparallel.fx.scene.layout.VBox;
import com.jxparallel.fx.util.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transitions of native nodes: FadeTransition and friends are native models whose inherited
 * Animation methods run on a JavaFX animation that writes the node's properties.
 */
class NativeAnimationsTest extends NativeTestSupport {
    /** Like JavaFX, jumpTo only interpolates a started animation: start it, hold it, then jump. */
    private static void at(Animation animation, Duration time) {
        animation.play();
        animation.pause();
        animation.jumpTo(time);
    }

    @Test
    void fadeInterpolatesOpacityFromTheNodesValueToTheTarget() throws Exception {
        Label label = fx(() -> new Label("x"));
        FadeTransition fade = fx(() -> {
            FadeTransition f = new FadeTransition(Duration.millis(400), label);
            f.setToValue(0.2);
            return f;
        });
        assertEquals(400.0, fx(() -> fade.getDuration().toMillis()));
        assertSame(label, fx(() -> fade.getNode()));
        assertTrue(Double.isNaN(fx(() -> fade.getFromValue())), "an unset from value is NaN, like JavaFX");
        fx(() -> at(fade, Duration.millis(200)));
        assertEquals(0.6, fx(() -> label.getOpacity()), 1e-9, "halfway from 1 to 0.2");
        fx(() -> at(fade, Duration.millis(400)));
        assertEquals(0.2, fx(() -> label.getOpacity()), 1e-9);
    }

    @Test
    void fromAndByValuesAndClampedOpacity() throws Exception {
        Label label = fx(() -> new Label("x"));
        FadeTransition fade = fx(() -> {
            FadeTransition f = new FadeTransition(Duration.millis(100), label);
            f.setFromValue(0.5);
            f.setByValue(1.0);
            f.setInterpolator(Interpolator.LINEAR); // transitions ease in and out by default
            return f;
        });
        fx(() -> at(fade, Duration.millis(25)));
        assertEquals(0.75, fx(() -> label.getOpacity()), 1e-9);
        fx(() -> at(fade, Duration.millis(100)));
        assertEquals(1.0, fx(() -> label.getOpacity()), 1e-9, "1.5 is clamped to fully opaque");
    }

    @Test
    void playRunsOnThePulseAndFinishes() throws Exception {
        VBox root = fx(() -> new VBox(new Label("a")));
        Label label = fx(() -> (Label) root.getChildren().get(0));
        NativeScene scene = show(root, 200, 100);
        CountDownLatch finished = new CountDownLatch(1);
        FadeTransition fade = fx(() -> {
            FadeTransition f = new FadeTransition(Duration.millis(60), label);
            f.setFromValue(1.0);
            f.setToValue(0.0);
            f.setCycleCount(2);
            f.setAutoReverse(true);
            f.setOnFinished(e -> finished.countDown());
            f.play();
            return f;
        });
        assertEquals(Animation.Status.RUNNING, fx(() -> fade.getStatus()));
        assertEquals(2, (int) fx(() -> fade.getCycleCount()));
        assertTrue(finished.await(5, TimeUnit.SECONDS), "onFinished runs");
        assertEquals(1.0, fx(() -> label.getOpacity()), 1e-9, "auto-reversed back to 1");
        assertEquals(Animation.Status.STOPPED, fx(() -> fade.getStatus()));
        close(scene);
    }

    @Test
    void stopAndPlayFromStart() throws Exception {
        Label label = fx(() -> new Label("x"));
        FadeTransition fade = fx(() -> {
            FadeTransition f = new FadeTransition(Duration.seconds(10), label);
            f.setToValue(0.0);
            f.play();
            return f;
        });
        fx(() -> fade.stop());
        assertEquals(Animation.Status.STOPPED, fx(() -> fade.getStatus()));
        fx(() -> label.setOpacity(0.5));
        fx(() -> {
            fade.playFromStart();
            fade.pause();
            fade.jumpTo(Duration.seconds(5));
        });
        assertEquals(0.25, fx(() -> label.getOpacity()), 1e-9, "starts again from the node's current opacity");
        fx(() -> fade.stop());
    }

    @Test
    void translateScaleAndRotateChangeTheirProperties() throws Exception {
        Label label = fx(() -> new Label("x"));
        fx(() -> {
            TranslateTransition t = new TranslateTransition(Duration.millis(100), label);
            t.setToX(50);
            t.setByY(10);
            at(t, Duration.millis(50));
            ScaleTransition s = new ScaleTransition(Duration.millis(100), label);
            s.setToX(3);
            at(s, Duration.millis(50));
            RotateTransition r = new RotateTransition(Duration.millis(100), label);
            r.setFromAngle(0);
            r.setToAngle(90);
            at(r, Duration.millis(100));
        });
        assertEquals(25.0, fx(() -> label.getTranslateX()), 1e-9);
        assertEquals(5.0, fx(() -> label.getTranslateY()), 1e-9);
        assertEquals(2.0, fx(() -> label.getScaleX()), 1e-9, "from 1 to 3");
        assertEquals(1.0, fx(() -> label.getScaleY()), 1e-9, "an axis not animated keeps its value");
        assertEquals(90.0, fx(() -> label.getRotate()), 1e-9);
    }

    @Test
    void parallelAndSequentialTransitionsDriveTheirChildren() throws Exception {
        Label a = fx(() -> new Label("a"));
        Label b = fx(() -> new Label("b"));
        fx(() -> {
            FadeTransition fadeA = new FadeTransition(Duration.millis(100), a);
            fadeA.setToValue(0.0);
            FadeTransition fadeB = new FadeTransition(Duration.millis(100), b);
            fadeB.setToValue(0.0);
            SequentialTransition sequence = new SequentialTransition(new PauseTransition(Duration.millis(100)), fadeA);
            at(sequence, Duration.millis(150));
            ParallelTransition parallel = new ParallelTransition();
            parallel.getChildren().add(fadeB);
            at(parallel, Duration.millis(100));
        });
        assertEquals(0.5, fx(() -> a.getOpacity()), 1e-9, "after the pause, halfway");
        assertEquals(0.0, fx(() -> b.getOpacity()), 1e-9, "a child added later runs too");
    }

    @Test
    void shapeTransitionsAreNotSupported() throws Exception {
        FillTransition fill = fx(() -> new FillTransition(Duration.millis(100)));
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> fx(() -> fill.play()));
        assertTrue(e.getMessage().contains("FillTransition"));
    }
}
