package cbs.nova.starter.service;

import java.nio.file.Path;

public record DefaultDslWorkspaceResolver(Path sourceRoot, Path workspaceRoot)
    implements DslWorkspaceResolver {

}
