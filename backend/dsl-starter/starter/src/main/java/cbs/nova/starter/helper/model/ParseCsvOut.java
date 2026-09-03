package cbs.nova.starter.helper.model;

import java.util.List;

/**
 * Output for the built-in {@code parseCsv} helper.
 */
public record ParseCsvOut(List<List<String>> rows) {
}
