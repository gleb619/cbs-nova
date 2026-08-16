package cbs.nova.server.helpers;

import io.avaje.jsonb.Json;

@Json
public record GreeterOut(String message) {
}
