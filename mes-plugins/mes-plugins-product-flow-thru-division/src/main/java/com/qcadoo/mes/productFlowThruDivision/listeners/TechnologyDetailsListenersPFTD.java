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
package com.qcadoo.mes.productFlowThruDivision.listeners;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qcadoo.mes.productFlowThruDivision.constants.DivisionFieldsPFTD;
import com.qcadoo.mes.productFlowThruDivision.constants.OperationProductInComponentFieldsPFTD;
import com.qcadoo.mes.productFlowThruDivision.constants.OperationProductOutComponentFieldsPFTD;
import com.qcadoo.mes.productFlowThruDivision.constants.ProductionFlowComponent;
import com.qcadoo.mes.productFlowThruDivision.constants.Range;
import com.qcadoo.mes.productFlowThruDivision.constants.TechnologyFieldsPFTD;
import com.qcadoo.mes.productFlowThruDivision.hooks.TechnologyDetailsHooksPFTD;
import com.qcadoo.mes.productionCounting.constants.TechnologyFieldsPC;
import com.qcadoo.mes.productionCounting.constants.TypeOfProductionRecording;
import com.qcadoo.mes.technologies.OperationComponentDataProvider;
import com.qcadoo.mes.technologies.constants.OperationFields;
import com.qcadoo.mes.technologies.constants.OperationProductInComponentFields;
import com.qcadoo.mes.technologies.constants.OperationProductOutComponentFields;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.mes.technologies.constants.TechnologyOperationComponentFields;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.GridComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.api.components.lookup.FilterValueHolder;
import com.qcadoo.view.constants.QcadooViewConstants;

@Service
public class TechnologyDetailsListenersPFTD {

    @Autowired
    private TechnologyDetailsHooksPFTD technologyDetailsHooksPFTD;

    @Autowired
    private OperationComponentDataProvider operationComponentDataProvider;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    public void setWorkstationsLookup(final ViewDefinitionState view, final ComponentState componentState, final String[] args) {
        if (!(componentState instanceof GridComponent)) {
            return;
        }

        GridComponent grid = (GridComponent) componentState;

        if (grid.getSelectedEntities().isEmpty()) {
            return;
        }

        LookupComponent workstationLookup = (LookupComponent) view.getComponentByReference("workstationsForTOClookup");
        Entity productionLine = grid.getSelectedEntities().get(0).getBelongsToField("productionLine");

        FilterValueHolder filter = workstationLookup.getFilterValue();

        if (Objects.nonNull(productionLine)) {
            filter.put(OperationFields.PRODUCTION_LINE, productionLine.getId());
        } else {
            filter.remove(OperationFields.PRODUCTION_LINE);
        }

        workstationLookup.setFilterValue(filter);
    }

    @Transactional
    public void fillLocationsInComponents(final ViewDefinitionState view, final ComponentState componentState,
            final String[] args) {
        FormComponent technologyForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);

        Entity technology = technologyForm.getPersistedEntityWithIncludedFormValues();

        fillLocationsInComponents(technology);

