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
package com.qcadoo.mes.timeNormsForOperations.hooks;

import static com.google.common.base.Preconditions.checkArgument;
import static com.qcadoo.mes.technologies.constants.TechnologyOperationComponentFields.OPERATION;
import static com.qcadoo.mes.timeNormsForOperations.constants.TimeNormsConstants.FIELDS_OPERATION;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qcadoo.mes.basic.util.UnitService;
import com.qcadoo.mes.timeNormsForOperations.constants.TechnologyOperationComponentFieldsTNFO;
import com.qcadoo.mes.timeNormsForOperations.constants.TimeNormsConstants;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;

@Service
public class TechnologyOperationComponentHooksTNFO {

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private UnitService unitService;

    public void onCreate(final DataDefinition technologyOperationComponentDD, final Entity technologyOperationComponent) {
        setDefaultUnitOnCreate(technologyOperationComponent);
    }

    private void setDefaultUnitOnCreate(final Entity technologyOperationComponent) {
        if (StringUtils.isEmpty(technologyOperationComponent
                .getStringField(TechnologyOperationComponentFieldsTNFO.PRODUCTION_IN_ONE_CYCLE_UNIT))) {
            String defaultUnit = unitService.getDefaultUnitFromSystemParameters();
            technologyOperationComponent.setField(TechnologyOperationComponentFieldsTNFO.PRODUCTION_IN_ONE_CYCLE_UNIT,
                    defaultUnit);
        }
    }

    public void createTechOperCompTimeCalculations(final DataDefinition dd, final Entity technologyOperationComponent) {
        DataDefinition techOperCompTimeCalculationDD = dataDefinitionService.get(TimeNormsConstants.PLUGIN_IDENTIFIER,
                TimeNormsConstants.MODEL_TECH_OPER_COMP_TIME_CALCULATION);
        Entity techOperCompTimeCalculation = techOperCompTimeCalculationDD.create();
        techOperCompTimeCalculation = techOperCompTimeCalculationDD.save(techOperCompTimeCalculation);
        technologyOperationComponent.setField(TechnologyOperationComponentFieldsTNFO.TECH_OPER_COMP_TIME_CALCULATION,
                techOperCompTimeCalculation);
    }

    public void copyTimeNormsToTechnologyOperationComponent(final DataDefinition dd, final Entity technologyOperationComponent) {
        if (technologyOperationComponent.getBelongsToField(OPERATION) == null) {
            return;
        }
        copyTimeValuesFromGivenOperation(technologyOperationComponent, technologyOperationComponent.getBelongsToField(OPERATION));
    }

    private void copyTimeValuesFromGivenOperation(final Entity target, final Entity source) {
        checkArgument(target != null, "given target is null");
        checkArgument(source != null, "given source is null");

        if (!shouldPropagateValuesFromLowerInstance(target)) {
            return;
        }

        for (String fieldName : FIELDS_OPERATION) {
            if (source.getField(fieldName) == null) {
                continue;
            }
            target.setField(fieldName, source.getField(fieldName));
        }
    }

    private boolean shouldPropagateValuesFromLowerInstance(final Entity technologyOperationComponent) {
        for (String fieldName : FIELDS_OPERATION) {
            if (technologyOperationComponent.getField(fieldName) != null) {
                return false;
            }
        }
        return true;
    }

    public boolean onDelete(final DataDefinition technologyOperationComponentDD, final Entity technologyOperationComponent) {
        boolean isDeleted = true;

        Entity techOperCompTimeCalculation = technologyOperationComponent
                .getBelongsToField(TechnologyOperationComponentFieldsTNFO.TECH_OPER_COMP_TIME_CALCULATION);

        if (techOperCompTimeCalculation != null) {
            isDeleted = techOperCompTimeCalculation.getDataDefinition().delete(techOperCompTimeCalculation.getId())
                    .isSuccessfull();
        }

        return isDeleted;
    }

}
