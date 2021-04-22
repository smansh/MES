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
package com.qcadoo.mes.costCalculation.print;

import com.google.common.collect.Lists;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.costCalculation.constants.CostCalculationFields;
import com.qcadoo.mes.costCalculation.print.dto.CostCalculationMaterial;
import com.qcadoo.mes.costCalculation.print.dto.MaterialCostKey;
import com.qcadoo.mes.technologies.ProductQuantitiesService;
import com.qcadoo.mes.technologies.ProductQuantitiesWithComponentsService;
import com.qcadoo.mes.technologies.constants.*;
import com.qcadoo.mes.technologies.dto.OperationProductComponentHolder;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.plugin.api.PluginManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CostCalculationMaterialsService {

    public static final String ORDERS_FOR_SUBPRODUCTS_GENERATION = "ordersForSubproductsGeneration";

    @Autowired
    private PluginManager pluginManager;

    @Autowired
    private ProductsCostCalculationService productsCostCalculationService;

    @Autowired
    private NumberService numberService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private ProductQuantitiesService productQuantitiesService;

    @Autowired
    private ProductQuantitiesWithComponentsService productQuantitiesWithComponentsService;

    public List<CostCalculationMaterial> getSortedMaterialsFromProductQuantities(final Entity costCalculation,
            final Entity technology) {
        List<CostCalculationMaterial> materialCosts = Lists.newArrayList();
        BigDecimal quantity = costCalculation.getDecimalField(CostCalculationFields.QUANTITY);
        Map<OperationProductComponentHolder, BigDecimal> materialQuantitiesByOPC = getNeededProductQuantitiesByOPC(
                costCalculation, technology, quantity);
        DataDefinition operationProductComponentDD = dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_OPERATION_PRODUCT_IN_COMPONENT);
        String technologyNumber = technology.getStringField(TechnologyFields.NUMBER);
        String finalProductNumber = technology.getBelongsToField(TechnologyFields.PRODUCT).getStringField(ProductFields.NUMBER);
        for (Map.Entry<OperationProductComponentHolder, BigDecimal> neededProductQuantity : materialQuantitiesByOPC.entrySet()) {
            Entity product = neededProductQuantity.getKey().getProduct();
            Entity operationProductComponent = operationProductComponentDD
                    .get(neededProductQuantity.getKey().getOperationProductComponentId());
            BigDecimal costPerUnit = productsCostCalculationService.calculateOperationProductCostPerUnit(costCalculation, product,
                    operationProductComponent);

            BigDecimal productQuantity = neededProductQuantity.getValue();
            BigDecimal costForGivenQuantity = costPerUnit.multiply(productQuantity, numberService.getMathContext());

            String technologyInputProductTypeName = "";

            Entity technologyInputProductType = operationProductComponent
                    .getBelongsToField(OperationProductInComponentFields.TECHNOLOGY_INPUT_PRODUCT_TYPE);
            if (technologyInputProductType != null) {
                technologyInputProductTypeName = technologyInputProductType.getStringField(TechnologyInputProductTypeFields.NAME);
            }
            CostCalculationMaterial costCalculationMaterial = new CostCalculationMaterial();
            costCalculationMaterial.setTechnologyNumber(technologyNumber);
            costCalculationMaterial.setFinalProductNumber(finalProductNumber);
            costCalculationMaterial.setProductQuantity(productQuantity);
            costCalculationMaterial.setCostPerUnit(costPerUnit);
            costCalculationMaterial.setCostForGivenQuantity(costForGivenQuantity);
            costCalculationMaterial.setTechnologyInputProductType(technologyInputProductTypeName);
            costCalculationMaterial.setDifferentProductsInDifferentSizes(operationProductComponent
                    .getBooleanField(OperationProductInComponentFields.DIFFERENT_PRODUCTS_IN_DIFFERENT_SIZES));
            if (product != null) {
                costCalculationMaterial.setUnit(product.getStringField(ProductFields.UNIT));
                costCalculationMaterial.setProductNumber(product.getStringField(ProductFields.NUMBER));
                costCalculationMaterial.setProductName(product.getStringField(ProductFields.NAME));
            } else {
                costCalculationMaterial
                        .setUnit(operationProductComponent.getStringField(OperationProductInComponentFields.GIVEN_UNIT));
                costCalculationMaterial.setProductNumber("");
                costCalculationMaterial.setProductName("");
            }
            materialCosts.add(costCalculationMaterial);
        }
        return groupMaterialCosts(materialCosts);
    }

    private List<CostCalculationMaterial> groupMaterialCosts(List<CostCalculationMaterial> materialCosts) {
        List<CostCalculationMaterial> groupedMaterialCostsList = new ArrayList<>();
        Map<MaterialCostKey, CostCalculationMaterial> groupedMaterialCosts = new HashMap<>();
        for (CostCalculationMaterial costCalculationMaterial : materialCosts) {
            if (costCalculationMaterial.isDifferentProductsInDifferentSizes()) {
                groupedMaterialCostsList.add(costCalculationMaterial);
            } else {
                MaterialCostKey materialCostKey = new MaterialCostKey(costCalculationMaterial.getProductNumber(),
                        costCalculationMaterial.getProductName(), costCalculationMaterial.getUnit(),
                        costCalculationMaterial.getTechnologyInputProductType());
                if (groupedMaterialCosts.containsKey(materialCostKey)) {
                    CostCalculationMaterial groupedMaterialCost = groupedMaterialCosts.get(materialCostKey);
                    groupedMaterialCost.setProductQuantity(groupedMaterialCost.getProductQuantity()
                            .add(costCalculationMaterial.getProductQuantity(), numberService.getMathContext()));
                    groupedMaterialCost.setCostForGivenQuantity(groupedMaterialCost.getCostForGivenQuantity()
                            .add(costCalculationMaterial.getCostForGivenQuantity(), numberService.getMathContext()));
                    groupedMaterialCosts.put(materialCostKey, groupedMaterialCost);
                } else {
                    groupedMaterialCosts.put(materialCostKey, costCalculationMaterial);
                }
            }
        }
        groupedMaterialCostsList.addAll(groupedMaterialCosts.values());
        groupedMaterialCostsList.sort(Comparator.comparing(CostCalculationMaterial::getCostForGivenQuantity));
        return groupedMaterialCostsList;
    }

    private Map<OperationProductComponentHolder, BigDecimal> getNeededProductQuantitiesByOPC(final Entity costCalculation,
            final Entity technology, final BigDecimal quantity) {
        boolean includeComponents = costCalculation.getBooleanField(CostCalculationFields.INCLUDE_COMPONENTS);
        if (pluginManager.isPluginEnabled(ORDERS_FOR_SUBPRODUCTS_GENERATION) && includeComponents) {
            return productQuantitiesWithComponentsService.getNeededProductQuantitiesByOPC(technology, quantity,
                    MrpAlgorithm.ONLY_MATERIALS);
        }
        return productQuantitiesService.getNeededProductQuantitiesByOPC(technology, quantity, MrpAlgorithm.ONLY_COMPONENTS);
    }

}
