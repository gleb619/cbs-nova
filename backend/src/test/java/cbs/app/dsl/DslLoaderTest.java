package cbs.app.dsl;

import static org.mockito.Mockito.verifyNoInteractions;

import cbs.dsl.runtime.DslRegistry;
import cbs.dsl.script.ScriptHost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DslLoaderTest {

  @Mock
  ScriptHost scriptHost;

  @Mock
  DslRegistry dslRegistry;

  @InjectMocks
  DslLoader dslLoader;

  @Test
  @DisplayName("Should skip loading when scripts dir is blank")
  void shouldSkipLoadingWhenScriptsDirIsBlank() {
    dslLoader.scriptsDir = "";
    dslLoader.onApplicationEvent(null);
    verifyNoInteractions(scriptHost);
  }

  @Test
  @DisplayName("Should skip loading when scripts dir does not exist")
  void shouldSkipLoadingWhenScriptsDirDoesNotExist() {
    dslLoader.scriptsDir = "/nonexistent-path-that-does-not-exist";
    dslLoader.onApplicationEvent(null);
    verifyNoInteractions(scriptHost);
  }

  @Test
  @DisplayName("Should skip loading when scripts dir is null")
  void shouldSkipLoadingWhenScriptsDirIsNull() {
    dslLoader.scriptsDir = null;
    dslLoader.onApplicationEvent(null);
    verifyNoInteractions(scriptHost);
  }
}
