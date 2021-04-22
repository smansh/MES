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
package com.qcadoo.mes.timeNormsForOperations.constants;

import com.google.common.collect.Sets;

import java.util.Set;

public final class TimeNormsConstants {

    private TimeNormsConstants() {

    }

    public static final String PLUGIN_IDENTIFIER = "timeNormsForOperations";

    public static final String PLUGIN_PRODUCTION_SCHEDULING_IDENTIFIER = "productionScheduling";

    public static final String MODEL_ORDER_TIME_CALCULATION = "orderTimeCalculation";

    public static final String MODEL_OPER_COMP_TIME_CALCULATION = "operCompTimeCalculation";

    // MODEL
    public static final String MODEL_TECH_OPER_COMP_TIME_CALCULATION = "techOperCompTimeCalculation";

    public static final Set<String> FIELDS_OPERATION = Sets.newHashSet("tpz", "tj", "productionInOneCycle",
            "nextOperationAfterProducedType", "nextOperationAfterProducedQuantity", "nextOperationAfterProducedQuantityUNIT",
            "timeNextOperation", "machineUtilization", "laborUtilization", "productionInOneCycleUNIT",
            "areProductQuantitiesDivisible", "isTjDivisible");

    public static final Set<String> FIELDS_TECHNOLOGY = Sets.newHashSet("tpz", "tj", "productionInOneCycle",
            "nextOperationAfterProducedType", "nextOperationAfterProducedQuantity", "nextOperationAfterProducedQuantityUNIT",
            "timeNextOperation", "machineUtilization", "laborUtilization", "productionInOneCycleUNIT",
            "areProductQuantitiesDivisible", "isTjDivisible");

}
