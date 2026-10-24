package org.cfyash.silt.models;

import lombok.Builder;

@Builder
public record MemValue(boolean isDelete, byte[] value) {}
