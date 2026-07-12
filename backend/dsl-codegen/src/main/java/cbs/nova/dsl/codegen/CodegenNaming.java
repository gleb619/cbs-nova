package cbs.nova.dsl.codegen;

final class CodegenNaming {

  private static final String BASE_PACKAGE = "cbs.nova.dsl.generated";

  String versionedPackage(String name, String version) {
    String nameSegment = name.toLowerCase().replaceAll("[^a-z0-9]", "");
    String versionSegment = version.replaceAll("[^a-z0-9]", "");
    if (!versionSegment.isEmpty() && Character.isDigit(versionSegment.charAt(0))) {
      versionSegment = "v" + versionSegment;
    }
    return BASE_PACKAGE + "." + nameSegment + "." + versionSegment;
  }
}
