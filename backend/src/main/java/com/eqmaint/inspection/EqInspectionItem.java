package com.eqmaint.inspection;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("eq_inspection_item")
public class EqInspectionItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;
    private Integer seqNo;
    private String itemName;
    private Integer checkMethod;
    private String standardText;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String unit;
    private Integer requiredPhoto;
}