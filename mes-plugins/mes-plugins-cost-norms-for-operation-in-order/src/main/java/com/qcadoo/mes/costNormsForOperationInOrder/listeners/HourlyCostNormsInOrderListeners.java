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
package com.qcadoo.mes.costNormsForOperationInOrder.listeners;

import com.google.common.collect.Maps;
import com.qcadoo.mes.orders.hooks.CopyOfTechnologyHooks;
import com.qcadoo.model.api.Entity;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class HourlyCostNormsInOrderListeners {

    @Autowired
    private CopyOfTechnologyHooks copyOfTechnologyHooks;

    public final void showHourlyCostNorms(final ViewDefinitionState viewState, final ComponentState triggerState,
            final String[] args) {
        Entity order = copyOfTechnologyHooks.getOrderForTechnology(viewState);

        Map<String, Object> parameters = Maps.newHashMap();
        parameters.put("form.id", order.getId());

        String url = "../page/costNormsForOperationInOrder/hourlyCostNormsInOrderDetails.html";
        viewState.redirectTo(url, false, true, parameters);
    }
}
