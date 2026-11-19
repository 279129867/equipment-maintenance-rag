package com.eqmaint.inspection;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("eq_inspection_record_item")
public class EqInspectionRecordItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;
    private Long itemId;
    private Integer result;
    private BigDecimal measureValue;
    private String photoUrl;
    private String remark;
}