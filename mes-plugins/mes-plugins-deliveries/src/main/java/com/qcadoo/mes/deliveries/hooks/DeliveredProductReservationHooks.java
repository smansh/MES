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
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.deliveries.constants.DeliveredProductFields;
import com.qcadoo.mes.deliveries.constants.DeliveredProductReservationFields;
import com.qcadoo.mes.deliveries.constants.DeliveryFields;
import com.qcadoo.model.api.BigDecimalUtils;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.FieldDefinition;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchCriterion;
import com.qcadoo.model.api.search.SearchOrders;
import com.qcadoo.model.api.search.SearchProjection;
import com.qcadoo.model.api.search.SearchProjections;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.model.api.search.SearchResult;

@Service
public class DeliveredProductReservationHooks {

    private static final String L_SUM_OF_QUANTITY = "sumOfQuantity";

    public void onView(final DataDefinition deliveredProductReservationDD, final Entity deliveredProductReservation) {
        Entity deliveredProduct = deliveredProductReservation
                .getBelongsToField(DeliveredProductReservationFields.DELIVERED_PRODUCT);

        if (Objects.nonNull(deliveredProduct)) {
            Entity product = deliveredProduct.getBelongsToField(DeliveredProductFields.PRODUCT);

            if (Objects.nonNull(product)) {
                String unit = product.getStringField(ProductFields.UNIT);
                String additionalUnit = product.getStringField(ProductFields.ADDITIONAL_UNIT);

                if (Objects.isNull(additionalUnit)) {
                    additionalUnit = unit;
                }

                deliveredProductReservation.setField(DeliveredProductReservationFields.DELIVERED_QUANTITY_UNIT, unit);
                deliveredProductReservation.setField(DeliveredProductReservationFields.ADDITIONAL_QUANTITY_UNIT, additionalUnit);
            }
        }
    }

    public boolean validate(final DataDefinition deliveredProductReservationDD, final Entity deliveredProductReservation) {
        return locationUniqueToDelivery(deliveredProductReservation) && locationUnique(deliveredProductReservation)
                && sumIsNotExceeded(deliveredProductReservation);
    }

    private boolean locationUniqueToDelivery(final Entity deliveredProductReservation) {
        Entity deliveredProduct = deliveredProductReservation
                .getBelongsToField(DeliveredProductReservationFields.DELIVERED_PRODUCT);
        Entity delivery = deliveredProduct.getBelongsToField(DeliveredProductFields.DELIVERY);
        Entity deliveryLocation = delivery.getBelongsToField(DeliveryFields.LOCATION);

        Entity reservationLocation = deliveredProductReservation.getBelongsToField(DeliveredProductReservationFields.LOCATION);

        boolean locationOtherThenDelivery = Objects.isNull(deliveryLocation)
                || !deliveryLocation.getId().equals(reservationLocation.getId());

        if (!locationOtherThenDelivery) {
            FieldDefinition locationField = deliveredProductReservation.getDataDefinition()
                    .getField(DeliveredProductReservationFields.LOCATION);
            deliveredProductReservation.addError(locationField,
                    "deliveries.deliveredProductReservation.error.locationNotUniqueToDelivery");
        }

        return locationOtherThenDelivery;
    }

