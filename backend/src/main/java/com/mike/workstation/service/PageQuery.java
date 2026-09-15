package com.mike.workstation.service;

record PageQuery(int page, int size, long offset) {
    static PageQuery of(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("页码不能小于 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("每页数量必须在 1 到 100 之间");
        }
        return new PageQuery(page, size, (long) page * size);
    }
}
