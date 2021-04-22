/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo Framework
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
package com.qcadoo.mes.materialRequirementCoverageForOrder.aspects;

import com.google.common.collect.Lists;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.materialRequirementCoverageForOrder.constans.MaterialRequirementCoverageForOrderConstans;
import com.qcadoo.mes.orderSupplies.constants.CoverageProductFields;
import com.qcadoo.mes.orderSupplies.constants.CoverageRegisterFields;
import com.qcadoo.mes.orderSupplies.constants.OrderSuppliesConstants;
import com.qcadoo.mes.orderSupplies.constants.ProductType;
import com.qcadoo.mes.orderSupplies.register.RegisterService;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.mes.technologies.constants.TechnologyFields;
import com.qcadoo.mes.technologies.states.constants.TechnologyState;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.plugin.api.RunIfEnabled;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Aspect
@Configurable
@Service
@RunIfEnabled(MaterialRequirementCoverageForOrderConstans.PLUGIN_IDENTIFIER)
public class MRCServiceOverideAspect {

    @Autowired
    private RegisterService registerService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private static final String L_PRODUCT_TYPE = "productType";

    private static final String L_PLANNED_QUANTITY = "planedQuantity";

    @Pointcut("execution(public void com.qcadoo.mes.orderSupplies.coverage.MaterialRequirementCoverageServiceImpl.additionalProcessProductCoverage(..)) "
            + "&& args(materialRequirementCoverage, productAndCoverageProducts)")
    public void additionalProcessProductCoverageA(final Entity materialRequirementCoverage,
            final Map<Long, Entity> productAndCoverageProducts) {
    }

