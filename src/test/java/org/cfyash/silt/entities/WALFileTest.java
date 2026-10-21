package org.cfyash.silt.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WALFileTest {

  @TempDir Path dir;

  @Test
  @Disabled("TODO")
  void write_thenRead_returnsSameBytes() {}

  @Test
  @Disabled("TODO")
  void reopen_appendsAtEnd_doesNotOverwrite() {
    // write A, close, reopen, write B: file is A+B, not B over A
  }

  @Test
  @Disabled("TODO")
  void read_pastEnd_returnsShortCount() {
    // buffer bigger than remaining bytes: read() returns fewer than buffer size
  }

  @Test
  @Disabled("TODO")
  void truncate_shrinksFile_andNextWriteGoesAtNewEnd() {}
}
