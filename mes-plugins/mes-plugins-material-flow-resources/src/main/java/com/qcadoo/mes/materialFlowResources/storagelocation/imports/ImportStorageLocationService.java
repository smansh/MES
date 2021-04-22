package com.qcadoo.mes.materialFlowResources.storagelocation.imports;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.file.FileService;
import com.qcadoo.security.api.SecurityService;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportStorageLocationService {

    private static final Integer HEADER_ROW_NUMBER = 0;

    private static final String PATH_TO_FILE = "positionsFile";

    private static final String LOCATION = "location";

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private FileService fileService;

    @Autowired
    private SecurityService securityService;

    @Transactional
    public ImportStorageLocationsResult importPositionsFromFile(final Entity entity, final ViewDefinitionState view) {
        ImportStorageLocationsResult result = new ImportStorageLocationsResult();

        ImportedStorageLocationsPositionsContainer positionsContainer = importPositionsToContainer(entity, view);

        if (!positionsContainer.isImportedPositions()) {
            result.setImported(false);
            return result;
        }

        Entity warehouse = entity.getBelongsToField(LOCATION);
        warehouse.isValid();

        List<StorageLocationDto> storageLocations = findStorageLocationsForWarehouse(warehouse.getId());

        Map<String, StorageLocationDto> storageLocationsByNumber = storageLocations.stream().collect(
                Collectors.toMap(StorageLocationDto::getStorageLocationNumber, item -> item));

        List<ImportedStorageLocationPosition> storageLocationsToUpdate = Lists.newArrayList();

        String userLogin = securityService.getCurrentUserName();
        for (ImportedStorageLocationPosition position : positionsContainer.getPositions()) {
            if (storageLocationsByNumber.containsKey(position.getStorageLocation())) {
                storageLocationsToUpdate.add(position);
            } else {
                createStorageLocation(result, warehouse.getId(), position, userLogin);
            }
        }

        Iterable<List<ImportedStorageLocationPosition>> subSets = Iterables.partition(storageLocationsToUpdate, 100);

        subSets.forEach(list -> {
            List<String> productsNumber = storageLocationsToUpdate.stream().map(s -> s.getProduct()).collect(Collectors.toList());

            List<ProductDto> prods = findProductsByList(productsNumber);
            fillResultWithNotExistingProducts(result, productsNumber, prods);
            Map<String, Long> productsIdByNumber = prods.stream().collect(
                    Collectors.toMap(ProductDto::getProductNumber, ProductDto::getProductId));
            for (ImportedStorageLocationPosition position : list) {
                updateStorageLocation(storageLocationsByNumber.get(position.getStorageLocation()),
                        productsIdByNumber.get(position.getProduct()), userLogin);
            }

        });

        Set<String> storageLocationsToClearProduct = findStorageLocationsToClearProduct(storageLocationsByNumber.keySet(),
                positionsContainer.getPositions());

        storageLocationsToClearProduct.forEach(sl -> {
            updateStorageLocation(storageLocationsByNumber.get(sl), null, userLogin);
        });

        updateStorageLocationInResource(warehouse.getId());
        return result;
    }

    private void fillResultWithNotExistingProducts(ImportStorageLocationsResult result, List<String> productsNumberFromFile,
            List<ProductDto> existingProducts) {
        Map<String, ProductDto> existingProductsByNumber = existingProducts.stream().collect(
                Collectors.toMap(ProductDto::getProductNumber, item -> item));
        productsNumberFromFile.stream().filter(productNumber -> !existingProductsByNumber.containsKey(productNumber))
                .forEach(result::addNotExcitingProduct);
    }

    private int updateStorageLocationInResource(Long warehouse) {
        StringBuilder query = new StringBuilder();
        query.append("UPDATE materialflowresources_resource res ");
        query.append("SET storagelocation_id= ");
        query.append("(SELECT sl.id FROM materialflowresources_storagelocation sl WHERE sl.location_id =:location_id ");
        query.append("AND sl.product_id=res.product_id AND active = true LIMIT 1) ");
        query.append("WHERE res.location_id=:location_id");
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("location_id", warehouse);
        return jdbcTemplate.update(query.toString(), parameters);
    }

    private Set<String> findStorageLocationsToClearProduct(Set<String> storageLocationsDB,
            List<ImportedStorageLocationPosition> storaeLocationPositions) {
        Set<String> storageLocations = storaeLocationPositions.stream().map(sl -> sl.getStorageLocation())
                .collect(Collectors.toSet());
        storageLocationsDB.removeAll(storageLocations);
        return storageLocationsDB;
    }

    private void updateStorageLocation(StorageLocationDto storageLocationDto, Long productId, String userLogin) {
        String insert = "UPDATE materialflowresources_storagelocation SET product_id=:product_id WHERE id = :id";

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("product_id", productId);
        parameters.put("id", storageLocationDto.getStorageLocationId());

        jdbcTemplate.update(insert, parameters);
        insertAuditEntry(userLogin, parameters, storageLocationDto.getStorageLocationId());
    }

    private List<ProductDto> findProductsByList(List<String> list) {
        String queryProductByNumber = "SELECT id as productId, number as productNumber FROM basic_product WHERE number IN (:numbers)";
        Map<String, Object> queryProductByNumberParameters = new HashMap<String, Object>();
        queryProductByNumberParameters.put("numbers", list);

        return jdbcTemplate.query(queryProductByNumber, queryProductByNumberParameters, new BeanPropertyRowMapper(
                ProductDto.class));
    }

    private void createStorageLocation(ImportStorageLocationsResult result, Long id, ImportedStorageLocationPosition position,
            String userLogin) {
        String insert = "INSERT INTO materialflowresources_storagelocation(number, location_id, product_id, active) "
                + "VALUES (:number, :location_id, :product_id, :active);";

        Map<String, Object> parameters = new HashMap<String, Object>();

        if (StringUtils.isNotEmpty(position.getProduct())) {
            String queryProductByNumber = "SELECT id FROM basic_product WHERE number = :number";
            Map<String, Object> queryProductByNumberParameters = new HashMap<String, Object>();
            queryProductByNumberParameters.put("number", position.getProduct());
            try {
                Long productId = jdbcTemplate.queryForObject(queryProductByNumber, queryProductByNumberParameters, Long.class);
                parameters.put("product_id", productId);
            } catch (EmptyResultDataAccessException ex) {
                result.addNotExcitingProduct(position.getProduct());
                return;
            }

        } else {
            result.addNotExcitingProduct(position.getProduct());
            parameters.put("product_id", null);
        }

        parameters.put("number", position.getStorageLocation());
        parameters.put("location_id", id);
        parameters.put("active", true);

        jdbcTemplate.update(insert, parameters);

        insertAuditEntry(userLogin, parameters, null);
    }

    private ImportedStorageLocationsPositionsContainer importPositionsToContainer(final Entity entity,
            final ViewDefinitionState view) {
        ImportedStorageLocationsPositionsContainer positionsContainer = new ImportedStorageLocationsPositionsContainer();
        String path = entity.getStringField(PATH_TO_FILE);
        try {

            InputStream stream = fileService.getInputStream(path);
            HSSFWorkbook workbook = null;
            workbook = new HSSFWorkbook(stream);

            HSSFSheet sheet = workbook.getSheetAt(0);

            Iterator<Row> rowIterator = sheet.iterator();
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                if (row.getRowNum() > HEADER_ROW_NUMBER) {
                    positionsContainer.addPosition(row);
                }

            }

        } catch (Exception e) {
            positionsContainer.setImportedPositions(false);
            view.addMessage("materialFlowResources.importStorageLocationList.importPositions.import.wrongXlsFileStructure",
                    ComponentState.MessageType.FAILURE, false);
        }
        return positionsContainer;
    }

    private List<StorageLocationDto> findStorageLocationsForWarehouse(final Long id) {
        String query = buildQueryForFindStorageLocations();
        Map<String, Object> params = Maps.newHashMap();
        params.put("warehouse", id);
        return jdbcTemplate.query(query, params, new BeanPropertyRowMapper(StorageLocationDto.class));
    }

    private String buildQueryForFindStorageLocations() {
        StringBuilder builder = new StringBuilder();
        builder.append("SELECT ");
        builder.append("storagelocation.id as storageLocationId, storagelocation.number as storageLocationNumber, ");
        builder.append("product.id as productId, product.number as productNumber ");
        builder.append("FROM materialflowresources_storagelocation storagelocation ");
        builder.append("LEFT JOIN basic_product product ON product.id = storagelocation.product_id ");
        builder.append("WHERE location_id = :warehouse");
        return builder.toString();
    }

    private void insertAuditEntry(String userLogin, Map<String, Object> parameters, Long storageLocationId) {
        if (parameters.containsKey("product_id")) {
            String insertHistoryEntry;
            Map<String, Object> parametersForHistory = new HashMap<>();

            if (Objects.nonNull(storageLocationId)) {
                insertHistoryEntry = "INSERT INTO materialflowresources_storagelocationhistory(storagelocation_id, productto_id, createdate, updatedate, createuser, updateuser) "
                        + " VALUES (:storagelocation_id, :product_id, :createdate, :updatedate, :createuser, :updateuser);";
                parametersForHistory.put("storagelocation_id", storageLocationId);

            } else {
                insertHistoryEntry = "INSERT INTO materialflowresources_storagelocationhistory(storagelocation_id, productto_id, createdate, updatedate, createuser, updateuser) "
                        + " VALUES (currval('materialflowresources_storagelocation_id_seq'), :product_id, :createdate, :updatedate, :createuser, :updateuser);";
            }

            parametersForHistory.put("product_id", parameters.get("product_id"));
            parametersForHistory.put("createdate", new Date());
            parametersForHistory.put("updatedate", new Date());
            parametersForHistory.put("createuser", userLogin);
            parametersForHistory.put("updateuser", userLogin);
            jdbcTemplate.update(insertHistoryEntry, parametersForHistory);
        }
    }
}
