package com.eqmaint.stats;

import com.eqmaint.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public R<OverviewStat> overview(@RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to) {
        return R.ok(service.overview(from, to));
    }

    @GetMapping("/assets")
    public R<List<AssetStat>> assets(@RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to,
                                     @RequestParam(required = false) String workshop) {
        return R.ok(service.assets(from, to, workshop));
    }

    @GetMapping("/workshops")
    public R<List<WorkshopStat>> workshops(@RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        return R.ok(service.workshops(from, to));
    }

    @GetMapping("/fault-types")
    public R<List<FaultTypeStat>> faultTypes(@RequestParam(required = false) String from,
                                             @RequestParam(required = false) String to) {
        return R.ok(service.faultTypes(from, to));
    }

    @GetMapping("/trend")
    public R<List<TrendPoint>> trend(@RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to) {
        return R.ok(service.trend(from, to));
    }
}