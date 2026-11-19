package com.eqmaint.stats;

public class WorkshopStat {
    private String workshop;
    private Integer orderCount;
    private Integer assetCount;
    private Integer downtimeMinutes;
    private Integer mttrMinutes;
    private Integer responseMinutes;

    public String getWorkshop() { return workshop; }
    public void setWorkshop(String workshop) { this.workshop = workshop; }
    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public Integer getAssetCount() { return assetCount; }
    public void setAssetCount(Integer assetCount) { this.assetCount = assetCount; }
    public Integer getDowntimeMinutes() { return downtimeMinutes; }
    public void setDowntimeMinutes(Integer downtimeMinutes) { this.downtimeMinutes = downtimeMinutes; }
    public Integer getMttrMinutes() { return mttrMinutes; }
    public void setMttrMinutes(Integer mttrMinutes) { this.mttrMinutes = mttrMinutes; }
    public Integer getResponseMinutes() { return responseMinutes; }
    public void setResponseMinutes(Integer responseMinutes) { this.responseMinutes = responseMinutes; }
}