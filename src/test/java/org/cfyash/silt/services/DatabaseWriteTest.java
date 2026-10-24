package org.cfyash.silt.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.cfyash.silt.TestUtils;
import org.cfyash.silt.common.exceptions.*;
import org.cfyash.silt.config.DatabaseConfig;
import org.cfyash.silt.entities.WALFile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Slf4j
class DatabaseWriteTest {

  @TempDir Path dir;
  Database db;

  // ---------- input validation ----------
  @BeforeEach
  void setUp() throws IOException {
    db = new Database(dir.toUri().getPath());
  }

  @Test
  void write_emptyKey_throwsRecordLimit() {
    byte[] key = new byte[] {};
    byte[] val = TestUtils.generateByteArr(10);
    assertThrows(RecordLimitException.class, () -> db.write(key, val));
  }

  @Test
  void write_keyOverLimit_throwsRecordLimit() {
    // KEY_LIMIT + 1 bytes
    byte[] key = TestUtils.generateByteArr((int) DatabaseConfig.KEY_LIMIT + 1);
    byte[] val = TestUtils.generateByteArr(10);
    assertThrows(RecordLimitException.class, () -> db.write(key, val));
  }

  @Test
  void write_valueOverLimit_throwsRecordLimit() {
    byte[] val = TestUtils.generateByteArr((int) DatabaseConfig.KEY_LIMIT + 1);
    byte[] key = TestUtils.generateByteArr(10);
    assertThrows(RecordLimitException.class, () -> db.write(key, val));
  }

  @Test
  void write_keyAndValueExactlyAtLimit_isAccepted() {
    byte[] val = TestUtils.generateByteArr((int) DatabaseConfig.KEY_LIMIT);
    byte[] key = TestUtils.generateByteArr((int) DatabaseConfig.KEY_LIMIT);
    assertDoesNotThrow(() -> db.write(key, val));
  }

  @Test
  void write_rejectedInput_doesNotMakeDbReadOnly() {
    // oversized write throws, the next valid write still succeeds
    byte[] olKey = TestUtils.generateByteArr((int) DatabaseConfig.KEY_LIMIT + 1);
    byte[] olVal = TestUtils.generateByteArr(10);
    assertThrows(RecordLimitException.class, () -> db.write(olKey, olVal));
    byte[] val = TestUtils.generateByteArr((int) DatabaseConfig.KEY_LIMIT);
    byte[] key = TestUtils.generateByteArr((int) DatabaseConfig.KEY_LIMIT);
    assertDoesNotThrow(() -> db.write(key, val));
  }

  // ---------- durability / ack ----------

  @Test
  @Disabled("TODO")
  void write_afterReturn_recordIsDecodableFromWal() {
    // read dir/WAL_FILENAME with a fresh WALRecordHandler; key, value, token match
  }

  @Test
  @Disabled("TODO")
  @Timeout(5)
  void singleWrite_belowSyncLimit_isAckedByLingerTimer() {
    // one writer, nobody else: returns after ~LINGER_TIME_MS
  }

  @Test
  @Disabled("TODO")
  @Timeout(5)
  void writesReachingSyncLimit_areAckedWithoutWaitingForLinger() {
    // SYNC_LIMIT concurrent writers all return well under LINGER_TIME_MS
    // (timing based: keep the bound generous)
  }

  // ---------- concurrency ----------

  @Test
  @Disabled("TODO")
  @Timeout(10)
  void concurrentWriters_allAcked() {
    // N virtual threads x M writes each; every write() returns, none throws
  }

  @Test
  @Disabled("TODO")
  @Timeout(10)
  void concurrentWriters_walHasEveryRecord_withStrictlyIncreasingTokens() {
    // decode the whole WAL: count == N*M, tokens strictly increase in file order
  }

  @Test
  @Disabled("TODO")
  @Timeout(5)
  void interruptedWaitingWriter_isStillAcked_andDbStaysWritable() {
    // interrupt a writer while it waits in sync(); write() returns normally,
    // interrupt flag still set, next write succeeds
  }

  // ---------- fail-stop ----------
  // These need a way to make the WAL throw. Database builds its own WALFile, so add a seam,
  // e.g. a package-private constructor that takes the WALFile (or a factory), and a test
  // WALFile subclass whose write()/fsync() throw on demand.

  @RepeatedTest(10)
  @Timeout(3)
  void writeFailure_wakesWaitingWriter_withException() throws Exception {
    // writer A waits in sync(), writer B's WAL write throws: A gets an exception, no hang
    var exceptions = Collections.synchronizedList(new java.util.ArrayList<Throwable>());
    try (var walMock = mock(WALFile.class)) {
      var coordinatorF = Database.class.getDeclaredField("syncCoordinator");
      coordinatorF.setAccessible(true);

      var dbWithMock = new Database(walMock, 50000L, 10, 10);
      var coordinator = (SyncCoordinator) coordinatorF.get(dbWithMock);
      var lock = coordinator.getLock();
      var cond = coordinator.getCondition();

      AtomicBoolean failWrites = new AtomicBoolean(false);
      doAnswer(
              _ -> {
                if (failWrites.get()) throw new WALWriteException("mocked wal write exception");
                return null;
              })
          .when(walMock)
          .write(any());
      CountDownLatch awoke = new CountDownLatch(5);
      for (int i = 0; i < 5; i++) {
        var key = TestUtils.generateByteArr(i + 1);
        var val = TestUtils.generateByteArr(i + 1);
        Thread.startVirtualThread(
            () -> {
              try {
                dbWithMock.write(key, val);
              } catch (Exception e) {
                exceptions.add(e);
              } finally {
                awoke.countDown();
              }
            });
      }

      int inSyncThreads = 0;
      while (inSyncThreads <= 5) { // <= as flusher is also waiting condition
        lock.lock();
        try {
          inSyncThreads = lock.getWaitQueueLength(cond);
        } finally {
          lock.unlock();
        }
      }
      var key = TestUtils.generateByteArr(6);
      var val = TestUtils.generateByteArr(6);
      failWrites.set(true);
      assertThrows(WALWriteException.class, () -> dbWithMock.write(key, val));
      awoke.await();
      assertEquals(5, exceptions.size());
      for (var ex : exceptions) {
        assertInstanceOf(WALSyncException.class, ex);
      }
    }
  }

