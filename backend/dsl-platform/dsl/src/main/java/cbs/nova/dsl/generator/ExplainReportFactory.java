package cbs.nova.dsl.generator;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

//TODO: The stupid AI has perverted the very concept, its very wrong, to export data in that way. Everything should be done differently.
@Deprecated(forRemoval = true)
@RequiredArgsConstructor
public final class ExplainReportFactory {

  private final MermaidDiagramGenerator diagramGenerator;

  public @NonNull ExplainReport forProcess(
          @NonNull ProcessDslObject process, int budgetChars) {
    return report(process.descriptor(), diagramGenerator.forProcess(process), budgetChars);
  }

  public @NonNull ExplainReport forTransaction(
          @NonNull TransactionDslObject transaction, int budgetChars) {
    return report(
            transaction.describe(), diagramGenerator.forTransaction(transaction), budgetChars);
  }

  public @NonNull ExplainReport forFunction(
          @NonNull FunctionDslObject function, int budgetChars) {
    return report(function.describe(), null, budgetChars);
  }

  public @NonNull ExplainReport forHelper(
          @NonNull String name, @NonNull ExplainReport report, int budgetChars) {
    var diagram = report.mermaid().isEmpty()
            ? diagramGenerator.forHelper(name)
            : report.mermaid();
    return new ExplainReport(report.name(), report.description(), diagram).truncateTo(budgetChars);
  }

  private static @NonNull ExplainReport report(
          @NonNull DslDescriptor descriptor, @Nullable String diagram, int budgetChars) {
    var description = descriptor.description() != null
            ? descriptor.description()
            : "No description available for `" + descriptor.name() + "`.";
    return new ExplainReport(
            descriptor.name(), description, diagram != null ? diagram : "")
            .truncateTo(budgetChars);
  }
}
