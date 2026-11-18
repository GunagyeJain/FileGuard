package com.fileguard.model;

/**
 * A single row shown in a result table and stored in the database.
 *
 * @param status  what was detected
 * @param path    relative path of the file
 * @param detail  human readable explanation
 * @param size    file size in bytes (0 if unknown)
 * @param group   duplicate group number, 0 when not used
 */
public record ResultItem(ResultStatus status, String path, String detail, long size, int group) {

    public ResultItem(ResultStatus status, String path, String detail, long size) {
        this(status, path, detail, size, 0);
    }
}
