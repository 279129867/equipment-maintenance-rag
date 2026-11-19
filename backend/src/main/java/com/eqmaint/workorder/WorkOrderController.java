package com.eqmaint.workorder;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eqmaint.common.BizException;
import com.eqmaint.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workorders")
public class WorkOrderController {

    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    @PostMapping
    public R<WoOrder> create(@RequestBody CreateOrderReq req) {
        return R.ok(service.create(req.assetId(), req.faultType(), req.faultDesc(),
                req.urgency(), req.reporter()));
    }

    @PostMapping("/{id}/action")
    public R<WoOrder> action(@PathVariable Long id, @RequestBody ActionReq req) {
        OrderAction action;
        try {
            action = OrderAction.valueOf(req.action() == null ? "" : req.action().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("未知动作：" + req.action() + "（可选 DISPATCH/ACCEPT/SUSPEND/RESUME/FINISH/PASS/REWORK/CANCEL/REJECT）");
        }
        return R.ok(service.transition(id, action, req.operator(), req.remark()));
    }

    @GetMapping
    public R<Page<WoOrder>> page(@RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "10") int size,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) Long assetId) {
        return R.ok(service.page(page, size, status, assetId));
    }

    @GetMapping("/{id}")
    public R<WorkOrderDetail> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }
}