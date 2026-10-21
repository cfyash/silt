package org.cfyash.silt.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseRecoveryTest {

  @TempDir Path dir;

  @Test
  @Disabled("TODO")
  void freshDir_startsWithEmptyWal() {}

  @Test
  @Disabled("TODO")
  void reopen_afterCleanWrites_keepsAllRecords() {}

  @Test
  @Disabled("TODO")
  void reopen_continuesTokensAfterMaxToken() {
    // write 3, reopen, write 1: new record's token is 4
  }

  @Test
  @Disabled("TODO")
  void garbageTail_isTruncated_andEarlierRecordsSurvive() {
    // append random bytes to the WAL file, reopen
  }

  @Test
  @Disabled("TODO")
  void tornLastRecord_isTruncated() {
    // cut the file a few bytes short of the last record's end, reopen
  }

  @Test
  @Disabled("TODO")
  void corruptMiddleRecord_dropsItAndEverythingAfter() {
    // point-in-time recovery
  }

  @Test
  @Disabled("TODO")
  void writesAfterTruncation_landAtNewEnd() {}
}
