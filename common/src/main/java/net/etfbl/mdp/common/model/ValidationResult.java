package net.etfbl.mdp.common.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Rezultat validacije. Struktura je propisana zadatkom:
 * boolean valid, List<String> errors, String validatorTimestamp.
 *
 * Vraca se preko RMI-ja iz Validatora u Parser, pa mora biti Serializable.
 */
public class ValidationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean valid;
    private List<String> errors = new ArrayList<>();
    private String validatorTimestamp;

    public ValidationResult() {
    }

    public ValidationResult(boolean valid, List<String> errors, String validatorTimestamp) {
        this.valid = valid;
        this.errors = errors;
        this.validatorTimestamp = validatorTimestamp;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public String getValidatorTimestamp() {
        return validatorTimestamp;
    }

    public void setValidatorTimestamp(String validatorTimestamp) {
        this.validatorTimestamp = validatorTimestamp;
    }

    @Override
    public String toString() {
        return "ValidationResult{valid=" + valid + ", errors=" + errors
                + ", validatorTimestamp='" + validatorTimestamp + "'}";
    }
}