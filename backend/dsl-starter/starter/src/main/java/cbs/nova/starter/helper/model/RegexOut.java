package cbs.nova.starter.helper.model;

import java.util.List;

/**
 * Output for the built-in {@code regex} helper.
 *
 * <p>
 * Only the field relevant to the requested {@code op} is populated: {@code matched} for
 * {@code match} and {@code extract}, {@code value} for {@code extract} and {@code replace}, and
 * {@code values} for {@code split}. Unused fields are left {@code null}.
 */
public record RegexOut(String op, Boolean matched, String value, List<String> values) {
}
