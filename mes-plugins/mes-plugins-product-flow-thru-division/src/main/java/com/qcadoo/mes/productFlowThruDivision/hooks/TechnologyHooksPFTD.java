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
package com.qcadoo.mes.productFlowThruDivision.hooks;

import static com.qcadoo.model.api.search.SearchRestrictions.eq;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.qcadoo.mes.basic.ParameterService;
import com.qcadoo.mes.productFlowThruDivision.constants.OperationProductInComponentFieldsPFTD;
import com.qcadoo.mes.productFlowThruDivision.constants.ParameterFieldsPFTD;
import com.qcadoo.mes.productFlowThruDivision.constants.ProductionFlowComponent;
import com.qcadoo.mes.productFlowThruDivision.constants.Range;
import com.qcadoo.mes.productFlowThruDivision.constants.TechnologyFieldsPFTD;
import com.qcadoo.mes.productFlowThruDivision.constants.TechnologyOperationComponentFieldsPFTD;
import com.qcadoo.mes.productionCounting.constants.TechnologyFieldsPC;
import com.qcadoo.mes.productionCounting.constants.TypeOfProductionRecording;
import com.qcadoo.mes.technologies.constants.OperationProductOutComponentFields;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.mes.technologies.constants.TechnologyOperationComponentFields;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.JoinType;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchRestrictions;

@Service
public class TechnologyHooksPFTD {

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private ParameterService parameterService;

    public void onCreate(final DataDefinition technologyDD, final Entity technology) {
        fillRangeAndDivision(technologyDD, technology);
        fillProductionFlow(technologyDD, technology);
    }

    private void fillRangeAndDivision(final DataDefinition technologyDD, final Entity technology) {
        String range = technology.getStringField(TechnologyFieldsPFTD.RANGE);
        Entity division = technology.getBelongsToField(TechnologyFieldsPFTD.DIVISION);

        if (StringUtils.isEmpty(range)) {
            range = parameterService.getParameter().getStringField(ParameterFieldsPFTD.RANGE);

            if (StringUtils.isEmpty(range)) {
                range = Range.MANY_DIVISIONS.getStringValue();
            }
        }
        if (Objects.isNull(division)) {
            division = parameterService.getParameter().getBelongsToField(ParameterFieldsPFTD.DIVISION);
        }

        technology.setField(TechnologyFieldsPFTD.RANGE, range);
        technology.setField(TechnologyFieldsPFTD.DIVISION, division);
    }

    public void fillProductionFlow(final DataDefinition technologyDD, final Entity technology) {
        if (Objects.isNull(technology.getField(TechnologyFieldsPFTD.PRODUCTION_FLOW))) {
            technology.setField(TechnologyFieldsPFTD.PRODUCTION_FLOW,
                    ProductionFlowComponent.WITHIN_THE_PROCESS.getStringValue());
        }
    }

    public void onSave(final DataDefinition technologyDD, final Entity technology) {
        cleanUpOnRangeChange(technologyDD, technology);
        cleanUpOnProductionRecordingTypeChangeToCumulated(technologyDD, technology);
        fillDivision(technologyDD, technology);
        fillProductionLine(technologyDD, technology);
    }

    private void cleanUpOnProductionRecordingTypeChangeToCumulated(final DataDefinition technologyDD, final Entity technology) {
        if (Objects.isNull(technology.getId())) {
            return;
        }

        Entity technologyDB = technologyDD.get(technology.getId());

        if (TypeOfProductionRecording.CUMULATED.getStringValue()
                .equals(technology.getStringField(TechnologyFieldsPC.TYPE_OF_PRODUCTION_RECORDING))
                && !technology.getStringField(TechnologyFieldsPC.TYPE_OF_PRODUCTION_RECORDING)
                        .equals(technologyDB.getStringField(TechnologyFieldsPC.TYPE_OF_PRODUCTION_RECORDING))) {
            List<Entity> opocs = findOPOCs(technology.getId());

            for (Entity opoc : opocs) {
                cleanOperationProductProductionFlow(opoc);
            }

            List<Entity> opics = findOPICs(technology.getId());

            for (Entity opic : opics) {
                cleanOperationProductProductionFlow(opic);
            }
        }
    }

    private void cleanOperationProductProductionFlow(final Entity operationProduct) {
        operationProduct.setField(OperationProductInComponentFieldsPFTD.PRODUCTION_FLOW,
                ProductionFlowComponent.WITHIN_THE_PROCESS.getStringValue());
        operationProduct.setField(OperationProductInComponentFieldsPFTD.PRODUCTS_FLOW_LOCATION, null);

        operationProduct.getDataDefinition().fastSave(operationProduct);
    }

    private void fillProductionLine(final DataDefinition technologyDD, final Entity technology) {
        if (Objects.nonNull(technology.getId())) {
            if (technology.getField(TechnologyFieldsPFTD.RANGE).equals(Range.ONE_DIVISION.getStringValue())) {
                Entity technologyDB = technologyDD.get(technology.getId());
                Entity productionLineDb = technologyDB.getBelongsToField(TechnologyFieldsPFTD.PRODUCTION_LINE);

                if (Objects.isNull(technology.getBelongsToField(TechnologyFieldsPFTD.PRODUCTION_LINE))) {
                    List<Entity> tocs = getTechnologyOperationComponents(technology);

                    for (Entity toc : tocs) {
                        if (!toc.getBooleanField(TechnologyOperationComponentFieldsPFTD.PRODUCTION_LINE_CHANGE)) {
                            toc.setField(TechnologyOperationComponentFields.PRODUCTION_LINE, null);
                            toc.getDataDefinition().save(toc);
                        }
                    }
                } else if (Objects.isNull(productionLineDb) || !technology.getBelongsToField(TechnologyFieldsPFTD.PRODUCTION_LINE)
                        .getId().equals(productionLineDb.getId())) {
                    List<Entity> tocs = getTechnologyOperationComponents(technology);

                    for (Entity toc : tocs) {
                        toc.setField(TechnologyFieldsPFTD.PRODUCTION_LINE,
                                technology.getBelongsToField(TechnologyFieldsPFTD.PRODUCTION_LINE));
                        toc.getDataDefinition().save(toc);
                    }
                }
            } else {
                technology.setField(TechnologyFieldsPFTD.PRODUCTION_LINE, null);
            }
        }
    }

