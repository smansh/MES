/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 1.4
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.mes.orders.validators;

import com.google.common.collect.Lists;
import com.qcadoo.mes.orders.OperationalTasksService;
import com.qcadoo.mes.orders.constants.OperationalTaskFields;
import com.qcadoo.mes.orders.constants.OperationalTaskType;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.orders.constants.OrdersConstants;
import com.qcadoo.mes.orders.states.constants.OperationalTaskStateStringValues;
import com.qcadoo.mes.technologies.constants.TechnologyOperationComponentFields;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchRestrictions;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.qcadoo.model.api.search.SearchOrders.asc;
import static com.qcadoo.model.api.search.SearchProjections.alias;
import static com.qcadoo.model.api.search.SearchProjections.rowCount;

@Service
public class OperationalTaskValidators {

    private static final String NAME_IS_BLANK_MESSAGE = "orders.operationalTask.error.nameIsBlank";

    private static final String WRONG_DATES_ORDER_MESSAGE = "orders.operationalTask.error.finishDateIsEarlier";

    private static final String L_COUNT = "count";

    @Autowired
    private OperationalTasksService operationalTasksService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    public boolean onValidate(final DataDefinition operationalTaskDD, final Entity operationalTask) {
        boolean isValid = hasName(operationalTaskDD, operationalTask);
        isValid = datesAreInCorrectOrder(operationalTaskDD, operationalTask) && isValid;
        isValid = datesAreCorrect(operationalTaskDD, operationalTask) && isValid;
        isValid = checkIfOrderHasTechnology(operationalTaskDD, operationalTask) && isValid;
        isValid = checkIfFieldSet(operationalTaskDD, operationalTask) && isValid;
        isValid = checkIfAlreadyExists(operationalTaskDD, operationalTask) && isValid;
        isValid = checkWorkstationIsCorrect(operationalTaskDD, operationalTask) && isValid;

        return isValid;
    }

    private boolean checkIfAlreadyExists(DataDefinition operationalTaskDD, Entity operationalTask) {
        String type = operationalTask.getStringField(OperationalTaskFields.TYPE);

        if (OperationalTaskType.EXECUTION_OPERATION_IN_ORDER.getStringValue().equalsIgnoreCase(type)) {

            Entity order = operationalTask.getBelongsToField(OperationalTaskFields.ORDER);
            Entity toc = operationalTask.getBelongsToField(OperationalTaskFields.TECHNOLOGY_OPERATION_COMPONENT);

            SearchCriteriaBuilder scb = dataDefinitionService
                    .get(OrdersConstants.PLUGIN_IDENTIFIER, OrdersConstants.MODEL_OPERATIONAL_TASK).find()
                    .add(SearchRestrictions.belongsTo(OperationalTaskFields.ORDER, order))
                    .add(SearchRestrictions.belongsTo(OperationalTaskFields.TECHNOLOGY_OPERATION_COMPONENT, toc))
                    .add(SearchRestrictions.in(OperationalTaskFields.STATE,
                            Lists.newArrayList(OperationalTaskStateStringValues.PENDING, OperationalTaskStateStringValues.STARTED,
                                    OperationalTaskStateStringValues.FINISHED)));
            if (Objects.nonNull(operationalTask.getId())) {
                scb.add(SearchRestrictions.idNe(operationalTask.getId()));
            }

            scb.setProjection(alias(rowCount(), L_COUNT));
            scb.addOrder(asc(L_COUNT));

            Entity countProjection = scb.setMaxResults(1).uniqueResult();

            boolean isValid = ((countProjection == null) || ((Long) countProjection.getField(L_COUNT) == 0));
            if (!isValid) {
                operationalTask.addGlobalError("orders.operationalTask.error.notUnique");
            }
            return isValid;
        }

        return true;
    }

    private boolean hasName(final DataDefinition operationalTaskDD, final Entity operationalTask) {
        String type = operationalTask.getStringField(OperationalTaskFields.TYPE);

        if (OperationalTaskType.OTHER_CASE.getStringValue().equalsIgnoreCase(type) && hasBlankName(operationalTask)) {
            operationalTask.addError(operationalTaskDD.getField(OperationalTaskFields.NAME), NAME_IS_BLANK_MESSAGE);

            return false;
        }

        return true;
    }

    private boolean hasBlankName(final Entity operationalTask) {
        return StringUtils.isBlank(operationalTask.getStringField(OperationalTaskFields.NAME));
    }

    private boolean datesAreInCorrectOrder(final DataDefinition operationalTaskDD, final Entity operationalTask) {
        Date startDate = operationalTask.getDateField(OperationalTaskFields.START_DATE);
        Date finishDate = operationalTask.getDateField(OperationalTaskFields.FINISH_DATE);

        if (finishDate.before(startDate)) {
            operationalTask.addError(operationalTaskDD.getField(OperationalTaskFields.START_DATE), WRONG_DATES_ORDER_MESSAGE);
            operationalTask.addError(operationalTaskDD.getField(OperationalTaskFields.FINISH_DATE), WRONG_DATES_ORDER_MESSAGE);

            return false;
        }

        return true;
    }

