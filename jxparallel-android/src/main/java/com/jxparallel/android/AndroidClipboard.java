package com.jxparallel.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import com.jxparallel.ui.input.JXClipboard;

/** The system clipboard of Android as the {@link JXClipboard} the text inputs use on the desktop (plain text only). */
public final class AndroidClipboard implements JXClipboard {
    private final Context context;
    private final ClipboardManager manager;

    public AndroidClipboard(Context context) {
        this.context = context;
        this.manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public String getText() {
        if (manager == null || !manager.hasPrimaryClip()) {
            return null;
        }
        ClipData clip = manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return null;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(context);
        return text == null ? null : text.toString();
    }

    @Override
    public void setText(String value) {
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("JXParallel", value == null ? "" : value));
        }
    }

    /** Whether a paste would put something in. */
    public boolean hasText() {
        String text = getText();
        return text != null && !text.isEmpty();
    }
}
