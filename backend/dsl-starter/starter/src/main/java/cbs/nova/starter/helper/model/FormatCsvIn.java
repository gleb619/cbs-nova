package cbs.nova.starter.helper.model;

import java.util.List;

/**
 * Input for the built-in {@code formatCsv} helper.
 */
public record FormatCsvIn(List<List<String>> rows, List<String> headerRow, CsvOptions options) {
}
