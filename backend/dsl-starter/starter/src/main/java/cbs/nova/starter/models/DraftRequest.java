package cbs.nova.starter.models;

public record DraftRequest(
        String name,
        String type,
        String status,
        String version,
        String taskQueue) {

}