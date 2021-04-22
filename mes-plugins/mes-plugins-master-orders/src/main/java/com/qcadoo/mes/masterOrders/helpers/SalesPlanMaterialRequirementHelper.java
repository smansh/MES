package com.qcadoo.mes.masterOrders.helpers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.qcadoo.mes.basic.constants.ProductFamilyElementType;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.deliveries.constants.CompanyProductFields;
import com.qcadoo.mes.deliveries.constants.CompanyProductsFamilyFields;
import com.qcadoo.mes.deliveries.constants.DeliveriesConstants;
import com.qcadoo.mes.masterOrders.constants.MasterOrdersConstants;
import com.qcadoo.mes.masterOrders.constants.SalesPlanFields;
import com.qcadoo.mes.masterOrders.constants.SalesPlanMaterialRequirementFields;
import com.qcadoo.mes.masterOrders.constants.SalesPlanMaterialRequirementProductFields;
import com.qcadoo.mes.masterOrders.constants.SalesPlanProductFields;
import com.qcadoo.mes.materialFlowResources.MaterialFlowResourcesService;
import com.qcadoo.mes.technologies.ProductQuantitiesService;
import com.qcadoo.mes.technologies.constants.ProductBySizeGroupFields;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.mes.technologies.dto.OperationProductComponentHolder;
import com.qcadoo.mes.technologies.tree.ProductStructureTreeService;
import com.qcadoo.model.api.BigDecimalUtils;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.model.api.search.JoinType;
import com.qcadoo.model.api.search.SearchRestrictions;

@Service
public class SalesPlanMaterialRequirementHelper {

    private static final String L_DOT = ".";

    private static final String L_ID = "id";

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private NumberService numberService;

    @Autowired
    private ProductQuantitiesService productQuantitiesService;

    @Autowired
    private ProductStructureTreeService productStructureTreeService;

    @Autowired
    private MaterialFlowResourcesService materialFlowResourcesService;

    public List<Entity> generateSalesPlanMaterialRequirementProducts(final Entity salesPlanMaterialRequirement) {
        List<Entity> salesPlanMaterialRequirementProducts = Lists.newArrayList();

        Entity salesPlan = salesPlanMaterialRequirement.getBelongsToField(SalesPlanMaterialRequirementFields.SALES_PLAN);

        createSalesPlanMaterialRequirementProducts(salesPlanMaterialRequirementProducts, salesPlan);

        updateSalesPlanMaterialRequirementProducts(salesPlanMaterialRequirementProducts);

        return salesPlanMaterialRequirementProducts;
    }

    private void createSalesPlanMaterialRequirementProducts(final List<Entity> salesPlanMaterialRequirementProducts,
            final Entity salesPlan) {
        List<Entity> salesPlanProducts = salesPlan.getHasManyField(SalesPlanFields.PRODUCTS);

        for (Entity salesPlanProduct : salesPlanProducts) {
            Entity salesPlanProductTechnology = salesPlanProduct.getBelongsToField(SalesPlanProductFields.TECHNOLOGY);
            Entity salesPlanProductProduct = salesPlanProduct.getBelongsToField(SalesPlanProductFields.PRODUCT);
            BigDecimal plannedQuantity = salesPlanProduct.getDecimalField(SalesPlanProductFields.PLANNED_QUANTITY);

            Entity technology = getTechnology(salesPlanProductTechnology, salesPlanProductProduct);

            if (Objects.nonNull(technology)) {
                Map<OperationProductComponentHolder, BigDecimal> neededQuantities = productQuantitiesService
                        .getNeededProductQuantities(technology, salesPlanProductProduct, plannedQuantity);

                for (Map.Entry<OperationProductComponentHolder, BigDecimal> neededProductQuantity : neededQuantities.entrySet()) {
                    Long productId = neededProductQuantity.getKey().getProductId();
                    Long operationProductComponentId = neededProductQuantity.getKey().getOperationProductComponentId();
                    Entity product = neededProductQuantity.getKey().getProduct();
                    BigDecimal neededQuantity = neededProductQuantity.getValue();

                    if (Objects.isNull(productId)) {
                        List<Entity> productBySizeGroups = getProductBySizeGroups(operationProductComponentId);

                        for (Entity productBySizeGroup : productBySizeGroups) {
                            createSalesPlanMaterialRequirementProductFromProductBySizeGroup(salesPlanMaterialRequirementProducts,
                                    productBySizeGroup, neededQuantity);
                        }
                    } else {
                        createSalesPlanMaterialRequirementProductFromProduct(salesPlanMaterialRequirementProducts, product,
                                neededQuantity);
                    }
                }
            }
        }
    }

