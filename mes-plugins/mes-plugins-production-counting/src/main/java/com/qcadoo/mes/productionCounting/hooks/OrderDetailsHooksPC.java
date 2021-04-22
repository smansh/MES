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
package com.qcadoo.mes.productionCounting.hooks;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.qcadoo.mes.basic.ParameterService;
import com.qcadoo.mes.productionCounting.ProductionCountingService;
import com.qcadoo.mes.productionCounting.constants.OrderFieldsPC;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;

@Service
public class OrderDetailsHooksPC {

    private static final List<String> L_ORDER_FIELD_NAMES = Lists.newArrayList(OrderFieldsPC.REGISTER_QUANTITY_IN_PRODUCT,
            OrderFieldsPC.REGISTER_QUANTITY_OUT_PRODUCT, OrderFieldsPC.REGISTER_PRODUCTION_TIME,
            OrderFieldsPC.REGISTER_PIECEWORK);

    @Autowired
    private ProductionCountingService productionCountingService;

    @Autowired
    private ParameterService parameterService;

    public void checkTypeOfProductionRecording(final ViewDefinitionState view) {
        FieldComponent typeOfProductionRecordingField = (FieldComponent) view
                .getComponentByReference(OrderFieldsPC.TYPE_OF_PRODUCTION_RECORDING);
        String typeOfProductionRecording = (String) typeOfProductionRecordingField.getFieldValue();

        if (StringUtils.isEmpty(typeOfProductionRecording)
                || productionCountingService.isTypeOfProductionRecordingBasic(typeOfProductionRecording)) {
            productionCountingService.setComponentsState(view, L_ORDER_FIELD_NAMES, false, true);
        } else if (productionCountingService.isTypeOfProductionRecordingCumulated(typeOfProductionRecording)) {
            setRegisterPieceworkEnabledAndValue(view, false, false);
        }
    }

    private void setRegisterPieceworkEnabledAndValue(final ViewDefinitionState view, final boolean isEnabled, final boolean value) {
        FieldComponent fieldComponent = (FieldComponent) view.getComponentByReference(OrderFieldsPC.REGISTER_PIECEWORK);
        fieldComponent.setEnabled(isEnabled);
        fieldComponent.setFieldValue(value);
    }

    public void changeDoneQuantityAndAmountOfProducedQuantityFieldState(final ViewDefinitionState view) {
        productionCountingService.changeDoneQuantityAndAmountOfProducedQuantityFieldState(view);
    }

}
