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
package com.qcadoo.mes.deliveries.hooks;

import com.qcadoo.mes.basic.CompanyService;
import com.qcadoo.mes.deliveries.constants.CompanyFieldsD;
import com.qcadoo.model.api.Entity;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.WindowComponent;
import com.qcadoo.view.api.ribbon.RibbonActionItem;
import com.qcadoo.view.api.ribbon.RibbonGroup;
import com.qcadoo.view.constants.QcadooViewConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class CompanyDetailsHooksD {

    



    private static final String L_SUPPLIERS = "suppliers";

    private static final String L_REDIRECT_TO_FILTERED_DELIVERIES_LIST = "redirectToFilteredDeliveriesList";

    @Autowired
    private CompanyService companyService;

    public void disabledGridWhenCompanyIsOwner(final ViewDefinitionState view) {
        companyService.disabledGridWhenCompanyIsOwner(view, "productsFamilies", "products");
    }

    public void disableBufferWhenCompanyIsOwner(final ViewDefinitionState view) {
        FormComponent companyForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        FieldComponent bufferField = (FieldComponent) view.getComponentByReference(CompanyFieldsD.BUFFER);

        Boolean isOwner = companyService.isCompanyOwner(companyForm.getEntity());

        bufferField.setEnabled(!isOwner);
    }

    public void updateRibbonState(final ViewDefinitionState view) {
        FormComponent companyForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);

        WindowComponent window = (WindowComponent) view.getComponentByReference(QcadooViewConstants.L_WINDOW);
        RibbonGroup suppliers = window.getRibbon().getGroupByName(L_SUPPLIERS);
        RibbonActionItem redirectToFilteredDeliveriesList = suppliers.getItemByName(L_REDIRECT_TO_FILTERED_DELIVERIES_LIST);

        Entity company = companyForm.getEntity();

        boolean isEnabled = Objects.nonNull(company.getId());

        updateButtonState(redirectToFilteredDeliveriesList, isEnabled);
    }

    private void updateButtonState(final RibbonActionItem ribbonActionItem, final boolean isEnabled) {
        ribbonActionItem.setEnabled(isEnabled);
        ribbonActionItem.requestUpdate(true);
    }

}
