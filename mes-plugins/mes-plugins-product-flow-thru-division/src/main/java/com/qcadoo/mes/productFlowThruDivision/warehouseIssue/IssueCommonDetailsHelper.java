package com.qcadoo.mes.productFlowThruDivision.warehouseIssue;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qcadoo.mes.materialFlowResources.constants.MaterialFlowResourcesConstants;
import com.qcadoo.mes.materialFlowResources.constants.StorageLocationFields;
import com.qcadoo.mes.productFlowThruDivision.warehouseIssue.constans.ProductsToIssueFields;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.api.components.lookup.FilterValueHolder;
import com.qcadoo.view.constants.QcadooViewConstants;

@Service
public class IssueCommonDetailsHelper {

    public static final String STORAGE_LOCATION = "storageLocation";

    public static final String ADDITIONAL_CODE = "additionalCode";



    public static final String PRODUCT = "product";

    public static final String LOCATION = "location";

    @Autowired
    private DataDefinitionService dataDefinitionService;
    
    public void clearStorageLocationLookup(final ViewDefinitionState view) {
        LookupComponent storageLocationLookup = (LookupComponent) view.getComponentByReference(STORAGE_LOCATION);
        storageLocationLookup.setFieldValue(null);
        storageLocationLookup.requestComponentUpdateState();
    }

    public void clearAdditionalCodeLookup(final ViewDefinitionState view) {
        LookupComponent additionalCodeLookup = (LookupComponent) view.getComponentByReference(ADDITIONAL_CODE);
        additionalCodeLookup.setFieldValue(null);
        additionalCodeLookup.requestComponentUpdateState();
    }


    public void fillStorageLocation(ViewDefinitionState view) {
        FormComponent form = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        Entity productToIssue = form.getPersistedEntityWithIncludedFormValues();
        Entity product = productToIssue.getBelongsToField(ProductsToIssueFields.PRODUCT);
        Entity warehouse = productToIssue.getBelongsToField(ProductsToIssueFields.LOCATION);
        Entity storageLocation = productToIssue.getBelongsToField(ProductsToIssueFields.STORAGE_LOCATION);

        if(product != null && warehouse != null && storageLocation == null){
            Optional<Entity> option = findStorageLocationForProduct(product, warehouse);
            if(option.isPresent()){

                LookupComponent storageLocationLookup = (LookupComponent) view.getComponentByReference(
                        IssueCommonDetailsHelper.STORAGE_LOCATION);
                storageLocationLookup.setFieldValue(option.get().getId());
                storageLocationLookup.requestComponentUpdateState();
            }
        }

    }

    public void setFilterValue(final ViewDefinitionState view) {
        FormComponent form = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        Entity productToIssue = form.getPersistedEntityWithIncludedFormValues();
        Entity product = productToIssue.getBelongsToField(ProductsToIssueFields.PRODUCT);
        Entity warehouse = productToIssue.getBelongsToField(ProductsToIssueFields.LOCATION);

        LookupComponent storageLocationLookup = (LookupComponent) view.getComponentByReference(
                IssueCommonDetailsHelper.STORAGE_LOCATION);
        FilterValueHolder filter = storageLocationLookup.getFilterValue();
        LookupComponent additionalCodeLookup = (LookupComponent) view.getComponentByReference(ADDITIONAL_CODE);
        FilterValueHolder additionalCodeFilter = additionalCodeLookup.getFilterValue();
        if (product != null) {
            filter.put(PRODUCT, product.getId());
            additionalCodeFilter.put(PRODUCT, product.getId());
        } else if(filter.has(PRODUCT)){
            filter.remove(PRODUCT);
            additionalCodeFilter.remove(PRODUCT);
        }
        if (warehouse != null) {
            filter.remove(LOCATION);
            filter.put(LOCATION, warehouse.getId());
        }
        storageLocationLookup.setFilterValue(filter);

        additionalCodeLookup.setFilterValue(additionalCodeFilter);

    }


    public DataDefinition getStorageLocationDD() {
        return dataDefinitionService
                .get(MaterialFlowResourcesConstants.PLUGIN_IDENTIFIER, MaterialFlowResourcesConstants.MODEL_STORAGE_LOCATION);
    }

    public Optional<Entity> findStorageLocationForProduct(final Entity product, final Entity location){
        SearchCriteriaBuilder scb = getStorageLocationDD().find();
        scb.add(SearchRestrictions.belongsTo(StorageLocationFields.PRODUCT, product));
        scb.add(SearchRestrictions.belongsTo(StorageLocationFields.LOCATION, location));
        scb.add(SearchRestrictions.eq(StorageLocationFields.ACTIVE, true));
        return Optional.ofNullable(scb.setMaxResults(1).uniqueResult());
    }
}
