package cbs.nova.dsl.idea;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class CbsDslParserDefinition implements ParserDefinition {

  private final JavaParserDefinition delegate = new JavaParserDefinition();

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return delegate.createLexer(project);
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return delegate.createParser(project);
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return delegate.getFileNodeType();
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return delegate.getCommentTokens();
  }

  @Override
  public @NotNull TokenSet getStringLiteralElements() {
    return delegate.getStringLiteralElements();
  }

  @Override
  public @NotNull PsiElement createElement(ASTNode astNode) {
    return delegate.createElement(astNode);
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider fileViewProvider) {
    return delegate.createFile(fileViewProvider);
  }
}
