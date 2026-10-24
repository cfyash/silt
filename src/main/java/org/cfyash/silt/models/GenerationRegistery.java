package org.cfyash.silt.models;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class GenerationRegistery {
  private final Set<Long> registry = ConcurrentHashMap.newKeySet();
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition isEmptyCondition = lock.newCondition();

  public void register(long token) {
    registry.add(token);
  }

  public boolean unregister(long token) {
    if (registry.remove(token)) {
      if (registry.isEmpty()) {
        lock.lock();
        try {
          if (registry.isEmpty()) isEmptyCondition.signalAll();
        } finally {
          lock.unlock();
        }
      }
      return true;
    }
    return false;
  }

  public boolean isRegistered(long token) {
    return registry.contains(token);
  }

  public void awaitEmpty() {
    lock.lock();
    try {
      while (!registry.isEmpty()) {
        isEmptyCondition.awaitUninterruptibly();
      }
    } finally {
      lock.unlock();
    }
  }
}
