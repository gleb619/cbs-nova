package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.FromItem;
import com.github.squigglesql.squigglesql.Matchable;
import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@RequiredArgsConstructor
public class ExtendedSelectQueryExecutor {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public Builder select() {
    return new Builder();
  }

  public <T> List<T> query(ExtendedSelectQuery query, RowMapper<T> rowMapper) {
    return jdbcTemplate.query(query.toString(), Map.of(), rowMapper);
  }

  public <T> Stream<T> stream(ExtendedSelectQuery query, RowMapper<T> rowMapper) {
    return jdbcTemplate.queryForStream(query.toString(), Map.of(), rowMapper);
  }

  public <T> T queryForObject(ExtendedSelectQuery query, Class<T> type) {
    return Objects.requireNonNull(
            jdbcTemplate.queryForObject(query.toString(), Map.of(), type));
  }

  /**
   * Fluent builder for {@link ExtendedSelectQuery}.
   *
   * <p>
   * Condition composition rules:
   * <ul>
   * <li>Multiple {@link #where(Criteria)} calls are joined with an implicit {@code AND}. This is
   * the historical behavior and remains supported (not deprecated) because the generated SQL is
   * stable.</li>
   * <li>{@link #and(Criteria)} and {@link #or(Criteria)} explicitly compose the accumulated
   * criteria-so-far with the new criteria using {@code Criteria.and} / {@code Criteria.or}.
   * Combinators are left-associative: {@code where(a).and(b).or(c)} produces
   * {@code (a AND b) OR c}.</li>
   * <li>For clarity, prefer {@code where(a).and(b)} over {@code where(a).where(b)} when explicit
   * composition is intended.</li>
   * <li>{@link #andIf(boolean, Supplier)} / {@link #orIf(boolean, Supplier)} are conditional no-ops
   * when the flag is {@code false}.</li>
   * <li>Calling {@link #and(Criteria)} or {@link #or(Criteria)} with no criteria accumulated yet
   * behaves like {@link #where(Criteria)}.</li>
   * </ul>
   */
  public static final class Builder {

    private final ExtendedSelectQuery query = new ExtendedSelectQuery();
    private final List<Criteria> stagedCriterias = new ArrayList<>();

    public Builder from(FromItem fromItem) {
      query.addFrom(fromItem);
      return this;
    }

    public Builder select(Selectable... selection) {
      Arrays.stream(selection).forEach(query::addToSelection);
      return this;
    }

    public Builder select(List<Selectable> selections) {
      return select(selections.toArray(Selectable[]::new));
    }

    public Builder selectCount() {
      return select(Literal.unsafe("COUNT(*)"));
    }

    /**
     * Adds a criterion. Multiple {@code where} calls are joined with an implicit {@code AND}.
     *
     * @see Builder
     */
    public Builder where(Criteria criteria) {
      stage(criteria);
      return this;
    }

    public Builder whereIf(boolean condition, Supplier<Criteria> criteria) {
      if (condition) {
        stage(criteria.get());
      }
      return this;
    }

    /**
     * Explicitly composes the accumulated criteria-so-far with {@code criteria} using
     * {@code Criteria.and}. Left-associative.
     */
    public Builder and(Criteria criteria) {
      compose((left, right) -> Criteria.and(left, right), criteria);
      return this;
    }

    public Builder andIf(boolean condition, Supplier<Criteria> criteria) {
      if (condition) {
        and(criteria.get());
      }
      return this;
    }

    /**
     * Explicitly composes the accumulated criteria-so-far with {@code criteria} using
     * {@code Criteria.or}. Left-associative.
     */
    public Builder or(Criteria criteria) {
      compose((left, right) -> Criteria.or(left, right), criteria);
      return this;
    }

    public Builder orIf(boolean condition, Supplier<Criteria> criteria) {
      if (condition) {
        or(criteria.get());
      }
      return this;
    }

    public Builder groupBy(Matchable... expressions) {
      Arrays.stream(expressions).forEach(query::addGroupBy);
      return this;
    }

    public Builder orderBy(Selectable selectable, boolean ascending) {
      query.addOrder(selectable, ascending);
      return this;
    }

    public Builder orderByAsc(Selectable selectable) {
      return orderBy(selectable, true);
    }

    public Builder orderByDesc(Selectable selectable) {
      return orderBy(selectable, false);
    }

    public Builder limit(int limit) {
      query.limit(limit);
      return this;
    }

    public Builder offset(int offset) {
      query.offset(offset);
      return this;
    }

    public ExtendedSelectQuery build() {
      flushCriterias();
      return query;
    }

    public String sql() {
      flushCriterias();
      return query.toString();
    }

    private void stage(Criteria criteria) {
      stagedCriterias.add(Objects.requireNonNull(criteria, "Criteria can not be null."));
    }

    private Criteria accumulated() {
      if (stagedCriterias.isEmpty()) {
        return null;
      }
      if (stagedCriterias.size() == 1) {
        return stagedCriterias.get(0);
      }
      return Criteria.and(stagedCriterias);
    }

    private void compose(BiFunction<Criteria, Criteria, Criteria> combiner, Criteria right) {
      Objects.requireNonNull(right, "Criteria can not be null.");
      if (stagedCriterias.isEmpty()) {
        stage(right);
      } else {
        Criteria left = accumulated();
        stagedCriterias.clear();
        stage(combiner.apply(left, right));
      }
    }

    private void flushCriterias() {
      for (Criteria criteria : stagedCriterias) {
        query.addCriteria(criteria);
      }
      stagedCriterias.clear();
    }
  }
}
