package com.cqu.model;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 图数据导入校验结果：包含全部问题（错误 + 警告）。
 * 只要存在 ERROR 级别的问题，校验即不通过，导入必须被拒绝。
 */
public class ValidationResult {

    private final List<ValidationIssue> issues;

    public ValidationResult(List<ValidationIssue> issues) {
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public List<ValidationIssue> getIssues() {
        return issues;
    }

    public List<ValidationIssue> getErrors() {
        return issues.stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.ERROR)
                .collect(Collectors.toList());
    }

    public List<ValidationIssue> getWarnings() {
        return issues.stream()
                .filter(i -> i.getSeverity() == ValidationIssue.Severity.WARNING)
                .collect(Collectors.toList());
    }

    public boolean isValid() {
        return getErrors().isEmpty();
    }

    public int getErrorCount() {
        return getErrors().size();
    }

    public int getWarningCount() {
        return getWarnings().size();
    }
}
