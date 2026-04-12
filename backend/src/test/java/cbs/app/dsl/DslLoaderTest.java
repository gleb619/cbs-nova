package cbs.app.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.compiler.DslScriptHost;
import cbs.dsl.runtime.DslRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class DslLoaderTest {

  @TempDir
  Path tempDir;

  @Test
  @DisplayName("Should not call DslScriptHost when scripts-dir is not set")
  void shouldNotCallDslScriptHostWhenScriptsDirNotSet() {
    // Arrange
    DslScriptHost dslScriptHost = mock(DslScriptHost.class);
    DslRegistry dslRegistry = mock(DslRegistry.class);
    DslLoader loader = new DslLoader(dslScriptHost, dslRegistry);
    loader.scriptsDir = "";

    // Act
    loader.onApplicationEvent(mock(ApplicationReadyEvent.class));

    // Assert
    verify(dslScriptHost, never()).eval(anyString(), anyString());
  }

  @Test
  @DisplayName("Should call DslScriptHost twice when directory has two valid .kts files")
  void shouldCallDslScriptHostTwiceWhenTwoValidFiles() throws IOException {
    // Arrange
    String script1 = """
        workflow("wf1") {
          states("A", "B")
          initial("A")
          terminal("B")
        }
        event("evt1")
        transaction("tx1")
        """;
    String script2 = """
        workflow("wf2") {
          states("X", "Y")
          initial("X")
          terminal("Y")
        }
        event("evt2")
        """;

    Files.writeString(tempDir.resolve("script1.kts"), script1);
    Files.writeString(tempDir.resolve("script2.kts"), script2);

    DslScriptHost dslScriptHost = mock(DslScriptHost.class);
    DslRegistry sharedRegistry = new DslRegistry();

    DslRegistry registry1 = new DslRegistry();
    WorkflowDefinition wf1 = mockWorkflow("wf1");
    EventDefinition evt1 = mockEvent("evt1");
    TransactionDefinition tx1 = mockTransaction("tx1");
    registry1.register(wf1);
    registry1.register(evt1);
    registry1.register(tx1);

    DslRegistry registry2 = new DslRegistry();
    WorkflowDefinition wf2 = mockWorkflow("wf2");
    EventDefinition evt2 = mockEvent("evt2");
    registry2.register(wf2);
    registry2.register(evt2);

    when(dslScriptHost.eval(eq(script1), eq("script1.kts"))).thenReturn(registry1);
    when(dslScriptHost.eval(eq(script2), eq("script2.kts"))).thenReturn(registry2);

    DslLoader loader = new DslLoader(dslScriptHost, sharedRegistry);
    loader.scriptsDir = tempDir.toString();

    // Act
    loader.onApplicationEvent(mock(ApplicationReadyEvent.class));

    // Assert
    verify(dslScriptHost, times(2)).eval(anyString(), anyString());
    assertThat(sharedRegistry.getWorkflows()).hasSize(2);
    assertThat(sharedRegistry.getEvents()).hasSize(2);
    assertThat(sharedRegistry.getTransactions()).hasSize(1);
  }

  @Test
  @DisplayName("Should process second file when first file throws on eval")
  void shouldProcessSecondFileWhenFirstFails() throws IOException {
    // Arrange
    String badScript = "this is not valid kotlin";
    String goodScript = """
        workflow("wf-good") {
          states("A", "B")
          initial("A")
          terminal("B")
        }
        event("evt-good")
        """;

    Files.writeString(tempDir.resolve("bad.kts"), badScript);
    Files.writeString(tempDir.resolve("good.kts"), goodScript);

    DslScriptHost dslScriptHost = mock(DslScriptHost.class);
    DslRegistry sharedRegistry = new DslRegistry();

    when(dslScriptHost.eval(eq(badScript), eq("bad.kts")))
        .thenThrow(new IllegalStateException("Script evaluation failed for 'bad.kts': error"));

    DslRegistry goodRegistry = new DslRegistry();
    WorkflowDefinition wf = mockWorkflow("wf-good");
    EventDefinition evt = mockEvent("evt-good");
    goodRegistry.register(wf);
    goodRegistry.register(evt);

    when(dslScriptHost.eval(eq(goodScript), eq("good.kts"))).thenReturn(goodRegistry);

    DslLoader loader = new DslLoader(dslScriptHost, sharedRegistry);
    loader.scriptsDir = tempDir.toString();

    // Act
    loader.onApplicationEvent(mock(ApplicationReadyEvent.class));

    // Assert
    verify(dslScriptHost, times(2)).eval(anyString(), anyString());
    assertThat(sharedRegistry.getWorkflows()).hasSize(1);
    assertThat(sharedRegistry.getEvents()).hasSize(1);
    assertThat(sharedRegistry.getWorkflows()).containsKey("wf-good");
  }

  // -- Test helpers --

  private WorkflowDefinition mockWorkflow(String code) {
    WorkflowDefinition wf = mock(WorkflowDefinition.class);
    when(wf.getCode()).thenReturn(code);
    when(wf.getStates()).thenReturn(List.of("A", "B"));
    when(wf.getInitial()).thenReturn("A");
    when(wf.getTerminalStates()).thenReturn(List.of("B"));
    when(wf.getTransitions()).thenReturn(List.of());
    return wf;
  }

  private EventDefinition mockEvent(String code) {
    EventDefinition evt = mock(EventDefinition.class);
    when(evt.getCode()).thenReturn(code);
    return evt;
  }

  private TransactionDefinition mockTransaction(String code) {
    TransactionDefinition tx = mock(TransactionDefinition.class);
    when(tx.getCode()).thenReturn(code);
    return tx;
  }
}
