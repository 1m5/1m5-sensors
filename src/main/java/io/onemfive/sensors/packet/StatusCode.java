package io.onemfive.sensors.packet;

public enum StatusCode {
    OK,
    GENERAL_ERROR,
    NO_DATA_FOUND,
    INVALID_PACKET,
    INVALID_HASHCASH,
    INSUFFICIENT_HASHCASH,
    NO_AVAILABLE_STORAGE
}