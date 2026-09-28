package com.jxparallel.fx.sun.scene.control.skin;

import java.lang.reflect.Field;

import com.jxparallel.fx.Fx;
import com.jxparallel.fx.scene.Node;
import com.jxparallel.fx.scene.control.Skin;
import com.jxparallel.fx.scene.control.Skinnable;

/** Base of the JX counterparts of JavaFX's internal skins: delegates Skin to the JavaFX skin. */
@SuppressWarnings("unchecked")
public abstract class SkinWrapper<C extends Skinnable> implements Skin<C>, Fx.Backed {
    private final Object fxPeer;

    protected SkinWrapper(Object peer) {
        this.fxPeer = peer;
        Fx.register(peer, this);
    }

    /** A private field of the JavaFX skin, for JX counterparts that expose the same field. */
    protected static Object read(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                // look in the superclass
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    @Override
    public Object fxPeer() {
        return fxPeer;
    }

    @Override
    public C getSkinnable() {
        return (C) Fx.jx(((javafx.scene.control.Skin<?>) fxPeer).getSkinnable());
    }

    @Override
    public Node getNode() {
        return (Node) Fx.jx(((javafx.scene.control.Skin<?>) fxPeer).getNode());
    }

    @Override
    public void dispose() {
        ((javafx.scene.control.Skin<?>) fxPeer).dispose();
    }
}