    private Entity getTechnology(final Entity technology, final Entity product) {
        if (Objects.isNull(technology)) {
            String entityType = product.getStringField(ProductFields.ENTITY_TYPE);

            if (ProductFamilyElementType.PARTICULAR_PRODUCT.getStringValue().equals(entityType)) {
                Entity parent = product.getBelongsToField(ProductFields.PARENT);

                if (Objects.nonNull(parent)) {
                    return productStructureTreeService.findTechnologyForProduct(parent);
                } else {
                    return productStructureTreeService.findTechnologyForProduct(product);
                }
            } else {
                return productStructureTreeService.findTechnologyForProduct(product);
            }
        }

        return technology;
    }

    private List<Entity> getProductBySizeGroups(final Long operationProductComponentId) {
        return getProductBySizeGroupDD().find()
                .createAlias(ProductBySizeGroupFields.OPERATION_PRODUCT_IN_COMPONENT,
                        ProductBySizeGroupFields.OPERATION_PRODUCT_IN_COMPONENT, JoinType.LEFT)
                .add(SearchRestrictions.eq(ProductBySizeGroupFields.OPERATION_PRODUCT_IN_COMPONENT + L_DOT + L_ID,
                        operationProductComponentId))
                .list().getEntities();
    }

    private Entity createSalesPlanMaterialRequirementProductFromProduct(final List<Entity> salesPlanMaterialRequirementProducts,
            final Entity product, final BigDecimal neededQuantity) {
        Optional<Entity> mayBeSalesPlanMaterialRequirementProduct = salesPlanMaterialRequirementProducts.stream()
                .filter(salesPlanMaterialRequirementProduct -> filterByProduct(salesPlanMaterialRequirementProduct, product,
                        SalesPlanMaterialRequirementProductFields.QUANTITY))
                .findFirst();

        Entity salesPlanMaterialRequirementProduct;

        if (mayBeSalesPlanMaterialRequirementProduct.isPresent()) {
            salesPlanMaterialRequirementProduct = mayBeSalesPlanMaterialRequirementProduct.get();

            BigDecimal quantity = salesPlanMaterialRequirementProduct
                    .getDecimalField(SalesPlanMaterialRequirementProductFields.QUANTITY);

            quantity = quantity.add(neededQuantity, numberService.getMathContext());

            salesPlanMaterialRequirementProduct.setField(SalesPlanMaterialRequirementProductFields.QUANTITY, quantity);
        } else {
            salesPlanMaterialRequirementProduct = getSalesPlanMaterialRequirementProductDD().create();

            salesPlanMaterialRequirementProduct.setField(SalesPlanMaterialRequirementProductFields.PRODUCT, product);
            salesPlanMaterialRequirementProduct.setField(SalesPlanMaterialRequirementProductFields.QUANTITY, neededQuantity);
            salesPlanMaterialRequirementProduct.setField(SalesPlanMaterialRequirementProductFields.IS_DELIVERY_CREATED, false);

            salesPlanMaterialRequirementProducts.add(salesPlanMaterialRequirementProduct);
        }

        return salesPlanMaterialRequirementProduct;
    }

