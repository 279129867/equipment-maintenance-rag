package com.eqmaint.asset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("eq_asset")
public class EqAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "设备编号不能为空")
    private String assetCode;

    @NotBlank(message = "设备名称不能为空")
    private String assetName;

    @NotBlank(message = "设备类型不能为空")
    private String assetType;

    private String workshop;
    private String lineName;
    private String location;
    private String manufacturer;
    private String model;
    private String serialNo;
    private LocalDate installDate;
    private Integer criticalLevel;
    private Integer runStatus;
    private String ownerName;
    private String remark;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}