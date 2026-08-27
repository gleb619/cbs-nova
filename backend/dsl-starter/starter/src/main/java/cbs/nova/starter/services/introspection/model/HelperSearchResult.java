package cbs.nova.starter.services.introspection.model;

public record HelperSearchResult(
        String name,
        String type,
        String description,
        String inputType,
        String outputType) {
}