    private boolean locationUnique(final Entity deliveredProductReservation) {
        Entity location = deliveredProductReservation.getBelongsToField(DeliveredProductReservationFields.LOCATION);
        Entity deliveredProduct = deliveredProductReservation
                .getBelongsToField(DeliveredProductReservationFields.DELIVERED_PRODUCT);

        if (Objects.nonNull(location)) {
            SearchCriterion criterion;

            SearchCriterion criterionLocation = SearchRestrictions.belongsTo(DeliveredProductReservationFields.LOCATION,
                    location);
            SearchCriterion criterionDeliveredProduct = SearchRestrictions
                    .belongsTo(DeliveredProductReservationFields.DELIVERED_PRODUCT, deliveredProduct);

            Long deliveredProductReservationId = deliveredProductReservation.getId();

            if (Objects.isNull(deliveredProductReservationId)) {
                criterion = SearchRestrictions.and(criterionLocation, criterionDeliveredProduct);
            } else {
                SearchCriterion criterionId = SearchRestrictions.idNe(deliveredProductReservationId);

                criterion = SearchRestrictions.and(criterionLocation, criterionDeliveredProduct, criterionId);
            }

            boolean locationUnique = deliveredProductReservation.getDataDefinition().count(criterion) == 0;

            if (!locationUnique) {
                FieldDefinition locationField = deliveredProductReservation.getDataDefinition()
                        .getField(DeliveredProductReservationFields.LOCATION);
                deliveredProductReservation.addError(locationField,
                        "deliveries.deliveredProductReservation.error.locationNotUnique");
            }

            return locationUnique;
        }

        return true;
    }

    private boolean sumIsNotExceeded(final Entity deliveredProductReservation) {
        Entity deliveredProduct = deliveredProductReservation
                .getBelongsToField(DeliveredProductReservationFields.DELIVERED_PRODUCT);
        BigDecimal productDeliveredQuantity = deliveredProduct.getDecimalField(DeliveredProductFields.DELIVERED_QUANTITY);

        if (Objects.isNull(productDeliveredQuantity)) {
            return true;
        }

        BigDecimal reservationDeliveredQuantity = deliveredProductReservation
                .getDecimalField(DeliveredProductReservationFields.DELIVERED_QUANTITY);

        SearchCriteriaBuilder searchCriteriaBuilder = deliveredProductReservation.getDataDefinition().find();
        SearchProjection sumOfQuantityProjection = SearchProjections
                .alias(SearchProjections.sum(DeliveredProductReservationFields.DELIVERED_QUANTITY), L_SUM_OF_QUANTITY);
        searchCriteriaBuilder
                .setProjection(SearchProjections.list().add(sumOfQuantityProjection).add(SearchProjections.rowCount()));

        SearchCriterion criterion;
        SearchCriterion criterionDeliveredProduct = SearchRestrictions
                .belongsTo(DeliveredProductReservationFields.DELIVERED_PRODUCT, deliveredProduct);

        Long deliveredProductReservationId = deliveredProductReservation.getId();

        if (Objects.isNull(deliveredProductReservationId)) {
            criterion = criterionDeliveredProduct;
        } else {
            SearchCriterion criterionId = SearchRestrictions.idNe(deliveredProductReservationId);

            criterion = SearchRestrictions.and(criterionDeliveredProduct, criterionId);
        }

        searchCriteriaBuilder.add(criterion);
        searchCriteriaBuilder.addOrder(SearchOrders.asc(L_SUM_OF_QUANTITY));

        SearchResult resList = searchCriteriaBuilder.setMaxResults(1).list();

        BigDecimal sumOfQuantity = (resList.getTotalNumberOfEntities() == 0) ? BigDecimal.ZERO
                : resList.getEntities().get(0).getDecimalField(L_SUM_OF_QUANTITY);
        sumOfQuantity = BigDecimalUtils.convertNullToZero(sumOfQuantity);

        BigDecimal damagedQuantity = deliveredProduct.getDecimalField(DeliveredProductFields.DAMAGED_QUANTITY);
        damagedQuantity = BigDecimalUtils.convertNullToZero(damagedQuantity);

        productDeliveredQuantity = productDeliveredQuantity.subtract(damagedQuantity);

        boolean sumIsNotExceeded = productDeliveredQuantity.compareTo(reservationDeliveredQuantity.add(sumOfQuantity)) >= 0;

        if (!sumIsNotExceeded) {
            FieldDefinition deliveredQuantityField = deliveredProductReservation.getDataDefinition()
                    .getField(DeliveredProductReservationFields.DELIVERED_QUANTITY);

            deliveredProductReservation.addError(deliveredQuantityField,
                    "deliveries.deliveredProductReservation.error.sumIsExceeded");
        }

        return sumIsNotExceeded;
    }

}