    private Entity createSalesPlanMaterialRequirementProductFromProductBySizeGroup(
            final List<Entity> salesPlanMaterialRequirementProducts, final Entity productBySizeGroup,
            final BigDecimal neededQuantity) {
        Entity product = productBySizeGroup.getBelongsToField(ProductBySizeGroupFields.PRODUCT);
        Entity sizeGroup = productBySizeGroup.getBelongsToField(ProductBySizeGroupFields.SIZE_GROUP);

        Optional<Entity> mayBeSalesPlanMaterialRequirementProduct = salesPlanMaterialRequirementProducts.stream()
                .filter(salesPlanMaterialRequirementProduct -> filterByProduct(salesPlanMaterialRequirementProduct, product,
                        SalesPlanMaterialRequirementProductFields.SIZE_GROUP)
                        && filterBySizeGroup(salesPlanMaterialRequirementProduct, sizeGroup))
                .findFirst();

        Entity salesPlanMaterialRequirementProduct;

        if (mayBeSalesPlanMaterialRequirementProduct.isPresent()) {
            salesPlanMaterialRequirementProduct = mayBeSalesPlanMaterialRequirementProduct.get();

            BigDecimal sumForSizes = salesPlanMaterialRequirementProduct
                    .getDecimalField(SalesPlanMaterialRequirementProductFields.SUM_FOR_SIZES);

            sumForSizes = sumForSizes.add(neededQuantity, numberService.getMathContext());

            salesPlanMaterialRequirementProduct.setField(SalesPlanMaterialRequirementProductFields.SUM_FOR_SIZES, sumForSizes);
        } else {
            salesPlanMaterialRequirementProduct = getSalesPlanMaterialRequirementProductDD().create();

            salesPlanMaterialRequirementProduct.setField(SalesPlanMaterialRequirementProductFields.PRODUCT, product);
            salesPlanMaterialRequirementProduct.setField(SalesPlanMaterialRequirementProductFields.SIZE_GROUP, sizeGroup);
            salesPlanMaterialRequirementProduct.setField(SalesPlanMaterialRequirementProductFields.SUM_FOR_SIZES, neededQuantity);
            salesPlanMaterialRequirementProduct.setField(SalesPlanMaterialRequirementProductFields.IS_DELIVERY_CREATED, false);

            salesPlanMaterialRequirementProducts.add(salesPlanMaterialRequirementProduct);
        }

        return salesPlanMaterialRequirementProduct;
    }

    private boolean filterByProduct(final Entity salesPlanMaterialRequirementProduct, final Entity product,
            final String fieldName) {
        Entity salesPlanMaterialRequirementProductProduct = salesPlanMaterialRequirementProduct
                .getBelongsToField(SalesPlanMaterialRequirementProductFields.PRODUCT);

        return Objects.nonNull(salesPlanMaterialRequirementProductProduct)
                && Objects.nonNull(salesPlanMaterialRequirementProduct.getField(fieldName))
                && salesPlanMaterialRequirementProductProduct.getId().equals(product.getId());
    }

    private boolean filterBySizeGroup(final Entity salesPlanMaterialRequirementProduct, final Entity sizeGroup) {
        Entity salesPlanMaterialRequirementProductSizeGroup = salesPlanMaterialRequirementProduct
                .getBelongsToField(SalesPlanMaterialRequirementProductFields.SIZE_GROUP);

        return Objects.nonNull(salesPlanMaterialRequirementProductSizeGroup)
                && salesPlanMaterialRequirementProductSizeGroup.getId().equals(sizeGroup.getId());
    }

