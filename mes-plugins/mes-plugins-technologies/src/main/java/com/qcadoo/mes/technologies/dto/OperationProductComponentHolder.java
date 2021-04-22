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
package com.qcadoo.mes.technologies.dto;

import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.Entity;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class OperationProductComponentHolder {

    private static final String L_OPERATION_COMPONENT = "operationComponent";

    private static final String L_PRODUCT = "product";

    private final Long productId;

    private final Long technologyOperationComponentId;

    private final Long productionCountingQuantityId;

    private final Long operationProductComponentId;

    private final DataDefinition productDD;

    private final DataDefinition technologyOperationComponentDD;
    
    private final DataDefinition operationProductComponentDD;

    private final OperationProductComponentEntityType entityType;

    private final ProductMaterialType productMaterialType;

    public OperationProductComponentHolder(final Entity operationProductComponent) {
        Entity product = operationProductComponent.getBelongsToField(L_PRODUCT);
        Entity technologyOperationComponent = operationProductComponent.getBelongsToField(L_OPERATION_COMPONENT);

        OperationProductComponentEntityType entityType = OperationProductComponentEntityType
                .parseString(operationProductComponent.getDataDefinition().getName());

        this.productId = product != null ? product.getId() : null;
        this.technologyOperationComponentId = technologyOperationComponent.getId();
        this.productDD = product != null ? product.getDataDefinition() : null;
        this.technologyOperationComponentDD = technologyOperationComponent.getDataDefinition();
        this.entityType = entityType;
        this.productMaterialType = ProductMaterialType.NONE;
        this.productionCountingQuantityId = null;
        this.operationProductComponentId = operationProductComponent.getId();
        this.operationProductComponentDD = operationProductComponent.getDataDefinition();
    }

    public OperationProductComponentHolder(final Entity operationProductComponent, final Entity product) {
        Entity technologyOperationComponent = operationProductComponent.getBelongsToField(L_OPERATION_COMPONENT);

        OperationProductComponentEntityType entityType = OperationProductComponentEntityType
                .parseString(operationProductComponent.getDataDefinition().getName());

        this.productId = product.getId();
        this.technologyOperationComponentId = technologyOperationComponent.getId();
        this.productDD = product.getDataDefinition();
        this.technologyOperationComponentDD = technologyOperationComponent.getDataDefinition();
        this.entityType = entityType;
        this.productMaterialType = ProductMaterialType.NONE;
        this.productionCountingQuantityId = null;
        this.operationProductComponentId = operationProductComponent.getId();
        this.operationProductComponentDD = operationProductComponent.getDataDefinition();
    }

    public OperationProductComponentHolder(final Entity product, final Entity technologyOperationComponent,
            final Entity productionCountingQuantity, final OperationProductComponentEntityType entityType,
            final ProductMaterialType productMaterialType) {

        Long productId = product.getId();
        Long technologyOperationComponentId = (technologyOperationComponent == null) ? null
                : technologyOperationComponent.getId();
        DataDefinition productDD = product.getDataDefinition();
        DataDefinition technologyOperationComponentDD = (technologyOperationComponent == null) ? null
                : technologyOperationComponent.getDataDefinition();

        this.productId = productId;
        this.technologyOperationComponentId = technologyOperationComponentId;
        this.productDD = productDD;
        this.technologyOperationComponentDD = technologyOperationComponentDD;
        this.entityType = entityType;
        this.productMaterialType = productMaterialType;
        this.productionCountingQuantityId = (productionCountingQuantity == null) ? null : productionCountingQuantity.getId();
        this.operationProductComponentId = null;
        this.operationProductComponentDD = null;
    }

    public OperationProductComponentHolder(final Long productId, final Long technologyOperationComponentId,
            final DataDefinition productDD, final DataDefinition technologyOperationComponentDD,
            final OperationProductComponentEntityType entityType) {
        this.productId = productId;
        this.technologyOperationComponentId = technologyOperationComponentId;
        this.productDD = productDD;
        this.technologyOperationComponentDD = technologyOperationComponentDD;
        this.entityType = entityType;
        this.productMaterialType = ProductMaterialType.NONE;
        this.productionCountingQuantityId = null;
        this.operationProductComponentId = null;
        this.operationProductComponentDD = null;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getTechnologyOperationComponentId() {
        return technologyOperationComponentId;
    }

    public DataDefinition getProductDD() {
        return productDD;
    }

    public DataDefinition getTechnologyOperationComponentDD() {
        return technologyOperationComponentDD;
    }

    public OperationProductComponentEntityType getEntityType() {
        return entityType;
    }

    public Entity getProduct() {
        if ((getProductId() == null) || (getProductDD() == null)) {
            return null;
        } else {
            return getProductDD().get(getProductId());
        }
    }

    public Entity getOperationProductComponent() {
        if ((getOperationProductComponentId() == null) || (getOperationProductComponentDD() == null)) {
            return null;
        } else {
            return getOperationProductComponentDD().get(getOperationProductComponentId());
        }
    }

    public Entity getTechnologyOperationComponent() {
        if ((getTechnologyOperationComponentId() == null) || (getTechnologyOperationComponentDD() == null)) {
            return null;
        } else {
            return getTechnologyOperationComponentDD().get(getTechnologyOperationComponentId());
        }
    }

    public boolean isEntityTypeSame(final String operationProductComponentModelName) {
        return isEntityTypeSame(OperationProductComponentEntityType.parseString(operationProductComponentModelName));
    }

    public boolean isEntityTypeSame(final OperationProductComponentEntityType operationProductComponentEntityType) {
        return operationProductComponentEntityType.equals(getEntityType());
    }

    public ProductMaterialType getProductMaterialType() {
        return productMaterialType;
    }

    public Long getProductionCountingQuantityId() {
        return productionCountingQuantityId;
    }

    public DataDefinition getOperationProductComponentDD() {
        return operationProductComponentDD;
    }

    @Override
    public int hashCode() {
        if (productId != null) {
            return new HashCodeBuilder().append(productId).append(technologyOperationComponentId).append(entityType).toHashCode();
        } else {
            return new HashCodeBuilder().append(operationProductComponentId).append(technologyOperationComponentId)
                    .append(entityType).toHashCode();
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof OperationProductComponentHolder)) {
            return false;
        }

        OperationProductComponentHolder other = (OperationProductComponentHolder) obj;

        if (productId != null) {
            return new EqualsBuilder().append(productId, other.productId)
                    .append(technologyOperationComponentId, other.technologyOperationComponentId)
                    .append(entityType, other.entityType).isEquals();
        } else {
            return new EqualsBuilder().append(operationProductComponentId, other.operationProductComponentId)
                    .append(technologyOperationComponentId, other.technologyOperationComponentId)
                    .append(entityType, other.entityType).isEquals();
        }
    }

    public Long getOperationProductComponentId() {
        return operationProductComponentId;
    }
}
