package cbs.nova.dsl.idea;

import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reuses IntelliJ's bundled Java lexer for token coloring only. This deliberately does not add any
 * custom lexer/parser/inspection logic — the goal is "not plain black text and no errors" for
 * compact-DSL {@code .java} sources, not full semantic fidelity.
 */
public final class CbsDslSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

  @Override
  public @NotNull SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project,
          @Nullable VirtualFile file) {
    return new SyntaxHighlighterBase() {
      @Override
      public @NotNull com.intellij.lexer.Lexer getHighlightingLexer() {
        return new JavaParserDefinition().createLexer(project);
      }

      @Override
      public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        return pack(JavaHighlightingColors.KEYWORD);
      }
    };
  }
}
