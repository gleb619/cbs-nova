package cbs.nova.starter.helper.model;

import java.util.Map;

public record FormatMessageIn(String template, Map<String, Object> params) {

}
