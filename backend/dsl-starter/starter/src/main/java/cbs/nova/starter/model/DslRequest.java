package cbs.nova.starter.model;

import java.util.Map;

public record DslRequest(Object body, Map<String, Object> metadata) {

}
