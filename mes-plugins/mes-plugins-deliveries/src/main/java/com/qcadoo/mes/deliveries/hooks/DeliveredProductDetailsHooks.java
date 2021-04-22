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

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.qcadoo.mes.advancedGenealogy.criteriaModifier.BatchCriteriaModifier;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.basic.constants.UnitConversionItemFieldsB;
import com.qcadoo.mes.deliveries.DeliveriesService;
import com.qcadoo.mes.deliveries.constants.DeliveredProductFields;
import com.qcadoo.mes.deliveries.constants.DeliveryFields;
import com.qcadoo.mes.deliveries.constants.OrderedProductFields;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.model.api.units.PossibleUnitConversions;
import com.qcadoo.model.api.units.UnitConversionService;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.GridComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.api.components.lookup.FilterValueHolder;
import com.qcadoo.view.constants.QcadooViewConstants;

@Service
public class DeliveredProductDetailsHooks {

    

    private static final String L_DELIVERED_PRODUCT_RESERVATIONS = "deliveredProductReservations";

    private static final String L_LOCATION = "location";

    private static final String L_PRODUCT = "product";

    @Autowired
    private NumberService numberService;

    @Autowired
    private DeliveriesService deliveriesService;

    @Autowired
    private UnitConversionService unitConversionService;

    @Autowired
    private BatchCriteriaModifier batchCriteriaModifier;

    public void beforeRender(final ViewDefinitionState view) {
        FormComponent deliveredProductForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);

        Entity deliveredProduct = deliveredProductForm.getPersistedEntityWithIncludedFormValues();

        fillOrderedQuantities(view);
        fillUnitFields(view);
        fillCurrencyFields(view);
        setDeliveredQuantityFieldRequired(view);
        setAdditionalQuantityFieldRequired(view);
        lockConversion(view);
        setFilters(view);
        disableReservationsForWaste((view));

