package org.cfyash.silt.models;

import org.cfyash.silt.entities.Memtable;

public record MemState(Memtable active, Memtable frozen) {

  public static MemState init() {
    return new MemState(Memtable.create(), null);
  }

  public static MemState freeze(Memtable toBeFrozen) {
    return new MemState(Memtable.create(), toBeFrozen);
  }
}
