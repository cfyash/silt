package org.cfyash.silt.models;

import java.util.Arrays;
import lombok.Builder;

@Builder
public record MemKey(byte[] key, long token) implements Comparable<MemKey> {

  @Override
  public int compareTo(MemKey o) {
    int keyCompare = Arrays.compareUnsigned(this.key, o.key);
    if (keyCompare != 0) {
      return keyCompare;
    }
    return Long.compare(o.token, this.token);
  }
}
