package cbs.nova.starter.service.introspection.model;

public record HelperSearchResult(
        String name,
        String type,
        String description,
        String inputType,
        String outputType) {
}
