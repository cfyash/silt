package org.cfyash.silt.config;

public class DatabaseConfig {
  public static final long KEY_LIMIT = 1_048_576L;
  public static final long VALUE_LIMIT = 1_048_576L;
  public static final int SYNC_LIMIT = 10;
  public static final long LINGER_TIME_MS = 1000;
  public static final String WAL_FILENAME = "0001.log";

  private DatabaseConfig() {}
}