    private void updateSalesPlanMaterialRequirementProducts(final List<Entity> salesPlanMaterialRequirementProducts) {
        List<Entity> products = getSalesPlanMaterialRequirementProducts(salesPlanMaterialRequirementProducts);
        Set<Long> parentIds = products.stream()
                .filter(product -> Objects.nonNull(product.getBelongsToField(ProductFields.PARENT)))
                .map(product -> product.getBelongsToField(ProductFields.PARENT).getId()).collect(Collectors.toSet());
        Set<Long> productIds = products.stream().map(Entity::getId).collect(Collectors.toSet());

        Map<Long, Map<Long, BigDecimal>> resourceStocks = getResourceStocks(products);
        List<Entity> companyProducts = getCompanyProducts(productIds);
        List<Entity> companyProductsFamilies = getCompanyProductsFamilies(parentIds);
        Map<Long, BigDecimal> neededQuantitiesFromOrders = getNeededQuantitiesFromOrders(productIds);

        for (Entity salesPlanMaterialRequirementProduct : salesPlanMaterialRequirementProducts) {
            Entity product = salesPlanMaterialRequirementProduct
                    .getBelongsToField(SalesPlanMaterialRequirementProductFields.PRODUCT);

            Long productId = product.getId();
            Entity parent = product.getBelongsToField(ProductFields.PARENT);

            BigDecimal currentStock = BigDecimalUtils.convertNullToZero(getCurrentStock(resourceStocks, productId));
            BigDecimal neededQuantity = BigDecimalUtils.convertNullToZero(neededQuantitiesFromOrders.get(productId));

            Optional<Entity> mayBeCompanyProduct = getCompanyProduct(companyProducts, productId);

            Entity supplier = null;
            BigDecimal minimumOrderQuantity = null;

            if (mayBeCompanyProduct.isPresent()) {
                Entity companyProduct = mayBeCompanyProduct.get();

                supplier = companyProduct.getBelongsToField(CompanyProductFields.COMPANY);
                minimumOrderQuantity = companyProduct.getDecimalField(CompanyProductFields.MINIMUM_ORDER_QUANTITY);
            } else {
                if (Objects.nonNull(parent)) {
                    Optional<Entity> mayBeCompanyProductsFamily = getCompanyProductsFamily(companyProductsFamilies,
                            parent.getId());

                    if (mayBeCompanyProductsFamily.isPresent()) {
                        Entity companyProductsFamily = mayBeCompanyProductsFamily.get();

                        supplier = companyProductsFamily.getBelongsToField(CompanyProductsFamilyFields.COMPANY);
                        minimumOrderQuantity = companyProductsFamily
                                .getDecimalField(CompanyProductsFamilyFields.MINIMUM_ORDER_QUANTITY);
                    }
                }
            }

            salesPlanMaterialRequirementProduct.setField(SalesPlanMaterialRequirementProductFields.CURRENT_STOCK, currentStock);
            salesPlanMaterialRequirementProduct.setField(SalesPlanMaterialRequirementProductFields.NEEDED_QUANTITY,
                    neededQuantity);
            salesPlanMaterialRequirementProduct.setField(SalesPlanMaterialRequirementProductFields.SUPPLIER, supplier);
            salesPlanMaterialRequirementProduct.setField(SalesPlanMaterialRequirementProductFields.MINIMUM_ORDER_QUANTITY,
                    minimumOrderQuantity);
        }
    }

    private List<Entity> getSalesPlanMaterialRequirementProducts(final List<Entity> salesPlanMaterialRequirementProducts) {
        return salesPlanMaterialRequirementProducts.stream()
                .map(salesPlanMaterialRequirementProduct -> salesPlanMaterialRequirementProduct
                        .getBelongsToField(SalesPlanMaterialRequirementProductFields.PRODUCT))
                .collect(Collectors.toList());
    }

    private Map<Long, Map<Long, BigDecimal>> getResourceStocks(final List<Entity> products) {
        Map<Long, Map<Long, BigDecimal>> resourceStocks = Maps.newHashMap();

        if (!products.isEmpty()) {
            List<Entity> locations = materialFlowResourcesService.getWarehouseLocationsFromDB();

            resourceStocks = materialFlowResourcesService.getQuantitiesForProductsAndLocations(products, locations);
        }

        return resourceStocks;
    }

    private BigDecimal getCurrentStock(final Map<Long, Map<Long, BigDecimal>> resourceStocks, final Long productId) {
        BigDecimal currentStock = BigDecimal.ZERO;

        for (Map.Entry<Long, Map<Long, BigDecimal>> resourceStock : resourceStocks.entrySet()) {
            currentStock = currentStock.add(BigDecimalUtils.convertNullToZero(resourceStock.getValue().get(productId)),
                    numberService.getMathContext());
        }

        return currentStock;
    }

    private List<Entity> getCompanyProducts(final Set<Long> productIds) {
        List<Entity> companyProducts = Lists.newArrayList();

        if (!productIds.isEmpty()) {
            companyProducts = getCompanyProductDD().find()
                    .createAlias(CompanyProductFields.PRODUCT, CompanyProductFields.PRODUCT, JoinType.LEFT)
                    .add(SearchRestrictions.in(CompanyProductFields.PRODUCT + L_DOT + L_ID, productIds))
                    .add(SearchRestrictions.eq(CompanyProductFields.IS_DEFAULT, true)).list().getEntities();
        }

        return companyProducts;
    }

