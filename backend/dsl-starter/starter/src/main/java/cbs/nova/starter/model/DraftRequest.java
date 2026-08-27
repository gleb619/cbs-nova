package cbs.nova.starter.model;

public record DraftRequest(
        String name,
        String type,
        String status,
        String version,
        String taskQueue) {

}
