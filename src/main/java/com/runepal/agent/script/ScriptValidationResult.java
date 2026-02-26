package com.runepal.agent.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScriptValidationResult {
    private final List<String> errors;

    public ScriptValidationResult(List<String> errors) {
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public static ScriptValidationResult valid() {
        return new ScriptValidationResult(Collections.emptyList());
    }

    public static ScriptValidationResult invalid(List<String> errors) {
        return new ScriptValidationResult(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return errors;
    }
}