    private boolean datesAreCorrect(DataDefinition operationalTaskDD, Entity operationalTask) {
        Entity technologyOperationComponent = operationalTask
                .getBelongsToField(OperationalTaskFields.TECHNOLOGY_OPERATION_COMPONENT);
        if (technologyOperationComponent != null
                && operationalTask.getBelongsToField(OperationalTaskFields.WORKSTATION) != null) {
            Date startDate = operationalTask.getDateField(OperationalTaskFields.START_DATE);
            Date finishDate = operationalTask.getDateField(OperationalTaskFields.FINISH_DATE);
            Entity order = operationalTask.getBelongsToField(OperationalTaskFields.ORDER);
            Entity parent = operationalTasksService.getParent(technologyOperationComponent, order);
            if (parent != null && parent.getBelongsToField(OperationalTaskFields.WORKSTATION) != null) {
                if (parent.getDateField(OperationalTaskFields.START_DATE).before(startDate)) {
                    operationalTask.addError(operationalTaskDD.getField(OperationalTaskFields.START_DATE),
                            "orders.operationalTask.error.inappropriateStartDateNext");
                    return false;
                }
                if (parent.getDateField(OperationalTaskFields.FINISH_DATE).before(finishDate)) {
                    operationalTask.addError(operationalTaskDD.getField(OperationalTaskFields.FINISH_DATE),
                            "orders.operationalTask.error.inappropriateFinishDateNext");
                    return false;
                }
            }
            List<Entity> children = operationalTasksService.getChildren(technologyOperationComponent, order);
            for (Entity child : children) {
                if (child.getBelongsToField(OperationalTaskFields.WORKSTATION) != null) {
                    if (child.getDateField(OperationalTaskFields.START_DATE).after(startDate)) {
                        operationalTask.addError(operationalTaskDD.getField(OperationalTaskFields.START_DATE),
                                "orders.operationalTask.error.inappropriateStartDatePrevious");
                        return false;
                    }
                    if (child.getDateField(OperationalTaskFields.FINISH_DATE).after(finishDate)) {
                        operationalTask.addError(operationalTaskDD.getField(OperationalTaskFields.FINISH_DATE),
                                "orders.operationalTask.error.inappropriateFinishDatePrevious");
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean checkIfOrderHasTechnology(final DataDefinition operationalTaskDD, final Entity operationalTask) {
        Entity order = operationalTask.getBelongsToField(OperationalTaskFields.ORDER);

        if (order == null) {
            return true;
        }

        Entity technology = order.getBelongsToField(OrderFields.TECHNOLOGY);

        if (technology == null) {
            operationalTask.addError(operationalTaskDD.getField(OperationalTaskFields.ORDER),
                    "orders.operationalTask.order.error.technologyIsNull");
            return false;
        }

        return true;
    }

    private boolean checkIfFieldSet(DataDefinition operationalTaskDD, Entity operationalTask) {
        String type = operationalTask.getStringField(OperationalTaskFields.TYPE);

        if (operationalTasksService.isOperationalTaskTypeExecutionOperationInOrder(type)) {
            boolean valid = true;
            Entity order = operationalTask.getBelongsToField(OperationalTaskFields.ORDER);
            if (Objects.isNull(order)) {
                operationalTask.addError(operationalTaskDD.getField(OperationalTaskFields.ORDER),
                        "qcadooView.validate.field.error.missing");
                valid = false;
            }

            Entity toc = operationalTask.getBelongsToField(OperationalTaskFields.TECHNOLOGY_OPERATION_COMPONENT);
            if (Objects.isNull(toc)) {
                operationalTask.addError(operationalTaskDD.getField(OperationalTaskFields.TECHNOLOGY_OPERATION_COMPONENT),
                        "qcadooView.validate.field.error.missing");
                valid = false;
            }
            return valid;
        }
        return true;
    }

    private boolean checkWorkstationIsCorrect(DataDefinition operationalTaskDD, Entity operationalTask) {
        Entity technologyOperationComponent = operationalTask
                .getBelongsToField(OperationalTaskFields.TECHNOLOGY_OPERATION_COMPONENT);
        Entity workstation = operationalTask.getBelongsToField(OperationalTaskFields.WORKSTATION);
        if (technologyOperationComponent != null && workstation != null) {
            List<Entity> workstations = technologyOperationComponent
                    .getManyToManyField(TechnologyOperationComponentFields.WORKSTATIONS);
            if (!workstations.isEmpty() && workstations.stream().noneMatch(w -> w.getId().equals(workstation.getId()))) {
                operationalTask.addError(operationalTaskDD.getField(OperationalTaskFields.WORKSTATION),
                        "orders.error.inappropriateWorkstationForOperation");
                return false;
            }
        }
        return true;
    }
}
