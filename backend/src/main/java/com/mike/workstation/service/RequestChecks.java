package com.mike.workstation.service;

import com.mike.workstation.exception.BusinessException;
import java.util.Collection;

final class RequestChecks {
    private RequestChecks() {
    }

    /**
     * 校验必填文本。
     *
     * @param value 待校验文本
     * @param fieldName 字段中文名，用于错误提示
     */
    static void requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(fieldName + "不能为空");
        }
    }

    /**
     * 校验必填对象。
     *
     * @param value 待校验对象
     * @param fieldName 字段中文名，用于错误提示
     */
    static void requiredObject(Object value, String fieldName) {
        if (value == null) {
            throw new BusinessException(fieldName + "不能为空");
        }
    }

    /**
     * 校验关联对象是否存在。
     *
     * @param value 查询出来的对象
     * @param message 不存在时返回给前端的提示
     */
    static <T> void requiredExisting(T value, String message) {
        if (value == null) {
            throw new com.mike.workstation.exception.NotFoundException(message);
        }
    }

    /**
     * 判断集合是否包含有效元素。
     */
    static <T> boolean hasItems(Collection<T> values) {
        return values != null && !values.isEmpty();
    }
}
