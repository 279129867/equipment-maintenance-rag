package com.eqmaint.workorder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wo_order")
public class WoOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long assetId;
    private Integer sourceType;
    private Long sourceRefId;
    private String faultType;
    private String faultDesc;
    private Integer urgency;
    private Integer status;

    private String reporter;
    private LocalDateTime reportTime;

    private String dispatcher;
    private LocalDateTime dispatchTime;

    private String assignee;
    private LocalDateTime acceptTime;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;

    private Integer downtimeMinutes;
    private String repairSummary;

    private String acceptedBy;
    private Integer acceptResult;
    private Integer aiSummaryFlag;

    private Integer version;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}