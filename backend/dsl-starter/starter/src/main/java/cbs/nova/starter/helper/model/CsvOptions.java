package cbs.nova.starter.helper.model;

/**
 * Shared CSV options for {@code parseCsv} and {@code formatCsv}.
 *
 * <p>
 * All fields are nullable and have sensible defaults: delimiter {@code ","},
 * {@code withHeader = false}, and line separator {@code "\r\n"}.
 */
public record CsvOptions(String delimiter, Boolean withHeader, String lineSeparator) {
}
