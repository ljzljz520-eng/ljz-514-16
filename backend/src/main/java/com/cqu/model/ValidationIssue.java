package com.cqu.model;

/**
 * 单条校验问题（可读的错误或警告），用于管理员导入图数据前的校验结果。
 */
public class ValidationIssue {

    public enum Severity {
        ERROR,
        WARNING
    }

    public enum Code {
        EMPTY_NODE_LIST,
        EMPTY_EDGE_LIST,
        NULL_NODE,
        BLANK_NODE_ID,
        BLANK_NODE_NAME,
        INVALID_COORDINATE,
        DUPLICATE_NODE_ID,
        DUPLICATE_NODE_NAME,
        NULL_EDGE,
        BLANK_EDGE_ENDPOINT,
        SELF_LOOP,
        UNKNOWN_NODE,
        NEGATIVE_WEIGHT,
        INVALID_WEIGHT,
        MISSING_REVERSE_EDGE,
        ASYMMETRIC_WEIGHT,
        ISOLATED_NODE,
        MISSING_DISTANCE,
        DUPLICATE_EDGE,
        DISCONNECTED_GRAPH
    }

    private final Severity severity;
    private final Code code;
    private final String message;

    public ValidationIssue(Severity severity, Code code, String message) {
        this.severity = severity;
        this.code = code;
        this.message = message;
    }

    public static ValidationIssue error(Code code, String message) {
        return new ValidationIssue(Severity.ERROR, code, message);
    }

    public static ValidationIssue warning(Code code, String message) {
        return new ValidationIssue(Severity.WARNING, code, message);
    }

    public Severity getSeverity() {
        return severity;
    }

    public Code getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
