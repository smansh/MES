package com.qcadoo.mes.productFlowThruDivision.warehouseIssue.listeners;

import com.google.common.collect.Lists;
import com.qcadoo.mes.basic.CalculationQuantityService;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.basic.constants.UnitConversionItemFieldsB;
import com.qcadoo.mes.productFlowThruDivision.warehouseIssue.IssueCommonDetailsHelper;
import com.qcadoo.mes.productFlowThruDivision.warehouseIssue.constans.ProductsToIssueFields;
import com.qcadoo.mes.productFlowThruDivision.warehouseIssue.hooks.ProductToIssueDetailsHooks;
import com.qcadoo.model.api.BigDecimalUtils;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.model.api.units.PossibleUnitConversions;
import com.qcadoo.model.api.units.UnitConversionService;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.constants.QcadooViewConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductsToIssueDetailsListeners {



    @Autowired
    private ProductToIssueDetailsHooks productToIssueDetailsHooks;

    @Autowired
    private IssueCommonDetailsHelper issueCommonDetailsHelper;

    @Autowired
    private NumberService numberService;

    @Autowired
    private UnitConversionService unitConversionService;

    @Autowired
    private CalculationQuantityService calculationQuantityService;

    public void onProductSelect(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        issueCommonDetailsHelper.clearStorageLocationLookup(view);
        issueCommonDetailsHelper.clearAdditionalCodeLookup(view);
        issueCommonDetailsHelper.fillStorageLocation(view);
        productToIssueDetailsHooks.onBeforeRender(view);
        fillAdditionalUnit(view);
        onDemandQuantityChange(view, state, args);
    }

    public void onLocationSelect(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        issueCommonDetailsHelper.clearStorageLocationLookup(view);
        issueCommonDetailsHelper.fillStorageLocation(view);
        productToIssueDetailsHooks.onBeforeRender(view);
    }

    public void onDemandQuantityChange(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        FormComponent form = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        Entity productToIssue = form.getPersistedEntityWithIncludedFormValues();
        if (!checkIfDecimalFieldsCorrect(productToIssue, form)) {
            return;
        }
        BigDecimal conversion = productToIssue.getDecimalField(ProductsToIssueFields.CONVERSION);
        BigDecimal demandQuantity = productToIssue.getDecimalField(ProductsToIssueFields.DEMAND_QUANTITY);
        if (conversion != null && demandQuantity != null) {
            FieldComponent additionalDemandQuantity = (FieldComponent) view
                    .getComponentByReference(ProductsToIssueFields.ADDITIONAL_DEMAND_QUANTITY);

            Entity product = productToIssue.getBelongsToField(ProductsToIssueFields.PRODUCT);
            BigDecimal newAdditionalQuantity = calculationQuantityService.calculateAdditionalQuantity(demandQuantity, conversion,
                    product.getStringField(ProductFields.ADDITIONAL_UNIT));

            additionalDemandQuantity.setFieldValue(numberService.formatWithMinimumFractionDigits(newAdditionalQuantity, 0));
            additionalDemandQuantity.requestComponentUpdateState();
        }
    }

    public void onAdditionalDemandQuantityChange(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        FormComponent form = (FormComponent) view.getComponentByReference(QcadooViewConstants.L_FORM);
        Entity productToIssue = form.getPersistedEntityWithIncludedFormValues();
        if (!checkIfDecimalFieldsCorrect(productToIssue, form)) {
            return;
        }

        BigDecimal conversion = productToIssue.getDecimalField(ProductsToIssueFields.CONVERSION);
        BigDecimal additionalDemandQuantity = productToIssue.getDecimalField(ProductsToIssueFields.ADDITIONAL_DEMAND_QUANTITY);
        if (conversion != null && additionalDemandQuantity != null) {
            FieldComponent demandQuantity = (FieldComponent) view.getComponentByReference(ProductsToIssueFields.DEMAND_QUANTITY);
            Entity product = productToIssue.getBelongsToField(ProductsToIssueFields.PRODUCT);

            BigDecimal newDemandQuantity = calculationQuantityService.calculateQuantity(additionalDemandQuantity, conversion,
                    product.getStringField(ProductFields.UNIT));

            demandQuantity.setFieldValue(numberService.formatWithMinimumFractionDigits(newDemandQuantity, 0));
            demandQuantity.requestComponentUpdateState();
        }
    }

    public void onConversionChange(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        onDemandQuantityChange(view, state, args);
    }

    private boolean checkIfDecimalFieldsCorrect(final Entity productToIssue, final FormComponent form) {
        boolean isCorrectValue = true;
        List<String> fields = Lists.newArrayList(ProductsToIssueFields.DEMAND_QUANTITY,
                ProductsToIssueFields.ADDITIONAL_DEMAND_QUANTITY, ProductsToIssueFields.CONVERSION);
        for (String field : fields) {
            if (!BigDecimalUtils.checkIfCorrectDecimalValue(productToIssue, field)) {
                form.findFieldComponentByName(field).addMessage("qcadooView.validate.field.error.invalidNumericFormat",
                        ComponentState.MessageType.FAILURE);
                isCorrectValue = false;
            }
        }
        return isCorrectValue;
    }

    private BigDecimal getConversion(Entity product, String unit, String additionalUnit) {
        PossibleUnitConversions unitConversions = unitConversionService.getPossibleConversions(unit,
                searchCriteriaBuilder -> searchCriteriaBuilder.add(SearchRestrictions.belongsTo(
                        UnitConversionItemFieldsB.PRODUCT, product)));
        if (unitConversions.isDefinedFor(additionalUnit)) {
            return unitConversions.asUnitToConversionMap().get(additionalUnit);
        } else {
            return BigDecimal.ZERO;
        }
    }

    private void fillAdditionalUnit(ViewDefinitionState view) {
        LookupComponent productLookup = (LookupComponent) view.getComponentByReference(ProductsToIssueFields.PRODUCT);
        Entity product = productLookup.getEntity();

        if (product != null) {
            String unit = product.getStringField(ProductFields.UNIT);
            String additionalUnit = product.getStringField(ProductFields.ADDITIONAL_UNIT);
            FieldComponent conversionField = (FieldComponent) view.getComponentByReference("conversion");

            if (!StringUtils.isEmpty(additionalUnit)) {
                String conversion = numberService
                        .formatWithMinimumFractionDigits(getConversion(product, unit, additionalUnit), 0);
                conversionField.setFieldValue(conversion);
                conversionField.setEnabled(true);
                conversionField.requestComponentUpdateState();
            }
        }
    }
}
