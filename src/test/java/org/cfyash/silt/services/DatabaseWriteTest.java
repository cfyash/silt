package org.cfyash.silt.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class DatabaseWriteTest {

  @TempDir Path dir;

  @Test
  @Disabled("TODO")
  void write_returnsAfterRecordIsInWal() {}

  @Test
  @Disabled("TODO")
  @Timeout(5)
  void singleWrite_belowSyncLimit_isAckedByLingerTimer() {
    // one write, nobody else: returns within ~LINGER_TIME_MS, not forever
  }

  @Test
  @Disabled("TODO")
  @Timeout(10)
  void concurrentWriters_allAcked_andAllRecordsReplayable() {
    // many virtual threads write, then reopen and count records
  }

  @Test
  @Disabled("TODO")
  void concurrentWriters_tokensInFileAreStrictlyIncreasing() {
    // token order == file order
  }

  @Test
  @Disabled("TODO")
  void interruptedWriter_doesNotMakeDbReadOnly() {}

  @Test
  @Disabled("TODO")
  void keyOrValueOverLimit_isRejected() {}

  @Test
  @Disabled("TODO")
  void afterWriteFailure_furtherWritesThrowReadOnly() {
    // needs a way to make the WAL fail (e.g. a WALFile you can force to throw)
  }
}
