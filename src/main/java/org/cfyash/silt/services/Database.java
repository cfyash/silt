package org.cfyash.silt.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.cfyash.silt.common.exceptions.*;
import org.cfyash.silt.config.DatabaseConfig;
import org.cfyash.silt.entities.DBLifecycleManager;
import org.cfyash.silt.entities.TokenManager;
import org.cfyash.silt.entities.WALFile;
import org.cfyash.silt.handlers.DatabaseRecoveryHandler;
import org.cfyash.silt.handlers.MemtableHandler;
import org.cfyash.silt.handlers.WALRecordHandler;

@Slf4j
public class Database implements AutoCloseable {
  private final DBLifecycleManager lifecycleManager;
  private final WritePipeline writePipeline;
  private final ReadPipeline readPipeline;
  private final SyncCoordinator syncCoordinator;

  public Database(String dir) throws IOException {
    Files.createDirectories(Path.of(dir));
    this(
        new WALFile(Path.of(dir, DatabaseConfig.WAL_FILENAME)),
        DatabaseConfig.LINGER_TIME_MS,
        DatabaseConfig.SYNC_LIMIT,
        DatabaseConfig.MEMTABLE_FREEZE_LIMIT);
  }

  Database(WALFile walFile, final long lingerMS, int syncLimit, int memtableFreezeLimit) {
    this.lifecycleManager = new DBLifecycleManager();

    var walHandler = new WALRecordHandler(walFile);
    var memtableHandler = new MemtableHandler(memtableFreezeLimit);

    var recoveryHandler = new DatabaseRecoveryHandler(walHandler, memtableHandler);
    long latestToken = recoveryHandler.recover();

    var tokenManager = new TokenManager(latestToken);

    this.syncCoordinator =
        new SyncCoordinator(lifecycleManager, tokenManager, walHandler, syncLimit, lingerMS);

    this.writePipeline =
        new WritePipeline(
            syncCoordinator, walHandler, memtableHandler, tokenManager, lifecycleManager);
    this.readPipeline = new ReadPipeline(memtableHandler, lifecycleManager);
  }

  public void write(byte[] key, byte[] value) {
    writePipeline.write(key, value);
  }

  public void delete(byte[] key) {
    writePipeline.delete(key);
  }

  public byte[] get(byte[] key) {
    return readPipeline.get(key);
  }

  @Override
  public void close() throws Exception {
    syncCoordinator.close();
    writePipeline.close();
  }
}
