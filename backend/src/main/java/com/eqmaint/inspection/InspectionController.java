package com.eqmaint.inspection;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eqmaint.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inspection")
public class InspectionController {

    private final InspectionService service;

    public InspectionController(InspectionService service) {
        this.service = service;
    }

    @PostMapping("/plans")
    public R<InspectionPlanDetail> createPlan(@RequestBody PlanCreateReq req) {
        return R.ok(service.createPlan(req));
    }

    @GetMapping("/plans")
    public R<List<InspectionPlanDetail>> listPlans(@RequestParam(required = false) Long assetId,
                                                   @RequestParam(required = false) Integer enabled) {
        return R.ok(service.listPlans(assetId, enabled));
    }

    @GetMapping("/plans/{id}")
    public R<InspectionPlanDetail> planDetail(@PathVariable Long id) {
        return R.ok(service.planDetail(id));
    }

    @PostMapping("/records")
    public R<SubmitResult> submit(@RequestBody RecordSubmitReq req) {
        return R.ok(service.submit(req));
    }

    @GetMapping("/records")
    public R<Page<EqInspectionRecord>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) Long assetId,
                                            @RequestParam(required = false) Integer result) {
        return R.ok(service.page(page, size, assetId, result));
    }

    @GetMapping("/records/{id}")
    public R<InspectionRecordDetail> recordDetail(@PathVariable Long id) {
        return R.ok(service.recordDetail(id));
    }
}