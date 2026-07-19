package cbs.nova.dsl.idea;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighterProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reuses IntelliJ's bundled Java lexer for token coloring only. This deliberately does not add any
 * custom lexer/parser/inspection logic — the goal is "not plain black text and no errors" for
 * compact-DSL {@code .java} sources, not full semantic fidelity.
 */
public final class CbsDslSyntaxHighlighterFactory extends SyntaxHighlighterFactory implements
    SyntaxHighlighterProvider {

  @Override
  public @NotNull SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project,
          @Nullable VirtualFile file) {
    return new JavaFileHighlighter(project == null ? LanguageLevel.HIGHEST
        : JavaPsiImplementationHelper.getInstance(project).getEffectiveLanguageLevel(file));
  }

  public @Nullable SyntaxHighlighter create(@NotNull FileType fileType, @Nullable Project project, @Nullable VirtualFile file) {
    if (project != null && file != null) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (fileType == JavaClassFileType.INSTANCE && psiFile != null) {
        Language language = psiFile.getLanguage();
        if (language != JavaLanguage.INSTANCE) {
          return SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file);
        }
      }

      if (psiFile instanceof ClsFileImpl) {
        LanguageLevel sourceLevel = ((ClsFileImpl)psiFile).getLanguageLevel();
        return new JavaFileHighlighter(sourceLevel);
      }
    }

    return new JavaFileHighlighter();
  }
}
