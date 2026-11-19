package com.eqmaint.inspection;

import java.util.List;

public record InspectionPlanDetail(EqInspectionPlan plan, List<EqInspectionItem> items) {
}