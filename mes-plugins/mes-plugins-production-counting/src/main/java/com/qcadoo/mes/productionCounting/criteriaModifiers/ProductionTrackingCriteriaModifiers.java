package com.qcadoo.mes.productionCounting.criteriaModifiers;

import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.security.api.SecurityService;
import com.qcadoo.security.constants.QcadooSecurityConstants;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductionTrackingCriteriaModifiers {

    public static final String L_SHOW_ONLY_MY_REGISTRATION_RECORDS = "showOnlyMyRegistrationRecords";

    @Autowired
    private SecurityService securityService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    public void onlyMyRecords(final SearchCriteriaBuilder scb) {
        Long currentUserId = securityService.getCurrentUserId();
        if (Objects.nonNull(currentUserId)) {
            boolean showOnlyMyRegistrationRecords = userDataDefinition().get(currentUserId).getBooleanField(L_SHOW_ONLY_MY_REGISTRATION_RECORDS);
            if(showOnlyMyRegistrationRecords) {
                scb.add(SearchRestrictions.eq("createUser", securityService.getCurrentUserName()));
            }
        }
    }

    private DataDefinition userDataDefinition() {
        return dataDefinitionService.get(QcadooSecurityConstants.PLUGIN_IDENTIFIER, QcadooSecurityConstants.MODEL_USER);
    }
}
