package org.cfyash.silt.entities;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.cfyash.silt.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WALFileTest {

  @TempDir Path dir;
  WALFile walFile;

  @BeforeEach
  void setUp() {
    var filePath = Path.of(dir.toUri().getPath(), "0001.log");
    walFile = new WALFile(filePath);
  }

  @Test
  void write_thenRead_returnsSameBytes() {
    int randomSize = TestUtils.generateIntInRange(100, 500);
    byte[] w = TestUtils.generateByteArr(randomSize);
    ByteBuffer wbf = ByteBuffer.allocate(w.length);
    wbf.put(w);
    wbf.flip();
    walFile.write(wbf);

    ByteBuffer rbf = ByteBuffer.allocate(w.length + w.length);
    int readBytes = walFile.read(0, rbf);
    assertEquals(w.length, readBytes);

    byte[] r = new byte[readBytes];
    rbf.get(0, r);
    assertArrayEquals(w, r);
  }

  @Test
  void reopen_appendsAtEnd_doesNotOverwrite() throws Exception {
    int randomSize = TestUtils.generateIntInRange(100, 500);
    byte[] a = TestUtils.generateByteArr(randomSize);
    ByteBuffer abf = ByteBuffer.allocate(a.length);
    abf.put(a);
    abf.flip();
    walFile.write(abf);
    walFile.close();

    var filePath = Path.of(dir.toUri().getPath(), "0001.log");
    var walFile2 = new WALFile(filePath);
    int randomSize2 = TestUtils.generateIntInRange(100, 500);
    byte[] b = TestUtils.generateByteArr(randomSize2);
    ByteBuffer bbf = ByteBuffer.allocate(b.length);
    bbf.put(b);
    bbf.flip();
    walFile2.write(bbf);

    byte[] c = new byte[a.length + b.length];
    ByteBuffer cbf = ByteBuffer.allocate(c.length);
    int readBytes = walFile2.read(0, cbf);
    assertEquals(a.length + b.length, readBytes);
    cbf.get(0, c);

    assertArrayEquals(ByteBuffer.allocate(a.length + b.length).put(a).put(b).array(), c);
    walFile2.close();
  }

  @Test
  void read_pastEnd_returnsShortCount() {
    int randomSize = TestUtils.generateIntInRange(100, 500);
    var wbf = ByteBuffer.wrap(TestUtils.generateByteArr(randomSize));
    walFile.write(wbf);
    int randomSize2 = randomSize + TestUtils.generateIntInRange(100, 500);
    var rbf = ByteBuffer.allocate(randomSize2);
    int readBytes = walFile.read(0, rbf);
    assertEquals(randomSize, readBytes);
  }

  @Test
  void truncate_shrinksFile_andNextWriteGoesAtNewEnd() {
    int randomSize = TestUtils.generateIntInRange(100, 500);
    var wbf = ByteBuffer.wrap(TestUtils.generateByteArr(randomSize));
    walFile.write(wbf);
    int truncateOffset = TestUtils.generateIntInRange(1, 99);
    walFile.truncate(truncateOffset);
    var wbf2 = ByteBuffer.wrap(TestUtils.generateByteArr(randomSize));
    walFile.write(wbf2);
    assertEquals(truncateOffset + randomSize, walFile.size());

    var rbf = ByteBuffer.allocate(2 * randomSize);
    int readBytes = walFile.read(0, rbf);
    assertEquals(truncateOffset + randomSize, readBytes);
  }
}
