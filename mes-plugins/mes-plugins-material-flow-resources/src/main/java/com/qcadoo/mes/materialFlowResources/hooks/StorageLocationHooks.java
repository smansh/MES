package com.qcadoo.mes.materialFlowResources.hooks;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.qcadoo.mes.materialFlowResources.constants.MaterialFlowResourcesConstants;
import com.qcadoo.mes.materialFlowResources.constants.StorageLocationFields;
import com.qcadoo.mes.materialFlowResources.constants.StorageLocationHistoryFields;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;

@Service
public class StorageLocationHooks {

    @Autowired
    private DataDefinitionService dataDefinitionService;

    public void onSave(final DataDefinition storageLocationDD, final Entity storageLocation) {
        Entity oldProduct;
        Entity newProduct = storageLocation.getBelongsToField(StorageLocationFields.PRODUCT);
        if (storageLocation.getId() == null) {
            oldProduct = null;
        } else {
            Entity storageLocationFromDb = storageLocationDD.get(storageLocation.getId());
            oldProduct = storageLocationFromDb.getBelongsToField(StorageLocationFields.PRODUCT);
        }
        if (oldProduct == null) {
            if (newProduct != null) {
                auditChanges(storageLocation, null, newProduct);
            }
        } else {
            if (newProduct == null || !oldProduct.getId().equals(newProduct.getId())) {
                auditChanges(storageLocation, oldProduct, newProduct);
            }
        }
    }

    private void auditChanges(final Entity storageLocation, final Entity oldProduct, final Entity newProduct) {

        DataDefinition historyDD = dataDefinitionService.get(MaterialFlowResourcesConstants.PLUGIN_IDENTIFIER,
                MaterialFlowResourcesConstants.MODEL_STORAGE_LOCATION_HISTORY);
        Entity history = historyDD.create();

        history.setField(StorageLocationHistoryFields.STORAGE_LOCATION, storageLocation);
        history.setField(StorageLocationHistoryFields.PRODUCT_FROM, oldProduct);
        history.setField(StorageLocationHistoryFields.PRODUCT_TO, newProduct);
        Entity saved = historyDD.save(history);

        List<Entity> existingHistory = storageLocation.getHasManyField(StorageLocationFields.HISTORY);

        if (existingHistory == null) {
            existingHistory = Lists.newArrayList();
        } else {
            existingHistory = Lists.newArrayList(existingHistory);
        }
        existingHistory.add(saved);
        storageLocation.setField(StorageLocationFields.HISTORY, existingHistory);

        DataDefinition productHistoryDD = dataDefinitionService.get(MaterialFlowResourcesConstants.PLUGIN_IDENTIFIER,
                MaterialFlowResourcesConstants.MODEL_PRODUCT_STORAGE_LOCATION_HISTORY);
        if (oldProduct == null || (newProduct != null && !oldProduct.getId().equals(newProduct.getId()))) {
            Entity productToHistory = productHistoryDD.create();

            List<Entity> existingProductHistory = storageLocation.getHasManyField(StorageLocationFields.PRODUCT_TO_HISTORY);

            if (existingProductHistory == null) {
                existingProductHistory = Lists.newArrayList();
            } else {
                existingProductHistory = Lists.newArrayList(existingProductHistory);
            }
            productToHistory.setField(StorageLocationHistoryFields.STORAGE_LOCATION_TO, storageLocation);
            productToHistory.setField(StorageLocationHistoryFields.PRODUCT, newProduct);
            productToHistory.setField(StorageLocationHistoryFields.LOCATION,
                    storageLocation.getBelongsToField(StorageLocationFields.LOCATION));
            existingProductHistory.add(productHistoryDD.save(productToHistory));
            storageLocation.setField(StorageLocationFields.PRODUCT_TO_HISTORY, existingProductHistory);
        }
        if (newProduct == null || (oldProduct != null && !oldProduct.getId().equals(newProduct.getId()))) {
            Entity productFromHistory = productHistoryDD.create();
            List<Entity> existingProductHistory = storageLocation.getHasManyField(StorageLocationFields.PRODUCT_FROM_HISTORY);

            if (existingProductHistory == null) {
                existingProductHistory = Lists.newArrayList();
            } else {
                existingProductHistory = Lists.newArrayList(existingProductHistory);
            }
            productFromHistory.setField(StorageLocationHistoryFields.STORAGE_LOCATION_FROM, storageLocation);
            productFromHistory.setField(StorageLocationHistoryFields.PRODUCT, oldProduct);
            productFromHistory.setField(StorageLocationHistoryFields.LOCATION,
                    storageLocation.getBelongsToField(StorageLocationFields.LOCATION));

            existingProductHistory.add(productHistoryDD.save(productFromHistory));
            storageLocation.setField(StorageLocationFields.PRODUCT_FROM_HISTORY, existingProductHistory);
        }

    }

}
