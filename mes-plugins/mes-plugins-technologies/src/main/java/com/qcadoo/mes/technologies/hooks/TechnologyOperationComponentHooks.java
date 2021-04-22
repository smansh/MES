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
package com.qcadoo.mes.technologies.hooks;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.qcadoo.mes.technologies.TechnologyService;
import com.qcadoo.mes.technologies.constants.*;
import com.qcadoo.model.api.*;
import com.qcadoo.model.api.search.SearchRestrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TechnologyOperationComponentHooks {

    @Autowired
    private TechnologyService technologyService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    public void onCreate(final DataDefinition technologyOperationComponentDD, final Entity technologyOperationComponent) {
        copyCommentAndAttachmentFromOperation(technologyOperationComponent);
        setParentIfRootNodeAlreadyExists(technologyOperationComponent);
        setOperationOutProduct(technologyOperationComponent);
        copyReferencedTechnology(technologyOperationComponentDD, technologyOperationComponent);
        copyWorkstationsSettingsFromOperation(technologyOperationComponent);
    }

    public void copyWorkstationsSettingsFromOperation(final Entity technologyOperationComponent) {
        Entity operation = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.OPERATION);

        if (operation != null) {
            technologyOperationComponent.setField(TechnologyOperationComponentFields.QUANTITY_OF_WORKSTATIONS,
                    operation.getIntegerField(OperationFields.QUANTITY_OF_WORKSTATIONS));
            technologyOperationComponent.setField(TechnologyOperationComponentFields.ASSIGNED_TO_OPERATION,
                    operation.getField(OperationFields.ASSIGNED_TO_OPERATION));
            technologyOperationComponent.setField(TechnologyOperationComponentFields.WORKSTATION_TYPE,
                    operation.getBelongsToField(OperationFields.WORKSTATION_TYPE));
            technologyOperationComponent.setField(TechnologyOperationComponentFields.WORKSTATIONS,
                    operation.getManyToManyField(OperationFields.WORKSTATIONS));
            technologyOperationComponent.setField(TechnologyOperationComponentFields.DIVISION,
                    operation.getBelongsToField(OperationFields.DIVISION));
            technologyOperationComponent.setField(TechnologyOperationComponentFields.PRODUCTION_LINE,
                    operation.getBelongsToField(OperationFields.PRODUCTION_LINE));
        }
    }

    private void copyCommentAndAttachmentFromOperation(final Entity technologyOperationComponent) {
        technologyService.copyCommentAndAttachmentFromLowerInstance(technologyOperationComponent,
                TechnologyOperationComponentFields.OPERATION);
    }

    private void setParentIfRootNodeAlreadyExists(final Entity technologyOperationComponent) {
        Entity technology = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGY);
        EntityTree tree = technology.getTreeField(TechnologyFields.OPERATION_COMPONENTS);

        if (tree == null || tree.isEmpty()) {
            return;
        }

        EntityTreeNode rootNode = tree.getRoot();

        if ((rootNode == null)
                || (technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.PARENT) != null)) {
            return;
        }

        technologyOperationComponent.setField(TechnologyOperationComponentFields.PARENT, rootNode);
    }

    private void setOperationOutProduct(Entity technologyOperationComponent) {
        if (Objects.nonNull(technologyOperationComponent
                .getHasManyField(TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS))
                && technologyOperationComponent.getHasManyField(
                        TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS).isEmpty()) {
            Entity technology = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGY);
            EntityTree tree = technology.getTreeField(TechnologyFields.OPERATION_COMPONENTS);
            DataDefinition opocDD = dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                    TechnologiesConstants.MODEL_OPERATION_PRODUCT_OUT_COMPONENT);
            Entity opoc = opocDD.create();
            opoc.setField(OperationProductOutComponentFields.QUANTITY, 1);
            if (tree == null || tree.isEmpty()) {
                opoc.setField(OperationProductOutComponentFields.PRODUCT, technology.getBelongsToField(TechnologyFields.PRODUCT));
                technologyOperationComponent.setField(TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS,
                        Collections.singletonList(opoc));
            } else {
                Entity operation = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.OPERATION);
                if (Objects.nonNull(operation)) {
                    Entity product = operation.getBelongsToField(OperationFields.PRODUCT);
                    if (Objects.nonNull(product)) {
                        opoc.setField(OperationProductOutComponentFields.PRODUCT, product);
                        technologyOperationComponent.setField(
                                TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS,
                                Collections.singletonList(opoc));
                    }
                }
            }
        }
    }

    private void copyReferencedTechnology(final DataDefinition technologyOperationComponentDD,
            final Entity technologyOperationComponent) {
        if (technologyOperationComponent.getField(TechnologyOperationComponentFields.REFERENCE_TECHNOLOGY) == null) {
            return;
        }

        Entity technology = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGY);
        Entity referencedTechnology = technologyOperationComponent
                .getBelongsToField(TechnologyOperationComponentFields.REFERENCE_TECHNOLOGY);

        Set<Long> technologies = Sets.newHashSet();
        technologies.add(technology.getId());

        boolean isCyclic = checkForCyclicReferences(technologies, referencedTechnology);

        if (isCyclic) {
            technologyOperationComponent.addError(
                    technologyOperationComponentDD.getField(TechnologyOperationComponentFields.REFERENCE_TECHNOLOGY),
                    "technologies.technologyReferenceTechnologyComponent.error.cyclicDependency");

            return;
        }

        EntityTreeNode root = referencedTechnology.getTreeField(TechnologyFields.OPERATION_COMPONENTS).getRoot();

        if (root == null) {
            technologyOperationComponent.addError(
                    technologyOperationComponentDD.getField(TechnologyOperationComponentFields.REFERENCE_TECHNOLOGY),
                    "technologies.technologyReferenceTechnologyComponent.error.operationComponentsEmpty");

            return;
        }

        Entity copiedRoot = copyReferencedTechnologyOperations(root,
                technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.TECHNOLOGY));

        for (Entry<String, Object> entry : copiedRoot.getFields().entrySet()) {
            if (!(entry.getKey().equals("id") || entry.getKey().equals(TechnologyOperationComponentFields.PARENT))) {
                technologyOperationComponent.setField(entry.getKey(), entry.getValue());
            }
        }

        technologyOperationComponent.setField(TechnologyOperationComponentFields.REFERENCE_TECHNOLOGY, null);
    }

    private Entity copyReferencedTechnologyOperations(final Entity node, final Entity technology) {
        Entity copy = node.copy();

        copy.setId(null);
        copy.setField(TechnologyOperationComponentFields.PARENT, null);
        copy.setField(TechnologyOperationComponentFields.TECHNOLOGY, technology);

        for (Entry<String, Object> entry : node.getFields().entrySet()) {
            Object value = entry.getValue();

            if (value instanceof EntityList) {
                EntityList entities = (EntityList) value;

                List<Entity> copies = Lists.newArrayList();

                for (Entity entity : entities) {
                    copies.add(copyReferencedTechnologyOperations(entity, technology));
                }

                copy.setField(entry.getKey(), copies);
            }
        }
        copy.setField("productionCountingQuantities", null);
        copy.setField("productionCountingOperationRuns", null);
        copy.setField("coverageRegisters", null);
        copy.setField("operationalTasks", null);
        copy.setField("operCompTimeCalculations", null);
        copy.setField("barcodeOperationComponents", null);
        return copy;
    }

    private boolean checkForCyclicReferences(final Set<Long> technologies, final Entity referencedTechnology) {
        return technologies.contains(referencedTechnology.getId());

    }

    public void onSave(final DataDefinition technologyOperationComponentDD, final Entity technologyOperationComponent) {
        clearField(technologyOperationComponent);

        if (technologyOperationComponent.getId() != null) {
            copyWorkstations(technologyOperationComponentDD, technologyOperationComponent);
        }
    }

    private void copyWorkstations(final DataDefinition technologyOperationComponentDD, final Entity technologyOperationComponent) {
        Entity oldToc = technologyOperationComponentDD.get(technologyOperationComponent.getId());
        Entity operation = technologyOperationComponent.getBelongsToField(TechnologyOperationComponentFields.OPERATION);
        if (operation != null
                && !operation.getId().equals(oldToc.getBelongsToField(TechnologyOperationComponentFields.OPERATION).getId())) {

            technologyOperationComponent.setField(TechnologyOperationComponentFields.WORKSTATIONS,
                    operation.getManyToManyField(TechnologyOperationComponentFields.WORKSTATIONS));
        }
    }

    private void clearField(final Entity technologyOperationComponent) {
        String assignedToOperation = technologyOperationComponent
                .getStringField(TechnologyOperationComponentFields.ASSIGNED_TO_OPERATION);
        if (AssignedToOperation.WORKSTATIONS_TYPE.getStringValue().equals(assignedToOperation)) {
            technologyOperationComponent.setField(TechnologyOperationComponentFields.WORKSTATIONS, null);
        }
    }

    public boolean onDelete(final DataDefinition dataDefinition, final Entity entity) {
        List<Entity> usageInProductStructureTree = dataDefinitionService
                .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_PRODUCT_STRUCTURE_TREE_NODE).find()
                .add(SearchRestrictions.belongsTo(ProductStructureTreeNodeFields.OPERATION, entity)).list().getEntities();
        if (!usageInProductStructureTree.isEmpty()) {
            entity.addGlobalError(
                    "technologies.technologyDetails.window.treeTab.technologyTree.error.cannotDeleteOperationUsedInProductStructureTree",
                    false,
                    usageInProductStructureTree.stream()
                            .map(e -> e.getBelongsToField(ProductStructureTreeNodeFields.MAIN_TECHNOLOGY)
                                    .getStringField(TechnologyFields.NUMBER))
                            .distinct().collect(Collectors.joining(", ")));
            return false;
        }
        return true;
    }
}
