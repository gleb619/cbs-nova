package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslEventEntity;
import java.util.List;

public record DslEventSearchResult(List<DslEventEntity> items, long total) {
}
