package org.cfyash.silt.models;

import lombok.Builder;

@Builder
public record WALRecord(
    int checksum,
    long token,
    long timestamp,
    int keySize,
    int valueSize,
    byte[] key,
    byte[] value,
    long size,
    RecordType type) {}
