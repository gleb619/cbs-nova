package cbs.nova.dsl.idea;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public final class CbsDslFileType extends LanguageFileType {

  public static final CbsDslFileType INSTANCE = new CbsDslFileType();

  public CbsDslFileType() {
    super(CbsDslLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "CbsDsl";
  }

  @Override
  public @NotNull String getDescription() {
    return "cbs-nova compact DSL/model source";
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "java";
  }

  @Override
  public Icon getIcon() {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.JavaFileType);
  }

  public boolean isJVMDebuggingSupported() {
    return true;
  }
}
