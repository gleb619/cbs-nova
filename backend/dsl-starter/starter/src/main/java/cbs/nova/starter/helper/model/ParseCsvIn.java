package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code parseCsv} helper.
 */
public record ParseCsvIn(String payload, CsvOptions options) {
}
