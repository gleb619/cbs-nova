package cbs.nova.dsl.idea;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Path;
import java.util.Set;

public class DslFileTypeOverriderTest extends BasePlatformTestCase {

  public void testDefaultFallbackMatchesSrcDslAndSrcModels() {
    var overrider = new DslFileTypeOverrider();
    var dslFile = myFixture.addFileToProject("src/dsl/SimpleGreetingDsl.java", "define stuff")
            .getVirtualFile();
    var modelFile = myFixture.addFileToProject("src/models/GreetingModels.java", "record stuff")
            .getVirtualFile();
    var otherFile = myFixture.addFileToProject("src/main/java/Foo.java", "class Foo {}")
            .getVirtualFile();

    assertEquals(CbsDslFileType.INSTANCE, overrider.getOverriddenFileType(dslFile));
    assertEquals(CbsDslFileType.INSTANCE, overrider.getOverriddenFileType(modelFile));
    assertNull(overrider.getOverriddenFileType(otherFile));
  }

  public void testSyncedDirsTakePrecedenceOverFallback() {
    var overrider = new DslFileTypeOverrider();
    var customFile = myFixture.addFileToProject("custom/dsl-src/Foo.java", "define stuff")
            .getVirtualFile();
    assertNull(overrider.getOverriddenFileType(customFile));

    DslSyncedDirs.getInstance(getProject()).replace(
            Set.of(Path.of(customFile.getParent().getPath())));
    assertEquals(CbsDslFileType.INSTANCE, overrider.getOverriddenFileType(customFile));
  }
}
