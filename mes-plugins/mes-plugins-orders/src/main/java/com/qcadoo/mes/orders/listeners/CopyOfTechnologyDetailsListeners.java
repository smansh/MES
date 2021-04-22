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
package com.qcadoo.mes.orders.listeners;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.qcadoo.mes.orders.TechnologyServiceO;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.orders.constants.OrdersConstants;
import com.qcadoo.mes.orders.states.CopyOfTechnologyStateChangeVC;
import com.qcadoo.mes.states.service.client.util.ViewContextHolder;
import com.qcadoo.mes.technologies.constants.TechnologyFields;
import com.qcadoo.model.api.*;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.constants.QcadooViewConstants;
import org.apache.commons.lang3.ObjectUtils;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class CopyOfTechnologyDetailsListeners {

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private TechnologyServiceO technologyServiceO;

    @Autowired
    private CopyOfTechnologyStateChangeVC copyOfTechnologyStateChangeVC;

    @Transactional
    public void changePatternTechnology(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        final FormComponent technologyForm = (FormComponent) state;

        LookupComponent technologyPrototypeLookup = (LookupComponent) view
                .getComponentByReference(TechnologyFields.TECHNOLOGY_PROTOTYPE);

        Long technologyId = (Long) state.getFieldValue();

        if (technologyId != null) {
            Entity technology = technologyServiceO.getTechnologyDD().get(technologyId);
            Entity order = getOrderWithTechnology(view);

            Entity orderTechnologyPrototype = order.getBelongsToField(OrderFields.TECHNOLOGY_PROTOTYPE);

            Entity technologyPrototype = technologyPrototypeLookup.getEntity();

            if (technologyAndOrderPrototypesAreDifferent(orderTechnologyPrototype, technologyPrototype)) {
                Entity copyOfTechnology = copyTechnology(technologyPrototype, order);

                if (copyOfTechnology.isValid()) {
                    order.setField(OrderFields.TECHNOLOGY, null);

                    order = order.getDataDefinition().save(order);

                    deleteTechnology(technology);

                    order.setField(OrderFields.TECHNOLOGY, copyOfTechnology);
                    order.setField(OrderFields.TECHNOLOGY_PROTOTYPE, technologyPrototype);

                    order.getDataDefinition().save(order);

                    state.setFieldValue(copyOfTechnology.getId());

                    technologyForm.setEntity(copyOfTechnology);
                }
            }
        }
    }

    private boolean technologyAndOrderPrototypesAreDifferent(final Entity orderTechnologyPrototypeOrNull,
            final Entity technologyPrototypeOrNull) {
        return technologyPrototypeOrNull != null && orderTechnologyPrototypeOrNull != null
                && !ObjectUtils.equals(technologyPrototypeOrNull.getId(), orderTechnologyPrototypeOrNull.getId());
    }

    public void checkTechnology(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        final FormComponent technologyForm = (FormComponent) state;

        Long technologyId = (Long) state.getFieldValue();

        if (technologyId != null) {
            Entity technology1 = technologyServiceO.getTechnologyDD().get(technologyId);
            Entity order = getOrderWithTechnology(view);

            order = technologyServiceO.createTechnologyIfPktDisabled(order.getDataDefinition(), order);

            Entity technology2 = order.getBelongsToField(OrderFields.TECHNOLOGY);

            if (!Objects.equal(technology1.getId(), technology2.getId())) {
                Map<String, Object> parameters = Maps.newHashMap();

                parameters.put("form.id", technology2.getId());
                parameters.put("form.orderId", order.getId());

                String url = "../page/orders/copyOfTechnologyDetails.html";
                view.redirectTo(url, false, false, parameters);
            } else {
                copyOfTechnologyStateChangeVC.changeState(new ViewContextHolder(view, technologyForm), args[0], technology2);
            }
        }
    }

    public void performBack(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        Map<String, Object> parameters = Maps.newHashMap();

        Entity order = getOrderWithTechnology(view);
        parameters.put("form.id", order.getId());

        String url = "../page/orders/orderDetails.html";
        view.redirectTo(url, false, false, parameters);
    }

    @Transactional
    public void clearAndLoadPatternTechnology(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        final FormComponent technologyForm = (FormComponent) state;

        Entity technology = technologyForm.getPersistedEntityWithIncludedFormValues();
        Entity order = getOrderWithTechnology(view);

        Entity orderTechnologyPrototype = order.getBelongsToField(OrderFields.TECHNOLOGY_PROTOTYPE);

        if (orderTechnologyPrototype != null && technology.getId().equals(orderTechnologyPrototype.getId())) {
            Entity copyOfTechnology = copyTechnology(orderTechnologyPrototype, order);

            if (copyOfTechnology.isValid()) {
                order.setField(OrderFields.TECHNOLOGY, copyOfTechnology);

                order.getDataDefinition().save(order);

                state.setFieldValue(copyOfTechnology.getId());
                technologyForm.setEntity(copyOfTechnology);
            } else {
                technologyForm.addMessage("orders.copyOfTechnology.reloadFromPattern.failure.validationError",
                        ComponentState.MessageType.FAILURE);

                return;
            }

            EntityOpResult deleteResult = deleteTechnology(technology);

            if (!deleteResult.isSuccessfull()) {
                technologyForm.addMessage("orders.copyOfTechnology.reloadFromPattern.failure.deletePrevented",
                        ComponentState.MessageType.FAILURE);
            }
        }
    }

    private Entity getOrderWithTechnology(final ViewDefinitionState view) {
        DataDefinition orderDD = dataDefinitionService.get(OrdersConstants.PLUGIN_IDENTIFIER, OrdersConstants.MODEL_ORDER);

        String orderId = null;

        try {
            orderId = view.getJsonContext().getString("window.mainTab.technology.orderId");
        } catch (JSONException ex) {
            EntityList entities = ((FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM))
                    .getPersistedEntityWithIncludedFormValues().getHasManyField("orders");
            if (!entities.isEmpty()) {
                orderId = String.valueOf(entities.get(0).getId());
            }
        }

        return orderDD.get(Long.valueOf(orderId));
    }

    private Entity copyTechnology(final Entity technologyPrototype, final Entity order) {
        String number = technologyServiceO.generateNumberForTechnologyInOrder(order, technologyPrototype);

        Entity copyOfTechnology = technologyServiceO.getTechnologyDD().copy(technologyPrototype.getId()).get(0);

        copyOfTechnology.setField(TechnologyFields.NUMBER, number);
        copyOfTechnology.setField(TechnologyFields.TECHNOLOGY_PROTOTYPE, technologyPrototype);
        copyOfTechnology.setField(TechnologyFields.TECHNOLOGY_TYPE, TechnologyServiceO.WITH_PATTERN_TECHNOLOGY);
        copyOfTechnology = copyOfTechnology.getDataDefinition().save(copyOfTechnology);

        return copyOfTechnology;
    }

    private EntityOpResult deleteTechnology(final Entity technology) {
        return technology.getDataDefinition().delete(technology.getId());
    }
}
