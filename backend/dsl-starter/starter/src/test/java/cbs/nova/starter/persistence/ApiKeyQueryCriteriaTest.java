package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiKeyQueryCriteriaTest {

  private final ApiKeyTableColumns t = ApiKeyTableColumns.of();
  private final TableReference r = t.refer();

  @Test
  void matchesKeyHashRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            ApiKeyQueryCriteria.matchesKeyHash(t, r, "abc123hash"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_api_keys d
            WHERE
                d.key_hash = 'abc123hash'""");
  }

  @Test
  void matchesKeyHashEscapesEmbeddedSingleQuote() {
    ExtendedSelectQuery query = queryWithCriteria(
            ApiKeyQueryCriteria.matchesKeyHash(t, r, "hash'with-quote"));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_api_keys d
            WHERE
                d.key_hash = 'hash''with-quote'""");
  }

  @Test
  void isActiveRendersIsNullOnRevokedAt() {
    ExtendedSelectQuery query = queryWithCriteria(
            ApiKeyQueryCriteria.isActive(t, r));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_api_keys d
            WHERE
                d.revoked_at IS NULL""");
  }

  @Test
  void matchesIdRendersEqualityPredicate() {
    ExtendedSelectQuery query = queryWithCriteria(
            ApiKeyQueryCriteria.matchesId(t, r, 17L));

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_api_keys d
            WHERE
                d.id = 17""");
  }

  @Test
  void keyHashAndActiveComposedViaCriteriaAndRendersBothPredicates() {
    Criteria auth = Criteria.and(
            ApiKeyQueryCriteria.matchesKeyHash(t, r, "abc123hash"),
            ApiKeyQueryCriteria.isActive(t, r));

    ExtendedSelectQuery query = queryWithCriteria(auth);

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id
            FROM
                dsl_api_keys d
            WHERE
                (
                    d.key_hash = 'abc123hash' AND
                    d.revoked_at IS NULL
                )""");
  }

  @Test
  void fullSelectionCoversAllTableColumnsInOrder() {
    List<Selectable> selection = ApiKeyQueryCriteria.fullSelection(t, r);
    assertThat(selection).hasSize(7);

    ExtendedSelectQuery query = new ExtendedSelectQuery();
    selection.forEach(query::addToSelection);
    query.addFrom(r);

    assertThat(query.toString()).isEqualTo("""
            SELECT
                d.id,
                d.label,
                d.key_hash,
                d.key_prefix,
                d.created_at,
                d.revoked_at,
                d.last_used_at
            FROM
                dsl_api_keys d""");
  }

  private ExtendedSelectQuery queryWithCriteria(
          com.github.squigglesql.squigglesql.criteria.Criteria criteria) {
    ExtendedSelectQuery query = new ExtendedSelectQuery();
    query.addToSelection(r.get(t.id()));
    query.addFrom(r);
    query.addCriteria(criteria);
    return query;
  }
}
