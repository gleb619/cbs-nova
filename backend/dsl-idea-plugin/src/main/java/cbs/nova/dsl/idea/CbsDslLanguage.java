package cbs.nova.dsl.idea;

import com.intellij.lang.Language;

public final class CbsDslLanguage extends Language {

  public static final CbsDslLanguage INSTANCE = new CbsDslLanguage();

  private CbsDslLanguage() {
    super("CbsDsl");
  }
}
