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
package com.qcadoo.mes.basicProductionCounting;

import com.qcadoo.mes.technologies.constants.MrpAlgorithm;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.Entity;
import com.qcadoo.view.api.ViewDefinitionState;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface BasicProductionCountingService {


    /**
     * Updates production counting quantities
     * 
     * @param order
     *            order
     */
    void updateProductionCountingQuantitiesAndOperationRuns(final Entity order);

    /**
     * Create production counting
     *
     * @param order
     *            order
     */
    boolean createProductionCounting(final Entity order);

    /**
     * Creates basic production counting
     * 
     * @param order
     *            order
     * @param product
     *            product
     * 
     * @return basic production counting entity
     */
    Entity createBasicProductionCounting(final Entity order, final Entity product);

    /**
     * Gets basic production counting
     * 
     * @param basicProductionCoutningId
     *            basicProductionCoutningId
     * 
     * @return basic production counting
     */
    Entity getBasicProductionCounting(final Long basicProductionCoutningId);

    /**
     * Gets production counting quantity
     * 
     * @param productionCountingQuantityId
     *            productionCountingQuantityId
     * 
     * @return production counting quantity
     */
    Entity getProductionCountingQuantity(final Long productionCountingQuantityId);

    /**
     * Gets basic production counting data definition
     * 
     * @return basic production counting data definition
     */
    DataDefinition getBasicProductionCountingDD();

    /**
     * Gets production counting quantity data definition
     * 
     * @return production counting quantity data definition
     */
    DataDefinition getProductionCountingQuantityDD();

    /**
     * Gets produced quantity from basic production countings
     * 
     * @param order
     *            order
     * 
     * @return doneQuantity
     */
    BigDecimal getProducedQuantityFromBasicProductionCountings(final Entity order);

    /**
     * Fills unit fields
     * 
     * @param view
     *            view
     * 
     * @param productName
     *            product lookup reference name
     * 
     * @param referenceNames
     *            reference names to unit fields
     */
    void fillUnitFields(final ViewDefinitionState view, final String productName, final List<String> referenceNames);

    /**
     * Sets technology operation component field required
     * 
     * @param view
     *            view
     */
    void setTechnologyOperationComponentFieldRequired(final ViewDefinitionState view);

    /**
     * Fills row styles depends of type of material
     * 
     * @param productionCountingQuantity
     *            production counting quantity
     * 
     * @return row styles
     */
    Set<String> fillRowStylesDependsOfTypeOfMaterial(final Entity productionCountingQuantity);

    /**
     * Update producted quantity
     *
     * @param order
     */
    void updateProducedQuantity(final Entity order);

    /**
     * Get used materials from production counting quantities for order (components and intermediates)
     * 
     * @param order
     * @return
     */
    List<Entity> getUsedMaterialsFromProductionCountingQuantities(final Entity order);

    /**
     * Get used components from production counting quantities for order
     *
     * @param order
     * @param onlyComponents
     * @return
     */
    List<Entity> getUsedMaterialsFromProductionCountingQuantities(final Entity order, final boolean onlyComponents);

    /**
     * Get materials from production counting quantities for order and operation
     *
     * @param order
     * @param operationComponent
     * @return
     */
    List<Entity> getMaterialsForOperationFromProductionCountingQuantities(final Entity order, final Entity operationComponent);

    /**
     * Get needed product quantites from production counting quantities
     * 
     * @param orders
     * @param algorithm
     * @return
     */
    Map<Long, BigDecimal> getNeededProductQuantities(final List<Entity> orders, final MrpAlgorithm algorithm);

    void fillFlow(List<Entity> productionCountingQuantities, Entity order);
}
