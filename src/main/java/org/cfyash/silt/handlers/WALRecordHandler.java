package org.cfyash.silt.handlers;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.cfyash.silt.common.exceptions.*;
import org.cfyash.silt.common.utils.CRC32Utils;
import org.cfyash.silt.config.DatabaseConfig;
import org.cfyash.silt.entities.WALFile;
import org.cfyash.silt.models.RecordType;
import org.cfyash.silt.models.WALRecord;

@RequiredArgsConstructor
public class WALRecordHandler implements AutoCloseable {

  private static final int HEADER_LENGTH = 29;

  private final WALFile walFile;

  public WALRecord encodeAndWrite(
      long token, long timestamp, byte[] key, byte[] value, RecordType type) {
    ByteBuffer recordBuf =
        ByteBuffer.allocate(HEADER_LENGTH + key.length + value.length)
            .order(ByteOrder.LITTLE_ENDIAN);
    recordBuf.position(4);
    recordBuf.putLong(token);
    recordBuf.putLong(timestamp);
    recordBuf.put(type.getCode());
    recordBuf.putInt(key.length);
    recordBuf.putInt(value.length);
    recordBuf.put(key);
    recordBuf.put(value);
    int checksum = CRC32Utils.generateCheckSum(recordBuf.slice(4, recordBuf.limit() - 4));
    recordBuf.putInt(0, checksum);
    recordBuf.rewind();
    var record =
        WALRecord.builder()
            .checksum(checksum)
            .token(token)
            .timestamp(timestamp)
            .keySize(key.length)
            .valueSize(value.length)
            .key(key)
            .value(value)
            .size(recordBuf.limit())
            .type(type)
            .build();
    walFile.write(recordBuf);
    return record;
  }

  public WALRecord decodeNext(long offset) {
    if (offset < 0) {
      throw new NegativeOffsetException("offset value is negative");
    }
    if (offset > walFile.size()) {
      throw new EOFException("reached end of file");
    }
    ByteBuffer header = ByteBuffer.allocate(HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
    int readBytes = walFile.read(offset, header);
    if (readBytes != header.limit()) {
      throw new UnexpectedEOFException("torn record could not read complete header");
    }
    header.position(0);
    int checksum = header.getInt();
    long token = header.getLong();
    long timestamp = header.getLong();
    RecordType type = RecordType.fromCode(header.get());
    int keySize = header.getInt();
    int valueSize = header.getInt();
    if (keySize < 0 || valueSize < 0) {
      throw new RecordLimitException("key or value size is negative");
    }
    if ((long) keySize > DatabaseConfig.KEY_LIMIT
        || (long) valueSize > DatabaseConfig.VALUE_LIMIT) {
      throw new RecordLimitException("key or value size found greater than allowed value");
    }
    if (offset + (long) HEADER_LENGTH + (long) keySize + (long) valueSize > walFile.size()) {
      throw new RecordLimitException("read record length greater than WAL file size");
    }

    ByteBuffer body = ByteBuffer.allocate(keySize + valueSize).order(ByteOrder.LITTLE_ENDIAN);
    readBytes = walFile.read(offset + HEADER_LENGTH, body);
    if (readBytes != body.limit()) {
      throw new UnexpectedEOFException("torn record could not read complete body");
    }
    body.rewind();
    int calculatedChecksum =
        CRC32Utils.generateCheckSum(List.of(header.slice(4, header.limit() - 4), body));
    if (checksum != calculatedChecksum) {
      throw new ChecksumValidationException(
          "record corruption: calculated checksum didn't match recorded checksum");
    }
    byte[] key = new byte[keySize];
    byte[] value = new byte[valueSize];
    body.get(0, key);
    body.get(keySize, value);
    return WALRecord.builder()
        .checksum(checksum)
        .token(token)
        .timestamp(timestamp)
        .keySize(keySize)
        .valueSize(valueSize)
        .key(key)
        .value(value)
        .size(header.limit() + body.limit())
        .type(type)
        .build();
  }

  public boolean hasNext(long offset) {
    if (offset < 0) {
      throw new NegativeOffsetException("offset value is negative");
    }
    return offset < walFile.size();
  }

  public void truncateWAL(long offset) {
    walFile.truncate(offset);
  }

  public void syncToDisk() {
    walFile.fsync();
  }

  @Override
  public void close() throws Exception {
    walFile.close();
  }
}
