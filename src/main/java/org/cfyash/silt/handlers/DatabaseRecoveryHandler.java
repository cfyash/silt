package org.cfyash.silt.handlers;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cfyash.silt.common.exceptions.*;
import org.cfyash.silt.models.MemKey;
import org.cfyash.silt.models.MemValue;
import org.cfyash.silt.models.RecordType;
import org.cfyash.silt.models.WALRecord;

@RequiredArgsConstructor
@Slf4j
public class DatabaseRecoveryHandler {
  private final WALRecordHandler walRecordHandler;
  private final MemtableHandler memtableHandler;

  public long recover() {
    long offset = 0;
    WALRecord record;
    long maxToken = 0;
    try {
      while (walRecordHandler.hasNext(offset)) {
        record = walRecordHandler.decodeNext(offset);
        offset += record.size();
        maxToken = Math.max(record.token(), maxToken);
        replayToMemtable(record);
      }
    } catch (NegativeOffsetException e) {
      throw new WALReadException("unable to built in memory memtable");
    } catch (ChecksumValidationException
        | EOFException
        | RecordLimitException
        | UnexpectedEOFException
        | UnknownRecordTypeException e) {
      log.warn("error while reading wal file, might be a torn record, truncating WAL...");
      this.walRecordHandler.truncateWAL(offset);
      return maxToken;
    }
    return maxToken;
  }

  private void replayToMemtable(@NonNull WALRecord record) {
    memtableHandler.replay(
        MemKey.builder().key(record.key()).token(record.token()).build(),
        MemValue.builder()
            .value(record.value())
            .isDelete(record.type() == RecordType.DELETE)
            .build());
  }
}
