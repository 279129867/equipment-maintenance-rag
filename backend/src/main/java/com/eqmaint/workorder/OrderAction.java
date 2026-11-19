package com.eqmaint.workorder;

public enum OrderAction {

    DISPATCH("派单"),
    ACCEPT("接单"),
    SUSPEND("挂起"),
    RESUME("恢复"),
    FINISH("完工"),
    PASS("验收通过"),
    REWORK("验收返修"),
    CANCEL("取消"),
    REJECT("驳回");

    private final String label;

    OrderAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}