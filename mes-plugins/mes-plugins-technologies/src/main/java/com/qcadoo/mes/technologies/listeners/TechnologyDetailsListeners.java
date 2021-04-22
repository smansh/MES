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
package com.qcadoo.mes.technologies.listeners;

import static com.qcadoo.mes.technologies.constants.TechnologyFields.PRODUCT_STRUCTURE_TREE;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.qcadoo.mes.technologies.constants.OperationFields;
import com.qcadoo.mes.technologies.constants.OperationProductInComponentFields;
import com.qcadoo.mes.technologies.constants.OperationProductOutComponentFields;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.mes.technologies.constants.TechnologyFields;
import com.qcadoo.mes.technologies.constants.TechnologyOperationComponentFields;
import com.qcadoo.mes.technologies.states.listener.TechnologyValidationService;
import com.qcadoo.mes.technologies.tree.ProductStructureTreeService;
import com.qcadoo.mes.technologies.tree.RemoveTOCService;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.EntityTree;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ComponentState.MessageType;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.CheckBoxComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.api.components.TreeComponent;
import com.qcadoo.view.api.components.WindowComponent;
import com.qcadoo.view.constants.QcadooViewConstants;

@Service
public class TechnologyDetailsListeners {

    private static final String L_PRODUCT_STRUCTURE_FORM = "productStructureForm";

    private static final String L_PRODUCT_STRUCTURE = "productStructure";

    private static final String L_TECHNOLOGY_TREE_REFERENCE = "technologyTree";

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private ProductStructureTreeService productStructureTreeService;

    @Autowired
    private RemoveTOCService removeTOCService;

    @Autowired
    private TechnologyValidationService technologyValidationService;

    public void setGridEditable(final ViewDefinitionState view, final ComponentState state, final String[] args) {
    }

