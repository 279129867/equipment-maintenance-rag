package com.eqmaint.inspection;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("eq_inspection_plan")
public class EqInspectionPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "计划编号不能为空")
    private String planCode;

    @NotBlank(message = "计划名称不能为空")
    private String planName;

    @NotNull(message = "设备不能为空")
    private Long assetId;

    private Integer cycleType;
    private String executorRole;
    private Integer enabled;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}