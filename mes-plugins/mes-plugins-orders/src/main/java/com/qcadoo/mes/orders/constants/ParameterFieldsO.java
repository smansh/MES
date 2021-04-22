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
package com.qcadoo.mes.orders.constants;

public final class ParameterFieldsO {

    private ParameterFieldsO() {

    }

    public static final String REASON_NEEDED_WHEN_CORRECTING_DATE_FROM = "reasonNeededWhenCorrectingDateFrom";

    public static final String REASON_NEEDED_WHEN_CORRECTING_DATE_TO = "reasonNeededWhenCorrectingDateTo";

    public static final String REASON_NEEDED_WHEN_CHANGING_STATE_TO_DECLINED = "reasonNeededWhenChangingStateToDeclined";

    public static final String REASON_NEEDED_WHEN_CHANGING_STATE_TO_INTERRUPTED = "reasonNeededWhenChangingStateToInterrupted";

    public static final String REASON_NEEDED_WHEN_CHANGING_STATE_TO_ABANDONED = "reasonNeededWhenChangingStateToAbandoned";

    public static final String REASON_NEEDED_WHEN_DELAYED_EFFECTIVE_DATE_FROM = "reasonNeededWhenDelayedEffectiveDateFrom";

    public static final String REASON_NEEDED_WHEN_EARLIER_EFFECTIVE_DATE_FROM = "reasonNeededWhenEarlierEffectiveDateFrom";

    public static final String REASON_NEEDED_WHEN_DELAYED_EFFECTIVE_DATE_TO = "reasonNeededWhenDelayedEffectiveDateTo";

    public static final String REASON_NEEDED_WHEN_EARLIER_EFFECTIVE_DATE_TO = "reasonNeededWhenEarlierEffectiveDateTo";

    public static final String DELAYED_EFFECTIVE_DATE_FROM_TIME = "delayedEffectiveDateFromTime";

    public static final String EARLIER_EFFECTIVE_DATE_FROM_TIME = "earlierEffectiveDateFromTime";

    public static final String DELAYED_EFFECTIVE_DATE_TO_TIME = "delayedEffectiveDateToTime";

    public static final String EARLIER_EFFECTIVE_DATE_TO_TIME = "earlierEffectiveDateToTime";

    public static final String ALLOW_QUANTITY_CHANGE_IN_ACCEPTED_ORDER = "allowQuantityChangeInAcceptedOrder";

    public static final String ALLOW_TECHNOLOGY_TREE_CHANGE_INPENDING_ORDER = "allowTechnologyTreeChangeInPendingOrder";

    public static final String REASON_NEEDED_WHEN_CORRECTING_THE_REQUESTED_VOLUME = "reasonNeededWhenCorrectingTheRequestedVolume";

    public static final String ORDER_CATEGORY_COLORS = "orderCategoryColors";

    public static final String FILL_ORDER_DESCRIPTION_BASED_ON_TECHNOLOGY_DESCRIPTION = "fillOrderDescriptionBasedOnTechnologyDescription";

    public static final String SET_EFFECTIVE_DATE_FROM_ON_IN_PROGRESS = "setEffectiveDateFromOnInProgress";

    public static final String SET_EFFECTIVE_DATE_TO_ON_COMPLETED = "setEffectiveDateToOnCompleted";

    public static final String REALIZATION_FROM_STOCK = "realizationFromStock";

    public static final String ALWAYS_ORDER_ITEMS_WITH_PERSONALIZATION = "alwaysOrderItemsWithPersonalization";

    public static final String REALIZATION_LOCATIONS = "realizationLocations";

    public static final String ADVISE_START_DATE_OF_THE_ORDER = "adviseStartDateOfTheOrder";

    public static final String ORDER_START_DATE_BASED_ON = "orderStartDateBasedOn";

    public static final String GENERATE_PACKS_FOR_ORDERS = "generatePacksForOrders";

    public static final String OPTIMAL_PACK_SIZE = "optimalPackSize";

    public static final String REST_FEEDING_LAST_PACK = "restFeedingLastPack";

    public static final String INCLUDE_PACKS_GENERATING_PROCESSES_FOR_ORDER = "includePacksGeneratingProcessesForOrder";

}
