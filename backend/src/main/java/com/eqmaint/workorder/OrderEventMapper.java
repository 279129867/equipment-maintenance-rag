package com.eqmaint.workorder;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderEventMapper extends BaseMapper<WoOrderEvent> {
}