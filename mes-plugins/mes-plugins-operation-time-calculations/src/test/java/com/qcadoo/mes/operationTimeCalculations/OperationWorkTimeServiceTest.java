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
package com.qcadoo.mes.operationTimeCalculations;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.EntityList;
import com.qcadoo.model.api.NumberService;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OperationWorkTimeServiceTest {

    private OperationWorkTimeService operationWorkTimeService;

    @Mock
    private NumberService numberService;

    private MathContext mc = MathContext.DECIMAL64;

    @Mock
    private Entity operComp1, operComp2, operComp3;

    private BigDecimal neededNumberOfCycles1, neededNumberOfCycles2, neededNumberOfCycles3;

    private Integer workstations1, workstations2, workstations3;

    @Mock
    private OperationWorkTime operationWorkTime;

    @Mock
    private DataDefinition dataDefinition;

    private BigDecimal lu1, lu2, lu3;

    private BigDecimal mu1, mu2, mu3;

    private Map<Entity, BigDecimal> operationsRuns = Maps.newHashMap();

    private Map<Long, BigDecimal> operationRuns = Maps.newHashMap();

    private Map<Entity, Integer> workstations = Maps.newHashMap();

    private Map<Long, Integer> workstationsMap = Maps.newHashMap();

    @Before
    public void init() {
        operationWorkTimeService = new OperationWorkTimeServiceImpl();
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(operationWorkTimeService, "numberService", numberService);

        when(numberService.getMathContext()).thenReturn(mc);

        given(numberService.setScaleWithDefaultMathContext(Mockito.any(BigDecimal.class))).willAnswer(new Answer<BigDecimal>() {

            @Override
            public BigDecimal answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                BigDecimal number = (BigDecimal) args[0];
                return number.setScale(5, RoundingMode.HALF_EVEN);
            }
        });

        when(operComp1.getDataDefinition()).thenReturn(dataDefinition);
        when(operComp2.getDataDefinition()).thenReturn(dataDefinition);
        when(operComp3.getDataDefinition()).thenReturn(dataDefinition);

        Long id1 = 1L;
        Long id2 = 2L;
        Long id3 = 3L;

        when(operComp1.getId()).thenReturn(id1);
        when(operComp2.getId()).thenReturn(id2);
        when(operComp3.getId()).thenReturn(id3);

        when(dataDefinition.get(id1)).thenReturn(operComp1);
        when(dataDefinition.get(id2)).thenReturn(operComp2);
        when(dataDefinition.get(id3)).thenReturn(operComp3);

        when(dataDefinition.getName()).thenReturn("technologyOperationComponent");

        neededNumberOfCycles1 = BigDecimal.ONE;
        neededNumberOfCycles2 = new BigDecimal(2);
        neededNumberOfCycles3 = new BigDecimal(2.3);

        when(operComp1.getField("tj")).thenReturn(new Integer(600));
        when(operComp2.getField("tj")).thenReturn(new Integer(300));
        when(operComp3.getField("tj")).thenReturn(new Integer(150));

        when(operComp1.getField("tpz")).thenReturn(new Integer(600));
        when(operComp2.getField("tpz")).thenReturn(new Integer(900));
        when(operComp3.getField("tpz")).thenReturn(new Integer(1200));

        when(operComp1.getField("timeNextOperation")).thenReturn(new Integer(600));
        when(operComp2.getField("timeNextOperation")).thenReturn(new Integer(300));
        when(operComp3.getField("timeNextOperation")).thenReturn(new Integer(450));

        lu1 = new BigDecimal(2.2);
        lu2 = new BigDecimal(0.6);
        lu3 = new BigDecimal(0.8);

        when(operComp1.getDecimalField("laborUtilization")).thenReturn(lu1);
        when(operComp2.getDecimalField("laborUtilization")).thenReturn(lu2);
        when(operComp3.getDecimalField("laborUtilization")).thenReturn(lu3);

        mu1 = new BigDecimal(0.2);
        mu2 = new BigDecimal(1.6);
        mu3 = new BigDecimal(0.7);

        when(operComp1.getDecimalField("machineUtilization")).thenReturn(mu1);
        when(operComp2.getDecimalField("machineUtilization")).thenReturn(mu2);
        when(operComp3.getDecimalField("machineUtilization")).thenReturn(mu3);

        workstations1 = Integer.valueOf(1);
        workstations2 = Integer.valueOf(2);
        workstations3 = Integer.valueOf(1);
        workstations.put(operComp1, workstations1);
        workstations.put(operComp2, workstations2);
        workstations.put(operComp3, workstations3);

        workstationsMap.put(operComp1.getId(), workstations1);
        workstationsMap.put(operComp2.getId(), workstations2);
        workstationsMap.put(operComp3.getId(), workstations3);

        operationsRuns.put(operComp1, new BigDecimal(3));
        operationsRuns.put(operComp2, new BigDecimal(1.5));
        operationsRuns.put(operComp3, new BigDecimal(7));

        operationRuns.put(operComp1.getId(), new BigDecimal(3));
        operationRuns.put(operComp2.getId(), new BigDecimal(1.5));
        operationRuns.put(operComp3.getId(), new BigDecimal(7));
    }

    private static EntityList mockEntityList(final List<Entity> entities) {
        final EntityList entityList = mock(EntityList.class);
        given(entityList.iterator()).willAnswer(new Answer<Iterator<Entity>>() {

            @Override
            public Iterator<Entity> answer(final InvocationOnMock invocation) throws Throwable {
                return ImmutableList.copyOf(entities).iterator();
            }
        });
        given(entityList.isEmpty()).willReturn(entities.isEmpty());
        return entityList;
    }

    private void assertBigDecimalEquals(final BigDecimal expected, final BigDecimal actual) {
        if (expected.compareTo(actual) != 0) {
            Assert.fail("expected " + expected + " but actual value is " + actual);
        }
    }

    private void assertIntegerEquals(final Integer expected, final Integer actual) {
        if (expected.compareTo(actual) != 0) {
            Assert.fail("expected " + expected + " but actual value is " + actual);
        }
    }

    @Test
    public void shouldEstimateAbstractOperationWorkTimeWithoutTpzAndAdditionalTimeForOperComp1() throws Exception {
        // given
        BigDecimal abstractOperationWorkTime = BigDecimal.ZERO;
        when(numberService.setScaleWithDefaultMathContext(abstractOperationWorkTime)).thenReturn(abstractOperationWorkTime);

        abstractOperationWorkTime = operationWorkTimeService.estimateAbstractOperationWorkTime(operComp1, neededNumberOfCycles1,
                false, false, workstations1);
        // then
        Assert.assertNotNull(abstractOperationWorkTime);
        assertBigDecimalEquals(new BigDecimal(600), abstractOperationWorkTime);
    }

    @Test
    public void shouldEstimateAbstractOperationWorkTimeWithoutTpzAndAdditionalTimeForOperComp2() throws Exception {
        // given
        BigDecimal abstractOperationWorkTime = BigDecimal.ZERO;
        when(numberService.setScaleWithDefaultMathContext(abstractOperationWorkTime)).thenReturn(abstractOperationWorkTime);
        // when
        abstractOperationWorkTime = operationWorkTimeService.estimateAbstractOperationWorkTime(operComp2, neededNumberOfCycles2,
                false, false, workstations2);
        // then
        Assert.assertNotNull(abstractOperationWorkTime);
        assertBigDecimalEquals(new BigDecimal(600), abstractOperationWorkTime);
    }

    @Test
    public void shouldEstimateAbstractOperationWorkTimeWithoutTpzAndAdditionalTimeForOperComp3() throws Exception {
        // given
        // when
        BigDecimal abstractOperationWorkTime = operationWorkTimeService.estimateAbstractOperationWorkTime(operComp3,
                neededNumberOfCycles3, false, false, workstations3);
        // then
        Assert.assertNotNull(abstractOperationWorkTime);
        assertBigDecimalEquals(new BigDecimal(345), abstractOperationWorkTime);
    }

    @Test
    public void shouldEstimateAbstractOperationWorkTimeWithTpzAndAdditionalTimeForOperComp1() throws Exception {
        // when
        BigDecimal abstractOperationWorkTime = operationWorkTimeService.estimateAbstractOperationWorkTime(operComp1,
                neededNumberOfCycles1, true, true, workstations1);
        // then
        Assert.assertNotNull(abstractOperationWorkTime);
        assertBigDecimalEquals(new BigDecimal(1800), abstractOperationWorkTime);
    }

    @Test
    public void shouldEstimateAbstractOperationWorkTimeWithTpzAndAdditionalTimeForOperComp2() throws Exception {
        // when
        BigDecimal abstractOperationWorkTime = operationWorkTimeService.estimateAbstractOperationWorkTime(operComp2,
                neededNumberOfCycles2, true, true, workstations2);
        // then
        Assert.assertNotNull(abstractOperationWorkTime);
        assertBigDecimalEquals(new BigDecimal(1800), abstractOperationWorkTime);
    }

    @Test
    public void shouldEstimateAbstractOperationWorkTimeWithTpzAndAdditionalTimeForOperComp3() throws Exception {
        // when
        BigDecimal abstractOperationWorkTime = operationWorkTimeService.estimateAbstractOperationWorkTime(operComp3,
                neededNumberOfCycles3, true, true, workstations3);
        // then
        Assert.assertNotNull(abstractOperationWorkTime);
        assertBigDecimalEquals(new BigDecimal(1995), abstractOperationWorkTime);
    }

    @Test
    public void shouldEstimateAbstractOperationWorkTimeWithTpzAndWithoutAdditionalTimeForOperComp1() throws Exception {
        // when
        BigDecimal abstractOperationWorkTime = operationWorkTimeService.estimateAbstractOperationWorkTime(operComp1,
                neededNumberOfCycles1, true, false, workstations1);
        // then
        Assert.assertNotNull(abstractOperationWorkTime);
        assertBigDecimalEquals(new BigDecimal(1200), abstractOperationWorkTime);
    }

    @Test
    public void shouldEstimateAbstractOperationWorkTimeWithTpzAndWithoutAdditionalTimeForOperComp2() throws Exception {
        // when
        BigDecimal abstractOperationWorkTime = operationWorkTimeService.estimateAbstractOperationWorkTime(operComp2,
                neededNumberOfCycles2, true, false, workstations2);
        // then
        Assert.assertNotNull(abstractOperationWorkTime);
        assertBigDecimalEquals(new BigDecimal(1500), abstractOperationWorkTime);
    }

    @Test
    public void shouldEstimateAbstractOperationWorkTimeWithTpzAndWithoutAdditionalTimeForOperComp3() throws Exception {
        // when
        BigDecimal abstractOperationWorkTime = operationWorkTimeService.estimateAbstractOperationWorkTime(operComp3,
                neededNumberOfCycles3, true, false, workstations3);
        // then
        Assert.assertNotNull(abstractOperationWorkTime);
        assertBigDecimalEquals(new BigDecimal(1545), abstractOperationWorkTime);
    }

    @Test
    public void shouldReturnEstimatesOperationWorkTimeForOper1() throws Exception {
        // when

        operationWorkTime = operationWorkTimeService.estimateTechOperationWorkTime(operComp1, neededNumberOfCycles1, true, true,
                workstations1, false);
        // then
        assertIntegerEquals(operationWorkTime.getLaborWorkTime(), new Integer(3960));
        assertIntegerEquals(operationWorkTime.getMachineWorkTime(), new Integer(360));
    }

    @Test
    public void shouldReturnEstimatesOperationWorkTimeForOper2() throws Exception {
        // when
        operationWorkTime = operationWorkTimeService.estimateTechOperationWorkTime(operComp2, neededNumberOfCycles2, true, true,
                workstations2, false);
        // then
        assertIntegerEquals(operationWorkTime.getLaborWorkTime(), new Integer(1080));
        assertIntegerEquals(operationWorkTime.getMachineWorkTime(), new Integer(2880));
    }

    @Test
    public void shouldReturnEstimatesOperationWorkTimeForOper3() throws Exception {
        // when
        operationWorkTime = operationWorkTimeService.estimateTechOperationWorkTime(operComp3, neededNumberOfCycles3, true, true,
                workstations3, false);
        // then
        assertIntegerEquals(operationWorkTime.getLaborWorkTime(), new Integer(1596));
        assertIntegerEquals(operationWorkTime.getMachineWorkTime(), new BigDecimal(1396.5).intValue());
    }

    @Test
    public void shouldReturnEstimatesOperationWorkTimeWithoutTpzForOper1() throws Exception {
        // when

        operationWorkTime = operationWorkTimeService.estimateTechOperationWorkTime(operComp1, neededNumberOfCycles1, false, true,
                workstations1, false);
        // then
        assertIntegerEquals(operationWorkTime.getLaborWorkTime(), new Integer(2640));
        assertIntegerEquals(operationWorkTime.getMachineWorkTime(), new Integer(240));
    }

    @Test
    public void shouldReturnEstimatesOperationWorkTimeWithoutTpzForOper2() throws Exception {
        // when
        operationWorkTime = operationWorkTimeService.estimateTechOperationWorkTime(operComp2, neededNumberOfCycles2, false, true,
                workstations2, false);
        // then
        assertIntegerEquals(operationWorkTime.getLaborWorkTime(), new Integer(540));
        assertIntegerEquals(operationWorkTime.getMachineWorkTime(), new Integer(1440));
    }

    @Test
    public void shouldReturnEstimatesOperationWorkTimeWithoutTpzForOper3() throws Exception {
        // when
        operationWorkTime = operationWorkTimeService.estimateTechOperationWorkTime(operComp3, neededNumberOfCycles3, false, true,
                workstations3, false);
        // then
        assertIntegerEquals(operationWorkTime.getLaborWorkTime(), new Integer(636));
        assertIntegerEquals(operationWorkTime.getMachineWorkTime(), new BigDecimal(556.5).intValue());
    }

    @Test
    public void shouldReturnEstimatesOperationWorkTimeWithoutAddTimeForOper1() throws Exception {
        // when

        operationWorkTime = operationWorkTimeService.estimateTechOperationWorkTime(operComp1, neededNumberOfCycles1, true, false,
                workstations1, false);
        // then
        assertIntegerEquals(operationWorkTime.getLaborWorkTime(), new Integer(2640));
        assertIntegerEquals(operationWorkTime.getMachineWorkTime(), new Integer(240));
    }

    @Test
    public void shouldReturnEstimatesOperationWorkTimeWithoutAddTimeForOper2() throws Exception {
        // when
        operationWorkTime = operationWorkTimeService.estimateTechOperationWorkTime(operComp2, neededNumberOfCycles2, true, false,
                workstations2, false);
        // then
        assertIntegerEquals(operationWorkTime.getLaborWorkTime(), new Integer(900));
        assertIntegerEquals(operationWorkTime.getMachineWorkTime(), new Integer(2400));
    }

    @Test
    public void shouldReturnEstimatesOperationWorkTimeWithoutAddTimeForOper3() throws Exception {
        // when
        operationWorkTime = operationWorkTimeService.estimateTechOperationWorkTime(operComp3, neededNumberOfCycles3, true, false,
                workstations3, false);
        // then
        assertIntegerEquals(operationWorkTime.getLaborWorkTime(), new Integer(1236));
        assertIntegerEquals(operationWorkTime.getMachineWorkTime(), new BigDecimal(1081).intValue());
    }

    @Test
    public void shouldReturnEstimatesOperationWorkTimeWithoutTpzAndAddTimeForOper1() throws Exception {
        // when

        operationWorkTime = operationWorkTimeService.estimateTechOperationWorkTime(operComp1, neededNumberOfCycles1, false, false,
                workstations1, false);
        // then
        assertIntegerEquals(operationWorkTime.getLaborWorkTime(), new Integer(1320));
        assertIntegerEquals(operationWorkTime.getMachineWorkTime(), new Integer(120));
    }

    @Test
    public void shouldReturnEstimatesOperationWorkTimeWithoutTpzAndAddTimeForOper2() throws Exception {
        // when
        operationWorkTime = operationWorkTimeService.estimateTechOperationWorkTime(operComp2, neededNumberOfCycles2, false, false,
                workstations2, false);
        // then
        assertIntegerEquals(operationWorkTime.getLaborWorkTime(), new Integer(360));
        assertIntegerEquals(operationWorkTime.getMachineWorkTime(), new Integer(960));
    }

    @Test
    public void shouldReturnEstimatesOperationWorkTimeWithoutTpzAndAddTimeForOper3() throws Exception {
        // when
        operationWorkTime = operationWorkTimeService.estimateTechOperationWorkTime(operComp3, neededNumberOfCycles3, false, false,
                workstations3, false);
        // then
        assertIntegerEquals(operationWorkTime.getLaborWorkTime(), new Integer(276));
        assertIntegerEquals(operationWorkTime.getMachineWorkTime(), new BigDecimal(241.5).intValue());
    }

    @Test
    public void shouldReturnEstimateOperationsMapIncludedTpzAndAdditionalTime() throws Exception {
        // given
        EntityList operationComponents = mockEntityList(Arrays.asList(operComp1, operComp2, operComp3));
        // when
        Map<Entity, OperationWorkTime> operationDurations = operationWorkTimeService.estimateOperationsWorkTime(
                operationComponents, operationsRuns, true, true, workstations, false);
        // then
        assertEquals(3, operationDurations.values().size());
        assertEquals(new Integer(6600), operationDurations.get(operComp1).getLaborWorkTime());
        assertEquals(new Integer(600), operationDurations.get(operComp1).getMachineWorkTime());

        assertEquals(new Integer(990), operationDurations.get(operComp2).getLaborWorkTime());
        assertEquals(new Integer(2640), operationDurations.get(operComp2).getMachineWorkTime());

        assertEquals(new Integer(2160), operationDurations.get(operComp3).getLaborWorkTime());
        assertEquals(new Integer(1890), operationDurations.get(operComp3).getMachineWorkTime());
    }

    @Test
    public void shouldReturnEstimateOperationsMapIncludedTpz() throws Exception {
        // given
        EntityList operationComponents = mockEntityList(Arrays.asList(operComp1, operComp2, operComp3));
        // when
        Map<Entity, OperationWorkTime> operationDurations = operationWorkTimeService.estimateOperationsWorkTime(
                operationComponents, operationsRuns, true, false, workstations, false);
        // then
        assertEquals(3, operationDurations.values().size());
        assertEquals(new Integer(5280), operationDurations.get(operComp1).getLaborWorkTime());
        assertEquals(new Integer(480), operationDurations.get(operComp1).getMachineWorkTime());

        assertEquals(new Integer(810), operationDurations.get(operComp2).getLaborWorkTime());
        assertEquals(new Integer(2160), operationDurations.get(operComp2).getMachineWorkTime());

        assertEquals(new Integer(1800), operationDurations.get(operComp3).getLaborWorkTime());
        assertEquals(new Integer(1575), operationDurations.get(operComp3).getMachineWorkTime());
    }

    @Test
    public void shouldReturnEstimateOperationsMapIncludedAdditionalTime() throws Exception {
        // given
        EntityList operationComponents = mockEntityList(Arrays.asList(operComp1, operComp2, operComp3));
        // when
        Map<Entity, OperationWorkTime> operationDurations = operationWorkTimeService.estimateOperationsWorkTime(
                operationComponents, operationsRuns, false, true, workstations, false);
        // then
        assertEquals(3, operationDurations.values().size());
        assertEquals(new Integer(5280), operationDurations.get(operComp1).getLaborWorkTime());
        assertEquals(new Integer(480), operationDurations.get(operComp1).getMachineWorkTime());

        assertEquals(new Integer(450), operationDurations.get(operComp2).getLaborWorkTime());
        assertEquals(new Integer(1200), operationDurations.get(operComp2).getMachineWorkTime());

        assertEquals(new Integer(1200), operationDurations.get(operComp3).getLaborWorkTime());
        assertEquals(new Integer(1050), operationDurations.get(operComp3).getMachineWorkTime());
    }

    @Test
    public void shouldEstimateTotalOrderTimeWithTpzAndAdditionalTime() throws Exception {
        // given

        EntityList operationComponents = mockEntityList(Arrays.asList(operComp1, operComp2, operComp3));
        // when
        operationWorkTime = operationWorkTimeService.estimateTotalWorkTime(operationComponents, operationRuns, true, true,
                workstationsMap, false);
        // then
        assertEquals(operationWorkTime.getLaborWorkTime(), new Integer(9750));
        assertEquals(operationWorkTime.getMachineWorkTime(), new Integer(5130));
    }

    @Test
    public void shouldEstimateTotalOrderTimeWithTpz() throws Exception {
        // given

        EntityList operationComponents = mockEntityList(Arrays.asList(operComp1, operComp2, operComp3));
        // // when
        operationWorkTime = operationWorkTimeService.estimateTotalWorkTime(operationComponents, operationRuns, true, false,
                workstationsMap, false);
        // then
        assertEquals(operationWorkTime.getLaborWorkTime(), new Integer(7890));
        assertEquals(operationWorkTime.getMachineWorkTime(), new Integer(4215));
    }

    @Test
    public void shouldEstimateTotalOrderTimeWithAdditionalTime() throws Exception {
        // // given

        EntityList operationComponents = mockEntityList(Arrays.asList(operComp1, operComp2, operComp3));
        // when
        operationWorkTime = operationWorkTimeService.estimateTotalWorkTime(operationComponents, operationRuns, false, true,
                workstationsMap, false);
        // // then
        assertEquals(operationWorkTime.getLaborWorkTime(), new Integer(6930));
        assertEquals(operationWorkTime.getMachineWorkTime(), new Integer(2730));
    }

}
