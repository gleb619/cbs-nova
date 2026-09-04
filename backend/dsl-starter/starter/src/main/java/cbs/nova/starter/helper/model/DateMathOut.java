package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code dateMath} helper.
 *
 * <p>
 * Exactly one of {@code value}, {@code number}, or {@code flag} is populated per op:
 * <ul>
 * <li>{@code add} / {@code startOf} → {@code value} (string-formatted result).</li>
 * <li>{@code diff} → {@code number} (signed long).</li>
 * <li>{@code before} / {@code after} → {@code flag}.</li>
 * </ul>
 * The other fields are {@code null}.
 */
public record DateMathOut(String value, Long number, Boolean flag) {
}
