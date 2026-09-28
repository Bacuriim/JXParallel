package com.jxparallel.fx.scene.input;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import com.jxparallel.fx.scene.image.Image;

/**
 * JXParallel counterpart of {@link javafx.scene.input.ClipboardContent}: a map from data format to
 * data, converted to JavaFX's when put on a clipboard or dragboard.
 */
public class ClipboardContent extends HashMap<DataFormat, Object> {
    private static final long serialVersionUID = 1L;

    public final boolean hasString() {
        return containsKey(DataFormat.PLAIN_TEXT);
    }

    public final boolean putString(String s) {
        return put(DataFormat.PLAIN_TEXT, s, s != null);
    }

    public final String getString() {
        return (String) get(DataFormat.PLAIN_TEXT);
    }

    public final boolean hasUrl() {
        return containsKey(DataFormat.URL);
    }

    public final boolean putUrl(String url) {
        return put(DataFormat.URL, url, url != null);
    }

    public final String getUrl() {
        return (String) get(DataFormat.URL);
    }

    public final boolean hasHtml() {
        return containsKey(DataFormat.HTML);
    }

    public final boolean putHtml(String html) {
        return put(DataFormat.HTML, html, html != null);
    }

    public final String getHtml() {
        return (String) get(DataFormat.HTML);
    }

    public final boolean hasRtf() {
        return containsKey(DataFormat.RTF);
    }

    public final boolean putRtf(String rtf) {
        return put(DataFormat.RTF, rtf, rtf != null);
    }

    public final String getRtf() {
        return (String) get(DataFormat.RTF);
    }

    public final boolean hasImage() {
        return containsKey(DataFormat.IMAGE);
    }

    public final boolean putImage(Image i) {
        return put(DataFormat.IMAGE, i, i != null);
    }

    public final Image getImage() {
        return (Image) get(DataFormat.IMAGE);
    }

    public final boolean hasFiles() {
        return containsKey(DataFormat.FILES);
    }

    public final boolean putFiles(List<File> files) {
        return put(DataFormat.FILES, files, files != null);
    }

    public final boolean putFilesByPath(List<String> filePaths) {
        List<File> files = new java.util.ArrayList<>();
        for (String path : filePaths) {
            files.add(new File(path));
        }
        return putFiles(files);
    }

    @SuppressWarnings("unchecked")
    public final List<File> getFiles() {
        return (List<File>) get(DataFormat.FILES);
    }

    private boolean put(DataFormat format, Object value, boolean present) {
        if (present) {
            put(format, value);
        } else {
            remove(format);
        }
        return true;
    }
}
