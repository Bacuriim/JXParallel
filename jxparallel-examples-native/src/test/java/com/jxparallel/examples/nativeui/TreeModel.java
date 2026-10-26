package com.jxparallel.examples.nativeui;

import java.util.ArrayList;
import java.util.List;

/** What the live tree tests bind: folders with children (mutable), the first row and the number of rows of the window. */
public class TreeModel {
    /** A folder of the model. */
    public static class Folder {
        private String name;
        private boolean open = true;
        private String size;
        private final List<String> children = new ArrayList<String>();

        public Folder(int k, int count) {
            this.name = "Folder " + k;
            this.size = String.valueOf(k * 11);
            for (int c = 0; c < count; c++) {
                children.add("file " + k + "." + c);
            }
        }

        public String getName() {
            return name;
        }

        public boolean getOpen() {
            return open;
        }

        public String getSize() {
            return size;
        }

        public List<String> getChildren() {
            return children;
        }

        public void edit(String name, boolean open, String size) {
            this.name = name;
            this.open = open;
            this.size = size;
        }
    }

    private List<Folder> folders = new ArrayList<Folder>();
    private int first;
    private int window = 14;

    public TreeModel(int folders, int children) {
        for (int k = 0; k < folders; k++) {
            this.folders.add(new Folder(k, children));
        }
    }

    public List<Folder> getFolders() {
        return folders;
    }

    public void setFolders(List<Folder> folders) {
        this.folders = folders;
    }

    public int getFirst() {
        return first;
    }

    public void setFirst(int first) {
        this.first = first;
    }

    public int getWindow() {
        return window;
    }

    public void setWindow(int window) {
        this.window = window;
    }
}
