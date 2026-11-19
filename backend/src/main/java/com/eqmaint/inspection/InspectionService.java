package com.eqmaint.inspection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eqmaint.asset.AssetMapper;
import com.eqmaint.asset.EqAsset;
import com.eqmaint.common.BizException;
import com.eqmaint.workorder.OrderMapper;
import com.eqmaint.workorder.WoOrder;
import com.eqmaint.workorder.WorkOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Service
public class InspectionService {

    private final InspectionPlanMapper planMapper;
    private final InspectionItemMapper itemMapper;
    private final InspectionRecordMapper recordMapper;
    private final InspectionRecordItemMapper recordItemMapper;
    private final AssetMapper assetMapper;
    private final OrderMapper orderMapper;
    private final WorkOrderService workOrderService;

    public InspectionService(InspectionPlanMapper planMapper,
                             InspectionItemMapper itemMapper,
                             InspectionRecordMapper recordMapper,
                             InspectionRecordItemMapper recordItemMapper,
                             AssetMapper assetMapper,
                             OrderMapper orderMapper,
                             WorkOrderService workOrderService) {
        this.planMapper = planMapper;
        this.itemMapper = itemMapper;
        this.recordMapper = recordMapper;
        this.recordItemMapper = recordItemMapper;
        this.assetMapper = assetMapper;
        this.orderMapper = orderMapper;
        this.workOrderService = workOrderService;
    }

    @Transactional
    public InspectionPlanDetail createPlan(PlanCreateReq req) {
        if (req.assetId() == null) {
            throw new BizException("设备不能为空");
        }
        EqAsset asset = assetMapper.selectById(req.assetId());
        if (asset == null) {
            throw new BizException("设备不存在：" + req.assetId());
        }
        if (!StringUtils.hasText(req.planCode()) || !StringUtils.hasText(req.planName())) {
            throw new BizException("计划编号与计划名称不能为空");
        }
        EqInspectionPlan plan = new EqInspectionPlan();
        plan.setPlanCode(req.planCode());
        plan.setPlanName(req.planName());
        plan.setAssetId(req.assetId());
        plan.setCycleType(req.cycleType() == null ? 1 : req.cycleType());
        plan.setExecutorRole(StringUtils.hasText(req.executorRole()) ? req.executorRole() : "操作工");
        plan.setEnabled(1);
        planMapper.insert(plan);

        List<EqInspectionItem> items = new ArrayList<>();
        if (!CollectionUtils.isEmpty(req.items())) {
            int seq = 1;
            for (PlanCreateReq.ItemReq ir : req.items()) {
                EqInspectionItem item = new EqInspectionItem();
                item.setPlanId(plan.getId());
                item.setSeqNo(seq++);
                item.setItemName(ir.itemName());
                item.setCheckMethod(ir.checkMethod() == null ? 1 : ir.checkMethod());
                item.setStandardText(ir.standardText() == null ? "" : ir.standardText());
                item.setMinValue(ir.minValue());
                item.setMaxValue(ir.maxValue());
                item.setUnit(ir.unit() == null ? "" : ir.unit());
                item.setRequiredPhoto(ir.requiredPhoto() == null ? 0 : ir.requiredPhoto());
                itemMapper.insert(item);
                items.add(item);
            }
        }
        return new InspectionPlanDetail(plan, items);
    }

    public List<InspectionPlanDetail> listPlans(Long assetId, Integer enabled) {
        List<EqInspectionPlan> plans = planMapper.selectList(new LambdaQueryWrapper<EqInspectionPlan>()
                .eq(assetId != null, EqInspectionPlan::getAssetId, assetId)
                .eq(enabled != null, EqInspectionPlan::getEnabled, enabled)
                .orderByAsc(EqInspectionPlan::getId));
        List<InspectionPlanDetail> result = new ArrayList<>();
        for (EqInspectionPlan p : plans) {
            result.add(new InspectionPlanDetail(p, itemsOf(p.getId())));
        }
        return result;
    }

