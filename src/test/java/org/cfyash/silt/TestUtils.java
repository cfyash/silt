package org.cfyash.silt;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestUtils {

  public long generateToken() {
    return ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
  }

  public long generateTimestamp() {
    return Instant.now().toEpochMilli();
  }

  public String generateString(int length) {
    return ThreadLocalRandom.current()
        .ints(length, 32, 127)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString();
  }

  public byte[] generateByteArr(int length) {
    byte[] randomBytes = new byte[length];
    ThreadLocalRandom.current().nextBytes(randomBytes);
    return randomBytes;
  }

  public static int generateIntInRange(int low, int high) {
    return ThreadLocalRandom.current().nextInt(low, high + 1);
  }
}
