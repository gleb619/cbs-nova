package cbs.nova.starter.helpers.model;

public record FileLatchIn(String lockFileName, String releaseFileName, String payload) {
}