    public InspectionPlanDetail planDetail(Long planId) {
        EqInspectionPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException("点检计划不存在：" + planId);
        }
        return new InspectionPlanDetail(plan, itemsOf(planId));
    }

    @Transactional
    public SubmitResult submit(RecordSubmitReq req) {
        if (req.planId() == null) {
            throw new BizException("点检计划不能为空");
        }
        EqInspectionPlan plan = planMapper.selectById(req.planId());
        if (plan == null) {
            throw new BizException("点检计划不存在：" + req.planId());
        }
        if (CollectionUtils.isEmpty(req.items())) {
            throw new BizException("点检明细不能为空");
        }

        Map<Long, EqInspectionItem> defMap = itemsOf(plan.getId()).stream()
                .collect(Collectors.toMap(EqInspectionItem::getId, d -> d));

        LocalDateTime now = LocalDateTime.now();
        EqInspectionRecord record = new EqInspectionRecord();
        record.setPlanId(plan.getId());
        record.setAssetId(plan.getAssetId());
        record.setRecordDate(LocalDate.now());
        record.setInspector(req.inspector() == null ? "" : req.inspector());
        record.setCostMinutes(req.costMinutes() == null ? 0 : req.costMinutes());
        record.setSubmitTime(now);

        int abnormalCount = 0;
        StringJoiner abnormalDesc = new StringJoiner("；");
        List<EqInspectionRecordItem> rows = new ArrayList<>();

        for (RecordSubmitReq.RecordItemReq ri : req.items()) {
            EqInspectionItem def = defMap.get(ri.itemId());
            if (def == null) {
                throw new BizException("点检项不属于该计划：" + ri.itemId());
            }
            int judged = judge(def, ri.result(), ri.measureValue());
            EqInspectionRecordItem row = new EqInspectionRecordItem();
            row.setItemId(def.getId());
            row.setResult(judged);
            row.setMeasureValue(ri.measureValue());
            row.setPhotoUrl(ri.photoUrl() == null ? "" : ri.photoUrl());
            row.setRemark(ri.remark() == null ? "" : ri.remark());
            rows.add(row);
            if (judged == 2) {
                abnormalCount++;
                abnormalDesc.add(describe(def, ri.measureValue()));
            }
        }

        record.setResult(abnormalCount > 0 ? 2 : 1);
        if (StringUtils.hasText(req.abnormalDesc())) {
            record.setAbnormalDesc(truncate(req.abnormalDesc(), 250));
        } else {
            record.setAbnormalDesc(truncate(abnormalDesc.toString(), 250));
        }
        recordMapper.insert(record);

        for (EqInspectionRecordItem row : rows) {
            row.setRecordId(record.getId());
            recordItemMapper.insert(row);
        }

        Long orderId = null;
        String orderNo = null;
        if (abnormalCount > 0) {
            WoOrder order = workOrderService.create(plan.getAssetId(), 2, record.getId(), "点检异常",
                    truncate("点检异常：" + record.getAbnormalDesc(), 500), null, record.getInspector());
            orderId = order.getId();
            orderNo = order.getOrderNo();
        }
        return new SubmitResult(record.getId(), record.getResult(), abnormalCount, orderId, orderNo);
    }

    public Page<EqInspectionRecord> page(int pageNo, int size, Long assetId, Integer result) {
        LambdaQueryWrapper<EqInspectionRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(assetId != null, EqInspectionRecord::getAssetId, assetId)
          .eq(result != null, EqInspectionRecord::getResult, result)
          .orderByDesc(EqInspectionRecord::getSubmitTime)
          .orderByDesc(EqInspectionRecord::getId);
        return recordMapper.selectPage(new Page<>(pageNo, size), qw);
    }

    public InspectionRecordDetail recordDetail(Long recordId) {
        EqInspectionRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException("点检记录不存在：" + recordId);
        }
        List<EqInspectionRecordItem> rows = recordItemMapper.selectList(
                new LambdaQueryWrapper<EqInspectionRecordItem>()
                        .eq(EqInspectionRecordItem::getRecordId, recordId)
                        .orderByAsc(EqInspectionRecordItem::getId));
        WoOrder order = orderMapper.selectOne(new LambdaQueryWrapper<WoOrder>()
                .eq(WoOrder::getSourceType, 2)
                .eq(WoOrder::getSourceRefId, recordId)
                .orderByDesc(WoOrder::getId)
                .last("limit 1"));
        return new InspectionRecordDetail(record, rows,
                order == null ? null : order.getId(),
                order == null ? null : order.getOrderNo());
    }

    private List<EqInspectionItem> itemsOf(Long planId) {
        return itemMapper.selectList(new LambdaQueryWrapper<EqInspectionItem>()
                .eq(EqInspectionItem::getPlanId, planId)
                .orderByAsc(EqInspectionItem::getSeqNo));
    }

    private int judge(EqInspectionItem def, Integer given, BigDecimal measure) {
        if (given != null) {
            return given == 2 ? 2 : 1;
        }
        if (measure != null) {
            if (def.getMinValue() != null && measure.compareTo(def.getMinValue()) < 0) {
                return 2;
            }
            if (def.getMaxValue() != null && measure.compareTo(def.getMaxValue()) > 0) {
                return 2;
            }
        }
        return 1;
    }

    private String describe(EqInspectionItem def, BigDecimal measure) {
        StringBuilder sb = new StringBuilder(def.getItemName());
        if (measure != null) {
            sb.append(" 实测").append(measure.toPlainString())
              .append(def.getUnit() == null ? "" : def.getUnit());
        }
        if (StringUtils.hasText(def.getStandardText())) {
            sb.append("（标准 ").append(def.getStandardText()).append("）");
        }
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}