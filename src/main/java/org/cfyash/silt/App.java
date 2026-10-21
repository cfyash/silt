package org.cfyash.silt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.cfyash.silt.services.Database;

@Slf4j
public class App {

  static void main() throws IOException {
    log.info("starting application silt...");
    Database database = new Database("data");
    database.write("yellow", "hello".getBytes(StandardCharsets.UTF_8));
  }
}
