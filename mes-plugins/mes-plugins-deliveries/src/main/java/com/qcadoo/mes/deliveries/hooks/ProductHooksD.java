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
package com.qcadoo.mes.deliveries.hooks;

import com.qcadoo.mes.basic.constants.BasicConstants;
import com.qcadoo.mes.basic.constants.ProductFamilyElementType;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.deliveries.CompanyProductService;
import com.qcadoo.mes.deliveries.constants.CompanyProductFields;
import com.qcadoo.mes.deliveries.constants.CompanyProductsFamilyFields;
import com.qcadoo.mes.deliveries.constants.DeliveriesConstants;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.SearchRestrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ProductHooksD {

    @Autowired
    private CompanyProductService companyProductService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    public void onSave(final DataDefinition dataDefinition, final Entity product) {
        if (Objects.nonNull(product.getId())) {
            Entity productFromDB = dataDefinition.get(product.getId());

            ProductFamilyElementType oldProductType = ProductFamilyElementType.from(productFromDB);
            ProductFamilyElementType newProductType = ProductFamilyElementType.from(product);

            if (oldProductType.compareTo(newProductType) != 0) {
                if (ProductFamilyElementType.PARTICULAR_PRODUCT.compareTo(newProductType) == 0) {
                    moveCompanyProductFamiliesToCompanyProducts(product);
                } else if (ProductFamilyElementType.PRODUCTS_FAMILY.compareTo(newProductType) == 0) {
                    moveCompanyProductsToCompanyProductFamilies(product);
                }
            }
        }
    }

    private void moveCompanyProductsToCompanyProductFamilies(final Entity particularProduct) {
        DataDefinition companyProductDD = getCompanyProductDD();
        DataDefinition companyProductsFamilyDD = getCompanyProductsFamily();

        Entity productFromDB = getProduct(particularProduct);

        List<Entity> productCompanies = companyProductDD.find()
                .add(SearchRestrictions.belongsTo(CompanyProductFields.PRODUCT, productFromDB)).list().getEntities();

        for (Entity productCompany : productCompanies) {
            Entity companyProductsFamily = companyProductsFamilyDD.create();

            companyProductsFamily.setField(CompanyProductsFamilyFields.COMPANY,
                    productCompany.getBelongsToField(CompanyProductFields.COMPANY));
            companyProductsFamily.setField(CompanyProductsFamilyFields.PRODUCT, particularProduct);
            companyProductsFamily.setField(CompanyProductsFamilyFields.IS_DEFAULT,
                    productCompany.getBooleanField(CompanyProductFields.IS_DEFAULT));

            companyProductsFamilyDD.save(companyProductsFamily);

            companyProductDD.delete(productCompany.getId());
        }
    }

    private void moveCompanyProductFamiliesToCompanyProducts(final Entity productFamily) {
        DataDefinition companyProductsFamilyDD = getCompanyProductsFamily();
        DataDefinition companyProductDD = getCompanyProductDD();

        Entity productFromDB = getProduct(productFamily);

        List<Entity> productFamilyCompanies = companyProductsFamilyDD.find()
                .add(SearchRestrictions.belongsTo(CompanyProductsFamilyFields.PRODUCT, productFromDB)).list().getEntities();

        for (Entity productFamilyCompany : productFamilyCompanies) {
            Entity companyProduct = companyProductDD.create();

            companyProduct.setField(CompanyProductFields.COMPANY,
                    productFamilyCompany.getBelongsToField(CompanyProductsFamilyFields.COMPANY));
            companyProduct.setField(CompanyProductFields.PRODUCT, productFamily);
            companyProduct.setField(CompanyProductFields.IS_DEFAULT,
                    productFamilyCompany.getBooleanField(CompanyProductsFamilyFields.IS_DEFAULT));

            companyProductDD.save(companyProduct);

            companyProductsFamilyDD.delete(productFamilyCompany.getId());
        }
    }

    public boolean checkIfDefaultSupplierIsUnique(final DataDefinition productDD, final Entity product) {
        Entity parent = product.getBelongsToField(ProductFields.PARENT);

        if (Objects.nonNull(parent) && companyProductService.checkIfDefaultExistsForProductFamily(parent)
                && companyProductService.checkIfDefaultExistsForParticularProduct(product)) {
            product.addError(productDD.getField(ProductFields.PARENT),
                    "basic.company.message.defaultAlreadyExistsForProductAndFamily");

            return false;
        }

        return true;
    }

    private Entity getProduct(final Entity product) {
        return getProductDD().get(product.getId());
    }

    private DataDefinition getProductDD() {
        return dataDefinitionService.get(BasicConstants.PLUGIN_IDENTIFIER, BasicConstants.MODEL_PRODUCT);
    }

    private DataDefinition getCompanyProductDD() {
        return dataDefinitionService.get(DeliveriesConstants.PLUGIN_IDENTIFIER, DeliveriesConstants.MODEL_COMPANY_PRODUCT);
    }

    private DataDefinition getCompanyProductsFamily() {
        return dataDefinitionService.get(DeliveriesConstants.PLUGIN_IDENTIFIER,
                DeliveriesConstants.MODEL_COMPANY_PRODUCTS_FAMILY);
    }

}
