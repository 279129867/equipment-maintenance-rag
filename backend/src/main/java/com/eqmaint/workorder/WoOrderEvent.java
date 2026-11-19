package com.eqmaint.workorder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wo_order_event")
public class WoOrderEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Integer fromStatus;
    private Integer toStatus;
    private String action;
    private String operator;
    private String remark;
    private LocalDateTime occurTime;
}