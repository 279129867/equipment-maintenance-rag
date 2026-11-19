package com.eqmaint.stats;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StatsMapper {

    OverviewStat overview(@Param("from") String from, @Param("to") String to);

    List<AssetStat> assetStats(@Param("from") String from, @Param("to") String to,
                               @Param("workshop") String workshop);

    List<WorkshopStat> workshopStats(@Param("from") String from, @Param("to") String to);

    List<FaultTypeStat> faultTypeStats(@Param("from") String from, @Param("to") String to);

    List<TrendPoint> trend(@Param("from") String from, @Param("to") String to);
}