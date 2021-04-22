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
package com.qcadoo.mes.productFlowThruDivision.hooks;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qcadoo.mes.productFlowThruDivision.constants.MaterialAvailabilityFields;
import com.qcadoo.mes.productFlowThruDivision.constants.ProductFlowThruDivisionConstants;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.GridComponent;
import com.qcadoo.view.api.components.WindowComponent;
import com.qcadoo.view.api.ribbon.RibbonActionItem;
import com.qcadoo.view.api.ribbon.RibbonGroup;
import com.qcadoo.view.constants.QcadooViewConstants;

@Service
public class OrderWithMaterialAvailabilityListHooks {





    private static final String L_MATERIAL_AVAILABILITY = "materialAvailability";

    private static final String L_SHOW_AVAILABILITY = "showAvailability";

    private static final String L_SHOW_REPLACEMENTS_AVAILABILITY = "showReplacementsAvailability";

    private static final String FROM_TERMINAL = "window.mainTab.availabilityComponentForm.gridLayout.terminal";

    @Autowired
    private DataDefinitionService dataDefinitionService;

    public void toggleShowAvailabilityButton(final ViewDefinitionState view) {
        WindowComponent window = (WindowComponent) view.getComponentByReference(QcadooViewConstants.L_WINDOW);
        RibbonGroup materialAvailability = (RibbonGroup) window.getRibbon().getGroupByName(L_MATERIAL_AVAILABILITY);
        RibbonActionItem showAvailability = (RibbonActionItem) materialAvailability.getItemByName(L_SHOW_AVAILABILITY);
        RibbonActionItem showReplacementsAvailability = (RibbonActionItem) materialAvailability
                .getItemByName(L_SHOW_REPLACEMENTS_AVAILABILITY);
        JSONObject obj = view.getJsonContext();

        GridComponent grid = (GridComponent) view.getComponentByReference(QcadooViewConstants.L_GRID);

        if (grid.getSelectedEntitiesIds().size() != 1) {
            showAvailability.setEnabled(false);
            showReplacementsAvailability.setEnabled(false);
        } else {
            showAvailability.setEnabled(true);
            Entity selected = dataDefinitionService.get(ProductFlowThruDivisionConstants.PLUGIN_IDENTIFIER,
                    ProductFlowThruDivisionConstants.MODEL_MATERIAL_AVAILABILITY).get(
                    grid.getSelectedEntitiesIds().stream().findFirst().get());
            if (selected.getBooleanField(MaterialAvailabilityFields.REPLACEMENT)) {
                showReplacementsAvailability.setEnabled(true);
            } else {
                showReplacementsAvailability.setEnabled(false);
            }
        }
        showAvailability.setMessage("orderWithMaterialAvailabilityList.materialAvailability.ribbon.message.selectOneRecord");
        showReplacementsAvailability
                .setMessage("orderWithMaterialAvailabilityList.materialAvailability.ribbon.message.selectOneRecordWithReplacements");
        if (obj.has(FROM_TERMINAL)) {
            showAvailability.setEnabled(false);
            showAvailability.setMessage(null);
            showReplacementsAvailability.setEnabled(false);
            showReplacementsAvailability.setMessage(null);

        }
        showAvailability.requestUpdate(true);
        showReplacementsAvailability.requestUpdate(true);
    }
}