    private Optional<Entity> getCompanyProduct(final List<Entity> companyProducts, final Long productId) {
        return companyProducts.stream().filter(
                companyProduct -> companyProduct.getBelongsToField(CompanyProductFields.PRODUCT).getId().equals(productId))
                .findAny();
    }

    private List<Entity> getCompanyProductsFamilies(final Set<Long> productIds) {
        List<Entity> companyProductFamilies = Lists.newArrayList();

        if (!productIds.isEmpty()) {
            companyProductFamilies = getCompanyProductsFamilyDD().find()
                    .createAlias(CompanyProductsFamilyFields.PRODUCT, CompanyProductsFamilyFields.PRODUCT, JoinType.LEFT)
                    .add(SearchRestrictions.in(CompanyProductsFamilyFields.PRODUCT + L_DOT + L_ID, productIds))
                    .add(SearchRestrictions.eq(CompanyProductsFamilyFields.IS_DEFAULT, true)).list().getEntities();
        }

        return companyProductFamilies;
    }

    private Optional<Entity> getCompanyProductsFamily(final List<Entity> companyProductsFamilies, final Long productId) {
        return companyProductsFamilies.stream().filter(companyProductsFamily -> companyProductsFamily
                .getBelongsToField(CompanyProductsFamilyFields.PRODUCT).getId().equals(productId)).findAny();
    }

    private Map<Long, BigDecimal> getNeededQuantitiesFromOrders(final Set<Long> productIds) {
        Map<Long, BigDecimal> neededQuantitiesFromOrders = Maps.newHashMap();

        if (!productIds.isEmpty()) {
            StringBuilder queryBuilder = new StringBuilder();

            queryBuilder.append("SELECT ");
            queryBuilder.append("productid,  SUM(COALESCE(plannedquantity, 0) - COALESCE(usedquantity, 0)) AS neededQuantity ");
            queryBuilder.append("FROM basicproductioncounting_productioncountingquantitydto ");
            queryBuilder.append("WHERE productid IN (:productIds) ");
            queryBuilder.append("AND orderid IN ( ");
            queryBuilder.append(
                    "SELECT id FROM orders_order WHERE state NOT IN ('01pending', '04completed', '05declined', '07abandoned') ");
            queryBuilder.append(") ");
            queryBuilder.append("GROUP BY productid");

            Map<String, Object> params = Maps.newHashMap();

            params.put("productIds", productIds);

            try {
                List<Map<String, Object>> values = jdbcTemplate.queryForList(queryBuilder.toString(), params);

                for (Map<String, Object> value : values) {
                    Long productId = Long.valueOf(value.get("productId").toString());
                    BigDecimal neededQuantity = BigDecimalUtils.convertNullToZero(value.get("neededQuantity"));

                    neededQuantitiesFromOrders.put(productId, neededQuantity);
                }
            } catch (EmptyResultDataAccessException e) {
                return neededQuantitiesFromOrders;
            }
        }

        return neededQuantitiesFromOrders;
    }

    private DataDefinition getSalesPlanMaterialRequirementProductDD() {
        return dataDefinitionService.get(MasterOrdersConstants.PLUGIN_IDENTIFIER,
                MasterOrdersConstants.MODEL_SALES_PLAN_MATERIAL_REQUIREMENT_PRODUCT);
    }

    private DataDefinition getProductBySizeGroupDD() {
        return dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_PRODUCT_BY_SIZE_GROUP);
    }

    private DataDefinition getCompanyProductDD() {
        return dataDefinitionService.get(DeliveriesConstants.PLUGIN_IDENTIFIER, DeliveriesConstants.MODEL_COMPANY_PRODUCT);
    }

    private DataDefinition getCompanyProductsFamilyDD() {
        return dataDefinitionService.get(DeliveriesConstants.PLUGIN_IDENTIFIER,
                DeliveriesConstants.MODEL_COMPANY_PRODUCTS_FAMILY);
    }

}
