package io;

/** Una linea del archivo de requerimientos: en que momento y que comando. */
public record ScheduledCommand(long atMs, String command, int lineNumber) {
}
