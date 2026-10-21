package com.jxparallel.examples.nativeui.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A folder of the tree model: 99 children, so 100 folders and their children are 10,000 visible rows. */
public final class BenchFolder {
    private final String name;
    private final List<String> children;
    private final List<String> size;

    BenchFolder(int f) {
        this.name = "Folder " + f;
        List<String> list = new ArrayList<String>(99);
        for (int c = 0; c < 99; c++) {
            list.add("File " + f + "." + c);
        }
        this.children = Collections.unmodifiableList(list);
        this.size = Collections.singletonList((f + 1) + " KB");
    }

    public String getName() {
        return name;
    }

    public List<String> getChildren() {
        return children;
    }

    /** The text of the Size column (a one element list, as the {@code values} of a TreeItem are). */
    public List<String> getSize() {
        return size;
    }
}
