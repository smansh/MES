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
package com.qcadoo.mes.basic.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.basic.validators.ProductValidators;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.FieldDefinition;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchCriterion;
import com.qcadoo.model.api.search.SearchProjection;
import com.qcadoo.model.api.search.SearchRestrictions;

public class ProductValidatorsTest {

    private ProductValidators productValidators;

    @Mock
    private DataDefinition dataDefinition;

    @Mock
    private FieldDefinition fieldDefinition;

    @Mock
    private Entity entity;

    @Mock
    private SearchCriteriaBuilder scb;

    @Before
    public final void init() {
        MockitoAnnotations.initMocks(this);

        given(scb.setProjection(any(SearchProjection.class))).willReturn(scb);
        given(scb.add(any(SearchCriterion.class))).willReturn(scb);
        given(scb.setMaxResults(anyInt())).willReturn(scb);

        given(dataDefinition.find()).willReturn(scb);

        productValidators = new ProductValidators();
    }

    public final void shouldCheckEanUniquenessJustReturnTrueIfValueWasNotChanged() {
        // given
        String oldVal = "123456"; // for example when updating product without modifying ean
        String newVal = oldVal;

        // when
        boolean isValid = productValidators.checkEanUniqueness(dataDefinition, fieldDefinition, entity, oldVal, newVal);

        // then
        assertTrue(isValid);
        verify(entity, never()).addError(any(FieldDefinition.class), anyString());
    }

    @Test
    public final void shouldCheckEanUniquenessJustReturnTrueIfNewEanIsEmpty() {
        // given
        String oldVal = "123456";
        String newVal = "";

        // when
        boolean isValid = productValidators.checkEanUniqueness(dataDefinition, fieldDefinition, entity, oldVal, newVal);

        // then
        assertTrue(isValid);
        verify(entity, never()).addError(any(FieldDefinition.class), anyString());
    }

    @Test
    public final void shouldCheckEanUniquenessJustReturnTrueIfNewEanIsNull() {
        // given
        String oldVal = "123456";
        String newVal = null;

        // when
        boolean isValid = productValidators.checkEanUniqueness(dataDefinition, fieldDefinition, entity, oldVal, newVal);

        // then
        assertTrue(isValid);
        verify(entity, never()).addError(any(FieldDefinition.class), anyString());
    }

    @Test
    public final void shouldCheckEanUniquenessReturnTrueIfThereIsNoExistingProductsWithGivenId() {
        // given
        String oldVal = "123456";
        String newVal = "654321";
        ArgumentCaptor<SearchCriterion> criterionCaptor = ArgumentCaptor.forClass(SearchCriterion.class);

        stubSearchCriteriaWith(null);

        // when
        boolean isValid = productValidators.checkEanUniqueness(dataDefinition, fieldDefinition, entity, oldVal, newVal);

        // then
        assertTrue(isValid);
        verify(entity, never()).addError(any(FieldDefinition.class), anyString());
        verify(scb).add(criterionCaptor.capture());
        assertEquals(SearchRestrictions.eq(ProductFields.EAN, newVal), criterionCaptor.getValue());
    }

    @Test
    public final void shouldCheckEanUniquenessReturnFalsAndMarkFieldAsInvalidIfProductsWithGivenIdExists() {
        // given
        String oldVal = "123456";
        String newVal = "654321";
        ArgumentCaptor<SearchCriterion> criterionCaptor = ArgumentCaptor.forClass(SearchCriterion.class);

        Entity resultEntity = mock(Entity.class);
        stubSearchCriteriaWith(resultEntity);

        // when
        boolean isValid = productValidators.checkEanUniqueness(dataDefinition, fieldDefinition, entity, oldVal, newVal);

        // then
        assertFalse(isValid);
        verify(entity).addError(fieldDefinition, "qcadooView.validate.field.error.duplicated");
        verify(scb).add(criterionCaptor.capture());
        assertEquals(SearchRestrictions.eq(ProductFields.EAN, newVal), criterionCaptor.getValue());
    }

    private void stubSearchCriteriaWith(final Entity entity) {
        given(scb.uniqueResult()).willReturn(entity);
    }

}
