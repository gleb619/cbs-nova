package cbs.nova.starter.model;

import java.util.List;


public record PageResponse<T>(List<T> items, long total, int offset, int limit) {
}
