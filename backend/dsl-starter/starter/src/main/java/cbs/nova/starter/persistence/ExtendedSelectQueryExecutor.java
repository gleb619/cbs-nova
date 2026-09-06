package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.FromItem;
import com.github.squigglesql.squigglesql.Matchable;
import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@RequiredArgsConstructor
public class ExtendedSelectQueryExecutor {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public Builder select(
      //TODO: add here a some lamdba, to make api fluent one, like stream api
  ) {
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

  public static final class Builder {

    private final ExtendedSelectQuery query = new ExtendedSelectQuery();

    public Builder from(FromItem fromItem) {
      query.addFrom(fromItem);
      return this;
    }

    public Builder select(Selectable... selection) {
      Arrays.stream(selection).forEach(query::addToSelection);
      return this;
    }

    //TODO: add 'or' and 'and' methods, for proper configuration of conditions
    public Builder where(Criteria criteria) {
      query.addCriteria(criteria);
      return this;
    }

    public Builder whereIf(boolean condition, Supplier<Criteria> criteria) {
      if (condition) {
        query.addCriteria(criteria.get());
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
      return query;
    }

    public String sql() {
      return query.toString();
    }
  }
}
