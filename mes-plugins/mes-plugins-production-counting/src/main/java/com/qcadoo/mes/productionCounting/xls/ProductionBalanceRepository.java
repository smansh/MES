package com.qcadoo.mes.productionCounting.xls;

import com.qcadoo.mes.costCalculation.constants.MaterialCostsUsed;
import com.qcadoo.mes.costCalculation.constants.SourceOfOperationCosts;
import com.qcadoo.mes.productionCounting.constants.ProductionBalanceFields;
import com.qcadoo.mes.productionCounting.xls.dto.*;
import com.qcadoo.model.api.Entity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class ProductionBalanceRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    List<ProducedQuantity> getProducedQuantities(final List<Long> ordersIds) {
        StringBuilder query = new StringBuilder();
        query.append("SELECT ");
        query.append("o.number AS orderNumber, ");
        query.append("prod.number AS productNumber, ");
        query.append("prod.name AS productName, ");
        query.append("MIN(o.plannedquantity) AS plannedQuantity, ");
        appendProducedQuantity(query);
        query.append("AS producedQuantity, ");
        query.append("COALESCE(SUM(topoc.wastesquantity), 0) AS wastesQuantity, ");
        query.append("COALESCE(prodWaste.producedWastes, 0) AS producedWastes, ");
        appendProducedQuantity(query);
        query.append("- MIN(o.plannedQuantity) AS deviation, ");
        query.append("prod.unit AS productUnit ");
        query.append("FROM orders_order o ");
        query.append("JOIN basic_product prod ON o.product_id = prod.id ");
        query.append("LEFT JOIN productioncounting_productiontracking pt ON pt.order_id = o.id AND pt.state = '02accepted' ");
        query.append(
                "LEFT JOIN productioncounting_trackingoperationproductoutcomponent topoc ON topoc.productiontracking_id = pt.id AND topoc.product_id = prod.id ");
        query.append("LEFT JOIN ");
        query.append(
                "(SELECT pcq.order_id as orderId, wastePt.order_id AS wastePtOrderId, COALESCE(SUM(wasteTopoc.usedquantity), 0) AS producedWastes ");
        query.append("FROM basicproductioncounting_productioncountingquantity pcq ");
        query.append(
                "LEFT JOIN productioncounting_trackingoperationproductoutcomponent wasteTopoc ON  wasteTopoc.product_id = pcq.product_id ");
        query.append(
                "LEFT JOIN productioncounting_productiontracking wastePt ON wasteTopoc.productiontracking_id = wastePt.id AND wastePt.state = '02accepted' ");
        query.append("WHERE pcq.typeofmaterial = '04waste' AND pcq.role = '02produced' ");
        query.append(
                "GROUP BY orderId, wastePtOrderId) prodWaste ON prodWaste.orderId = o.id AND prodWaste.wastePtOrderId = o.id ");
        appendWhereClause(query);
        query.append("GROUP BY orderNumber, productNumber, productName, productUnit, prodWaste.producedWastes ");
        query.append("ORDER BY orderNumber ");

        return jdbcTemplate.query(query.toString(), new MapSqlParameterSource("ordersIds", ordersIds),
                BeanPropertyRowMapper.newInstance(ProducedQuantity.class));
    }

    private void appendProducedQuantity(StringBuilder query) {
        query.append("COALESCE(SUM(topoc.usedquantity), 0) ");
    }

    private void appendWhereClause(StringBuilder query) {
        query.append("WHERE o.id IN (:ordersIds) ");
    }

    List<MaterialCost> getMaterialCosts(Entity entity, List<Long> ordersIds) {
        StringBuilder query = new StringBuilder();
        appendCumulatedPlannedQuantities(query);
        appendMaterialCostsSelectionClause(query, entity);
        query.append("q.replacementTo AS replacementTo, ");
        query.append("NULL AS operationNumber ");
        appendMaterialCostsFromClause(query, entity);
        query.append("LEFT JOIN productioncounting_productiontracking pt ON pt.order_id = o.id AND pt.state = '02accepted' ");
        query.append(
                "LEFT JOIN productioncounting_trackingoperationproductincomponent topic ON topic.productiontracking_id = pt.id AND topic.product_id = p.id ");
        query.append("GROUP BY o.id, o.number, p.number, p.name, p.unit, topic.wasteunit, q.replacementTo) ");
        query.append("UNION ");
        appendForEachPlannedQuantities(query);
        appendMaterialCostsSelectionClause(query, entity);
        query.append("q.replacementTo AS replacementTo, ");
        query.append("op.number AS operationNumber ");
        appendMaterialCostsFromClause(query, entity);
        query.append("JOIN technologies_operation op ON q.operation_id = op.id ");
        query.append(
                "JOIN technologies_technologyoperationcomponent toc ON toc.operation_id = op.id AND o.technology_id = toc.technology_id ");
        query.append(
                "LEFT JOIN productioncounting_productiontracking pt ON pt.order_id = o.id AND pt.technologyoperationcomponent_id = toc.id AND pt.state = '02accepted' ");
        query.append(
                "LEFT JOIN productioncounting_trackingoperationproductincomponent topic ON topic.productiontracking_id = pt.id AND topic.product_id = p.id ");
        query.append("GROUP BY o.id, o.number, op.number, p.number, p.name, p.unit, topic.wasteunit, q.replacementTo) ");
        query.append("ORDER BY orderNumber, operationNumber, productNumber ");

        return jdbcTemplate.query(query.toString(), new MapSqlParameterSource("ordersIds", ordersIds),
                BeanPropertyRowMapper.newInstance(MaterialCost.class));
    }

    private void appendForEachPlannedQuantities(StringBuilder query) {
        query.append("(WITH planned_quantity (order_id, operation_id, product_id, quantity, childsQuantity) AS (SELECT ");
        query.append("o.id AS orderId, ");
        query.append("toc.operation_id AS operationId, ");
        query.append("p.id AS productId, ");
        query.append("COALESCE(SUM(pcq.plannedquantity), 0) AS plannedQuantity, ");
        query.append("0 AS childsQuantity, ");
        query.append("replacementto.number AS replacementTo ");
        query.append("FROM orders_order o ");
        query.append("JOIN basicproductioncounting_productioncountingquantity pcq ON pcq.order_id = o.id ");
        query.append("LEFT JOIN basic_product replacementto ON replacementto.id = pcq.replacementto_id ");
        query.append("JOIN basic_product p ON pcq.product_id = p.id ");
        query.append("LEFT JOIN technologies_technology t ON t.product_id = p.id AND t.master = TRUE ");
        query.append("JOIN technologies_technologyoperationcomponent toc ON pcq.technologyoperationcomponent_id = toc.id ");
        appendWhereClause(query);
        query.append("AND o.typeofproductionrecording = '03forEach' ");
        query.append("AND pcq.role = '01used' AND pcq.typeofmaterial = '01component' AND (t.id IS NULL ");
        query.append("OR t.id IS NOT NULL) ");
        query.append("GROUP BY o.id, toc.operation_id, p.id, replacementto.number) ");
    }

    private void appendCumulatedPlannedQuantities(StringBuilder query) {
        query.append("(WITH planned_quantity (order_id, product_id, quantity, childsQuantity) AS (SELECT ");
        query.append("o.id AS orderId, ");
        query.append("p.id AS productId, ");
        query.append("COALESCE(SUM(pcq.plannedquantity), 0) AS plannedQuantity, ");
        query.append("0 AS childsQuantity, ");
        query.append("replacementto.number AS replacementTo ");
        query.append("FROM orders_order o ");
        query.append("JOIN basicproductioncounting_productioncountingquantity pcq ON pcq.order_id = o.id ");
        query.append("LEFT JOIN basic_product replacementto ON replacementto.id = pcq.replacementto_id ");
        query.append("JOIN basic_product p ON pcq.product_id = p.id ");
        query.append("LEFT JOIN technologies_technology t ON t.product_id = p.id AND t.master = TRUE ");
        appendWhereClause(query);
        query.append("AND o.typeofproductionrecording = '02cumulated' ");
        query.append("AND pcq.role = '01used' AND pcq.typeofmaterial = '01component' AND t.id IS NULL ");
        query.append("GROUP BY o.id, replacementto.number, p.id ");
        query.append("UNION ");
        query.append("SELECT ");
        query.append("o.id AS orderId, ");
        query.append("p.id AS productId, ");
        query.append("COALESCE(SUM(pcq.plannedquantity), 0) AS plannedQuantity, ");
        query.append("COALESCE(SUM(och.plannedquantity), 0) AS childsQuantity, ");
        query.append("replacementto.number AS replacementTo ");
        query.append("FROM orders_order o ");
        query.append("JOIN basicproductioncounting_productioncountingquantity pcq ON pcq.order_id = o.id ");
        query.append("LEFT JOIN basic_product replacementto ON replacementto.id = pcq.replacementto_id ");
        query.append("JOIN basic_product p ON pcq.product_id = p.id ");
        query.append("LEFT JOIN technologies_technology t ON t.product_id = p.id AND t.master = TRUE ");
        query.append("LEFT JOIN orders_order och ON och.product_id = p.id AND och.parent_id = o.id ");
        appendWhereClause(query);
        query.append("AND o.typeofproductionrecording = '02cumulated' ");
        query.append("AND pcq.role = '01used' AND pcq.typeofmaterial = '01component' AND t.id IS NOT NULL ");
        query.append(
                "GROUP BY o.id, replacementto.number, p.id HAVING COALESCE(SUM(pcq.plannedquantity), 0) - COALESCE(SUM(och.plannedquantity), 0) > 0) ");
    }

    private void appendMaterialCostsSelectionClause(StringBuilder query, Entity entity) {
        query.append("SELECT ");
        query.append("o.id AS orderId, ");
        query.append("o.number AS orderNumber, ");
        query.append("p.number AS productNumber, ");
        query.append("p.name AS productName, ");
        query.append("p.unit AS productUnit, ");
        query.append("topic.wasteunit AS usedWasteUnit, ");
        appendPlannedQuantity(query);
        query.append("AS plannedQuantity, ");
        appendUsedQuantity(query);
        query.append("AS usedQuantity, ");
        appendUsedQuantity(query);
        query.append("- ");
        appendPlannedQuantity(query);
        query.append("AS quantitativeDeviation, ");
        appendPlannedCost(query, entity);
        query.append("AS plannedCost, ");
        appendRealCost(query, entity);
        query.append("AS realCost, ");
        appendRealCost(query, entity);
        query.append("- ");
        appendPlannedCost(query, entity);
        query.append("AS valueDeviation, ");
        query.append("COALESCE(SUM(topic.wasteusedquantity), 0) AS usedWasteQuantity, ");
    }

    private void appendMaterialCostsFromClause(StringBuilder query, Entity entity) {
        query.append("FROM orders_order o ");
        query.append("JOIN planned_quantity q ON q.order_id = o.id ");
        query.append("JOIN basic_product p ON q.product_id = p.id ");
        if (MaterialCostsUsed.COST_FOR_ORDER.getStringValue()
                .equals(entity.getStringField(ProductionBalanceFields.MATERIAL_COSTS_USED))) {
            query.append("LEFT JOIN costnormsformaterials_technologyinstoperproductincomp tiopic ");
            query.append("ON tiopic.product_id = p.id AND tiopic.order_id = o.id ");
        }
    }

    private void appendPlannedCost(StringBuilder query, Entity entity) {
        if (MaterialCostsUsed.COST_FOR_ORDER.getStringValue()
                .equals(entity.getStringField(ProductionBalanceFields.MATERIAL_COSTS_USED))) {
            query.append("CASE WHEN ");
            appendUsedQuantity(query);
            query.append("<> 0 THEN ");
            appendPlannedQuantity(query);
            query.append("* ");
            appendRealCost(query, entity);
            query.append("/ ");
            appendUsedQuantity(query);
            query.append("ELSE 0 END ");
        } else {
            String componentPriceClause = evaluateComponentPrice(
                    entity.getStringField(ProductionBalanceFields.MATERIAL_COSTS_USED));
            appendPlannedQuantity(query);
            query.append("* ").append(componentPriceClause);
        }
    }

    private void appendRealCost(StringBuilder query, Entity entity) {
        if (MaterialCostsUsed.COST_FOR_ORDER.getStringValue()
                .equals(entity.getStringField(ProductionBalanceFields.MATERIAL_COSTS_USED))) {
            query.append("COALESCE(MIN(tiopic.costfororder), 0) ");
        } else {
            String componentPriceClause = evaluateComponentPrice(
                    entity.getStringField(ProductionBalanceFields.MATERIAL_COSTS_USED));
            appendUsedQuantity(query);
            query.append("* ").append(componentPriceClause);
        }
    }

    private String evaluateComponentPrice(String materialCostsUsed) {
        switch (MaterialCostsUsed.parseString(materialCostsUsed)) {
            case NOMINAL:
                return "COALESCE(MIN(p.nominalcost), 0) ";
            case AVERAGE:
                return "COALESCE(MIN(p.averagecost), 0) ";
            case LAST_PURCHASE:
                return "COALESCE(MIN(p.lastpurchasecost), 0) ";
            case AVERAGE_OFFER_COST:
                return "COALESCE(MIN(p.averageoffercost), 0) ";
            case LAST_OFFER_COST:
                return "COALESCE(MIN(p.lastoffercost), 0) ";
            default:
                throw new IllegalStateException("Unsupported materialCostsUsed: " + materialCostsUsed);
        }
    }

    private void appendPlannedQuantity(StringBuilder query) {
        query.append("MIN(q.quantity - q.childsQuantity) ");
    }

    private void appendUsedQuantity(StringBuilder query) {
        query.append("(COALESCE(SUM(topic.usedquantity), 0) - MIN(q.childsQuantity)) ");
    }

    List<PieceworkDetails> getPieceworkDetails(List<Long> ordersIds) {
        StringBuilder query = new StringBuilder();
        query.append("SELECT ");
        query.append("o.number AS orderNumber, ");
        query.append("op.number AS operationNumber, ");
        query.append("COALESCE(SUM(pt.executedoperationcycles), 0) AS totalexecutedoperationcycles ");
        query.append("FROM orders_order o ");
        query.append("LEFT JOIN productioncounting_productiontracking pt ON o.id = pt.order_id AND pt.state = '02accepted' ");
        query.append("LEFT JOIN technologies_technologyoperationcomponent toc ON pt.technologyoperationcomponent_id = toc.id ");
        query.append("LEFT JOIN technologies_operation op ON toc.operation_id = op.id ");
        appendWhereClause(query);
        query.append("AND o.typeofproductionrecording = '03forEach' ");
        query.append("GROUP BY orderNumber, operationNumber ");
        query.append("ORDER BY orderNumber, operationNumber ");

        return jdbcTemplate.query(query.toString(), new MapSqlParameterSource("ordersIds", ordersIds),
                BeanPropertyRowMapper.newInstance(PieceworkDetails.class));
    }

    List<LaborTime> getLaborTime(List<Long> ordersIds) {
        StringBuilder query = new StringBuilder();
        query.append("SELECT ");
        query.append("o.number AS orderNumber, ");
        query.append("op.number AS operationNumber, ");
        query.append("stf.number AS staffNumber, ");
        query.append("stf.name AS staffName, ");
        query.append("stf.surname AS staffSurname, ");
        query.append("COALESCE(stf.laborhourlycost, 0) AS staffLaborHourlyCost, ");
        query.append("wg.name AS wageGroupName, ");
        query.append("COALESCE(SUM(swt.labortime), 0) AS laborTime ");
        query.append("FROM orders_order o ");
        query.append("LEFT JOIN productioncounting_productiontracking pt ON o.id = pt.order_id AND pt.state = '02accepted' ");
        query.append("LEFT JOIN productioncounting_staffworktime swt ON pt.id = swt.productionrecord_id ");
        query.append("LEFT JOIN basic_staff stf ON swt.worker_id = stf.id ");
        query.append("LEFT JOIN technologies_technologyoperationcomponent toc ON pt.technologyoperationcomponent_id = toc.id ");
        query.append("LEFT JOIN technologies_operation op ON toc.operation_id = op.id ");
        query.append("LEFT JOIN wagegroups_wagegroup wg ON stf.wagegroup_id = wg.id ");
        appendWhereClause(query);
        query.append(
                "GROUP BY orderNumber, operationNumber, staffNumber, staffName, staffSurname, staffLaborHourlyCost, wageGroupName ");
        query.append("ORDER BY orderNumber, operationNumber, staffNumber ");

        return jdbcTemplate.query(query.toString(), new MapSqlParameterSource("ordersIds", ordersIds),
                BeanPropertyRowMapper.newInstance(LaborTime.class));
    }

    List<LaborTimeDetails> getLaborTimeDetails(Entity entity, List<Long> ordersIds) {
        StringBuilder query = new StringBuilder();
        query.append("(WITH planned_time (order_id, staff_time, machine_time) AS (SELECT o.id AS orderId, ");
        appendPlannedStaffTime(entity, query);
        query.append("AS plannedStaffTime, ");
        appendPlannedMachineTime(entity, query);
        query.append("AS plannedMachineTime ");
        query.append("FROM orders_order o ");
        query.append("JOIN technologies_technology t ON o.technology_id = t.id ");
        query.append("JOIN technologies_technologyoperationcomponent toc ON toc.technology_id = t.id ");
        query.append(
                "LEFT JOIN basicproductioncounting_productioncountingoperationrun pcor ON pcor.order_id = o.id AND pcor.technologyoperationcomponent_id = toc.id ");
        appendWhereClause(query);
        query.append("AND o.typeofproductionrecording = '02cumulated' ");
        query.append("GROUP BY o.id) ");
        query.append("SELECT ");
        query.append("d.number AS divisionNumber, ");
        query.append("pl.number AS productionLineNumber, ");
        query.append("o.number AS orderNumber, ");
        query.append("o.state AS orderState, ");
        query.append("o.datefrom AS plannedDateFrom, ");
        query.append("o.effectivedatefrom AS effectiveDateFrom, ");
        query.append("o.dateTo AS plannedDateTo, ");
        query.append("o.effectivedateto AS effectiveDateTo, ");
        query.append("p.number AS productNumber, ");
        query.append("o.name AS orderName, ");
        query.append("o.plannedquantity AS plannedQuantity, ");
        query.append("COALESCE(o.amountofproductproduced, 0::numeric) AS amountOfProductProduced, ");
        query.append("stf.number AS staffNumber, ");
        query.append("stf.name AS staffName, ");
        query.append("stf.surname AS staffSurname, ");
        query.append("NULL AS operationNumber, ");
        query.append("pt.timerangefrom AS timeRangeFrom, ");
        query.append("pt.timerangeto AS timeRangeTo, ");
        query.append("sh.name AS shiftName, ");
        query.append("pt.createdate AS createDate, ");
        query.append("COALESCE(swt.labortime, 0) AS laborTime, ");
        query.append("plt.staff_time AS plannedLaborTime, ");
        query.append("COALESCE(swt.labortime, 0) - plt.staff_time AS laborTimeDeviation, ");
        query.append("COALESCE(pt.machinetime, 0) AS machineTime, ");
        query.append("plt.machine_time AS plannedMachineTime, ");
        query.append("COALESCE(pt.machinetime, 0) - plt.machine_time AS machineTimeDeviation ");
        query.append("FROM orders_order o ");
        query.append("JOIN planned_time plt ON plt.order_id = o.id ");
        query.append("JOIN basic_product p ON p.id = o.product_id ");
        query.append("JOIN technologies_technology t ON t.id = o.technology_id ");
        query.append("LEFT JOIN basic_division d ON d.id = t.division_id ");
        query.append("LEFT JOIN productionlines_productionline pl ON pl.id = o.productionline_id ");
        query.append("LEFT JOIN productioncounting_productiontracking pt ON o.id = pt.order_id AND pt.state = '02accepted' ");
        query.append("LEFT JOIN productioncounting_staffworktime swt ON pt.id = swt.productionrecord_id ");
        query.append("LEFT JOIN basic_staff stf ON swt.worker_id = stf.id ");
        query.append("LEFT JOIN basic_shift sh ON sh.id = pt.shift_id) ");
        query.append("UNION ");
        query.append(
                "(WITH planned_time (order_id, toc_id, staff_time, machine_time) AS (SELECT o.id AS orderId, toc.id AS tocId, ");
        appendPlannedStaffTime(entity, query);
        query.append("AS plannedStaffTime, ");
        appendPlannedMachineTime(entity, query);
        query.append("AS plannedMachineTime ");
        query.append("FROM orders_order o ");
        query.append("JOIN technologies_technology t ON o.technology_id = t.id ");
        query.append("JOIN technologies_technologyoperationcomponent toc ON toc.technology_id = t.id ");
        query.append(
                "LEFT JOIN basicproductioncounting_productioncountingoperationrun pcor ON pcor.order_id = o.id AND pcor.technologyoperationcomponent_id = toc.id ");
        appendWhereClause(query);
        query.append("AND o.typeofproductionrecording = '03forEach' ");
        query.append("GROUP BY o.id, toc.id) ");
        query.append("SELECT ");
        query.append("d.number AS divisionNumber, ");
        query.append("pl.number AS productionLineNumber, ");
        query.append("o.number AS orderNumber, ");
        query.append("o.state AS orderState, ");
        query.append("o.datefrom AS plannedDateFrom, ");
        query.append("o.effectivedatefrom AS effectiveDateFrom, ");
        query.append("o.dateTo AS plannedDateTo, ");
        query.append("o.effectivedateto AS effectiveDateTo, ");
        query.append("p.number AS productNumber, ");
        query.append("o.name AS orderName, ");
        query.append("o.plannedquantity AS plannedQuantity, ");
        query.append("COALESCE(o.amountofproductproduced, 0::numeric) AS amountOfProductProduced, ");
        query.append("stf.number AS staffNumber, ");
        query.append("stf.name AS staffName, ");
        query.append("stf.surname AS staffSurname, ");
        query.append("op.number AS operationNumber, ");
        query.append("pt.timerangefrom AS timeRangeFrom, ");
        query.append("pt.timerangeto AS timeRangeTo, ");
        query.append("sh.name AS shiftName, ");
        query.append("pt.createdate AS createDate, ");
        query.append("COALESCE(swt.labortime, 0) AS laborTime, ");
        query.append("plt.staff_time AS plannedLaborTime, ");
        query.append("COALESCE(swt.labortime, 0) - plt.staff_time AS laborTimeDeviation, ");
        query.append("COALESCE(pt.machinetime, 0) AS machineTime, ");
        query.append("plt.machine_time AS plannedMachineTime, ");
        query.append("COALESCE(pt.machinetime, 0) - plt.machine_time AS machineTimeDeviation ");
        query.append("FROM orders_order o ");
        query.append("JOIN basic_product p ON p.id = o.product_id ");
        query.append("JOIN technologies_technology t ON t.id = o.technology_id ");
        query.append("LEFT JOIN basic_division d ON d.id = t.division_id ");
        query.append("LEFT JOIN productionlines_productionline pl ON pl.id = o.productionline_id ");
        query.append("LEFT JOIN productioncounting_productiontracking pt ON o.id = pt.order_id AND pt.state = '02accepted' ");
        query.append("LEFT JOIN planned_time plt ON plt.order_id = o.id AND plt.toc_id = pt.technologyoperationcomponent_id ");
        query.append("LEFT JOIN productioncounting_staffworktime swt ON pt.id = swt.productionrecord_id ");
        query.append("LEFT JOIN basic_staff stf ON swt.worker_id = stf.id ");
        query.append("LEFT JOIN basic_shift sh ON sh.id = pt.shift_id ");
        query.append("LEFT JOIN technologies_technologyoperationcomponent toc ON pt.technologyoperationcomponent_id = toc.id ");
        query.append("LEFT JOIN technologies_operation op ON toc.operation_id = op.id ");
        appendWhereClause(query);
        query.append("AND o.typeofproductionrecording = '03forEach') ");
        query.append("ORDER BY orderNumber, operationNumber, staffNumber ");

        return jdbcTemplate.query(query.toString(), new MapSqlParameterSource("ordersIds", ordersIds),
                BeanPropertyRowMapper.newInstance(LaborTimeDetails.class));
    }

    List<ProductionCost> getProductionCosts(Entity entity, List<Long> ordersIds) {
        StringBuilder query = new StringBuilder();
        query.append("(WITH planned_time (order_id, staff_time, machine_time) AS (SELECT o.id AS orderId, ");
        appendPlannedStaffTime(entity, query);
        query.append("AS plannedStaffTime, ");
        appendPlannedMachineTime(entity, query);
        query.append("AS plannedMachineTime ");
        query.append("FROM orders_order o ");
        query.append("JOIN technologies_technology t ON o.technology_id = t.id ");
        query.append("JOIN technologies_technologyoperationcomponent toc ON toc.technology_id = t.id ");
        query.append(
                "LEFT JOIN basicproductioncounting_productioncountingoperationrun pcor ON pcor.order_id = o.id AND pcor.technologyoperationcomponent_id = toc.id ");
        appendWhereClause(query);
        query.append("AND o.typeofproductionrecording = '02cumulated' ");
        query.append("GROUP BY o.id) ");
        appendRealStaffCosts(entity, query, "'02cumulated'");
        query.append("SELECT ");
        query.append("o.id AS orderId, ");
        query.append("o.number AS orderNumber, ");
        query.append("NULL AS operationNumber, ");
        query.append("MIN(plt.staff_time) AS plannedStaffTime, ");
        appendRealStaffTime(entity, query);
        query.append("AS realStaffTime, ");
        query.append("MIN(plt.machine_time) AS plannedMachineTime, ");
        appendRealMachineTime(query);
        query.append("AS realMachineTime, ");
        appendCumulatedPlannedStaffCosts(query);
        query.append("AS plannedStaffCosts, ");
        appendCumulatedRealStaffCosts(entity, query);
        query.append("AS realStaffCosts, ");
        appendCumulatedRealStaffCosts(entity, query);
        query.append("- ");
        appendCumulatedPlannedStaffCosts(query);
        query.append("AS staffCostsDeviation, ");
        appendCumulatedPlannedMachineCosts(query);
        query.append("AS plannedMachineCosts, ");
        appendCumulatedRealMachineCosts(query);
        query.append("AS realMachineCosts, ");
        appendCumulatedRealMachineCosts(query);
        query.append("- ");
        appendCumulatedPlannedMachineCosts(query);
        query.append("AS machineCostsDeviation, ");
        query.append("0 AS plannedPieceworkCosts, ");
        query.append("0 AS realPieceworkCosts, ");
        appendCumulatedPlannedStaffCosts(query);
        query.append("+ ");
        appendCumulatedPlannedMachineCosts(query);
        query.append("AS plannedCostsSum, ");
        appendCumulatedRealStaffCosts(entity, query);
        query.append("+ ");
        appendCumulatedRealMachineCosts(query);
        query.append("AS realCostsSum, ");
        appendCumulatedRealStaffCosts(entity, query);
        query.append("+ ");
        appendCumulatedRealMachineCosts(query);
        query.append("- ");
        appendCumulatedPlannedStaffCosts(query);
        query.append("- ");
        appendCumulatedPlannedMachineCosts(query);
        query.append("AS sumCostsDeviation ");
        query.append("FROM orders_order o ");
        query.append("JOIN planned_time plt ON plt.order_id = o.id ");
        query.append("LEFT JOIN productioncounting_productiontracking pt ON o.id = pt.order_id AND pt.state = '02accepted' ");
        appendRealStaffCostsJoin(entity, query);
        query.append("CROSS JOIN basic_parameter bp ");
        query.append("GROUP BY orderId, orderNumber) ");
        query.append("UNION ALL ");
        query.append(
                "(WITH planned_time (order_id, toc_id, staff_time, machine_time) AS (SELECT o.id AS orderId, toc.id AS tocId, ");
        appendPlannedStaffTime(entity, query);
        query.append("AS plannedStaffTime, ");
        appendPlannedMachineTime(entity, query);
        query.append("AS plannedMachineTime ");
        query.append("FROM orders_order o ");
        query.append("JOIN technologies_technology t ON o.technology_id = t.id ");
        query.append("JOIN technologies_technologyoperationcomponent toc ON toc.technology_id = t.id ");
        query.append(
                "LEFT JOIN basicproductioncounting_productioncountingoperationrun pcor ON pcor.order_id = o.id AND pcor.technologyoperationcomponent_id = toc.id ");
        appendWhereClause(query);
        query.append("AND o.typeofproductionrecording = '03forEach' ");
        query.append("GROUP BY o.id, toc.id) ");
        appendRealStaffCosts(entity, query, "'03forEach'");
        query.append("SELECT ");
        query.append("o.id AS orderId, ");
        query.append("o.number AS orderNumber, ");
        query.append("op.number AS operationNumber, ");
        query.append("MIN(plt.staff_time) AS plannedStaffTime, ");
        appendRealStaffTime(entity, query);
        query.append("AS realStaffTime, ");
        query.append("MIN(plt.machine_time) AS plannedMachineTime, ");
        appendRealMachineTime(query);
        query.append("AS realMachineTime, ");
        appendForEachPlannedStaffCosts(entity, query);
        query.append("AS plannedStaffCosts, ");
        appendForEachRealStaffCosts(entity, query);
        query.append("AS realStaffCosts, ");
        appendForEachRealStaffCosts(entity, query);
        query.append("- ");
        appendForEachPlannedStaffCosts(entity, query);
        query.append("AS staffCostsDeviation, ");
        appendForEachPlannedMachineCosts(entity, query);
        query.append("AS plannedMachineCosts, ");
        appendForEachRealMachineCosts(entity, query);
        query.append("AS realMachineCosts, ");
        appendForEachRealMachineCosts(entity, query);
        query.append("- ");
        appendForEachPlannedMachineCosts(entity, query);
        query.append("AS machineCostsDeviation, ");
        query.append("COALESCE(MIN(pcor.runs / toc.numberofoperations * toc.pieceworkcost), 0) AS plannedPieceworkCosts, ");
        query.append(
                "COALESCE(SUM(pt.executedoperationcycles) / MIN(toc.numberofoperations) * MIN(toc.pieceworkcost), 0) AS realPieceworkCosts, ");
        appendForEachPlannedStaffCosts(entity, query);
        query.append("+ ");
        appendForEachPlannedMachineCosts(entity, query);
        query.append("AS plannedCostsSum, ");
        appendForEachRealStaffCosts(entity, query);
        query.append("+ ");
        appendForEachRealMachineCosts(entity, query);
        query.append("AS realCostsSum, ");
        appendForEachRealStaffCosts(entity, query);
        query.append("+ ");
        appendForEachRealMachineCosts(entity, query);
        query.append("- ");
        appendForEachPlannedStaffCosts(entity, query);
        query.append("- ");
        appendForEachPlannedMachineCosts(entity, query);
        query.append("AS sumCostsDeviation ");
        query.append("FROM orders_order o ");
        query.append("LEFT JOIN productioncounting_productiontracking pt ON o.id = pt.order_id AND pt.state = '02accepted' ");
        query.append("LEFT JOIN planned_time plt ON plt.order_id = o.id AND plt.toc_id = pt.technologyoperationcomponent_id ");
        query.append("LEFT JOIN technologies_technologyoperationcomponent toc ON pt.technologyoperationcomponent_id = toc.id ");
        query.append(
                "LEFT JOIN basicproductioncounting_productioncountingoperationrun pcor ON pcor.order_id = o.id AND pcor.technologyoperationcomponent_id = toc.id ");
        query.append("LEFT JOIN technologies_operation op ON toc.operation_id = op.id ");
        appendRealStaffCostsJoin(entity, query);
        query.append("CROSS JOIN basic_parameter bp ");
        appendWhereClause(query);
        query.append("AND o.typeofproductionrecording = '03forEach' ");
        query.append("GROUP BY orderId, orderNumber, toc.id, operationNumber) ");
        query.append("ORDER BY orderNumber, operationNumber ");

        return jdbcTemplate.query(query.toString(), new MapSqlParameterSource("ordersIds", ordersIds),
                BeanPropertyRowMapper.newInstance(ProductionCost.class));
    }

    private void appendRealStaffCosts(Entity entity, StringBuilder query, String typeOfProductionRecording) {
        if (includeWageGroups(entity)) {
            query.append(", real_staff_cost (order_id, productiontracking_id, labor_time, staff_cost) AS ");
            query.append("(SELECT o.id AS orderId, pt.id AS productionTrackingId, MIN(swt.labortime) AS laborTime, ");
            query.append("COALESCE(MIN(swt.labortime), 0) / 3600 * COALESCE(MIN(s.laborhourlycost), 0) AS staffCost ");
            query.append("FROM orders_order o ");
            query.append("JOIN productioncounting_productiontracking pt ON pt.order_id = o.id ");
            query.append("JOIN productioncounting_staffworktime swt ON swt.productionrecord_id = pt.id ");
            query.append("JOIN basic_staff s ON swt.worker_id = s.id ");
            appendWhereClause(query);
            query.append("AND pt.state = '02accepted' AND o.typeofproductionrecording = ").append(typeOfProductionRecording);
            query.append("GROUP BY o.id, swt.id, pt.id) ");
        }
    }

    private void appendRealStaffCostsJoin(Entity entity, StringBuilder query) {
        if (includeWageGroups(entity)) {
            query.append("LEFT JOIN real_staff_cost rsc ON rsc.order_id = o.id AND rsc.productiontracking_id = pt.id ");
        }
    }

    private void appendRealStaffCostsFromWageGroups(StringBuilder query) {
        query.append("COALESCE(SUM(rsc.staff_cost), 0) ");
    }

    private boolean includeWageGroups(Entity entity) {
        return entity.getBooleanField(ProductionBalanceFields.INCLUDE_WAGE_GROUPS);
    }

    private void appendRealMachineTime(StringBuilder query) {
        query.append("COALESCE(SUM(pt.machinetime), 0) ");
    }

    private void appendRealStaffTime(Entity entity, StringBuilder query) {
        if (includeWageGroups(entity)) {
            query.append("COALESCE(SUM(rsc.labor_time), 0) ");
        } else {
            query.append("COALESCE(SUM(pt.labortime), 0) ");
        }
    }

    private void appendForEachRealMachineCosts(Entity entity, StringBuilder query) {
        appendRealMachineTime(query);
        query.append("::numeric/ 3600 * ");
        appendForEachMachineHourCost(entity, query);
    }

    private void appendForEachPlannedMachineCosts(Entity entity, StringBuilder query) {
        query.append("COALESCE(MIN(plt.machine_time), 0) / 3600 * ");
        appendForEachMachineHourCost(entity, query);
    }

    private void appendForEachRealStaffCosts(Entity entity, StringBuilder query) {
        if (includeWageGroups(entity)) {
            appendRealStaffCostsFromWageGroups(query);
        } else {
            appendRealStaffTime(entity, query);
            query.append("::numeric/ 3600 * ");
            appendForEachStaffHourCost(entity, query);
        }
    }

    private void appendForEachPlannedStaffCosts(Entity entity, StringBuilder query) {
        query.append("COALESCE(MIN(plt.staff_time), 0) / 3600 * ");
        appendForEachStaffHourCost(entity, query);
    }

    private void appendCumulatedRealMachineCosts(StringBuilder query) {
        appendRealMachineTime(query);
        query.append("::numeric/ 3600 * ");
        appendCumulatedMachineHourCost(query);
    }

    private void appendCumulatedPlannedMachineCosts(StringBuilder query) {
        query.append("MIN(plt.machine_time) / 3600 * ");
        appendCumulatedMachineHourCost(query);
    }

    private void appendCumulatedRealStaffCosts(Entity entity, StringBuilder query) {
        if (includeWageGroups(entity)) {
            appendRealStaffCostsFromWageGroups(query);
        } else {
            appendRealStaffTime(entity, query);
            query.append("::numeric/ 3600 * ");
            appendCumulatedStaffHourCost(query);
        }
    }

    private void appendCumulatedPlannedStaffCosts(StringBuilder query) {
        query.append("MIN(plt.staff_time) / 3600 * ");
        appendCumulatedStaffHourCost(query);
    }

    private void appendPlannedMachineTime(Entity entity, StringBuilder query) {
        query.append("COALESCE(SUM((toc.tj * pcor.runs ");
        appendTPZandAdditionalTime(entity, query);
        query.append(") * toc.machineutilization), 0) ");
    }

    private void appendPlannedStaffTime(Entity entity, StringBuilder query) {
        query.append("COALESCE(SUM((toc.tj * pcor.runs ");
        appendTPZandAdditionalTime(entity, query);
        query.append(") * toc.laborutilization), 0) ");
    }

    private void appendCumulatedStaffHourCost(StringBuilder query) {
        query.append("COALESCE(MIN(bp.averagelaborhourlycostpb), 0) ");
    }

    private void appendCumulatedMachineHourCost(StringBuilder query) {
        query.append("COALESCE(MIN(bp.averagemachinehourlycostpb), 0) ");
    }

    private void appendForEachStaffHourCost(Entity entity, StringBuilder query) {
        if (SourceOfOperationCosts.TECHNOLOGY_OPERATION.getStringValue()
                .equals(entity.getStringField(ProductionBalanceFields.SOURCE_OF_OPERATION_COSTS))) {
            query.append("COALESCE(MIN(toc.laborhourlycost), 0) ");
        } else if (SourceOfOperationCosts.PARAMETERS.getStringValue()
                .equals(entity.getStringField(ProductionBalanceFields.SOURCE_OF_OPERATION_COSTS))) {
            query.append("COALESCE(MIN(bp.averagelaborhourlycostpb), 0) ");
        }
    }

    private void appendForEachMachineHourCost(Entity entity, StringBuilder query) {
        if (SourceOfOperationCosts.TECHNOLOGY_OPERATION.getStringValue()
                .equals(entity.getStringField(ProductionBalanceFields.SOURCE_OF_OPERATION_COSTS))) {
            query.append("COALESCE(MIN(toc.machinehourlycost), 0) ");
        } else if (SourceOfOperationCosts.PARAMETERS.getStringValue()
                .equals(entity.getStringField(ProductionBalanceFields.SOURCE_OF_OPERATION_COSTS))) {
            query.append("COALESCE(MIN(bp.averagemachinehourlycostpb), 0) ");
        }
    }

    private void appendTPZandAdditionalTime(Entity entity, StringBuilder query) {
        if (entity.getBooleanField(ProductionBalanceFields.INCLUDE_TPZ)) {
            query.append("+ toc.tpz ");
        }
        if (entity.getBooleanField(ProductionBalanceFields.INCLUDE_ADDITIONAL_TIME)) {
            query.append("+ toc.timenextoperation ");
        }
    }

    List<OrderBalance> getOrdersBalance(Entity entity, List<Long> ordersIds, List<MaterialCost> materialCosts,
            List<ProductionCost> productionCosts) {
        StringBuilder query = new StringBuilder();
        appendOrdersBalanceWithQueries(materialCosts, productionCosts, query);
        appendOrdersBalanceSelectionClause(entity, query);
        query.append("MIN(COALESCE(gmc.cost, 0)) AS materialCosts, ");
        query.append("MIN(gpc.cost) AS productionCosts, ");
        query.append("MIN(COALESCE(gmc.cost, 0)) + MIN(gpc.cost) AS technicalProductionCosts, ");
        appendMaterialCostMarginValue(entity, query);
        query.append("AS materialCostMarginValue, ");
        appendProductionCostMarginValue(entity, query);
        query.append("AS productionCostMarginValue, ");
        appendTotalCosts(entity, query);
        query.append("AS totalCosts, ");
        appendRegistrationPrice(entity, query);
        query.append("AS registrationPrice, ");
        appendRegistrationPriceOverheadValue(entity, query);
        query.append("AS registrationPriceOverheadValue, ");
        appendRealProductionCosts(entity, query);
        query.append("AS realProductionCosts, ");
        appendProfitValue(entity, query);
        query.append("AS profitValue, ");
        appendRealProductionCosts(entity, query);
        query.append("+ ");
        appendProfitValue(entity, query);
        query.append("AS sellPrice ");
        query.append("FROM orders_order o ");
        query.append("JOIN basic_product prod ON o.product_id = prod.id ");
        query.append("LEFT JOIN productioncounting_productiontracking pt ON pt.order_id = o.id AND pt.state = '02accepted' ");
        query.append(
                "LEFT JOIN productioncounting_trackingoperationproductoutcomponent topoc ON topoc.productiontracking_id = pt.id AND topoc.product_id = prod.id ");
        query.append("LEFT JOIN grouped_material_cost gmc ON gmc.order_id = o.id ");
        query.append("JOIN grouped_production_cost gpc ON gpc.order_id = o.id ");
        appendWhereClause(query);
        query.append("GROUP BY orderId, rootId, orderNumber, productNumber, productName ");
        query.append("ORDER BY orderNumber ");

        return jdbcTemplate.query(query.toString(), new MapSqlParameterSource("ordersIds", ordersIds),
                BeanPropertyRowMapper.newInstance(OrderBalance.class));
    }

    private void appendOrdersBalanceWithQueries(List<MaterialCost> materialCosts, List<ProductionCost> productionCosts,
            StringBuilder query) {
        query.append("WITH real_material_cost (order_id, cost) AS (VALUES ");
        if (materialCosts.isEmpty()) {
            query.append("(NULL::numeric, NULL::numeric) ");
        } else {
            for (int i = 0; i < materialCosts.size(); i++) {
                MaterialCost materialCost = materialCosts.get(i);
                query.append("(" + materialCost.getOrderId() + ", " + materialCost.getRealCost() + ") ");
                if (i != materialCosts.size() - 1) {
                    query.append(", ");
                }
            }
        }
        query.append("), ");
        query.append("grouped_material_cost AS (SELECT order_id, SUM(cost) AS cost FROM real_material_cost GROUP BY order_id), ");
        query.append("real_production_cost (order_id, cost) AS (VALUES ");
        for (int i = 0; i < productionCosts.size(); i++) {
            ProductionCost productionCost = productionCosts.get(i);
            query.append("(" + productionCost.getOrderId() + ", " + productionCost.getRealCostsSum() + ") ");
            if (i != productionCosts.size() - 1) {
                query.append(", ");
            }
        }
        query.append("), ");
        query.append(
                "grouped_production_cost AS (SELECT order_id, SUM(cost) AS cost FROM real_production_cost GROUP BY order_id) ");
    }

    private void appendOrdersBalanceSelectionClause(Entity entity, StringBuilder query) {
        query.append("SELECT ");
        query.append("o.id AS orderId, ");
        query.append("o.root_id AS rootId, ");
        query.append("o.number AS orderNumber, ");
        query.append("prod.number AS productNumber, ");
        query.append("prod.name AS productName, ");
        appendProducedQuantity(query);
        query.append("AS producedQuantity, ");
        appendMaterialCostMargin(entity, query);
        query.append("AS materialCostMargin, ");
        appendProductionCostMargin(entity, query);
        query.append("AS productionCostMargin, ");
        appendAdditionalOverhead(entity, query);
        query.append("AS additionalOverhead, ");
        appendDirectAdditionalCost(query);
        query.append("AS directAdditionalCost, ");
        appendRegistrationPriceOverhead(entity, query);
        query.append("AS registrationPriceOverhead, ");
        appendProfit(entity, query);
        query.append("AS profit, ");
    }

    private void appendMaterialCostMargin(Entity entity, StringBuilder query) {
        query.append("COALESCE(" + entity.getDecimalField(ProductionBalanceFields.MATERIAL_COST_MARGIN) + ", 0) ");
    }

    private void appendProductionCostMargin(Entity entity, StringBuilder query) {
        query.append("COALESCE(" + entity.getDecimalField(ProductionBalanceFields.PRODUCTION_COST_MARGIN) + ", 0) ");
    }

    private void appendAdditionalOverhead(Entity entity, StringBuilder query) {
        query.append("COALESCE(" + entity.getDecimalField(ProductionBalanceFields.ADDITIONAL_OVERHEAD) + ", 0) ");
    }

    private void appendDirectAdditionalCost(StringBuilder query) {
        query.append("COALESCE(MIN(o.directadditionalcost), 0) ");
    }

    private void appendRegistrationPriceOverhead(Entity entity, StringBuilder query) {
        query.append("COALESCE(" + entity.getDecimalField(ProductionBalanceFields.REGISTRATION_PRICE_OVERHEAD) + ", 0) ");
    }

    private void appendProfit(Entity entity, StringBuilder query) {
        query.append("COALESCE(" + entity.getDecimalField(ProductionBalanceFields.PROFIT) + ", 0) ");
    }

    private void appendProfitValue(Entity entity, StringBuilder query) {
        appendRealProductionCosts(entity, query);
        query.append(" / 100 * ");
        appendProfit(entity, query);
    }

    private void appendRealProductionCosts(Entity entity, StringBuilder query) {
        query.append("( ");
        appendRegistrationPrice(entity, query);
        query.append("+ ");
        appendRegistrationPriceOverheadValue(entity, query);
        query.append(") ");
    }

    private void appendRegistrationPriceOverheadValue(Entity entity, StringBuilder query) {
        appendRegistrationPrice(entity, query);
        query.append(" / 100 * ");
        appendRegistrationPriceOverhead(entity, query);
    }

    private void appendRegistrationPrice(Entity entity, StringBuilder query) {
        query.append("CASE WHEN ");
        appendProducedQuantity(query);
        query.append("<> 0 THEN (");
        appendTotalCosts(entity, query);
        query.append(")/ ");
        appendProducedQuantity(query);
        query.append("ELSE 0 END ");
    }

    private void appendTotalCosts(Entity entity, StringBuilder query) {
        query.append("MIN(COALESCE(gmc.cost, 0)) + MIN(gpc.cost) + ");
        appendMaterialCostMarginValue(entity, query);
        query.append("+ ");
        appendProductionCostMarginValue(entity, query);
        query.append("+ ");
        appendAdditionalOverhead(entity, query);
        query.append("+ ");
        appendDirectAdditionalCost(query);
    }

    private void appendProductionCostMarginValue(Entity entity, StringBuilder query) {
        appendProductionCostMargin(entity, query);
        query.append("/ 100 * MIN(gpc.cost) ");
    }

    private void appendMaterialCostMarginValue(Entity entity, StringBuilder query) {
        appendMaterialCostMargin(entity, query);
        query.append("/ 100 * MIN(COALESCE(gmc.cost, 0)) ");
    }

    List<OrderBalance> getComponentsBalance(Entity entity, List<Long> ordersIds, List<OrderBalance> ordersBalance) {
        StringBuilder query = new StringBuilder();
        appendComponentsBalanceWithQueries(ordersBalance, query);
        query.append("SELECT ");
        query.append("o.number AS orderNumber, ");
        query.append("prod.id AS productId, ");
        query.append("prod.number AS productNumber, ");
        query.append("prod.name AS productName, ");
        appendProducedQuantity(query);
        query.append("AS producedQuantity, ");
        appendMaterialCostMargin(entity, query);
        query.append("AS materialCostMargin, ");
        appendProductionCostMargin(entity, query);
        query.append("AS productionCostMargin, ");
        appendRegistrationPriceOverhead(entity, query);
        query.append("AS registrationPriceOverhead, ");
        appendProfit(entity, query);
        query.append("AS profit, ");
        query.append("MIN(obr.additional_overhead) AS additionalOverhead, ");
        query.append("MIN(obr.direct_additional_cost) AS directAdditionalCost, ");
        query.append("MIN(obr.material_costs) AS materialCosts, ");
        query.append("MIN(obr.production_costs) AS productionCosts, ");
        query.append("MIN(obr.technical_production_costs) AS technicalProductionCosts, ");
        query.append("MIN(obr.material_cost_margin_value) AS materialCostMarginValue, ");
        query.append("MIN(obr.production_cost_margin_value) AS productionCostMarginValue, ");
        query.append("MIN(obr.total_costs) AS totalCosts, ");
        appendComponentsBalanceRegistrationPrice(query);
        query.append("AS registrationPrice, ");
        appendComponentsBalanceRegistrationPriceOverheadValue(entity, query);
        query.append("AS registrationPriceOverheadValue, ");
        appendComponentsBalanceRealProductionCosts(entity, query);
        query.append("AS realProductionCosts, ");
        appendComponentsBalanceProfitValue(entity, query);
        query.append("AS profitValue, ");
        appendComponentsBalanceRealProductionCosts(entity, query);
        query.append("+ ");
        appendComponentsBalanceProfitValue(entity, query);
        query.append("AS sellPrice ");
        query.append("FROM orders_order o ");
        query.append("JOIN basic_product prod ON o.product_id = prod.id ");
        query.append("LEFT JOIN productioncounting_productiontracking pt ON pt.order_id = o.id AND pt.state = '02accepted' ");
        query.append(
                "LEFT JOIN productioncounting_trackingoperationproductoutcomponent topoc ON topoc.productiontracking_id = pt.id AND topoc.product_id = prod.id ");
        query.append("JOIN order_balance_rec obr ON obr.order_id = o.id ");
        appendWhereClause(query);
        query.append("AND o.root_id IS NULL ");
        query.append("GROUP BY orderNumber, productId, productNumber, productName ");
        query.append("ORDER BY orderNumber ");

        return jdbcTemplate.query(query.toString(), new MapSqlParameterSource("ordersIds", ordersIds),
                BeanPropertyRowMapper.newInstance(OrderBalance.class));
    }

    private void appendComponentsBalanceProfitValue(Entity entity, StringBuilder query) {
        appendComponentsBalanceRealProductionCosts(entity, query);
        query.append(" / 100 * ");
        appendProfit(entity, query);
    }

    private void appendComponentsBalanceRealProductionCosts(Entity entity, StringBuilder query) {
        query.append("( ");
        appendComponentsBalanceRegistrationPrice(query);
        query.append("+ ");
        appendComponentsBalanceRegistrationPriceOverheadValue(entity, query);
        query.append(") ");
    }

    private void appendComponentsBalanceRegistrationPriceOverheadValue(Entity entity, StringBuilder query) {
        appendComponentsBalanceRegistrationPrice(query);
        query.append(" / 100 * ");
        appendRegistrationPriceOverhead(entity, query);
    }

    private void appendComponentsBalanceRegistrationPrice(StringBuilder query) {
        query.append("CASE WHEN ");
        appendProducedQuantity(query);
        query.append("<> 0 THEN MIN(obr.total_costs) ");
        query.append("/ ");
        appendProducedQuantity(query);
        query.append("ELSE 0 END ");
    }

    private void appendComponentsBalanceWithQueries(List<OrderBalance> ordersBalance, StringBuilder query) {
        query.append("WITH order_balance (order_id, root_id, material_costs, ");
        query.append("production_costs, technical_production_costs, material_cost_margin_value, ");
        query.append("production_cost_margin_value, additional_overhead, direct_additional_cost, total_costs ");
        query.append(") AS (VALUES ");

        for (int i = 0; i < ordersBalance.size(); i++) {
            OrderBalance orderBalance = ordersBalance.get(i);
            query.append("(");
            query.append(orderBalance.getOrderId() + ", ");
            query.append(orderBalance.getRootId() + "::INTEGER, ");
            query.append(orderBalance.getMaterialCosts() + ", ");
            query.append(orderBalance.getProductionCosts() + ", ");
            query.append(orderBalance.getTechnicalProductionCosts() + ", ");
            query.append(orderBalance.getMaterialCostMarginValue() + ", ");
            query.append(orderBalance.getProductionCostMarginValue() + ", ");
            query.append(orderBalance.getAdditionalOverhead() + ", ");
            query.append(orderBalance.getDirectAdditionalCost() + ", ");
            query.append(orderBalance.getTotalCosts());
            query.append(") ");
            if (i != ordersBalance.size() - 1) {
                query.append(", ");
            }

        }
        query.append("), ");
        query.append("order_balance_rec AS (WITH RECURSIVE order_balance_rec AS ");
        query.append("(SELECT order_id, order_id AS root_id, material_costs, ");
        query.append("production_costs, technical_production_costs, material_cost_margin_value, ");
        query.append("production_cost_margin_value, additional_overhead, direct_additional_cost, total_costs ");
        query.append("FROM order_balance WHERE root_id IS NULL ");
        query.append("UNION ALL ");
        query.append("SELECT obr.order_id, ob.order_id, ob.material_costs, ");
        query.append("ob.production_costs, ob.technical_production_costs, ob.material_cost_margin_value, ");
        query.append("ob.production_cost_margin_value, ob.additional_overhead, ob.direct_additional_cost, ob.total_costs ");
        query.append("FROM order_balance_rec obr JOIN order_balance ob USING(root_id)) ");
        query.append("SELECT order_id, SUM(material_costs) AS material_costs, SUM(production_costs) AS production_costs, ");
        query.append(
                "SUM(technical_production_costs) AS technical_production_costs, SUM(material_cost_margin_value) AS material_cost_margin_value, ");
        query.append(
                "SUM(production_cost_margin_value) AS production_cost_margin_value, SUM(additional_overhead) AS additional_overhead,  ");
        query.append("SUM(direct_additional_cost) AS direct_additional_cost, SUM(total_costs) AS total_costs ");
        query.append("FROM order_balance_rec GROUP BY order_id) ");
    }

    List<OrderBalance> getProductsBalance(Entity entity, List<Long> ordersIds, List<OrderBalance> componentsBalance) {
        StringBuilder query = new StringBuilder();
        appendProductsBalanceWithQueries(componentsBalance, query);
        query.append("SELECT ");
        query.append("prod.number AS productNumber, ");
        query.append("prod.name AS productName, ");
        appendMaterialCostMargin(entity, query);
        query.append("AS materialCostMargin, ");
        appendProductionCostMargin(entity, query);
        query.append("AS productionCostMargin, ");
        appendRegistrationPriceOverhead(entity, query);
        query.append("AS registrationPriceOverhead, ");
        appendProfit(entity, query);
        query.append("AS profit, ");
        query.append("MIN(gcb.produced_quantity) AS producedQuantity, ");
        query.append("MIN(gcb.additional_overhead) AS additionalOverhead, ");
        query.append("MIN(gcb.direct_additional_cost) AS directAdditionalCost, ");
        query.append("MIN(gcb.material_costs) AS materialCosts, ");
        query.append("MIN(gcb.production_costs) AS productionCosts, ");
        query.append("MIN(gcb.technical_production_costs) AS technicalProductionCosts, ");
        query.append("MIN(gcb.material_cost_margin_value) AS materialCostMarginValue, ");
        query.append("MIN(gcb.production_cost_margin_value) AS productionCostMarginValue, ");
        query.append("MIN(gcb.total_costs) AS totalCosts, ");
        appendProductsBalanceRegistrationPrice(query);
        query.append("AS registrationPrice, ");
        appendProductsBalanceRegistrationPriceOverheadValue(entity, query);
        query.append("AS registrationPriceOverheadValue, ");
        appendProductsBalanceRealProductionCosts(entity, query);
        query.append("AS realProductionCosts, ");
        appendProductsBalanceProfitValue(entity, query);
        query.append("AS profitValue, ");
        appendProductsBalanceRealProductionCosts(entity, query);
        query.append("+ ");
        appendProductsBalanceProfitValue(entity, query);
        query.append("AS sellPrice ");
        query.append("FROM orders_order o ");
        query.append("JOIN basic_product prod ON o.product_id = prod.id ");
        query.append("JOIN grouped_component_balance gcb ON gcb.product_id = prod.id ");
        appendWhereClause(query);
        query.append("AND o.root_id IS NULL ");
        query.append("GROUP BY productNumber, productName ");
        query.append("ORDER BY productNumber ");

        return jdbcTemplate.query(query.toString(), new MapSqlParameterSource("ordersIds", ordersIds),
                BeanPropertyRowMapper.newInstance(OrderBalance.class));
    }

    private void appendProductsBalanceProfitValue(Entity entity, StringBuilder query) {
        appendProductsBalanceRealProductionCosts(entity, query);
        query.append(" / 100 * ");
        appendProfit(entity, query);
    }

    private void appendProductsBalanceRealProductionCosts(Entity entity, StringBuilder query) {
        query.append("( ");
        appendProductsBalanceRegistrationPrice(query);
        query.append("+ ");
        appendProductsBalanceRegistrationPriceOverheadValue(entity, query);
        query.append(") ");
    }

    private void appendProductsBalanceRegistrationPriceOverheadValue(Entity entity, StringBuilder query) {
        appendProductsBalanceRegistrationPrice(query);
        query.append(" / 100 * ");
        appendRegistrationPriceOverhead(entity, query);
    }

    private void appendProductsBalanceRegistrationPrice(StringBuilder query) {
        query.append("CASE WHEN MIN(gcb.produced_quantity) <> 0 THEN MIN(gcb.total_costs) ");
        query.append("/ MIN(gcb.produced_quantity) ELSE 0 END ");
    }

    private void appendProductsBalanceWithQueries(List<OrderBalance> componentsBalance, StringBuilder query) {
        query.append("WITH component_balance (product_id, produced_quantity, material_costs, ");
        query.append("production_costs, technical_production_costs, material_cost_margin_value, ");
        query.append("production_cost_margin_value, additional_overhead, direct_additional_cost, total_costs ");
        query.append(") AS (VALUES ");
        if (componentsBalance.isEmpty()) {
            query.append("(NULL::integer, NULL::numeric, NULL::numeric, NULL::numeric, NULL::numeric, NULL::numeric, ");
            query.append("NULL::numeric, NULL::numeric, NULL::numeric, NULL::numeric) ");
        } else {
            for (int i = 0; i < componentsBalance.size(); i++) {
                OrderBalance orderBalance = componentsBalance.get(i);
                query.append("(");
                query.append(orderBalance.getProductId() + ", ");
                query.append(orderBalance.getProducedQuantity() + ", ");
                query.append(orderBalance.getMaterialCosts() + ", ");
                query.append(orderBalance.getProductionCosts() + ", ");
                query.append(orderBalance.getTechnicalProductionCosts() + ", ");
                query.append(orderBalance.getMaterialCostMarginValue() + ", ");
                query.append(orderBalance.getProductionCostMarginValue() + ", ");
                query.append(orderBalance.getAdditionalOverhead() + ", ");
                query.append(orderBalance.getDirectAdditionalCost() + ", ");
                query.append(orderBalance.getTotalCosts());
                query.append(") ");
                if (i != componentsBalance.size() - 1) {
                    query.append(", ");
                }
            }
        }
        query.append("), ");
        query.append("grouped_component_balance AS (SELECT product_id, SUM(produced_quantity) AS produced_quantity, ");
        query.append("SUM(material_costs) AS material_costs, SUM(production_costs) AS production_costs, ");
        query.append(
                "SUM(technical_production_costs) AS technical_production_costs, SUM(material_cost_margin_value) AS material_cost_margin_value, ");
        query.append(
                "SUM(production_cost_margin_value) AS production_cost_margin_value, SUM(additional_overhead) AS additional_overhead,  ");
        query.append("SUM(direct_additional_cost) AS direct_additional_cost, SUM(total_costs) AS total_costs ");
        query.append("FROM component_balance GROUP BY product_id) ");
    }

    List<Stoppage> getStoppages(List<Long> ordersIds) {
        StringBuilder query = new StringBuilder();
        query.append("SELECT ");
        query.append("o.number AS orderNumber, ");
        query.append("pt.number AS productionTrackingNumber, ");
        query.append("pt.state AS productionTrackingState, ");
        query.append("s.duration, ");
        query.append("s.datefrom AS dateFrom, ");
        query.append("s.dateto AS dateTo, ");
        query.append("sr.name AS reason, ");
        query.append("s.description, ");
        query.append("COALESCE(ptd.number, od.number) AS division, ");
        query.append("pl.number AS productionLine, ");
        query.append("w.number AS workstation, ");
        query.append("stf.name || ' ' || stf.surname AS worker ");
        query.append("FROM stoppage_stoppage s ");
        query.append("JOIN orders_order o ON o.id = s.order_id ");
        query.append("LEFT JOIN productioncounting_productiontracking pt ON pt.id = s.productiontracking_id ");
        query.append("JOIN stoppage_stoppagereason sr ON sr.id = s.reason_id ");
        query.append("LEFT JOIN basic_division ptd ON ptd.id = pt.division_id ");
        query.append("LEFT JOIN basic_division od ON od.id = o.division_id ");
        query.append("LEFT JOIN productionlines_productionline pl ON pl.id = o.productionline_id ");
        query.append("LEFT JOIN basic_workstation w ON w.id = pt.workstation_id ");
        query.append("LEFT JOIN basic_staff stf ON pt.staff_id = stf.id ");
        appendWhereClause(query);
        query.append("ORDER BY orderNumber, productionTrackingNumber, dateFrom ");

        return jdbcTemplate.query(query.toString(), new MapSqlParameterSource("ordersIds", ordersIds),
                BeanPropertyRowMapper.newInstance(Stoppage.class));
    }
}
