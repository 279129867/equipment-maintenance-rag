package com.eqmaint.inspection;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("eq_inspection_record")
public class EqInspectionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;
    private Long assetId;
    private LocalDate recordDate;
    private String inspector;
    private Integer result;
    private String abnormalDesc;
    private Integer costMinutes;
    private LocalDateTime submitTime;
    private LocalDateTime createTime;
}