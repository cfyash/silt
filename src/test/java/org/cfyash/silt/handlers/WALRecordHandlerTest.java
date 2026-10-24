package org.cfyash.silt.handlers;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.cfyash.silt.TestUtils;
import org.cfyash.silt.common.exceptions.ChecksumValidationException;
import org.cfyash.silt.common.exceptions.RecordLimitException;
import org.cfyash.silt.common.exceptions.UnexpectedEOFException;
import org.cfyash.silt.config.DatabaseConfig;
import org.cfyash.silt.entities.WALFile;
import org.cfyash.silt.models.WALRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("ResultOfMethodCallIgnored")
class WALRecordHandlerTest {

  @TempDir Path dir;

  WALRecordHandler handler;

  @BeforeEach
  void setUp() {
    var filePath = Path.of(dir.toUri().getPath(), "0001.log");
    handler = new WALRecordHandler(new WALFile(filePath));
  }

  @Test
  void encode_thenDecode_roundTripsAllFields() {
    var token = TestUtils.generateToken();
    var timestamp = TestUtils.generateTimestamp();
    var key = TestUtils.generateString(1000);
    var value = TestUtils.generateByteArr(1000);
    WALRecord record =
        handler.encodeAndWrite(token, timestamp, key.getBytes(StandardCharsets.UTF_8), value);

    assertEquals(token, record.token());
    assertEquals(timestamp, record.timestamp());
    assertArrayEquals(key.getBytes(StandardCharsets.UTF_8), record.key());
    assertArrayEquals(value, record.value());

    assertTrue(handler.hasNext(0));
    WALRecord readRecord = handler.decodeNext(0);

    assertRecordEquality(record, readRecord);
  }

  @Test
  void decode_multipleRecords_inOrder() {
    var records = generateContent();
    long offset = 0;
    var readRecords = new ArrayList<WALRecord>();
    while (handler.hasNext(offset)) {
      WALRecord readRecord = handler.decodeNext(offset);
      offset += readRecord.size();
      readRecords.add(readRecord);
    }
    assertEquals(records.size(), readRecords.size());
    for (int i = 0; i < records.size(); i++) {
      assertRecordEquality(records.get(i), readRecords.get(i));
    }
  }

  @Test
  void decode_emptyKeyOrValue() {
    var records = generateContent(new int[] {100, 1000}, new int[] {0, 1000}, new int[] {0, 0});
    records.addAll(generateContent(new int[] {100, 1000}, new int[] {0, 0}, new int[] {0, 1000}));
    records.addAll(generateContent(new int[] {100, 10000}, new int[] {0, 0}, new int[] {0, 0}));
    var readRecords = new ArrayList<WALRecord>();
    long offset = 0;
    while (handler.hasNext(offset)) {
      WALRecord readRecord = handler.decodeNext(offset);
      readRecords.add(readRecord);
      offset += readRecord.size();
    }
    assertEquals(records.size(), readRecords.size());
    for (int i = 0; i < records.size(); i++) {
      assertRecordEquality(records.get(i), readRecords.get(i));
    }
  }

  @RepeatedTest(100)
  void decode_flippedByteInPayload_throwsChecksumValidation() throws IOException {
    generateContent();
    Path logPath = Path.of(dir.toUri().getPath(), "0001.log");
    try (FileChannel fileChannel =
        FileChannel.open(logPath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
      long targetPosition = TestUtils.generateIntInRange(4, 19);
      ByteBuffer buffer = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN);
      fileChannel.read(buffer, targetPosition);
      buffer.rewind();
      byte originalByte = buffer.get();
      byte corruptedByte = (byte) (originalByte ^ 0xFF);
      buffer.clear();
      buffer.put(corruptedByte);
      buffer.rewind();
      fileChannel.write(buffer, targetPosition);
    }
    assertThrows(
        ChecksumValidationException.class,
        () -> {
          long offset = 0;
          while (handler.hasNext(offset)) {
            WALRecord readRecord = handler.decodeNext(offset);
            offset += readRecord.size();
          }
        });
  }

