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
package com.qcadoo.mes.masterOrders.hooks;

import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.masterOrders.constants.MasterOrderPositionDtoFields;
import com.qcadoo.mes.masterOrders.constants.MasterOrderProductFields;
import com.qcadoo.mes.masterOrders.constants.MasterOrdersConstants;
import com.qcadoo.model.api.BigDecimalUtils;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.plugin.api.PluginUtils;
import com.qcadoo.view.api.ComponentState.MessageType;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.constants.QcadooViewConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Objects;

@Service
public class MasterOrderProductDetailsHooks {

    @Autowired
    private MasterOrderDetailsHooks masterOrderDetailsHooks;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private NumberService numberService;

    public void fillUnitField(final ViewDefinitionState view) {
        LookupComponent productField = (LookupComponent) view.getComponentByReference(MasterOrderProductFields.PRODUCT);
        Entity product = productField.getEntity();
        String unit = null;

        if (product != null) {
            unit = product.getStringField(ProductFields.UNIT);

        }
        for (String reference : Arrays.asList("cumulatedOrderQuantityUnit", "masterOrderQuantityUnit",
                "producedOrderQuantityUnit", "leftToReleaseUnit", "quantityRemainingToOrderUnit",
                "quantityTakenFromWarehouseUnit")) {
            FieldComponent field = (FieldComponent) view.getComponentByReference(reference);
            field.setFieldValue(unit);
            field.requestComponentUpdateState();
        }

    }

    public void fillQuantities(final ViewDefinitionState view) {
        FormComponent form = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        Entity productComponent = form.getEntity();
        if (productComponent.getId() != null) {
            Entity masterOrderProductDto = dataDefinitionService.get(MasterOrdersConstants.PLUGIN_IDENTIFIER,
                    MasterOrdersConstants.MODEL_MASTER_ORDER_POSITION_DTO).get(productComponent.getId());
            FieldComponent cumulatedOrderQuantity = (FieldComponent) view
                    .getComponentByReference(MasterOrderProductFields.CUMULATED_ORDER_QUANTITY);
            cumulatedOrderQuantity.setFieldValue(numberService.format(masterOrderProductDto
                    .getDecimalField(MasterOrderPositionDtoFields.CUMULATED_MASTER_ORDER_QUANTITY)));
            cumulatedOrderQuantity.requestComponentUpdateState();

            FieldComponent leftToRelease = (FieldComponent) view.getComponentByReference(MasterOrderProductFields.LEFT_TO_RELASE);
            leftToRelease.setFieldValue(numberService.format(masterOrderProductDto
                    .getDecimalField(MasterOrderPositionDtoFields.LEFT_TO_RELEASE)));
            leftToRelease.requestComponentUpdateState();

            FieldComponent producedOrderQuantity = (FieldComponent) view
                    .getComponentByReference(MasterOrderProductFields.PRODUCED_ORDER_QUANTITY);
            producedOrderQuantity.setFieldValue(numberService.format(masterOrderProductDto
                    .getDecimalField(MasterOrderPositionDtoFields.PRODUCED_ORDER_QUANTITY)));
            producedOrderQuantity.requestComponentUpdateState();

            FieldComponent quantityRemainingToOrder = (FieldComponent) view
                    .getComponentByReference(MasterOrderProductFields.QUANTITY_REMAINING_TO_ORDER);
            quantityRemainingToOrder.setFieldValue(numberService.format(masterOrderProductDto
                    .getDecimalField(MasterOrderPositionDtoFields.QUANTITY_REMAINING_TO_ORDER)));
            quantityRemainingToOrder.requestComponentUpdateState();
        }
    }

    public void fillDefaultTechnology(final ViewDefinitionState view) {
        if (PluginUtils.isEnabled("goodFood")) {
            FieldComponent technology = (FieldComponent) view.getComponentByReference("technology");
            technology.setRequired(true);
            technology.requestComponentUpdateState();
        }
        masterOrderDetailsHooks.fillDefaultTechnology(view);
    }

    public void showErrorWhenCumulatedQuantity(final ViewDefinitionState view) {
        if (view.isViewAfterRedirect()) {
            FormComponent masterOrderProductForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
            Entity masterOrderProduct = masterOrderProductForm.getPersistedEntityWithIncludedFormValues();

            if ((masterOrderProduct == null) || !masterOrderProduct.isValid()) {
                return;
            }

            if (Objects.nonNull(masterOrderProduct.getId())) {
                Entity masterOrderProductDto = dataDefinitionService.get(MasterOrdersConstants.PLUGIN_IDENTIFIER,
                        MasterOrdersConstants.MODEL_MASTER_ORDER_POSITION_DTO).get(masterOrderProduct.getId());
                if (BigDecimal.ZERO.compareTo(BigDecimalUtils.convertNullToZero(masterOrderProductDto
                        .getDecimalField(MasterOrderPositionDtoFields.QUANTITY_REMAINING_TO_ORDER))) < 0) {
                    masterOrderProductForm.addMessage("masterOrders.masterOrder.masterOrderCumulatedQuantityField.wrongQuantity",
                            MessageType.INFO, false);
                }
            }
        }
    }

}
