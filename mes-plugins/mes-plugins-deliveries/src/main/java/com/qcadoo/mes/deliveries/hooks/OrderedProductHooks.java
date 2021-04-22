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
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;
import com.qcadoo.mes.advancedGenealogy.AdvancedGenealogyService;
import com.qcadoo.mes.advancedGenealogy.constants.BatchNumberUniqueness;
import com.qcadoo.mes.advancedGenealogy.hooks.BatchModelValidators;
import com.qcadoo.mes.deliveries.DeliveriesService;
import com.qcadoo.mes.deliveries.ReservationService;
import com.qcadoo.mes.deliveries.constants.DeliveryFields;
import com.qcadoo.mes.deliveries.constants.OrderedProductFields;
import com.qcadoo.mes.deliveries.constants.OrderedProductReservationFields;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.EntityList;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchRestrictions;

@Service
public class OrderedProductHooks {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private NumberService numberService;

    @Autowired
    private DeliveriesService deliveriesService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private AdvancedGenealogyService advancedGenealogyService;

    @Autowired
    private BatchModelValidators batchModelValidators;

    public void onSave(final DataDefinition orderedProductDD, final Entity orderedProduct) {
        deliveriesService.calculatePricePerUnit(orderedProduct, OrderedProductFields.ORDERED_QUANTITY);

        calculateReservationQuantities(orderedProduct);
        reservationService.deleteReservationsForOrderedProductIfChanged(orderedProduct);

        createBatch(orderedProduct);
    }

    private void calculateReservationQuantities(final Entity orderedProduct) {
        EntityList reservations = orderedProduct.getHasManyField(OrderedProductFields.RESERVATIONS);

        if (Objects.nonNull(reservations)) {
            BigDecimal conversion = orderedProduct.getDecimalField(OrderedProductFields.CONVERSION);

            for (Entity reservation : reservations) {
                BigDecimal orderedQuantity = reservation.getDecimalField(OrderedProductReservationFields.ORDERED_QUANTITY);
                BigDecimal newAdditionalQuantity = orderedQuantity.multiply(conversion, numberService.getMathContext());

                newAdditionalQuantity = newAdditionalQuantity.setScale(NumberService.DEFAULT_MAX_FRACTION_DIGITS_IN_DECIMAL,
                        RoundingMode.HALF_UP);

                reservation.setField(OrderedProductReservationFields.ADDITIONAL_QUANTITY, newAdditionalQuantity);
            }
        }
    }

    private void createBatch(final Entity orderedProduct) {
        String batchNumber = orderedProduct.getStringField(OrderedProductFields.BATCH_NUMBER);
        Entity product = orderedProduct.getBelongsToField(OrderedProductFields.PRODUCT);
        Entity delivery = orderedProduct.getBelongsToField(OrderedProductFields.DELIVERY);

        if (Objects.nonNull(batchNumber) && Objects.nonNull(product)) {
            Entity supplier = delivery.getBelongsToField(DeliveryFields.SUPPLIER);

            Entity batch = advancedGenealogyService.createOrGetBatch(batchNumber, product, supplier);

            if (batch.isValid()) {
                orderedProduct.setField(OrderedProductFields.BATCH_NUMBER, null);
                orderedProduct.setField(OrderedProductFields.BATCH, batch);
            } else {
                BatchNumberUniqueness batchNumberUniqueness = batchModelValidators.getBatchNumberUniqueness();
                String errorMessage = batchModelValidators.getBatchNumberErrorMessage(batchNumberUniqueness);

                orderedProduct.addGlobalError(errorMessage);
            }
        }
    }

    public void onDelete(final DataDefinition orderedProductDD, final Entity orderedProduct) {
        nullifyDeliveredProducts(orderedProduct);
    }

    private void nullifyDeliveredProducts(final Entity orderedProduct) {
        String sql = "UPDATE deliveries_deliveredproduct SET orderedproduct_id = null"
                + " WHERE orderedproduct_id = :orderedProductId";

        Map<String, Object> parameters = Maps.newHashMap();

        parameters.put("orderedProductId", orderedProduct.getId());

        SqlParameterSource namedParameters = new MapSqlParameterSource(parameters);

        jdbcTemplate.update(sql, namedParameters);
    }

    public boolean validatesWith(final DataDefinition orderedProductDD, final Entity orderedProduct) {
        return checkIfOrderedProductAlreadyExists(orderedProductDD, orderedProduct);
    }

    public boolean checkIfOrderedProductAlreadyExists(final DataDefinition orderedProductDD, final Entity orderedProduct) {
        SearchCriteriaBuilder searchCriteriaBuilder = deliveriesService
                .getSearchCriteriaBuilderForOrderedProduct(orderedProductDD.find(), orderedProduct);

        Long orderedProductId = orderedProduct.getId();

        if (Objects.nonNull(orderedProductId)) {
            searchCriteriaBuilder.add(SearchRestrictions.ne("id", orderedProductId));
        }

        Entity orderedProductFromDB = searchCriteriaBuilder.setMaxResults(1).uniqueResult();

        if (Objects.isNull(orderedProductFromDB)) {
            return true;
        } else {
            orderedProduct.addError(orderedProductDD.getField(OrderedProductFields.PRODUCT),
                    "deliveries.orderedProduct.error.productAlreadyExists");

            return false;
        }
    }

}