  @RepeatedTest(100)
  void decode_tornHeader_throws() throws IOException {
    generateContent(new int[] {1, 1}, new int[] {1, 1}, new int[] {1, 1});
    FileChannel fileChannel =
        FileChannel.open(Path.of(dir.toUri().getPath(), "0001.log"), StandardOpenOption.WRITE);
    fileChannel.truncate(TestUtils.generateIntInRange(1, 27));
    fileChannel.close();
    assertThrows(
        UnexpectedEOFException.class,
        () -> {
          long offset = 0;
          while (handler.hasNext(offset)) {
            WALRecord readRecord = handler.decodeNext(offset);
            offset += readRecord.size();
          }
        });
  }

  @RepeatedTest(100)
  void decode_tornPayload_throws() throws IOException {
    var records = generateContent(new int[] {1, 1}, new int[] {1, 1}, new int[] {1, 1});
    var filePath = Path.of(dir.toUri().getPath(), "0001.log");
    FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.WRITE);
    int keyStart = 28;
    int valueEnd = 28 + records.getFirst().keySize() + records.getFirst().valueSize() - 1;
    fileChannel.truncate(TestUtils.generateIntInRange(keyStart, valueEnd));
    fileChannel.close();
    var handler2 = new WALRecordHandler(new WALFile(filePath));
    assertThrows(
        RecordLimitException.class,
        () -> {
          long offset = 0;
          while (handler2.hasNext(offset)) {
            WALRecord readRecord = handler2.decodeNext(offset);
            offset += readRecord.size();
          }
        });
  }

  @Test
  void decode_negativeKeySize_isRejectedBeforeAllocate() throws IOException {
    String key = TestUtils.generateString(TestUtils.generateIntInRange(0, 500));
    byte[] value = TestUtils.generateByteArr(TestUtils.generateIntInRange(0, 500));
    long token = TestUtils.generateToken();
    long timestamp = TestUtils.generateTimestamp();
    handler.encodeAndWrite(token, timestamp, key.getBytes(StandardCharsets.UTF_8), value);
    FileChannel fileChannel =
        FileChannel.open(Path.of(dir.toUri().getPath(), "0001.log"), StandardOpenOption.WRITE);
    ByteBuffer bf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
    bf.putInt(-1);
    bf.rewind();
    fileChannel.write(bf, 20);
    fileChannel.close();
    assertThrows(
        RecordLimitException.class,
        () -> {
          long offset = 0;
          while (handler.hasNext(offset)) {
            WALRecord readRecord = handler.decodeNext(offset);
            offset += readRecord.size();
          }
        });
  }

  @Test
  void decode_keySizeAboveLimit_isRejected() throws IOException {
    String key = TestUtils.generateString(TestUtils.generateIntInRange(0, 500));
    byte[] value = TestUtils.generateByteArr(TestUtils.generateIntInRange(0, 500));
    long token = TestUtils.generateToken();
    long timestamp = TestUtils.generateTimestamp();
    handler.encodeAndWrite(token, timestamp, key.getBytes(StandardCharsets.UTF_8), value);
    FileChannel fileChannel =
        FileChannel.open(Path.of(dir.toUri().getPath(), "0001.log"), StandardOpenOption.WRITE);
    ByteBuffer bf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
    bf.putInt((int) DatabaseConfig.KEY_LIMIT + 1);
    bf.rewind();
    fileChannel.write(bf, 20);
    fileChannel.close();
    assertThrows(
        RecordLimitException.class,
        () -> {
          long offset = 0;
          while (handler.hasNext(offset)) {
            WALRecord readRecord = handler.decodeNext(offset);
            offset += readRecord.size();
          }
        });
  }

  @Test
  void decode_negativeValueSize_isRejectedBeforeAllocate() throws IOException {
    String key = TestUtils.generateString(TestUtils.generateIntInRange(0, 500));
    byte[] value = TestUtils.generateByteArr(TestUtils.generateIntInRange(0, 500));
    long token = TestUtils.generateToken();
    long timestamp = TestUtils.generateTimestamp();
    handler.encodeAndWrite(token, timestamp, key.getBytes(StandardCharsets.UTF_8), value);
    FileChannel fileChannel =
        FileChannel.open(Path.of(dir.toUri().getPath(), "0001.log"), StandardOpenOption.WRITE);
    ByteBuffer bf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
    bf.putInt(-1);
    bf.rewind();
    fileChannel.write(bf, 24);
    fileChannel.close();
    assertThrows(
        RecordLimitException.class,
        () -> {
          long offset = 0;
          while (handler.hasNext(offset)) {
            WALRecord readRecord = handler.decodeNext(offset);
            offset += readRecord.size();
          }
        });
  }

  @Test
  void decode_ValueSizeAboveLimit_isRejected() throws IOException {
    String key = TestUtils.generateString(TestUtils.generateIntInRange(0, 500));
    byte[] value = TestUtils.generateByteArr(TestUtils.generateIntInRange(0, 500));
    long token = TestUtils.generateToken();
    long timestamp = TestUtils.generateTimestamp();
    handler.encodeAndWrite(token, timestamp, key.getBytes(StandardCharsets.UTF_8), value);
    FileChannel fileChannel =
        FileChannel.open(Path.of(dir.toUri().getPath(), "0001.log"), StandardOpenOption.WRITE);
    ByteBuffer bf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
    bf.putInt((int) DatabaseConfig.KEY_LIMIT + 1);
    bf.rewind();
    fileChannel.write(bf, 24);
    fileChannel.close();
    assertThrows(
        RecordLimitException.class,
        () -> {
          long offset = 0;
          while (handler.hasNext(offset)) {
            WALRecord readRecord = handler.decodeNext(offset);
            offset += readRecord.size();
          }
        });
  }

  @RepeatedTest(100)
  void decode_readReturnsShort_throws() throws IOException {
    var records = generateContent(new int[] {1, 1}, new int[] {50, 50}, new int[] {50, 50});
    var filePath = Path.of(dir.toUri().getPath(), "0001.log");
    FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.WRITE);
    int keyStart = 28;
    int valueEnd = 28 + records.getFirst().keySize() + records.getFirst().valueSize() - 1;
    fileChannel.truncate(TestUtils.generateIntInRange(keyStart, valueEnd));
    fileChannel.close();
    assertThrows(
        UnexpectedEOFException.class,
        () -> {
          long offset = 0;
          while (handler.hasNext(offset)) {
            WALRecord readRecord = handler.decodeNext(offset);
            offset += readRecord.size();
          }
        });
  }

  @RepeatedTest(10)
  void hasNext_falseExactlyAtEndOfFile() {
    String key = TestUtils.generateString(TestUtils.generateIntInRange(0, 500));
    byte[] value = TestUtils.generateByteArr(TestUtils.generateIntInRange(0, 500));
    long token = TestUtils.generateToken();
    long timestamp = TestUtils.generateTimestamp();
    int recordLength = 28 + key.length() + value.length;
    handler.encodeAndWrite(token, timestamp, key.getBytes(StandardCharsets.UTF_8), value);
    assertFalse(handler.hasNext(recordLength));
  }

  private List<WALRecord> generateContent(int[] numRecordsRange, int[] keyRange, int[] valueRange) {
    List<WALRecord> items = new ArrayList<>();
    int n = TestUtils.generateIntInRange(numRecordsRange[0], numRecordsRange[1]);
    for (int i = 0; i < n; i++) {
      var token = TestUtils.generateToken();
      var timestamp = TestUtils.generateTimestamp();
      var key = TestUtils.generateString(TestUtils.generateIntInRange(keyRange[0], keyRange[1]));
      var value =
          TestUtils.generateByteArr(TestUtils.generateIntInRange(valueRange[0], valueRange[1]));
      items.add(
          handler.encodeAndWrite(token, timestamp, key.getBytes(StandardCharsets.UTF_8), value));
    }
    return items;
  }

  private List<WALRecord> generateContent() {
    return generateContent(new int[] {100, 1000}, new int[] {100, 1000}, new int[] {100, 1000});
  }

  private void assertRecordEquality(WALRecord a, WALRecord b) {
    assertEquals(a.size(), b.size());
    assertEquals(a.checksum(), b.checksum());
    assertEquals(a.token(), b.token());
    assertEquals(a.timestamp(), b.timestamp());
    assertEquals(a.keySize(), b.keySize());
    assertEquals(a.valueSize(), b.valueSize());
    assertArrayEquals(a.key(), b.key());
    assertArrayEquals(a.value(), b.value());
  }
}
