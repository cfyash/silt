package org.cfyash.silt.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WALRecordHandlerTest {

  @TempDir Path dir;

  @Test
  @Disabled("TODO")
  void encode_thenDecode_roundTripsAllFields() {
    // token, timestamp, key, value, size
  }

  @Test
  @Disabled("TODO")
  void decode_multipleRecords_inOrder() {}

  @Test
  @Disabled("TODO")
  void decode_emptyKeyOrValue() {}

  @Test
  @Disabled("TODO")
  void decode_flippedByteInPayload_throwsChecksumValidation() {}

  @Test
  @Disabled("TODO")
  void decode_tornHeader_throws() {
    // file ends in the middle of the 28-byte header
  }

  @Test
  @Disabled("TODO")
  void decode_tornPayload_throws() {
    // header complete, key/value cut short
  }

  @Test
  @Disabled("TODO")
  void decode_negativeKeySize_isRejectedBeforeAllocate() {
    // write a header by hand with keySize = -1 (valid CRC or not): must not throw IllegalArgumentException
  }

  @Test
  @Disabled("TODO")
  void decode_keySizeAboveLimit_isRejected() {}

  @Test
  @Disabled("TODO")
  void hasNext_falseExactlyAtEndOfFile() {}
}
