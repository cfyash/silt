package org.cfyash.silt.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.cfyash.silt.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DatabaseReadTest {

  @TempDir Path dir;
  Database db;

  // ---------- input validation ----------
  @BeforeEach
  void setUp() throws IOException {
    db = new Database(dir.toUri().getPath());
  }

  @Test
  void read_succeedsAfterWrite() {
    var k = TestUtils.generateByteArr(10);
    var v = TestUtils.generateByteArr(10);
    db.write(k, v);
    var readV = db.get(k);
    assertArrayEquals(v, readV);
  }

  @Test
  void overwrite_returnsLatestValue() {
    var k = TestUtils.generateByteArr(10);
    var v1 = TestUtils.generateByteArr(10);
    db.write(k, v1);
    var v2 = TestUtils.generateByteArr(10);
    db.write(k, v2);
    var read = db.get(k);
    assertArrayEquals(v2, read);
  }

  @Test
  void read_afterDelete_getsANull() {
    var k = TestUtils.generateByteArr(10);
    var v = TestUtils.generateByteArr(10);
    db.write(k, v);
    db.delete(k);
    var read = db.get(k);
    assertNull(read);
  }

  @Test
  void read_afterClose_readsCorrectValue() throws Exception {
    var k = TestUtils.generateByteArr(10);
    var v = TestUtils.generateByteArr(10);
    db.write(k, v);
    db.close();
    db = new Database(dir.toUri().getPath());
    var read = db.get(k);
    assertArrayEquals(v, read);
  }

  @Test
  void read_missingKeyInMiddle_returnNull() {
    var k1 = "a".getBytes(StandardCharsets.UTF_8);
    var k2 = "c".getBytes(StandardCharsets.UTF_8);
    var v = TestUtils.generateByteArr(10);
    db.write(k1, v);
    db.write(k2, v);
    var read = db.get("b".getBytes(StandardCharsets.UTF_8));
    assertNull(read);
  }
}
