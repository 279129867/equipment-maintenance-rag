package com.eqmaint.stats;

import java.time.LocalDateTime;

public class AssetStat {
    private Long assetId;
    private String assetCode;
    private String assetName;
    private String workshop;
    private Integer orderCount;
    private Integer closedCount;
    private Integer downtimeMinutes;
    private Integer mttrMinutes;
    private Integer responseMinutes;
    private LocalDateTime lastReportTime;

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public String getWorkshop() { return workshop; }
    public void setWorkshop(String workshop) { this.workshop = workshop; }
    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public Integer getClosedCount() { return closedCount; }
    public void setClosedCount(Integer closedCount) { this.closedCount = closedCount; }
    public Integer getDowntimeMinutes() { return downtimeMinutes; }
    public void setDowntimeMinutes(Integer downtimeMinutes) { this.downtimeMinutes = downtimeMinutes; }
    public Integer getMttrMinutes() { return mttrMinutes; }
    public void setMttrMinutes(Integer mttrMinutes) { this.mttrMinutes = mttrMinutes; }
    public Integer getResponseMinutes() { return responseMinutes; }
    public void setResponseMinutes(Integer responseMinutes) { this.responseMinutes = responseMinutes; }
    public LocalDateTime getLastReportTime() { return lastReportTime; }
    public void setLastReportTime(LocalDateTime lastReportTime) { this.lastReportTime = lastReportTime; }
}