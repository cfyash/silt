package org.cfyash.silt.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.cfyash.silt.TestUtils;
import org.cfyash.silt.common.exceptions.SyncTokenUpdateException;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class TokenManagerTest {

  @Test
  void incrementAndGet_startsFromInitToken() {
    var tk = new TokenManager(5);
    assertEquals(6, tk.incrementAndGet());
    assertEquals(5, tk.getLastSynced());
    assertEquals(5, tk.getLastWritten());
  }

  @RepeatedTest(100)
  void incrementAndGet_isUniqueUnderConcurrency() throws Exception{
    long initToken = TestUtils.generateIntInRange(10, 50);
    var tk = new TokenManager(initToken);
    int n = TestUtils.generateIntInRange(10, 50);
    int m = TestUtils.generateIntInRange(5000, 10000);
    var counter = new AtomicInteger(m);
    CountDownLatch startLatch = new CountDownLatch(1);
    Set<Long> collector = ConcurrentHashMap.newKeySet();
    try (var exec = Executors.newFixedThreadPool(n)) {
      List<Future<?>> futures = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        futures.add(
            exec.submit(
                () -> {
                  try {
                    startLatch.await();
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                  while (counter.getAndUpdate(val -> val > 0 ? val - 1 : 0) > 0) {
                    long token = tk.incrementAndGet();
                    collector.add(token);
                  }
                }));
      }
      startLatch.countDown();
      for (var future : futures) {
        future.get();
      }
    }
    assertEquals(0, counter.get());
    assertEquals(m, collector.size());
    long maxToken =
        collector.stream().max(Comparator.comparingLong(Long::longValue)).orElse(initToken);
    assertEquals(m + initToken, maxToken);
  }

  @Test
  void pendingSyncs_isCurrentMinusLastSynced() {
    var tk = new TokenManager(6);
    assertEquals(0, tk.getPendingSyncs());
    int writes = TestUtils.generateIntInRange(100, 1000);
    long lastToken = 0L;
    for (int i = 0; i < writes; i++) {
      lastToken = tk.incrementAndGet();
    }
    assertEquals(writes, tk.getPendingSyncs());
    int syncToken = TestUtils.generateIntInRange(7, (int) lastToken);
    tk.incrementLastSynced(syncToken);
    assertEquals(lastToken - syncToken, tk.getPendingSyncs());
  }

  @Test
  void lastSynced_neverMovesBackwards() {
    var tk = new TokenManager(6);
    tk.incrementLastSynced(100);
    assertThrows(SyncTokenUpdateException.class, () -> tk.incrementLastSynced(7));
  }
}
