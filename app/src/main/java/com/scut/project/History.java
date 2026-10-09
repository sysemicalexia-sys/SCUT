package com.scut.project;

import java.util.ArrayList;
import java.util.List;

public final class History {

    private static final int LIMIT = 60;

    private final ArrayList<List<Clip>> undo = new ArrayList<List<Clip>>();
    private final ArrayList<List<Clip>> redo = new ArrayList<List<Clip>>();

    public void record(List<Clip> current) {
        undo.add(Timeline.snapshot(current));
        if (undo.size() > LIMIT) {
            undo.remove(0);
        }
        redo.clear();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public boolean undo(List<Clip> current) {
        if (undo.isEmpty()) {
            return false;
        }
        redo.add(Timeline.snapshot(current));
        Timeline.restore(current, undo.remove(undo.size() - 1));
        return true;
    }

    public boolean redo(List<Clip> current) {
        if (redo.isEmpty()) {
            return false;
        }
        undo.add(Timeline.snapshot(current));
        Timeline.restore(current, redo.remove(redo.size() - 1));
        return true;
    }
}
