package cbs.nova.starter.helper.model;

public record FileLatchIn(String lockFileName, String releaseFileName, String payload) {
}
