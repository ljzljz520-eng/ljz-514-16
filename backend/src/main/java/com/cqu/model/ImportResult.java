package com.cqu.model;

/**
 * 导入执行结果。校验失败时 success=false，图数据保持原样不变。
 */
public class ImportResult {

    private final boolean success;
    private final String message;
    private final int nodeCount;
    private final int edgeCount;
    private final ValidationResult validation;

    private ImportResult(boolean success, String message, int nodeCount, int edgeCount,
                         ValidationResult validation) {
        this.success = success;
        this.message = message;
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.validation = validation;
    }

    public static ImportResult rejected(ValidationResult validation) {
        return new ImportResult(false, "校验失败，图数据未做任何修改", 0, 0, validation);
    }

    public static ImportResult imported(ValidationResult validation, int nodeCount, int edgeCount) {
        return new ImportResult(true, "校验通过，图数据已更新", nodeCount, edgeCount, validation);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public ValidationResult getValidation() {
        return validation;
    }
}
