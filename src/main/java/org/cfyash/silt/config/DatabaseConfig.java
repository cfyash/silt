package org.cfyash.silt.config;

public class DatabaseConfig {
  public static final int KEY_LIMIT = 100;
  public static final int VALUE_LIMIT = 100;
  public static final int SYNC_LIMIT = 10;
  public static final long LINGER_TIME_MS = 1000;
  public static final String WAL_FILENAME = "0001.log";

  private DatabaseConfig() {}
}