    private void fillDivision(final DataDefinition technologyDD, final Entity technology) {
        if (Objects.nonNull(technology.getId())) {
            if (technology.getField(TechnologyFieldsPFTD.RANGE).equals(Range.ONE_DIVISION.getStringValue())) {
                List<Entity> tocs = getTechnologyOperationComponents(technology);

                for (Entity toc : tocs) {
                    toc.setField(TechnologyFieldsPFTD.DIVISION, technology.getBelongsToField(TechnologyFieldsPFTD.DIVISION));
                    toc.getDataDefinition().save(toc);
                }
            } else {
                technology.setField(TechnologyFieldsPFTD.DIVISION, null);
            }
        }
    }

    private void cleanUpOnRangeChange(final DataDefinition technologyDD, final Entity technology) {
        if (Objects.isNull(technology.getId())) {
            return;
        }

        Entity technologyDB = technologyDD.get(technology.getId());

        if (!technology.getStringField(TechnologyFieldsPFTD.RANGE)
                .equals(technologyDB.getStringField(TechnologyFieldsPFTD.RANGE))) {
            cleanLocations(technology);

            if (technology.getField(TechnologyFieldsPFTD.RANGE).equals(Range.MANY_DIVISIONS.getStringValue())) {
                technology.setField(TechnologyFieldsPFTD.COMPONENTS_LOCATION, null);
                technology.setField(TechnologyFieldsPFTD.COMPONENTS_OUTPUT_LOCATION, null);
                technology.setField(TechnologyFieldsPFTD.PRODUCTS_INPUT_LOCATION, null);
                technology.setField(TechnologyFieldsPFTD.PRODUCTION_FLOW, null);
                technology.setField(TechnologyFieldsPFTD.PRODUCTS_FLOW_LOCATION, null);
            }
        }
    }

    private void cleanLocations(final Entity technology) {
        List<Entity> opocs = findOPOCs(technology.getId());

        for (Entity opoc : opocs) {
            cleanOperationProduct(opoc);
        }

        List<Entity> opics = findOPICs(technology.getId());

        for (Entity opic : opics) {
            cleanOperationProduct(opic);
        }
    }

    public List<Entity> findOPOCs(final Long technologyId) {
        SearchCriteriaBuilder scb = getOpocDD().find();

        scb.createAlias(OperationProductOutComponentFields.OPERATION_COMPONENT, "toc", JoinType.INNER);
        scb.createAlias("toc." + TechnologyOperationComponentFields.TECHNOLOGY, "tech", JoinType.INNER);

        scb.add(eq("tech.id", technologyId));

        return scb.list().getEntities();
    }

    public List<Entity> findOPICs(final Long technologyId) {
        SearchCriteriaBuilder scb = getOpicDD().find();

        scb.createAlias(OperationProductOutComponentFields.OPERATION_COMPONENT, "toc", JoinType.INNER);
        scb.createAlias("toc." + TechnologyOperationComponentFields.TECHNOLOGY, "tech", JoinType.INNER);

        scb.add(eq("tech.id", technologyId));

        return scb.list().getEntities();
    }

    private void cleanOperationProduct(final Entity operationProduct) {
        operationProduct.setField(OperationProductInComponentFieldsPFTD.PRODUCTION_FLOW,
                ProductionFlowComponent.WITHIN_THE_PROCESS.getStringValue());
        operationProduct.setField(OperationProductInComponentFieldsPFTD.PRODUCTS_FLOW_LOCATION, null);
        operationProduct.setField(OperationProductInComponentFieldsPFTD.COMPONENTS_LOCATION, null);
        operationProduct.setField(OperationProductInComponentFieldsPFTD.COMPONENTS_OUTPUT_LOCATION, null);
        operationProduct.setField(OperationProductInComponentFieldsPFTD.PRODUCTS_INPUT_LOCATION, null);

        operationProduct.getDataDefinition().fastSave(operationProduct);
    }

    public Entity getDivisionForOperation(final Entity technologyOperationComponent) {
        return technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.DIVISION);
    }

    private List<Entity> getTechnologyOperationComponents(final Entity technology) {
        return getTechnologyOperationComponentDD().find()
                .add(SearchRestrictions.belongsTo(TechnologyOperationComponentFields.TECHNOLOGY, technology)).list()
                .getEntities();
    }

    private DataDefinition getTechnologyOperationComponentDD() {
        return dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_TECHNOLOGY_OPERATION_COMPONENT);
    }

    private DataDefinition getOpicDD() {
        return dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_OPERATION_PRODUCT_IN_COMPONENT);
    }

    private DataDefinition getOpocDD() {
        return dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_OPERATION_PRODUCT_OUT_COMPONENT);
    }

}
