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
package com.qcadoo.mes.materialFlowResources.listeners;

import com.google.common.base.Optional;
import com.qcadoo.commons.functional.Either;
import com.qcadoo.mes.materialFlowResources.constants.MaterialFlowResourcesConstants;
import com.qcadoo.mes.materialFlowResources.constants.ResourceFields;
import com.qcadoo.mes.materialFlowResources.service.ResourceCorrectionService;
import com.qcadoo.model.api.BigDecimalUtils;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ComponentState.MessageType;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.constants.QcadooViewConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ResourceDetailsListeners {

    public static final String L_PRODUCT_ID = "product_id";

    public static final String L_LOCATION_ID = "location_id";

    public static final String L_QUANTITY = "availableQuantity";

    @Autowired
    private ResourceCorrectionService resourceCorrectionService;

    @Autowired
    private NumberService numberService;

    @Autowired
    private DataDefinitionService dataDefinitionService;



    public void createResourceCorrection(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        FormComponent resourceForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        FieldComponent quantityInput = (FieldComponent) view.getComponentByReference(ResourceFields.QUANTITY);
        FieldComponent priceInput = (FieldComponent) view.getComponentByReference(ResourceFields.PRICE);
        FieldComponent conversionInput = (FieldComponent) view.getComponentByReference(ResourceFields.CONVERSION);
        String newQuantity = (String) quantityInput.getFieldValue();
        String newPrice = (String) priceInput.getFieldValue();
        String newConversion = (String) conversionInput.getFieldValue();

        Either<Exception, Optional<BigDecimal>> quantity = BigDecimalUtils.tryParseAndIgnoreSeparator(newQuantity,
                view.getLocale());
        Either<Exception, Optional<BigDecimal>> price = BigDecimalUtils.tryParseAndIgnoreSeparator(newPrice, view.getLocale());
        Either<Exception, Optional<BigDecimal>> conversion = BigDecimalUtils.tryParseAndIgnoreSeparator(newConversion,
                view.getLocale());

        if (quantity.isRight() && quantity.getRight().isPresent()) {
            Entity resource = resourceForm.getPersistedEntityWithIncludedFormValues();
            Entity resourceDb = resource.getDataDefinition().get(resource.getId());
            BigDecimal correctQuantity = quantity.getRight().get();

            BigDecimal beforeQuantity = resourceDb.getDecimalField(ResourceFields.QUANTITY);
            BigDecimal difference = correctQuantity.subtract(beforeQuantity, numberService.getMathContext());

            Entity resourceStockDto = dataDefinitionService
                    .get(MaterialFlowResourcesConstants.PLUGIN_IDENTIFIER,
                            MaterialFlowResourcesConstants.MODEL_RESOURCE_STOCK_DTO).find()
                    .add(SearchRestrictions.eq(L_PRODUCT_ID, resourceDb.getBelongsToField(ResourceFields.PRODUCT).getId().intValue()))
                    .add(SearchRestrictions.eq(L_LOCATION_ID, resourceDb.getBelongsToField(ResourceFields.LOCATION).getId().intValue()))
                    .setMaxResults(1).uniqueResult();

            BigDecimal afterCorrectQuantity = resourceStockDto.getDecimalField(L_QUANTITY).add(difference,
                    numberService.getMathContext());


            if (afterCorrectQuantity.compareTo(BigDecimal.ZERO) < 0) {
                quantityInput.addMessage("materialFlow.error.correction.quantityLesserThanAvailable", MessageType.FAILURE);
            } else if (price.isRight()) {
                if (conversion.isRight() && conversion.getRight().isPresent()) {
                    BigDecimal resourceReservedQuantity = resource.getDecimalField(ResourceFields.RESERVED_QUANTITY);
                    if (correctQuantity.compareTo(BigDecimal.ZERO) > 0) {
                        if (correctQuantity.compareTo(resourceReservedQuantity) >= 0) {
                            boolean corrected = resourceCorrectionService.createCorrectionForResource(resource, false).isPresent();
                            if (!resource.isValid()) {
                                copyErrors(resource, resourceForm);

                            } else if (!corrected) {
                                resourceForm.addMessage("materialFlow.info.correction.resourceNotChanged", MessageType.INFO);

                            } else {
                                resourceForm.performEvent(view, "reset");
                                quantityInput.requestComponentUpdateState();
                                resourceForm.addMessage("materialFlow.success.correction.correctionCreated", MessageType.SUCCESS);
                            }

                        } else {
                            quantityInput.addMessage("materialFlow.error.correction.quantityLesserThanReserved",
                                    MessageType.FAILURE);
                        }

                    } else {
                        quantityInput.addMessage("materialFlow.error.correction.invalidQuantity", MessageType.FAILURE);
                    }
                } else {
                    conversionInput.addMessage("materialFlow.error.correction.invalidConversion", MessageType.FAILURE);
                }
            } else {
                priceInput.addMessage("materialFlow.error.correction.invalidPrice", MessageType.FAILURE);
            }
        } else {
            quantityInput.addMessage("materialFlow.error.correction.invalidQuantity", MessageType.FAILURE);
        }

    }

    private void copyErrors(Entity resource, FormComponent resourceForm) {
        resource.getGlobalErrors().forEach(error -> {
            resourceForm.addMessage(error);
        });

        resource.getErrors().values().forEach(error -> {
            resourceForm.addMessage(error);
        });
    }
}
