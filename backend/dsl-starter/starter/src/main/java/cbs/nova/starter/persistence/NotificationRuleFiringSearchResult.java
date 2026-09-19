package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.NotificationRuleFiringEntity;
import java.util.List;

public record NotificationRuleFiringSearchResult(List<NotificationRuleFiringEntity> items,
        long total) {
}
