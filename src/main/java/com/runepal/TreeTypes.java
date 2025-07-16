package com.runepal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum TreeTypes {
    TREE(1511, 1276, 1277, 1278, 1279, 1280),
    OAK(1521, 1281, 4540, 10820),
    WILLOW(1519, 1308, 10829, 10831, 10833),
    MAPLE(1517, 1307, 10832, 36681),
    YEW(1515, 1309, 10823, 36683),
    MAGIC(1513, 1306, 10834, 10835),
    TEAK(6333, 36686),
    MAHOGANY(6332, 36688);

    private final int logId;
    private final List<Integer> treeIds;

    TreeTypes(int logId, Integer... treeIds) {
        this.logId = logId;
        this.treeIds = Arrays.asList(treeIds);
    }

    public int getLogId() {
        return logId;
    }

    public List<Integer> getTreeIds() {
        return Collections.unmodifiableList(treeIds);
    }

    /**
     * Finds the corresponding log ID for a given tree ID.
     *
     * @param treeId The ID of the tree GameObject.
     * @return The item ID of the log, or -1 if no mapping is found.
     */
    public static int getLogForTree(int treeId) {
        for (TreeTypes tree : values()) {
            if (tree.getTreeIds().contains(treeId)) {
                return tree.getLogId();
            }
        }
        return -1; // No mapping found
    }
}