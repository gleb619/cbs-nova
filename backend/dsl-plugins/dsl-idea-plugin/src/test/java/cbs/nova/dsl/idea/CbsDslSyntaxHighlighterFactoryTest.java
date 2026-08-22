package cbs.nova.dsl.idea;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

class CbsDslSyntaxHighlighterFactoryTest extends BasePlatformTestCase {

  public void testGetSyntaxHighlighterReturnsJavaHighlighterWhenProjectIsNull() {
    SyntaxHighlighter highlighter = new CbsDslSyntaxHighlighterFactory()
            .getSyntaxHighlighter(null, null);

    assertThat(highlighter).isNotNull().isInstanceOf(JavaFileHighlighter.class);
  }

  public void testGetSyntaxHighlighterReturnsJavaHighlighterForDslFile() {
    var dslFile = myFixture.addFileToProject("src/dsl/SimpleGreetingDsl.java", "define stuff")
            .getVirtualFile();

    SyntaxHighlighter highlighter = new CbsDslSyntaxHighlighterFactory()
            .getSyntaxHighlighter(getProject(), dslFile);

    assertThat(highlighter).isNotNull().isInstanceOf(JavaFileHighlighter.class);
  }

  public void testCreateFallsBackToJavaHighlighterWhenProjectIsNull() {
    SyntaxHighlighter highlighter = new CbsDslSyntaxHighlighterFactory()
            .create(null, null, null);

    assertThat(highlighter).isNotNull().isInstanceOf(JavaFileHighlighter.class);
  }

  public void testCreateReturnsHighlighterForCbsDslFileType() {
    var dslFile = myFixture.addFileToProject("src/dsl/SimpleGreetingDsl.java", "define stuff")
            .getVirtualFile();

    SyntaxHighlighter highlighter = new CbsDslSyntaxHighlighterFactory()
            .create(CbsDslFileType.INSTANCE, getProject(), dslFile);

    assertThat(highlighter).isNotNull().isInstanceOf(JavaFileHighlighter.class);
  }

  public void testCreateReturnsHighlighterForNonJavaFileWithCustomLanguage() {
    var dslFile = myFixture.addFileToProject("src/dsl/SomeGreeting.java", "define stuff")
            .getVirtualFile();

    SyntaxHighlighter highlighter = new CbsDslSyntaxHighlighterFactory()
            .create(CbsDslFileType.INSTANCE, getProject(), dslFile);

    assertThat(highlighter).isNotNull();
  }
}