    @Around("additionalProcessProductCoverageA(materialRequirementCoverage, productAndCoverageProducts)")
    public void aroundAdditionalProcessProductCoverage(final ProceedingJoinPoint pjp, final Entity materialRequirementCoverage,
            final Map<Long, Entity> productAndCoverageProducts) throws Throwable {

        Entity order = materialRequirementCoverage.getBelongsToField("order");
        if (order == null) {
            pjp.proceed();
        } else {

            List<Entity> entries = registerService.getRegisterEntriesForOrder(order);

            for (Entity entry : entries) {
                Entity product = entry.getBelongsToField(CoverageRegisterFields.PRODUCT);

                Entity coverageProduct = productAndCoverageProducts.get(product.getId());
                if (coverageProduct == null) {
                    continue;
                }
                if (checkIfProductsAreSame(order, product.getId())) {
                    coverageProduct.setField(L_PRODUCT_TYPE, null);
                    continue;
                }
                List<Entity> technologiesForProduct = dataDefinitionService
                        .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_TECHNOLOGY).find()
                        .add(SearchRestrictions.belongsTo(TechnologyFields.PRODUCT, product))
                        .add(SearchRestrictions.isNull(TechnologyFields.TECHNOLOGY_TYPE))
                        .add(SearchRestrictions.eq(TechnologyFields.STATE, TechnologyState.ACCEPTED.getStringValue()))
                        .add(SearchRestrictions.eq(TechnologyFields.MASTER, true)).list().getEntities();

                if (technologiesForProduct.isEmpty()) {
                    coverageProduct.setField(L_PRODUCT_TYPE, ProductType.COMPONENT.getStringValue());
                    coverageProduct.setField(L_PLANNED_QUANTITY,
                            entry.getDecimalField(CoverageRegisterFields.PRODUCTION_COUNTING_QUANTITIES));

                } else {
                    coverageProduct.setField(L_PRODUCT_TYPE, ProductType.INTERMEDIATE.getStringValue());
                    coverageProduct.setField(L_PLANNED_QUANTITY,
                            entry.getDecimalField(CoverageRegisterFields.PRODUCTION_COUNTING_QUANTITIES));

                }
            }

            List<Entity> orders = Lists.newArrayList(order);
            materialRequirementCoverage.setField("coverageOrders", orders);

            String sql = "select distinct registry.product.id as productId from #orderSupplies_coverageRegister as registry "
                    + "where registry.order.id = :ids";
            List<Entity> regs = dataDefinitionService.get(OrderSuppliesConstants.PLUGIN_IDENTIFIER, "coverageRegister").find(sql)
                    .setParameter("ids", order.getId()).list().getEntities();
            List<Long> pids = getIdsFromRegisterProduct(regs);
            for (Map.Entry<Long, Entity> productAndCoverageProduct : productAndCoverageProducts.entrySet()) {
                Entity addedCoverageProduct = productAndCoverageProduct.getValue();
                if (pids.contains(productAndCoverageProduct.getKey())) {
                    addedCoverageProduct.setField(CoverageProductFields.FROM_SELECTED_ORDER, true);
                } else {
                    addedCoverageProduct.setField(CoverageProductFields.FROM_SELECTED_ORDER, false);
                }
            }
        }
    }

    private List<Long> getIdsFromRegisterProduct(List<Entity> registerProducts) {

        return registerProducts.stream().map(p -> (Long) p.getField("productId")).collect(Collectors.toList());
    }

    private boolean checkIfProductsAreSame(final Entity order, final Long product) {
        Entity orderProduct = order.getBelongsToField(OrderFields.PRODUCT);

        return orderProduct != null && product.equals(orderProduct.getId());
    }

    @Pointcut("execution(private void com.qcadoo.mes.orderSupplies.coverage.MaterialRequirementCoverageServiceImpl.saveCoverageProduct(..)) "
            + "&& args(materialRequirementCoverage, covProduct)")
    public void saveCoverageProductA(Entity materialRequirementCoverage, Entity covProduct) {
    }

    @Around("saveCoverageProductA(materialRequirementCoverage, covProduct)")
    public void aroundSaveCoverageProduct(final ProceedingJoinPoint pjp, Entity materialRequirementCoverage, Entity covProduct) {
        String sql = "INSERT INTO ordersupplies_coverageproduct "
                + "(materialrequirementcoverage_id, product_id, lackfromdate, demandquantity, coveredquantity, "
                + "reservemissingquantity, deliveredquantity, locationsquantity, state, productnumber, productname, "
                + "productunit, productType, planedQuantity, produceQuantity,fromSelectedOrder, allProductsType, company_id) "
                + "VALUES (:materialrequirementcoverage_id, :product_id, :lackfromdate, :demandquantity, :coveredquantity, "
                + ":reservemissingquantity, :deliveredquantity, :locationsquantity, :state, :productnumber, :productname, "
                + ":productunit, :productType, :planedQuantity, :produceQuantity,:fromSelectedOrder, :allProductsType, :company_id)";

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put(L_PRODUCT_TYPE, covProduct.getStringField(L_PRODUCT_TYPE));
        parameters.put(L_PLANNED_QUANTITY, covProduct.getDecimalField(L_PLANNED_QUANTITY));
        parameters.put("materialrequirementcoverage_id", materialRequirementCoverage.getId());
        parameters.put("product_id", covProduct.getBelongsToField(CoverageProductFields.PRODUCT).getId());
        Entity company = covProduct.getBelongsToField(CoverageProductFields.COMPANY);
        if (company != null) {
            parameters.put("company_id", company.getId());
        } else {
            parameters.put("company_id", null);
        }
        parameters.put("lackfromdate", covProduct.getDateField(CoverageProductFields.LACK_FROM_DATE));
        parameters.put("demandquantity", covProduct.getDecimalField(CoverageProductFields.DEMAND_QUANTITY));
        parameters.put("coveredquantity", covProduct.getDecimalField(CoverageProductFields.COVERED_QUANTITY));
        parameters.put("reservemissingquantity", covProduct.getDecimalField(CoverageProductFields.RESERVE_MISSING_QUANTITY));
        parameters.put("deliveredquantity", covProduct.getDecimalField(CoverageProductFields.DELIVERED_QUANTITY));
        parameters.put("locationsquantity", covProduct.getDecimalField(CoverageProductFields.LOCATIONS_QUANTITY));
        parameters.put("produceQuantity", covProduct.getDecimalField(CoverageProductFields.PRODUCE_QUANTITY));
        parameters.put("state", covProduct.getStringField(CoverageProductFields.STATE));
        parameters.put("productnumber",
                covProduct.getBelongsToField(CoverageProductFields.PRODUCT).getStringField(ProductFields.NUMBER));
        parameters.put("productname",
                covProduct.getBelongsToField(CoverageProductFields.PRODUCT).getStringField(ProductFields.NAME));
        parameters.put("productunit",
                covProduct.getBelongsToField(CoverageProductFields.PRODUCT).getStringField(ProductFields.UNIT));
        parameters.put("fromSelectedOrder", covProduct.getBooleanField(CoverageProductFields.FROM_SELECTED_ORDER));
        parameters.put("allProductsType", covProduct.getStringField("allProductsType"));

        SqlParameterSource namedParameters = new MapSqlParameterSource(parameters);
        jdbcTemplate.update(sql, namedParameters);
    }

}
