package cbs.dsl.builder;

import cbs.dsl.api.context.Pair;

import java.util.List;

public record ContextScope(List<Pair<String, Object>> data) {}
