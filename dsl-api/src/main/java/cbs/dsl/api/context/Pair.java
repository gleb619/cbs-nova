package cbs.dsl.api.context;

import lombok.Builder;

@Builder(toBuilder = true)
public record Pair<K, V>(K key, V value) {

}
