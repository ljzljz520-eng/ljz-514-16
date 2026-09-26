package com.cqu.model;

/**
 * 单条校验问题。message 为面向管理员的可读中文描述。
 */
public class ValidationIssue {
    public enum Severity {
        ERROR, WARNING
    }

    private final Severity severity;
    private final String code;
    private final String message;

    public ValidationIssue(Severity severity, String code, String message) {
        this.severity = severity;
        this.code = code;
        this.message = message;
    }

    public static ValidationIssue error(String code, String message) {
        return new ValidationIssue(Severity.ERROR, code, message);
    }

    public static ValidationIssue warning(String code, String message) {
        return new ValidationIssue(Severity.WARNING, code, message);
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return severity + " " + code + ": " + message;
    }
}
