package com.eqmaint.stats;

public class OverviewStat {
    private Integer orderCount;
    private Integer closedCount;
    private Integer openCount;
    private Integer downtimeMinutes;
    private Integer mttrMinutes;
    private Integer responseMinutes;
    private Integer abnormalRecordCount;
    private Integer inspectionCount;

    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public Integer getClosedCount() { return closedCount; }
    public void setClosedCount(Integer closedCount) { this.closedCount = closedCount; }
    public Integer getOpenCount() { return openCount; }
    public void setOpenCount(Integer openCount) { this.openCount = openCount; }
    public Integer getDowntimeMinutes() { return downtimeMinutes; }
    public void setDowntimeMinutes(Integer downtimeMinutes) { this.downtimeMinutes = downtimeMinutes; }
    public Integer getMttrMinutes() { return mttrMinutes; }
    public void setMttrMinutes(Integer mttrMinutes) { this.mttrMinutes = mttrMinutes; }
    public Integer getResponseMinutes() { return responseMinutes; }
    public void setResponseMinutes(Integer responseMinutes) { this.responseMinutes = responseMinutes; }
    public Integer getAbnormalRecordCount() { return abnormalRecordCount; }
    public void setAbnormalRecordCount(Integer abnormalRecordCount) { this.abnormalRecordCount = abnormalRecordCount; }
    public Integer getInspectionCount() { return inspectionCount; }
    public void setInspectionCount(Integer inspectionCount) { this.inspectionCount = inspectionCount; }
}