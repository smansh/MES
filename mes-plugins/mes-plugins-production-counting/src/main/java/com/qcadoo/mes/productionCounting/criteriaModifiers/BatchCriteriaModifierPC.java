package com.qcadoo.mes.productionCounting.criteriaModifiers;

import com.qcadoo.mes.advancedGenealogy.constants.AdvancedGenealogyConstants;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.view.api.components.lookup.FilterValueHolder;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BatchCriteriaModifierPC {

    public static final String ORDER_ID = "orderId";

    @Autowired
    private DataDefinitionService dataDefinitionService;

    public void filterByOrder(final SearchCriteriaBuilder scb, final FilterValueHolder filterValue) {

        if (filterValue.has(ORDER_ID)) {
            Long orderId = filterValue.getLong(ORDER_ID);
            List<Entity> entities = dataDefinitionService
                    .get(AdvancedGenealogyConstants.PLUGIN_IDENTIFIER, AdvancedGenealogyConstants.MODEL_TRACKING_RECORD)
                    .find("select producedBatch.id as batchId from #advancedGenealogy_trackingRecord where order.id = :orderId ")
                    .setLong("orderId", orderId).list().getEntities();
            if (!entities.isEmpty()) {
                scb.add(SearchRestrictions.in("id",
                        entities.stream().map(e -> e.getLongField("batchId")).collect(Collectors.toList())));
            } else {
                scb.add(SearchRestrictions.idEq(-1));
            }
        }
    }
}
