package com.eqmaint.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return new R<>(400, e.getMessage(), null);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("duplicate key: {}", e.getMessage());
        return new R<>(409, "数据已存在（编号/哈希重复）", null);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValid(Exception e) {
        FieldError fe = null;
        if (e instanceof MethodArgumentNotValidException m) {
            fe = m.getBindingResult().getFieldError();
        } else if (e instanceof BindException b) {
            fe = b.getBindingResult().getFieldError();
        }
        String msg = fe == null ? "参数校验失败" : fe.getField() + " " + fe.getDefaultMessage();
        return new R<>(400, msg, null);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("unexpected error", e);
        return new R<>(500, "系统异常：" + e.getMessage(), null);
    }
}