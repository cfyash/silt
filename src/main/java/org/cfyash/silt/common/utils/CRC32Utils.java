package org.cfyash.silt.common.utils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.CRC32C;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CRC32Utils {

  public static int generateCheckSum(ByteBuffer buf) {
    CRC32C crc = new CRC32C();
    crc.update(buf);
    return (int) crc.getValue();
  }

  public static int generateCheckSum(List<ByteBuffer> buffers) {
    CRC32C crc = new CRC32C();
    for (var buf : buffers) {
      crc.update(buf);
    }
    return (int) crc.getValue();
  }
}
