package cbs.dsl.api.context;

import lombok.Builder;

//TODO: remove
@Deprecated(forRemoval = true)
@Builder(toBuilder = true)
public record Pair<K, V>(K key, V value) {}
