package cbs.nova.starter.service;

import java.nio.file.Path;

public interface DslWorkspaceResolver {

  Path sourceRoot();

  Path workspaceRoot();
}
