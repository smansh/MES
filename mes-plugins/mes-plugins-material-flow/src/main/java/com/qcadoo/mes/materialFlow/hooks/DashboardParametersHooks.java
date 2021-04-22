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
package com.qcadoo.mes.materialFlow.hooks;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.qcadoo.localization.api.TranslationService;
import com.qcadoo.mes.materialFlow.constants.ParameterFieldsMF;
import com.qcadoo.mes.materialFlow.constants.WhatToShowOnDashboard;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.constants.QcadooViewConstants;

@Component
public class DashboardParametersHooks {

    @Autowired
    private TranslationService translationService;

    public void onBeforeRender(final ViewDefinitionState view) {
        setFieldsEnabledAndClear(view);
    }

    private void setFieldsEnabledAndClear(final ViewDefinitionState view) {
        FormComponent parameterForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        FieldComponent whatToShowOnDashboardField = (FieldComponent) view
                .getComponentByReference(ParameterFieldsMF.WHAT_TO_SHOW_ON_DASHBOARD);
        LookupComponent dashboardOperationLookup = (LookupComponent) view
                .getComponentByReference(ParameterFieldsMF.DASHBOARD_OPERATION);

        Long parameterId = parameterForm.getEntityId();
        String whatToShowOnDashboard = (String) whatToShowOnDashboardField.getFieldValue();

        boolean isEnabled = Objects.nonNull(parameterId);
        boolean isOrders = WhatToShowOnDashboard.ORDERS.getStringValue().equals(whatToShowOnDashboard);

        if (!isOrders) {
            dashboardOperationLookup.setFieldValue(null);
        }

        dashboardOperationLookup.setEnabled(isEnabled && isOrders);
        dashboardOperationLookup.requestComponentUpdateState();
    }

}
