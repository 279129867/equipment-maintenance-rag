package com.eqmaint.stats;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Service
public class StatsService {

    private final StatsMapper mapper;

    public StatsService(StatsMapper mapper) {
        this.mapper = mapper;
    }

    public OverviewStat overview(String from, String to) {
        return mapper.overview(defFrom(from), defTo(to));
    }

    public List<AssetStat> assets(String from, String to, String workshop) {
        return mapper.assetStats(defFrom(from), defTo(to), workshop);
    }

    public List<WorkshopStat> workshops(String from, String to) {
        return mapper.workshopStats(defFrom(from), defTo(to));
    }

    public List<FaultTypeStat> faultTypes(String from, String to) {
        return mapper.faultTypeStats(defFrom(from), defTo(to));
    }

    public List<TrendPoint> trend(String from, String to) {
        return mapper.trend(defFrom(from), defTo(to));
    }

    private String defFrom(String from) {
        return StringUtils.hasText(from) ? from : LocalDate.now().minusDays(29).toString();
    }

    private String defTo(String to) {
        return StringUtils.hasText(to) ? to : LocalDate.now().toString();
    }
}