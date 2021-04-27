package server.util;

import java.io.File;
import java.util.ArrayList;

public class FileTree {
    private final File value;
    private final ArrayList<FileTree> children = new ArrayList<>();

    FileTree(File f) {
        this.value = f;
        File[] files;

        if (f.isDirectory() && (files = f.listFiles()) != null) {
            for (File fi : files) {
                this.children.add(new FileTree(fi));
            }
        }
    }

    public ArrayList<File> all() {
        ArrayList<File> all = new ArrayList<>();
        all.add(this.value);

        for (FileTree ft : this.children) {
            all.addAll(ft.all());
        }

        return all;
    }

    // https://stackoverflow.com/a/8948691/6713362
    public void print() {
        print("", true);
    }

    private void print(String prefix, boolean isTail) {
        System.out.println(prefix + (isTail ? "└── " : "├── ") + this.value.getName());

        for (int i = 0; i < this.children.size() - 1; i++) {
            this.children.get(i).print(prefix + (isTail ? "    " : "│   "), false);
        }

        if (this.children.size() > 0) {
            this.children.get(this.children.size() - 1).print(prefix + (isTail ? "    " : "│   "), true);
        }
    }
}
