package com.cqu.service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 当前生效图数据的持有者。导入校验通过后原子替换，查询侧始终读到一致的快照。
 */
public class GraphRegistry {
    private final AtomicReference<GraphService> current;

    public GraphRegistry(GraphService initial) {
        if (initial == null) {
            throw new IllegalArgumentException("initial graph must not be null");
        }
        this.current = new AtomicReference<>(initial);
    }

    public GraphService current() {
        return current.get();
    }

    public void replace(GraphService next) {
        if (next == null) {
            throw new IllegalArgumentException("next graph must not be null");
        }
        current.set(next);
    }
}
