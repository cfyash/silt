package org.cfyash.silt.handlers;

import org.cfyash.silt.common.exceptions.MemtableInsertException;
import org.cfyash.silt.models.MemKey;
import org.cfyash.silt.models.MemState;
import org.cfyash.silt.models.MemValue;

public class MemtableHandler {

  private volatile MemState state;

  public MemtableHandler(int limit) {
    this.state = MemState.init();
  }

  public MemValue get(MemKey key) {
    var s = state;
    var v = s.active().get(key);
    if (v == null && s.frozen() != null) v = s.frozen().get(key);
    return v;
  }

  public void put(MemKey k, MemValue v) {
    var state = this.state;
    if (state.active().insert(k, v)) {
      return;
    }
    var frozen = state.frozen();
    if (frozen != null && frozen.insert(k, v)) {
      return;
    }
    throw new MemtableInsertException("could not find registered memtable");
  }

  public void freeze() {
    var state = this.state;
    var active = state.active();
    var frozen = state.frozen();
    frozen.awaitEmpty();
    // flush frozen to disk
    this.state = MemState.freeze(active);
  }

  public void registerOnActive(long token) {
    var state = this.state;
    state.active().register(token);
  }

  public void unregister(long token) {
    var state = this.state;
    if (!state.active().unregister(token)) {
      if (state.frozen() != null) {
        state.frozen().unregister(token);
      }
    }
  }

  public void replay(MemKey k, MemValue v) {
    var state = this.state;
    var active = state.active();
    active.replay(k, v);
  }
}
