package com.cqu.model;

/**
 * 单条导入校验问题（可读，供管理员逐条修正）。
 */
public class ImportValidationIssue {
    public enum Severity {
        ERROR, WARNING
    }

    private final Severity severity;
    private final String code;
    private final String message;

    public ImportValidationIssue(Severity severity, String code, String message) {
        this.severity = severity;
        this.code = code;
        this.message = message;
    }

    public static ImportValidationIssue error(String code, String message) {
        return new ImportValidationIssue(Severity.ERROR, code, message);
    }

    public static ImportValidationIssue warning(String code, String message) {
        return new ImportValidationIssue(Severity.WARNING, code, message);
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
        return "[" + severity + "] " + code + ": " + message;
    }
}
