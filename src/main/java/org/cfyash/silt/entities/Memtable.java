package org.cfyash.silt.entities;

import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListMap;
import org.cfyash.silt.models.GenerationRegistery;
import org.cfyash.silt.models.MemKey;
import org.cfyash.silt.models.MemValue;

public final class Memtable {
  private final ConcurrentSkipListMap<MemKey, MemValue> map;
  private final GenerationRegistery registry;

  private Memtable(ConcurrentSkipListMap<MemKey, MemValue> map, GenerationRegistery registry) {
    this.map = map;
    this.registry = registry;
  }

  public static Memtable create() {
    return new Memtable(new ConcurrentSkipListMap<>(), new GenerationRegistery());
  }

  public MemValue get(MemKey probe) {
    var e = map.ceilingEntry(probe);
    return (e != null && Arrays.equals(e.getKey().key(), probe.key())) ? e.getValue() : null;
  }

  public boolean insert(MemKey k, MemValue v) {
    if (!this.registry.isRegistered(k.token())) {
      return false;
    }
    this.map.put(k, v);
    return true;
  }

  public void awaitEmpty() {
    this.registry.awaitEmpty();
  }

  public void register(long token) {
    this.registry.register(token);
  }

  public boolean unregister(long token) {
    return this.registry.unregister(token);
  }

  public void replay(MemKey k, MemValue v) {
    this.map.put(k, v);
  }
}
