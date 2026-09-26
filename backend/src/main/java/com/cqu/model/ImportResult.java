package com.cqu.model;

import java.util.List;

/**
 * 导入执行结果：校验未通过时 success=false 且图数据保持不变。
 */
public class ImportResult {
    private final boolean success;
    private final ImportValidationResult validation;
    private final int nodeCount;
    private final int edgeCount;

    private ImportResult(boolean success, ImportValidationResult validation, int nodeCount, int edgeCount) {
        this.success = success;
        this.validation = validation;
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
    }

    public static ImportResult rejected(ImportValidationResult validation) {
        return new ImportResult(false, validation, 0, 0);
    }

    public static ImportResult applied(ImportValidationResult validation, int nodeCount, int edgeCount) {
        return new ImportResult(true, validation, nodeCount, edgeCount);
    }

    public boolean isSuccess() {
        return success;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public List<ValidationIssue> getErrors() {
        return validation.getErrors();
    }

    public List<ValidationIssue> getWarnings() {
        return validation.getWarnings();
    }

    /**
     * 非 getter 命名，避免被 JSON 序列化为嵌套结构。
     */
    public ImportValidationResult validation() {
        return validation;
    }
}
