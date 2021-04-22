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

import com.google.common.collect.Lists;
import com.qcadoo.mes.advancedGenealogy.criteriaModifier.BatchCriteriaModifier;
import com.qcadoo.mes.basic.constants.AdditionalCodeFields;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.deliveries.DeliveriesService;
import com.qcadoo.mes.deliveries.constants.OrderedProductFields;
import com.qcadoo.mes.technologies.criteriaModifiers.QualityCardCriteriaModifiers;
import com.qcadoo.model.api.Entity;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.api.components.lookup.FilterValueHolder;
import com.qcadoo.view.constants.QcadooViewConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class OrderedProductDetailsHooks {

    

    @Autowired
    private DeliveriesService deliveriesService;

    @Autowired
    private BatchCriteriaModifier batchCriteriaModifier;

    public void beforeRender(final ViewDefinitionState view) {
        FormComponent orderedProductForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);

        Entity orderedProduct = orderedProductForm.getPersistedEntityWithIncludedFormValues();

        fillUnitFields(view);
        fillCurrencyFields(view);

        setBatchLookupProductFilterValue(view, orderedProduct);
        fillCriteriaModifiers(view);
    }

    public void fillUnitFields(final ViewDefinitionState view) {
        List<String> referenceNames = Lists.newArrayList("orderedQuantityUnit");
        List<String> additionalUnitNames = Lists.newArrayList("additionalQuantityUnit");

        deliveriesService.fillUnitFields(view, OrderedProductFields.PRODUCT, referenceNames, additionalUnitNames);

        fillConversion(view);
        fillAdditionalCodesLookup(view);
    }

    private void fillConversion(final ViewDefinitionState view) {
        LookupComponent productLookup = (LookupComponent) view.getComponentByReference(OrderedProductFields.PRODUCT);

        Entity product = productLookup.getEntity();

        if (Objects.nonNull(product)) {
            String additionalUnit = product.getStringField(ProductFields.ADDITIONAL_UNIT);

            FieldComponent conversionField = (FieldComponent) view.getComponentByReference(OrderedProductFields.CONVERSION);

            if (StringUtils.isEmpty(additionalUnit)) {
                conversionField.setFieldValue(BigDecimal.ONE);
                conversionField.setEnabled(false);
                conversionField.requestComponentUpdateState();
            }
        }
    }

    private void fillAdditionalCodesLookup(final ViewDefinitionState view) {
        LookupComponent additionalCodeLookup = (LookupComponent) view
                .getComponentByReference(OrderedProductFields.ADDITIONAL_CODE);
        LookupComponent productLookup = (LookupComponent) view.getComponentByReference(OrderedProductFields.PRODUCT);

        Entity product = productLookup.getEntity();

        if (Objects.nonNull(product)) {
            additionalCodeLookup.setEnabled(true);
            FilterValueHolder filterValueHolder = additionalCodeLookup.getFilterValue();
            filterValueHolder.put(AdditionalCodeFields.PRODUCT, product.getId());
            additionalCodeLookup.setFilterValue(filterValueHolder);
        } else {
            additionalCodeLookup.setFieldValue(null);
            additionalCodeLookup.setEnabled(false);
        }

        additionalCodeLookup.requestComponentUpdateState();
    }

    public void fillCurrencyFields(final ViewDefinitionState view) {
        FormComponent orderedProductForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);

        List<String> referenceNames = Lists.newArrayList("totalPriceCurrency", "pricePerUnitCurrency");

        Entity orderedProduct = orderedProductForm.getEntity();
        Entity delivery = orderedProduct.getBelongsToField(OrderedProductFields.DELIVERY);

        deliveriesService.fillCurrencyFieldsForDelivery(view, referenceNames, delivery);
    }

    public void setBatchLookupProductFilterValue(final ViewDefinitionState view, final Entity orderedProduct) {
        LookupComponent batchLookup = (LookupComponent) view.getComponentByReference(OrderedProductFields.BATCH);

        Entity product = orderedProduct.getBelongsToField(OrderedProductFields.PRODUCT);

        if (Objects.nonNull(product)) {
            batchCriteriaModifier.putProductFilterValue(batchLookup, product);
        }
    }

    private void fillCriteriaModifiers(final ViewDefinitionState viewDefinitionState) {
        LookupComponent product = (LookupComponent) viewDefinitionState.getComponentByReference("product");
        LookupComponent qualityCard = (LookupComponent) viewDefinitionState.getComponentByReference("qualityCard");
        if (product.getEntity() != null) {
            FilterValueHolder filter = qualityCard.getFilterValue();
            filter.put(QualityCardCriteriaModifiers.L_PRODUCT_ID, product.getEntity().getId());
            qualityCard.setFilterValue(filter);
            qualityCard.requestComponentUpdateState();
        }
    }

}
