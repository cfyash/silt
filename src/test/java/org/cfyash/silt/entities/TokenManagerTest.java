package org.cfyash.silt.entities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TokenManagerTest {

  @Test
  @Disabled("TODO")
  void incrementAndGet_startsFromInitToken() {
    // new TokenManager(5): first incrementAndGet() returns what you expect?
  }

  @Test
  @Disabled("TODO")
  void incrementAndGet_isUniqueUnderConcurrency() {
    // N threads x M increments: all tokens distinct, max == N*M (+ init offset)
  }

  @Test
  @Disabled("TODO")
  void pendingSyncs_isCurrentMinusLastSynced() {}

  @Test
  @Disabled("TODO")
  void lastSynced_neverMovesBackwards() {
    // incrementLastSynced(10) then incrementLastSynced(7): lastSynced stays 10?
  }
}