  @Test
  @Timeout(3)
  void writeFailure_laterWritesThrowReadOnlyMode() throws Exception {
    var exceptions = Collections.synchronizedList(new java.util.ArrayList<Throwable>());
    try (var walMock = mock(WALFile.class)) {
      var dbWithMock = new Database(walMock, 5000L, 10, 10);
      var k = TestUtils.generateByteArr(10);
      var v = TestUtils.generateByteArr(10);
      doThrow(WALWriteException.class).when(walMock).write(any());
      assertThrows(WALWriteException.class, () -> dbWithMock.write(k, v));
      CountDownLatch awoke = new CountDownLatch(10);
      for (int i = 0; i < 10; i++) {
        Thread.startVirtualThread(
            () -> {
              try {
                dbWithMock.write(k, v);
              } catch (Exception e) {
                exceptions.add(e);
              } finally {
                awoke.countDown();
              }
            });
      }
      awoke.await();
      assertEquals(10, exceptions.size());
      for (var ex : exceptions) {
        assertInstanceOf(ReadOnlyModeException.class, ex);
      }
    }
  }

  @RepeatedTest(10)
  @Timeout(3)
  void fsyncFailure_laterWritesThrowReadOnlyMode() throws Exception {
    var exceptions = Collections.synchronizedList(new java.util.ArrayList<Throwable>());
    try (var walMock = mock(WALFile.class)) {
      doThrow(WALSyncException.class).when(walMock).fsync();
      var k = TestUtils.generateByteArr(10);
      var v = TestUtils.generateByteArr(10);
      var coordinatorF = Database.class.getDeclaredField("syncCoordinator");
      coordinatorF.setAccessible(true);

      var dbWithMock = new Database(walMock, 50000L, 10, 10);
      var coordinator = (SyncCoordinator) coordinatorF.get(dbWithMock);
      var lock = coordinator.getLock();
      var cond = coordinator.getCondition();

      CountDownLatch awoke = new CountDownLatch(9);
      for (int i = 0; i < 9; i++) {
        Thread.ofVirtual()
            .name("test-thread-" + i)
            .start(
                () -> {
                  try {
                    dbWithMock.write(k, v);
                  } catch (Exception e) {
                    exceptions.add(e);
                  } finally {
                    awoke.countDown();
                  }
                });
      }

      int inSyncThreads = 0;
      while (inSyncThreads
          < 10) { // < 10 as flusher is also waiting condition, 9 writers + 1 flusher
        lock.lock();
        try {
          inSyncThreads = lock.getWaitQueueLength(cond);
        } finally {
          lock.unlock();
        }
      }
      assertThrows(WALSyncException.class, () -> dbWithMock.write(k, v));
      awoke.await();
      assertEquals(9, exceptions.size());
      for (var ex : exceptions) {
        assertInstanceOf(WALSyncException.class, ex);
      }
      var exceptions2 = Collections.synchronizedList(new java.util.ArrayList<Throwable>());
      CountDownLatch awoke2 = new CountDownLatch(10);
      for (int i = 0; i < 10; i++) {
        Thread.startVirtualThread(
            () -> {
              try {
                dbWithMock.write(k, v);
              } catch (Exception e) {
                exceptions2.add(e);
              } finally {
                awoke2.countDown();
              }
            });
      }
      awoke2.await();
      assertEquals(10, exceptions2.size());
      for (var ex : exceptions2) {
        assertInstanceOf(ReadOnlyModeException.class, ex);
      }
    }
  }

  @RepeatedTest(10)
  @Timeout(3)
  void fsyncFailureInBgFlush_laterWritesThrowReadOnlyMode() throws Exception {
    var exceptions = Collections.synchronizedList(new java.util.ArrayList<Throwable>());
    AtomicReference<Exception> exception1 = new AtomicReference<>();
    try (var walMock = mock(WALFile.class)) {
      doThrow(WALSyncException.class).when(walMock).fsync();
      var k = TestUtils.generateByteArr(10);
      var v = TestUtils.generateByteArr(10);
      var dbWithMock = new Database(walMock, 100L, 10, 10);
      var t0 =
          Thread.ofVirtual()
              .name("test-thread-" + 0)
              .start(
                  () -> {
                    try {
                      dbWithMock.write(k, v);
                    } catch (Exception e) {
                      exception1.set(e);
                    }
                  });
      CountDownLatch awoke = new CountDownLatch(10);
      t0.join();
      for (int i = 0; i < 10; i++) {
        Thread.ofVirtual()
            .name("test-thread-" + (i + 1))
            .start(
                () -> {
                  try {
                    dbWithMock.write(k, v);
                  } catch (Exception e) {
                    exceptions.add(e);
                  } finally {
                    awoke.countDown();
                  }
                });
      }
      awoke.await();
      assertInstanceOf(WALSyncException.class, exception1.get());
      assertEquals(10, exceptions.size());
      for (var ex : exceptions) {
        assertInstanceOf(ReadOnlyModeException.class, ex);
      }
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    db.close();
  }
}
