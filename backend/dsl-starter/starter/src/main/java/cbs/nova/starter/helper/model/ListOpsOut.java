package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code listOps} helper.
 *
 * <p>
 * The shape of {@code result} depends on the selected {@code mode}:
 * <ul>
 * <li>{@code "pluck"}, {@code "flatten"}, {@code "distinct"}: {@code List<Object>}.</li>
 * <li>{@code "groupBy"}: {@code Map<Object, List<Map<String, Object>>>}.</li>
 * <li>{@code "countBy"}: {@code Map<Object, Long>}.</li>
 * <li>{@code "sumBy"}: {@code Double}.</li>
 * <li>{@code "minBy"}, {@code "maxBy"}: {@code Map<String, Object>} (the winning record).</li>
 * </ul>
 */
public record ListOpsOut(Object result) {
}
