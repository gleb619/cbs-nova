package cbs.nova.dsl.generator;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.ExplainSupport;
import cbs.nova.dsl.function.FunctionDslObject;
import cbs.nova.dsl.process.ProcessDslObject;
import cbs.nova.dsl.transaction.TransactionDslObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public final class ExplainReportFactory {

  private final MermaidDiagramGenerator diagramGenerator;

  public @NonNull ExplainReport forProcess(
          @NonNull ProcessDslObject process, int budgetChars) {
    return report(process.describe(), diagramGenerator.forProcess(process), budgetChars);
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
    return new ExplainReport(
            report.name(),
            report.description(),
            ExplainSupport.truncateToBudget(
                    diagram, budgetChars - report.description().length()));
  }

  private static @NonNull ExplainReport report(
          @NonNull DslDescriptor descriptor, @Nullable String diagram, int budgetChars) {
    var description = descriptor.description() != null
            ? descriptor.description()
            : "No description available for `" + descriptor.name() + "`.";
    var boundedDescription = ExplainSupport.truncateToBudget(description, budgetChars);
    var boundedDiagram = ExplainSupport.truncateToBudget(
            diagram != null ? diagram : "", budgetChars - boundedDescription.length());
    return new ExplainReport(descriptor.name(), boundedDescription, boundedDiagram);
  }
}
