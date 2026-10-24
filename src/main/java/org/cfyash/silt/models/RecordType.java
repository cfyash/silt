package org.cfyash.silt.models;

import org.cfyash.silt.common.exceptions.UnknownRecordTypeException;

public enum RecordType {
  PUT((byte) 0),
  DELETE((byte) 1);
  final byte code;

  RecordType(byte code) {
    this.code = code;
  }

  public static RecordType fromCode(byte b) {
    return switch (b) {
      case 0 -> PUT;
      case 1 -> DELETE;
      default -> throw new UnknownRecordTypeException("unknown record type: " + b);
    };
  }

  public byte getCode() {
    return this.code;
  }
}
