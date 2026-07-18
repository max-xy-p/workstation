package com.mike.workstation.service;

import com.mike.workstation.exception.BusinessException;
import java.util.Collection;

final class RequestChecks {
    private RequestChecks() {
    }

    static void requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(fieldName + "不能为空");
        }
    }

    static void requiredObject(Object value, String fieldName) {
        if (value == null) {
            throw new BusinessException(fieldName + "不能为空");
        }
    }

    static <T> void requiredExisting(T value, String message) {
        if (value == null) {
            throw new com.mike.workstation.exception.NotFoundException(message);
        }
    }

    static <T> boolean hasItems(Collection<T> values) {
        return values != null && !values.isEmpty();
    }
}
