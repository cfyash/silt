package org.cfyash.silt.entities;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.cfyash.silt.common.exceptions.WALReadException;
import org.cfyash.silt.common.exceptions.WALSyncException;
import org.cfyash.silt.common.exceptions.WALWriteException;

public class WALFile implements AutoCloseable {

  private final FileChannel channel;
  private long channelSize;

  public WALFile(Path filePath) {
    try {
      this.channel =
          FileChannel.open(
              filePath,
              StandardOpenOption.CREATE,
              StandardOpenOption.READ,
              StandardOpenOption.WRITE);
      this.channelSize = this.channel.size();
      this.channel.position(this.channelSize);
    } catch (IOException e) {
      throw new WALReadException("error opening WAL file:" + e.getMessage());
    }
  }

  public void write(ByteBuffer bf) {
    try {
      while (bf.hasRemaining()) {
        int writtenBytes = channel.write(bf);
        this.channelSize += writtenBytes;
      }
    } catch (IOException e) {
      throw new WALWriteException("error in writing to WAL:", e);
    }
  }

  public long size() {
    return this.channelSize;
  }

  public int read(long offset, ByteBuffer bf) {
    try {
      int readBytes = 0;
      while (bf.hasRemaining()) {
        int read = channel.read(bf, offset);
        if (read == -1) {
          return readBytes;
        }
        offset += read;
        readBytes += read;
      }
      return readBytes;
    } catch (IOException e) {
      throw new WALReadException("error in reading WAL: ", e);
    }
  }

  @Override
  public void close() throws Exception {}

  public void truncate(long offset) {
    try {
      channel.truncate(offset);
      this.channelSize = channel.size();
      this.channel.position(channelSize);
    } catch (IOException e) {
      throw new WALWriteException("error in truncating WAL file", e);
    }
  }

  public void fsync() {
    try {
      channel.force(true);
    } catch (IOException e) {
      throw new WALSyncException("Failed to force WAL file to disk", e);
    }
  }
}
