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
package com.qcadoo.mes.technologies;

import com.google.common.collect.Lists;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OperationComponentDataProvider {

    public static final String L_IS_INTERMEDIATE = "isIntermediate";

    public static final String L_TECHNOLOGY_ID = "technologyID";

    public static final String L_OPOC_ID = "opocId";

    public static final String L_OPIC_ID = "opicId";

    public static final String L_OPERATION_PRODUCT = "operationProduct";

    @Autowired
    private DataDefinitionService dataDefinitionService;

      private static final String inComponentHQL = "select DISTINCT opic.id as opicId, "
            + "(select count(*) from "
            + "#technologies_operationProductOutComponent opoc "
            + "left join opoc.operationComponent oc  "
            + "left join oc.technology as tech "
            + "left join oc.parent par  "
            + "where "
            + "opoc.product = inputProd and par.id = toc.id ) as isIntermediate "
            + "from #technologies_operationProductInComponent opic "
            + "left join opic.product as inputProd "
            + "left join opic.operationComponent toc "
            + "left join toc.technology tech "
            + "where tech.id = :technologyID ";

    private static final String inComponentEntityHQL = "select DISTINCT opic as operationProduct, "
            + "(select count(*) from "
            + "#technologies_operationProductOutComponent opoc "
            + "left join opoc.operationComponent oc  "
            + "left join oc.technology as tech "
            + "left join oc.parent par  "
            + "where "
            + "opoc.product = inputProd and par.id = toc.id ) as isIntermediate "
            + "from #technologies_operationProductInComponent opic "
            + "left join opic.product as inputProd "
            + "left join opic.operationComponent toc "
            + "left join toc.technology tech "
            + "where tech.id = :technologyID ";


    private static final String intermediateOutHQL = "select  opoc.id as opocId                       \n"
            + "         from  #technologies_operationProductOutComponent opoc                         \n"
            + "                   left join opoc.operationComponent toc                               \n"
            + "                   left join toc.technology tech                                       \n"
            + "                   left join toc.parent as parentToc                                   \n"
            + "         where tech.id = :technologyID and parentToc IS NOT NULL";

    private static final String finalHQL = "select  opoc.id as opocId                       \n"
            + "         from  #technologies_operationProductOutComponent opoc                         \n"
            + "                   left join opoc.operationComponent toc                               \n"
            + "                   left join toc.technology tech                                       \n"
            + "                   left join toc.parent as parentToc                                   \n"
            + "         where tech.id = :technologyID and parentToc IS NULL";

    private List<Entity> findInProductsForTechnology(final Long technologyId) {

        List<Entity> coveredOrderProductsTo = dataDefinitionService
                .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_OPERATION_PRODUCT_IN_COMPONENT)
                .find(inComponentHQL)
                .setLong(L_TECHNOLOGY_ID, technologyId).list().getEntities();

        return coveredOrderProductsTo;
    }

    public List<Entity> findComponentsForTechnology(final Long technologyId) {

        List<Entity> allProducts = findInProductsForTechnology(technologyId);
        List<Entity> components = allProducts.stream().filter(p -> (Long) p.getField(L_IS_INTERMEDIATE) == 0l)
                .collect(Collectors.toList());
        return components;
    }

    public List<Entity> getOperationProductsForTechnology(final Long technologyId) {

        List<Entity> operationProductsTo = dataDefinitionService
                .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_OPERATION_PRODUCT_IN_COMPONENT)
                .find(inComponentEntityHQL)
                .setLong(L_TECHNOLOGY_ID, technologyId).list().getEntities();
        return operationProductsTo.stream().filter(p -> (Long) p.getField(L_IS_INTERMEDIATE) == 0l).map(p -> p.getBelongsToField(L_OPERATION_PRODUCT))
                .collect(Collectors.toList());
    }

    public List<Entity> getOperationsIntermediateInProductsForTechnology(final Long technologyId) {

        List<Entity> operationProductsTo = dataDefinitionService
                .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_OPERATION_PRODUCT_IN_COMPONENT)
                .find(inComponentEntityHQL)
                .setLong(L_TECHNOLOGY_ID, technologyId).list().getEntities();
        return operationProductsTo.stream().filter(p -> (Long) p.getField(L_IS_INTERMEDIATE) > 0l).map(
                p -> p.getBelongsToField(L_OPERATION_PRODUCT))
                .collect(Collectors.toList());
    }

    public List<Long> getComponentsForTechnology(final Long technologyId) {
        List<Entity> components = findComponentsForTechnology(technologyId);
        Collection<Long> componentsIds = CollectionUtils
                .collect(components, new Transformer() {

                    public Long transform(Object o) {
                        return (Long) ((Entity) o).getField(L_OPIC_ID);
                    }
                });
        return Lists.newArrayList(componentsIds);
    }

    public List<Entity> findIntermediateInProductsForTechnology(final Long technologyId) {

        List<Entity> allProducts = findInProductsForTechnology(technologyId);
        List<Entity> components = allProducts.stream().filter(p -> (Long) p.getField(L_IS_INTERMEDIATE) == 1l)
                .collect(Collectors.toList());
        return components;
    }

    public List<Long> getIntermediateInProductsForTechnology(final Long technologyId) {
        List<Entity> components = findIntermediateInProductsForTechnology(technologyId);
        Collection<Long> componentsIds = CollectionUtils
                .collect(components, new Transformer() {

                    public Long transform(Object o) {
                        return (Long) ((Entity) o).getField(L_OPIC_ID);
                    }
                });
        return Lists.newArrayList(componentsIds);
    }

    public List<Entity> findIntermediateOutProductsForTechnology(final Long technologyId) {
        List<Entity> intermediateOutProducts = dataDefinitionService
                .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_OPERATION_PRODUCT_OUT_COMPONENT)
                .find(intermediateOutHQL)
                .setLong(L_TECHNOLOGY_ID, technologyId).list().getEntities();
        return intermediateOutProducts;
    }

    public List<Long> getIntermediateOutProductsForTechnology(final Long technologyId) {
        List<Entity> components = findIntermediateOutProductsForTechnology(technologyId);
        Collection<Long> componentsIds = CollectionUtils
                .collect(components, new Transformer() {

                    public Long transform(Object o) {
                        return (Long) ((Entity) o).getField(L_OPOC_ID);
                    }
                });
        return Lists.newArrayList(componentsIds);
    }

    public List<Entity> findFinalProductsForTechnology(final Long technologyId) {
        List<Entity> intermediateOutProducts = dataDefinitionService
                .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_OPERATION_PRODUCT_OUT_COMPONENT)
                .find(finalHQL)
                .setLong(L_TECHNOLOGY_ID, technologyId).list().getEntities();
        return intermediateOutProducts;
    }

    public List<Long> getFinalProductsForTechnology(final Long technologyId) {
        List<Entity> components = findFinalProductsForTechnology(technologyId);
        Collection<Long> componentsIds = CollectionUtils
                .collect(components, new Transformer() {

                    public Long transform(Object o) {
                        return (Long) ((Entity) o).getField(L_OPOC_ID);
                    }
                });
        return Lists.newArrayList(componentsIds);
    }
}
