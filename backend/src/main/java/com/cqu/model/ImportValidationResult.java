package com.cqu.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 导入校验结果：包含全部问题列表；只要存在 ERROR 级别问题即视为不通过。
 */
public class ImportValidationResult {
    private final List<ImportValidationIssue> issues;

    public ImportValidationResult(List<ImportValidationIssue> issues) {
        this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public List<ImportValidationIssue> getIssues() {
        return issues;
    }

    public List<ImportValidationIssue> getErrors() {
        return issues.stream()
                .filter(i -> i.getSeverity() == ImportValidationIssue.Severity.ERROR)
                .collect(Collectors.toList());
    }

    public List<ImportValidationIssue> getWarnings() {
        return issues.stream()
                .filter(i -> i.getSeverity() == ImportValidationIssue.Severity.WARNING)
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
