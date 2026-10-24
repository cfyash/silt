package org.cfyash.silt.services;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Deferred until the memtable exists: these need Database.get() to check what came back,
// and Database.close() so a test can reopen the same dir in one JVM.
class DatabaseRecoveryTest {

  @TempDir Path dir;

  @Test
  @Disabled("after memtable")
  void open_missingDir_createsDirAndEmptyWal() {}

  @Test
  @Disabled("after memtable")
  void reopen_afterCleanWrites_everyKeyReadable() {}

  @Test
  @Disabled("after memtable")
  void reopen_sameKeyWrittenTwice_newestValueWins() {}

  @Test
  @Disabled("after memtable")
  void reopen_nextTokenIsMaxTokenPlusOne() {
    // write 3, close, reopen, write 1: its token in the WAL is 4
  }

  @Test
  @Disabled("after memtable")
  void reopen_garbageTail_isTruncated_earlierRecordsReadable() {
    // append random bytes to the WAL, reopen
  }

  @Test
  @Disabled("after memtable")
  void reopen_tornLastRecord_isTruncated_earlierRecordsReadable() {
    // cut the file inside the last record, reopen
  }

  @Test
  @Disabled("after memtable")
  void reopen_corruptMiddleRecord_dropsItAndEverythingAfter() {
    // point-in-time recovery: records before the corrupt one survive, none after
  }

  @Test
  @Disabled("after memtable")
  void reopen_afterTruncation_newWritesLandAtTruncatedEnd() {
    // reopen twice: second reopen sees old good records + new ones, nothing in between
  }
}
