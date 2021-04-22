package com.qcadoo.mes.productionScheduling.listeners;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.qcadoo.mes.basic.ParameterService;
import com.qcadoo.mes.basic.constants.ProductFamilyElementType;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.operationTimeCalculations.OperationWorkTimeService;
import com.qcadoo.mes.operationTimeCalculations.OrderRealizationTimeService;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.orders.constants.OrdersConstants;
import com.qcadoo.mes.orders.constants.ScheduleFields;
import com.qcadoo.mes.orders.constants.SchedulePositionFields;
import com.qcadoo.mes.orders.listeners.ScheduleDetailsListeners;
import com.qcadoo.mes.productionLines.constants.ProductionLinesConstants;
import com.qcadoo.mes.productionScheduling.constants.OperCompTimeCalculation;
import com.qcadoo.mes.productionScheduling.constants.OrderFieldsPS;
import com.qcadoo.mes.productionScheduling.constants.OrderTimeCalculationFields;
import com.qcadoo.mes.technologies.ProductQuantitiesService;
import com.qcadoo.mes.technologies.TechnologyService;
import com.qcadoo.mes.technologies.constants.OperationProductOutComponentFields;
import com.qcadoo.mes.technologies.constants.TechnologyFields;
import com.qcadoo.mes.technologies.dto.OperationProductComponentWithQuantityContainer;
import com.qcadoo.mes.timeNormsForOperations.constants.TechnologyOperationComponentFieldsTNFO;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.GridComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class ScheduleDetailsListenersPS {

    private static final String L_ORDERS = "orders";

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private ProductQuantitiesService productQuantitiesService;

    @Autowired
    private OrderRealizationTimeService orderRealizationTimeService;

    @Autowired
    private OperationWorkTimeService operationWorkTimeService;

    @Autowired
    private ParameterService parameterService;

    @Autowired
    private TechnologyService technologyService;

    @Autowired
    private ScheduleDetailsListeners scheduleDetailsListeners;

    @Transactional
    public void generatePlan(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        getOperations(view, state, args);
        scheduleDetailsListeners.assignOperationsToWorkstations(view, state, args);
        scheduleDetailsListeners.assignWorkersToOperations(view, state, args);
    }

    @Transactional
    public void getOperations(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        GridComponent ordersGrid = (GridComponent) view.getComponentByReference(L_ORDERS);
        Map<Long, OperationProductComponentWithQuantityContainer> ordersOperationsQuantity = Maps.newHashMap();
        List<Entity> orders = ordersGrid.getEntities();
        for (Entity order : orders) {
            OperationProductComponentWithQuantityContainer operationProductComponentWithQuantityContainer = generateRealizationTime(
                    order, order.getBelongsToField(OrderFields.PRODUCTION_LINE).getId());
            ordersOperationsQuantity.put(order.getId(), operationProductComponentWithQuantityContainer);
        }

        DataDefinition schedulePositionDD = dataDefinitionService.get(OrdersConstants.PLUGIN_IDENTIFIER,
                OrdersConstants.MODEL_SCHEDULE_POSITION);
        List<Entity> positions = Lists.newArrayList();
        FormComponent formComponent = (FormComponent) state;
        Entity schedule = formComponent.getEntity();
        for (Entity order : orders) {
            List<Entity> orderTimeCalculations = order.getHasManyField(OrderFieldsPS.ORDER_TIME_CALCULATIONS);
            if (!orderTimeCalculations.isEmpty()) {
                List<Entity> operCompTimeCalculations = orderTimeCalculations.get(0)
                        .getHasManyField(OrderTimeCalculationFields.OPER_COMP_TIME_CALCULATIONS);
                for (Entity operCompTimeCalculation : operCompTimeCalculations) {
                    Entity schedulePosition = createSchedulePosition(schedule, schedulePositionDD, order, operCompTimeCalculation,
                            ordersOperationsQuantity);
                    positions.add(schedulePosition);
                }
            }
        }

        schedule.setField(ScheduleFields.POSITIONS, positions);
        schedule = schedule.getDataDefinition().save(schedule);
        formComponent.setEntity(schedule);
        view.addMessage("productionScheduling.info.schedulePositionsGenerated", ComponentState.MessageType.SUCCESS);
    }

    private Entity createSchedulePosition(Entity schedule, DataDefinition schedulePositionDD, Entity order,
            Entity operCompTimeCalculation, Map<Long, OperationProductComponentWithQuantityContainer> ordersOperationsQuantity) {
        Entity technologyOperationComponent = operCompTimeCalculation
                .getBelongsToField(OperCompTimeCalculation.TECHNOLOGY_OPERATION_COMPONENT);
        Entity schedulePosition = schedulePositionDD.create();
        schedulePosition.setField(SchedulePositionFields.SCHEDULE, schedule);
        schedulePosition.setField(OrdersConstants.MODEL_ORDER, order);
        schedulePosition.setField(SchedulePositionFields.TECHNOLOGY_OPERATION_COMPONENT, technologyOperationComponent);
        Entity mainOutputProductComponent = technologyService.getMainOutputProductComponent(technologyOperationComponent);
        Entity product = mainOutputProductComponent.getBelongsToField(OperationProductOutComponentFields.PRODUCT);
        if (ProductFamilyElementType.PRODUCTS_FAMILY.getStringValue().equals(product.getField(ProductFields.ENTITY_TYPE))) {
            product = order.getBelongsToField(OrderFields.PRODUCT);
        }
        schedulePosition.setField(SchedulePositionFields.PRODUCT, product);
        OperationProductComponentWithQuantityContainer operationProductComponentWithQuantityContainer = ordersOperationsQuantity
                .get(order.getId());
        BigDecimal productComponentQuantity = operationProductComponentWithQuantityContainer.get(mainOutputProductComponent);
        schedulePosition.setField(SchedulePositionFields.QUANTITY, productComponentQuantity);
        schedulePosition.setField(SchedulePositionFields.ADDITIONAL_TIME,
                technologyOperationComponent.getIntegerField(TechnologyOperationComponentFieldsTNFO.TIME_NEXT_OPERATION));
        schedulePosition.setField(OperCompTimeCalculation.LABOR_WORK_TIME,
                operCompTimeCalculation.getIntegerField(OperCompTimeCalculation.LABOR_WORK_TIME));
        schedulePosition.setField(SchedulePositionFields.MACHINE_WORK_TIME,
                operCompTimeCalculation.getIntegerField(OperCompTimeCalculation.MACHINE_WORK_TIME));
        return schedulePosition;
    }

    @Transactional
    public OperationProductComponentWithQuantityContainer generateRealizationTime(final Entity order,
            final Long productionLineId) {
        Entity productionLine = dataDefinitionService
                .get(ProductionLinesConstants.PLUGIN_IDENTIFIER, ProductionLinesConstants.MODEL_PRODUCTION_LINE)
                .get(productionLineId);

        BigDecimal quantity = orderRealizationTimeService
                .getBigDecimalFromField(order.getDecimalField(OrderFields.PLANNED_QUANTITY), LocaleContextHolder.getLocale());
        boolean includeTpz = parameterService.getParameter().getBooleanField("includeTpzPS");
        boolean includeAdditionalTime = parameterService.getParameter().getBooleanField("includeAdditionalTimePS");

        final Map<Long, BigDecimal> operationRuns = Maps.newHashMap();
        Entity technology = order.getBelongsToField(OrderFields.TECHNOLOGY);
        if (technology == null) {
            return null;
        }

        OperationProductComponentWithQuantityContainer operationProductComponentWithQuantityContainer = productQuantitiesService
                .getProductComponentQuantities(technology, quantity, operationRuns);

        operationWorkTimeService.deleteOperCompTimeCalculations(order);

        operationWorkTimeService.estimateTotalWorkTimeForOrder(order, operationRuns, includeTpz, includeAdditionalTime,
                productionLine, true);

        orderRealizationTimeService.estimateMaxOperationTimeConsumptionForWorkstation(order,
                technology.getTreeField(TechnologyFields.OPERATION_COMPONENTS).getRoot(), quantity, includeTpz,
                includeAdditionalTime, productionLine);
        return operationProductComponentWithQuantityContainer;
    }
}