    public void removeOnlySelectedOperation(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        final TreeComponent technologyTree = (TreeComponent) view.getComponentByReference(L_TECHNOLOGY_TREE_REFERENCE);
        final Long selectedEntityId = technologyTree.getSelectedEntityId();

        Entity selectedOperation = getTechnologyOperationComponentDD().get(selectedEntityId);

        boolean removed = removeTOCService.removeOnlySelectedOperation(selectedOperation, view);

        if (removed) {
            FormComponent technologyForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);

            technologyForm.performEvent(view, "reset");

            view.addMessage("technologies.technologyDetails.window.treeTab.technologyTree.success",
                    ComponentState.MessageType.SUCCESS);
        }
    }

    public void generateProductStructure(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        FormComponent technologyForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        FormComponent productStructureForm = (FormComponent) view.getComponentByReference(L_PRODUCT_STRUCTURE_FORM);

        Entity technology = technologyForm.getEntity();
        Entity productTechnology = technology.copy();
        EntityTree generatedTree = productStructureTreeService.generateProductStructureTree(view, technology);

        productTechnology.setField(PRODUCT_STRUCTURE_TREE, generatedTree);
        productStructureForm.setEntity(productTechnology);

        WindowComponent window = (WindowComponent) view.getComponentByReference(QcadooViewConstants.L_WINDOW);
        window.setActiveTab(L_PRODUCT_STRUCTURE);
    }

    public void fillProducts(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        FormComponent technologyForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);

        Entity technology = getTechnologyDD().get(technologyForm.getEntityId());

        List<Entity> operationsWithManyOutProducts = fillProducts(technology);

        if (!operationsWithManyOutProducts.isEmpty()) {
            state.addMessage("technologies.technologyDetails.window.tooManyOutProductsInOperation", MessageType.INFO,
                    operationsWithManyOutProducts.stream().map(o -> o.getStringField(OperationFields.NUMBER))
                            .collect(Collectors.joining(", ")));
        }
    }

    public List<Entity> fillProducts(Entity technology) {
        List<Entity> technologyOperationComponents = technology.getHasManyField(TechnologyFields.OPERATION_COMPONENTS);
        List<Entity> operationsWithManyOutProducts = Lists.newArrayList();

        for (Entity technologyOperationComponent : technologyOperationComponents) {
            List<Entity> operationProductOutComponents = technologyOperationComponent
                    .getHasManyField(TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS);
            Entity parent = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.PARENT);

            if (operationProductOutComponents.size() == 1 && Objects.nonNull(parent)) {
                Entity operationProductOutComponent = operationProductOutComponents.get(0);
                Entity operationProductInComponent = getOperationProductInComponentDD().create();

                operationProductInComponent.setField(OperationProductInComponentFields.QUANTITY,
                        operationProductOutComponent.getField(OperationProductOutComponentFields.QUANTITY));
                operationProductInComponent.setField(OperationProductInComponentFields.PRODUCT,
                        operationProductOutComponent.getBelongsToField(OperationProductOutComponentFields.PRODUCT));
                operationProductInComponent.setField(OperationProductInComponentFields.OPERATION_COMPONENT, parent);

                operationProductInComponent.getDataDefinition().save(operationProductInComponent);
            } else if (operationProductOutComponents.size() > 1) {
                operationsWithManyOutProducts
                        .add(technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.OPERATION));
            }
        }
        return operationsWithManyOutProducts;
    }

    public void openOperationProductInComponentsImportPage(final ViewDefinitionState view, final ComponentState state,
            final String[] args) {
        FormComponent technologyForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        Entity technology = technologyForm.getPersistedEntityWithIncludedFormValues();

        Long technologyId = technology.getId();

        if (Objects.nonNull(technologyId)) {
            Map<String, Object> parameters = Maps.newHashMap();

            parameters.put("form.id", technologyId);

            JSONObject context = new JSONObject(parameters);

            String url = "../page/technologies/operationProductInComponentsImport.html?context=" + context.toString();
            view.openModal(url);
        }
    }

    public void clearQualityCard(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        LookupComponent qualityCardLookup = (LookupComponent) view.getComponentByReference(TechnologyFields.QUALITY_CARD);
        qualityCardLookup.setFieldValue(null);
        qualityCardLookup.requestComponentUpdateState();
    }

    public void acceptTemplate(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        FormComponent technologyForm = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        CheckBoxComponent isTemplateAcceptedCheckBox = (CheckBoxComponent) view
                .getComponentByReference(TechnologyFields.IS_TEMPLATE_ACCEPTED);

        Entity technology = technologyForm.getEntity();

        Long technologyId = technology.getId();

        boolean isTemplateAccepted = isTemplateAcceptedCheckBox.isChecked();

        if (Objects.nonNull(technologyId)) {
            technology = technology.getDataDefinition().save(technology);

            if (isTemplateAccepted || validateTemplate(technologyForm, technology.getDataDefinition().get(technologyId))) {
                isTemplateAcceptedCheckBox.setChecked(!isTemplateAccepted);

                technologyForm.performEvent(view, "save");

                if (technologyForm.isHasError()) {
                    isTemplateAcceptedCheckBox.setChecked(isTemplateAccepted);
                }
            }
        }
    }

    private boolean validateTemplate(final FormComponent technologyForm, final Entity technology) {
        boolean isValid = true;

        isValid = isValid && technologyValidationService.checkIfTechnologyTreeIsSet(technologyForm, technology);
        isValid = isValid
                && technologyValidationService.checkTopComponentsProducesProductForTechnology(technologyForm, technology);
        isValid = isValid && technologyValidationService.checkIfOperationsUsesSubOperationsProds(technologyForm, technology);

        return isValid;
    }

    private DataDefinition getTechnologyDD() {
        return dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_TECHNOLOGY);
    }

    private DataDefinition getTechnologyOperationComponentDD() {
        return dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_TECHNOLOGY_OPERATION_COMPONENT);
    }

    private DataDefinition getOperationProductInComponentDD() {
        return dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_OPERATION_PRODUCT_IN_COMPONENT);
    }

}
