package com.eqmaint.workorder;

import com.eqmaint.common.BizException;

public enum OrderStatus {

    REPORTED(10, "待派单"),
    WAIT_ACCEPT(20, "待接单"),
    REPAIRING(30, "维修中"),
    SUSPENDED(35, "挂起"),
    WAIT_ACCEPTANCE(40, "待验收"),
    CLOSED(50, "已关闭"),
    REJECTED(60, "已驳回"),
    CANCELED(70, "已取消");

    private final int code;
    private final String label;

    OrderStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static OrderStatus of(Integer code) {
        if (code == null) {
            throw new BizException("工单状态为空");
        }
        for (OrderStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new BizException("未知工单状态：" + code);
    }
}