package com.cqu.model;

import java.util.List;

/**
 * 导入校验结果：errors 非空则校验不通过，warnings 仅提示不阻断。
 */
public class ImportValidationResult {
    private final List<ValidationIssue> errors;
    private final List<ValidationIssue> warnings;

    public ImportValidationResult(List<ValidationIssue> errors, List<ValidationIssue> warnings) {
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<ValidationIssue> getErrors() {
        return errors;
    }

    public List<ValidationIssue> getWarnings() {
        return warnings;
    }

    public int getErrorCount() {
        return errors.size();
    }

    public int getWarningCount() {
        return warnings.size();
    }
}