        setBatchLookupProductFilterValue(view, deliveredProduct);
    }

    private void disableReservationsForWaste(final ViewDefinitionState view) {
        FormComponent deliveredProductForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        GridComponent reservationsGrid = (GridComponent) view.getComponentByReference(L_DELIVERED_PRODUCT_RESERVATIONS);

        Entity deliveredProduct = deliveredProductForm.getEntity();

        boolean isWaste = !deliveredProduct.getBooleanField(DeliveredProductFields.IS_WASTE);

        reservationsGrid.setEnabled(isWaste);
    }

    public void fillUnitFields(final ViewDefinitionState view) {
        List<String> unitNames = Lists.newArrayList("damagedQuantityUnit", "deliveredQuantityUnit", "orderedQuantityUnit");
        List<String> additionalUnitNames = Lists.newArrayList("additionalQuantityUnit");

        deliveriesService.fillUnitFields(view, DeliveredProductFields.PRODUCT, unitNames, additionalUnitNames);
    }

    public void fillCurrencyFields(final ViewDefinitionState view) {
        FormComponent deliveredProductForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);

        List<String> referenceNames = Lists.newArrayList("totalPriceCurrency", "pricePerUnitCurrency");

        Entity deliveredProduct = deliveredProductForm.getEntity();
        Entity delivery = deliveredProduct.getBelongsToField(DeliveredProductFields.DELIVERY);

        deliveriesService.fillCurrencyFieldsForDelivery(view, referenceNames, delivery);
    }

    public void fillOrderedQuantities(final ViewDefinitionState view) {
        FormComponent deliveredProductForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        LookupComponent productLookup = (LookupComponent) view.getComponentByReference(DeliveredProductFields.PRODUCT);
        FieldComponent orderedQuantity = (FieldComponent) view.getComponentByReference(OrderedProductFields.ORDERED_QUANTITY);

        Entity deliveredProduct = deliveredProductForm.getEntity();
        Entity product = productLookup.getEntity();

        if (Objects.isNull(product)) {
            orderedQuantity.setFieldValue(null);
        } else {
            orderedQuantity.setFieldValue(numberService.format(getOrderedProductQuantity(deliveredProduct)));
        }

        orderedQuantity.requestComponentUpdateState();
    }

    public void fillConversion(final ViewDefinitionState view) {
        LookupComponent productLookup = (LookupComponent) view.getComponentByReference(DeliveredProductFields.PRODUCT);

        Entity product = productLookup.getEntity();

        if (Objects.nonNull(product)) {
            String additionalUnit = product.getStringField(ProductFields.ADDITIONAL_UNIT);
            String unit = product.getStringField(ProductFields.UNIT);

            FieldComponent conversionField = (FieldComponent) view.getComponentByReference(DeliveredProductFields.CONVERSION);

            if (StringUtils.isEmpty(additionalUnit)) {
                conversionField.setFieldValue(BigDecimal.ONE);
                conversionField.setEnabled(false);
            } else {
                String conversion = numberService.formatWithMinimumFractionDigits(getConversion(product, unit, additionalUnit),
                        0);

                conversionField.setFieldValue(conversion);
                conversionField.setEnabled(true);
            }

            conversionField.requestComponentUpdateState();
        }
    }

    public BigDecimal getDefaultConversion(final Entity product) {
        if (Objects.nonNull(product)) {
            String additionalUnit = product.getStringField(ProductFields.ADDITIONAL_UNIT);
            String unit = product.getStringField(ProductFields.UNIT);

            if (StringUtils.isNotEmpty(additionalUnit) && !unit.equals(additionalUnit)) {
                return getConversion(product, unit, additionalUnit);
            }
        }

        return BigDecimal.ONE;
    }

    private BigDecimal getConversion(final Entity product, final String unit, final String additionalUnit) {
        PossibleUnitConversions unitConversions = unitConversionService.getPossibleConversions(unit,
                searchCriteriaBuilder -> searchCriteriaBuilder
                        .add(SearchRestrictions.belongsTo(UnitConversionItemFieldsB.PRODUCT, product)));

        if (unitConversions.isDefinedFor(additionalUnit)) {
            return unitConversions.asUnitToConversionMap().get(additionalUnit);
        } else {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal getOrderedProductQuantity(final Entity deliveredProduct) {
        BigDecimal orderedQuantity = null;

        Optional<Entity> maybeOrderedProduct = deliveriesService.getOrderedProductForDeliveredProduct(deliveredProduct);

        if (maybeOrderedProduct.isPresent()) {
            Entity orderedProduct = maybeOrderedProduct.get();

            orderedQuantity = orderedProduct.getDecimalField(OrderedProductFields.ORDERED_QUANTITY);
        } else {
            maybeOrderedProduct = deliveriesService.getSuitableOrderedProductForDeliveredProduct(deliveredProduct);

            if (maybeOrderedProduct.isPresent()) {
                Entity orderedProduct = maybeOrderedProduct.get();

                orderedQuantity = orderedProduct.getDecimalField(OrderedProductFields.ORDERED_QUANTITY);
            }
        }

        return orderedQuantity;
    }

    public void setDeliveredQuantityFieldRequired(final ViewDefinitionState view) {
        FieldComponent deliveredQuantityField = (FieldComponent) view
                .getComponentByReference(DeliveredProductFields.DELIVERED_QUANTITY);

        deliveredQuantityField.setRequired(true);
        deliveredQuantityField.requestComponentUpdateState();
    }

    public void setAdditionalQuantityFieldRequired(final ViewDefinitionState view) {
        FieldComponent additionalQuantityField = (FieldComponent) view
                .getComponentByReference(DeliveredProductFields.ADDITIONAL_QUANTITY);

        additionalQuantityField.setRequired(true);
        additionalQuantityField.requestComponentUpdateState();
    }

    private void lockConversion(final ViewDefinitionState view) {
        String unit = (String) view.getComponentByReference("deliveredQuantityUnit").getFieldValue();
        String additionalUnit = (String) view.getComponentByReference("additionalQuantityUnit").getFieldValue();

        if (Objects.nonNull(additionalUnit) && additionalUnit.equals(unit)) {
            view.getComponentByReference(DeliveredProductFields.CONVERSION).setEnabled(false);
        }
    }

    private void setFilters(final ViewDefinitionState view) {
        FormComponent deliveredProductForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        LookupComponent productLookup = (LookupComponent) view.getComponentByReference(DeliveredProductFields.PRODUCT);
        LookupComponent storageLocationsLookup = (LookupComponent) view
                .getComponentByReference(DeliveredProductFields.STORAGE_LOCATION);
        LookupComponent additionalCodeLookup = (LookupComponent) view
                .getComponentByReference(DeliveredProductFields.ADDITIONAL_CODE);

        Entity deliveredProductEntity = deliveredProductForm.getEntity();
        Entity product = productLookup.getEntity();

        Entity delivery = deliveredProductEntity.getBelongsToField(DeliveredProductFields.DELIVERY);
        Entity location = delivery.getBelongsToField(DeliveryFields.LOCATION);

        if (Objects.nonNull(product)) {
            filterBy(additionalCodeLookup, DeliveredProductFields.PRODUCT, product.getId());
        }

        if (Objects.nonNull(product) && Objects.nonNull(location)) {
            filterBy(storageLocationsLookup, L_LOCATION, location.getId());
            filterBy(storageLocationsLookup, L_PRODUCT, product.getId());
        } else {
            storageLocationsLookup.setFieldValue(null);
            storageLocationsLookup.setEnabled(false);
            storageLocationsLookup.requestComponentUpdateState();
        }
    }

    private void filterBy(final LookupComponent componentState, final String field, final Long id) {
        FilterValueHolder filterValueHolder = componentState.getFilterValue();
        filterValueHolder.put(field, id);

        componentState.setEnabled(true);
        componentState.setFilterValue(filterValueHolder);
        componentState.requestComponentUpdateState();
    }

    public void setBatchLookupProductFilterValue(final ViewDefinitionState view, final Entity deliveredProduct) {
        LookupComponent batchLookup = (LookupComponent) view.getComponentByReference(DeliveredProductFields.BATCH);

        Entity product = deliveredProduct.getBelongsToField(DeliveredProductFields.PRODUCT);

        if (Objects.nonNull(product)) {
            batchCriteriaModifier.putProductFilterValue(batchLookup, product);
        }
    }

}
