/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo Framework
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
package com.qcadoo.mes.productFlowThruDivision.listeners;

import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;
import com.qcadoo.mes.orders.constants.OrdersConstants;
import com.qcadoo.mes.productFlowThruDivision.OrderMaterialAvailability;
import com.qcadoo.mes.productionCounting.constants.ProductionTrackingFields;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.constants.QcadooViewConstants;

@Service
public class OrderDetailsListenersPFTD {

    private static final String L_WINDOW_ACTIVE_MENU = "window.activeMenu";

    private static final String L_GRID_OPTIONS = "grid.options";

    private static final String L_FILTERS = "filters";

    

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private OrderMaterialAvailability orderMaterialAvailability;

    public void showWarehouseIssuesForOrder(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        FormComponent form = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        Entity order = form.getEntity();

        if (order.getId() == null) {
            return;
        }

        String orderNumber = order.getStringField("number");

        if (orderNumber == null) {
            return;
        }

        Map<String, String> filters = Maps.newHashMap();
        filters.put("order", applyInOperator(orderNumber));

        Map<String, Object> gridOptions = Maps.newHashMap();
        gridOptions.put(L_FILTERS, filters);

        Map<String, Object> parameters = Maps.newHashMap();
        parameters.put(L_GRID_OPTIONS, gridOptions);

        parameters.put(L_WINDOW_ACTIVE_MENU, "requirements.warehouseIssue");

        String url = "/page/productFlowThruDivision/warehouseIssueList.html";
        view.redirectTo(url, false, true, parameters);
    }

    private String applyInOperator(final String value) {
        StringBuilder builder = new StringBuilder();
        return builder.append("[").append(value).append("]").toString();
    }

    public void showMaterialAvailabilityForProductionTracking(final ViewDefinitionState view, final ComponentState state,
            final String[] args) {
        FormComponent productionRecordForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);

        Entity productionRecord = productionRecordForm.getEntity();

        Long orderId = productionRecord.getBelongsToField(ProductionTrackingFields.ORDER).getId();

        showMaterialAvailability(view, orderId);
    }

    public void showMaterialAvailabilityForOrder(final ViewDefinitionState view, final ComponentState state,
            final String[] args) {
        Long orderId = (Long) state.getFieldValue();
        showMaterialAvailability(view, orderId);
    }

    private void showMaterialAvailability(ViewDefinitionState view, Long orderId) {
        JSONObject json = new JSONObject();

        try {
            json.put("order.id", orderId);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }

        orderMaterialAvailability.generateAndSaveMaterialAvailabilityForOrder(getOrderDD().get(orderId));

        String url = "../page/productFlowThruDivision/orderWithMaterialAvailabilityList.html?context=" + json.toString();
        view.redirectTo(url, false, true);
    }

    private DataDefinition getOrderDD() {
        return dataDefinitionService.get(OrdersConstants.PLUGIN_IDENTIFIER, OrdersConstants.MODEL_ORDER);
    }

}