        view.addMessage("productFlowThruDivision.location.filled", ComponentState.MessageType.SUCCESS);
    }

    @Transactional
    public void fillLocationsInComponents(final Entity technology) {
        if (Range.ONE_DIVISION.getStringValue().equals(technology.getStringField(TechnologyFieldsPFTD.RANGE))) {
            fillForOneDivision(technology);
        } else {
            fillForManyDivision(technology);
        }
    }

    private void fillForOneDivision(final Entity technology) {
        fillForComponentsOne(technology);
        fillForProductsIntermediateInOne(technology);
        fillForProductsIntermediateOutOne(technology);
        fillForFinalOne(technology);
    }

    private void fillForManyDivision(final Entity technology) {
        fillForComponentsMany(technology);
        fillForProductsIntermediateInMany(technology);
        fillForProductsIntermediateOutMany(technology);
        fillForFinalMany(technology);
    }

    private void fillForComponentsMany(final Entity technology) {
        List<Entity> opics = operationComponentDataProvider.getOperationProductsForTechnology(technology.getId());

        for (Entity op : opics) {
            cleanOperationProduct(op);

            Entity division = op.getBelongsToField(OperationProductInComponentFields.OPERATION_COMPONENT)
                    .getBelongsToField(TechnologyOperationComponentFields.DIVISION);

            if (Objects.nonNull(division)) {
                Entity componentsLocation = division.getBelongsToField(DivisionFieldsPFTD.COMPONENTS_LOCATION);
                Entity componentsOutputLocation = division.getBelongsToField(DivisionFieldsPFTD.COMPONENTS_OUTPUT_LOCATION);

                op.setField(OperationProductInComponentFieldsPFTD.COMPONENTS_LOCATION, componentsLocation);
                op.setField(OperationProductInComponentFieldsPFTD.COMPONENTS_OUTPUT_LOCATION, componentsOutputLocation);

            } else {
                op.setField(OperationProductInComponentFieldsPFTD.COMPONENTS_LOCATION, null);
                op.setField(OperationProductInComponentFieldsPFTD.COMPONENTS_OUTPUT_LOCATION, null);
            }

            op.getDataDefinition().fastSave(op);
        }
    }

    private void fillForFinalMany(final Entity technology) {
        List<Long> ids = operationComponentDataProvider.getFinalProductsForTechnology(technology.getId());

        if (ids.isEmpty()) {
            return;
        }

        List<Entity> opocs = getOperationProductOutComponent().find().add(SearchRestrictions.in("id", ids)).list().getEntities();

        for (Entity op : opocs) {
            cleanOperationProduct(op);

            Entity division = op.getBelongsToField(OperationProductOutComponentFields.OPERATION_COMPONENT)
                    .getBelongsToField(TechnologyOperationComponentFields.DIVISION);

            if (Objects.nonNull(division)) {
                Entity productsInputLocation = division.getBelongsToField(DivisionFieldsPFTD.PRODUCTS_INPUT_LOCATION);
                op.setField(OperationProductOutComponentFieldsPFTD.PRODUCTS_INPUT_LOCATION, productsInputLocation);
            } else {
                op.setField(OperationProductOutComponentFieldsPFTD.PRODUCTS_INPUT_LOCATION, null);
            }

            op.getDataDefinition().fastSave(op);
        }
    }

    private void fillForProductsIntermediateOutMany(final Entity technology) {
        List<Long> ids = operationComponentDataProvider.getIntermediateOutProductsForTechnology(technology.getId());

        if (ids.isEmpty()) {
            return;
        }

        List<Entity> opocs = getOperationProductOutComponent().find().add(SearchRestrictions.in("id", ids)).list().getEntities();

        for (Entity op : opocs) {
            cleanOperationProduct(op);

            Entity toc = op.getBelongsToField(OperationProductOutComponentFields.OPERATION_COMPONENT);
            Entity division = toc.getBelongsToField(TechnologyOperationComponentFields.DIVISION);
            String typeOfProductionRecording = toc.getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGY)
                    .getStringField(TechnologyFieldsPC.TYPE_OF_PRODUCTION_RECORDING);

            if (Objects.nonNull(division)
                    && !TypeOfProductionRecording.CUMULATED.getStringValue().equals(typeOfProductionRecording)) {
                String productionFlow = division.getStringField(DivisionFieldsPFTD.PRODUCTION_FLOW);

                Entity productsFlowLocation = division
                        .getBelongsToField(OperationProductOutComponentFieldsPFTD.PRODUCTS_FLOW_LOCATION);

                op.setField(OperationProductOutComponentFieldsPFTD.PRODUCTION_FLOW, productionFlow);
                op.setField(OperationProductOutComponentFieldsPFTD.PRODUCTS_FLOW_LOCATION, productsFlowLocation);
            } else {
                op.setField(OperationProductOutComponentFieldsPFTD.PRODUCTION_FLOW, null);
                op.setField(OperationProductOutComponentFieldsPFTD.PRODUCTS_FLOW_LOCATION, null);
            }

            op.getDataDefinition().fastSave(op);
        }
    }

    private void fillForProductsIntermediateInMany(final Entity technology) {
        List<Entity> opics = operationComponentDataProvider.getOperationsIntermediateInProductsForTechnology(technology.getId());

        for (Entity op : opics) {
            cleanOperationProduct(op);

            Entity toc = op.getBelongsToField(OperationProductInComponentFields.OPERATION_COMPONENT);
            Entity division = toc.getBelongsToField(TechnologyOperationComponentFields.DIVISION);
            String typeOfProductionRecording = toc.getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGY)
                    .getStringField(TechnologyFieldsPC.TYPE_OF_PRODUCTION_RECORDING);

            if (Objects.nonNull(division)
                    && !TypeOfProductionRecording.CUMULATED.getStringValue().equals(typeOfProductionRecording)) {
                String productionFlow = division.getStringField(DivisionFieldsPFTD.PRODUCTION_FLOW);

                Entity productsFlowLocation = division.getBelongsToField(DivisionFieldsPFTD.PRODUCTS_FLOW_LOCATION);

                op.setField(OperationProductInComponentFieldsPFTD.PRODUCTION_FLOW, productionFlow);
                op.setField(OperationProductInComponentFieldsPFTD.PRODUCTS_FLOW_LOCATION, productsFlowLocation);
            } else {
                op.setField(OperationProductInComponentFieldsPFTD.PRODUCTION_FLOW, null);
                op.setField(OperationProductInComponentFieldsPFTD.PRODUCTS_FLOW_LOCATION, null);
            }

            op.getDataDefinition().fastSave(op);
        }
    }

    private void fillForFinalOne(final Entity technology) {
        List<Long> ids = operationComponentDataProvider.getFinalProductsForTechnology(technology.getId());

        if (ids.isEmpty()) {
            return;
        }

        List<Entity> opocs = getOperationProductOutComponent().find().add(SearchRestrictions.in("id", ids)).list().getEntities();

        Entity productsInputLocation = technology
                .getBelongsToField(OperationProductOutComponentFieldsPFTD.PRODUCTS_INPUT_LOCATION);

        for (Entity op : opocs) {
            cleanOperationProduct(op);

            op.setField(OperationProductOutComponentFieldsPFTD.PRODUCTS_INPUT_LOCATION, productsInputLocation);

            op.getDataDefinition().fastSave(op);
        }
    }

    private void fillForProductsIntermediateOutOne(final Entity technology) {
        List<Long> ids = operationComponentDataProvider.getIntermediateOutProductsForTechnology(technology.getId());

        if (ids.isEmpty()) {
            return;
        }

        List<Entity> opocs = getOperationProductOutComponent().find().add(SearchRestrictions.in("id", ids)).list().getEntities();

        String productionFlow = technology.getStringField(TechnologyFieldsPFTD.PRODUCTION_FLOW);
        Entity productsFlowLocation = technology.getBelongsToField(OperationProductOutComponentFieldsPFTD.PRODUCTS_FLOW_LOCATION);

        for (Entity op : opocs) {
            cleanOperationProduct(op);

            op.setField(OperationProductOutComponentFieldsPFTD.PRODUCTION_FLOW, productionFlow);
            op.setField(OperationProductOutComponentFieldsPFTD.PRODUCTS_FLOW_LOCATION, productsFlowLocation);

            op.getDataDefinition().fastSave(op);
        }
    }

    private void fillForProductsIntermediateInOne(final Entity technology) {
        List<Entity> opics = operationComponentDataProvider.getOperationsIntermediateInProductsForTechnology(technology.getId());

        String productionFlow = technology.getStringField(TechnologyFieldsPFTD.PRODUCTION_FLOW);
        Entity productsFlowLocation = technology.getBelongsToField(TechnologyFieldsPFTD.PRODUCTS_FLOW_LOCATION);

        for (Entity op : opics) {
            cleanOperationProduct(op);

            op.setField(OperationProductInComponentFieldsPFTD.PRODUCTION_FLOW, productionFlow);
            op.setField(OperationProductInComponentFieldsPFTD.PRODUCTS_FLOW_LOCATION, productsFlowLocation);

            op.getDataDefinition().fastSave(op);
        }
    }

    private void fillForComponentsOne(final Entity technology) {
        List<Entity> opics = operationComponentDataProvider.getOperationProductsForTechnology(technology.getId());

        Entity componentsLocation = technology.getBelongsToField(OperationProductOutComponentFieldsPFTD.COMPONENTS_LOCATION);
        Entity componentsOutputLocation = technology.getBelongsToField(TechnologyFieldsPFTD.COMPONENTS_OUTPUT_LOCATION);

        for (Entity op : opics) {
            cleanOperationProduct(op);

            op.setField(OperationProductInComponentFieldsPFTD.COMPONENTS_LOCATION, componentsLocation);
            op.setField(OperationProductInComponentFieldsPFTD.COMPONENTS_OUTPUT_LOCATION, componentsOutputLocation);

            op.getDataDefinition().fastSave(op);
        }
    }

    public void onProductionFlowComponentChange(final ViewDefinitionState view, final ComponentState componentState,
            final String[] args) {
        FormComponent form = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        Entity technology = form.getPersistedEntityWithIncludedFormValues();

        if (Range.ONE_DIVISION.getStringValue().equals(technology.getStringField(TechnologyFieldsPFTD.RANGE))) {
            LookupComponent productsFlowLocationLookup = (LookupComponent) view
                    .getComponentByReference(TechnologyFieldsPFTD.PRODUCTS_FLOW_LOCATION);

            if (ProductionFlowComponent.WAREHOUSE.getStringValue()
                    .equals(technology.getField(TechnologyFieldsPFTD.PRODUCTION_FLOW))) {
                productsFlowLocationLookup.setEnabled(true);
            } else {
                productsFlowLocationLookup.setEnabled(false);
                productsFlowLocationLookup.setFieldValue(null);
            }
        }
    }

    public void onDivisionChange(final ViewDefinitionState view, final ComponentState componentState, final String[] args) {
        technologyDetailsHooksPFTD.fillFieldsForOneDivisionRange(view);
    }

    private void setFieldsVisible(final ViewDefinitionState view, final List<String> references, final boolean isVisible) {
        for (String reference : references) {
            FieldComponent field = (FieldComponent) view.getComponentByReference(reference);

            field.setVisible(isVisible);
            field.requestComponentUpdateState();
        }
    }

    private void cleanOperationProduct(final Entity operationProduct) {
        operationProduct.setField(OperationProductInComponentFieldsPFTD.PRODUCTION_FLOW,
                ProductionFlowComponent.WITHIN_THE_PROCESS.getStringValue());
        operationProduct.setField(OperationProductInComponentFieldsPFTD.PRODUCTS_FLOW_LOCATION, null);
        operationProduct.setField(OperationProductInComponentFieldsPFTD.COMPONENTS_LOCATION, null);
        operationProduct.setField(OperationProductInComponentFieldsPFTD.COMPONENTS_OUTPUT_LOCATION, null);
        operationProduct.setField(OperationProductInComponentFieldsPFTD.PRODUCTS_INPUT_LOCATION, null);
    }

    private DataDefinition getOperationProductOutComponent() {
        return dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_OPERATION_PRODUCT_OUT_COMPONENT);
    }

}
