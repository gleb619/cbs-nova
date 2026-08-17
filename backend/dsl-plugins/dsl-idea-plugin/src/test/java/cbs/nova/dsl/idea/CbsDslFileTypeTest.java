package cbs.nova.dsl.idea;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CbsDslFileTypeTest {

  @Test
  void hasJavaCompatibleExtensionAndOwnLanguage() {
    var fileType = new CbsDslFileType();
    assertThat(fileType.getDefaultExtension()).isEqualTo("java");
    assertThat(fileType.getLanguage()).isSameAs(CbsDslLanguage.INSTANCE);
    assertThat(fileType.getName()).isEqualTo("CbsDsl");
  }
}
