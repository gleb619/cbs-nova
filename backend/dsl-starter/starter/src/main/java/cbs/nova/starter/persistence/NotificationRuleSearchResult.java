package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.NotificationRuleEntity;
import java.util.List;

public record NotificationRuleSearchResult(List<NotificationRuleEntity> items, long total) {
}
