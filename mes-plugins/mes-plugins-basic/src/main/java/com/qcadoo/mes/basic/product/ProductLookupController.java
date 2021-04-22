package com.qcadoo.mes.basic.product;

import com.google.common.base.Strings;
import com.qcadoo.localization.api.TranslationService;
import com.qcadoo.mes.basic.controllers.BasicLookupController;
import com.qcadoo.mes.basic.LookupUtils;
import com.qcadoo.mes.basic.constants.GlobalTypeOfMaterial;
import com.qcadoo.mes.basic.controllers.dataProvider.dto.ProductDTO;
import com.qcadoo.model.api.DictionaryService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(value = "product")
public class ProductLookupController extends BasicLookupController<ProductDTO> {

    @Autowired
    private DictionaryService dictionaryService;

    @Autowired
    private LookupUtils lookupUtils;

    @Autowired
    private TranslationService translationService;


    protected Map<String, Object> getConfigMap(List<String> columns) {
        Map<String, Object> config = lookupUtils.getConfigMap(columns);

        config = prepareConfigForGlobalTypeOfMaterial(config);
        config = prepareConfigForCategory(config);

        return config;
    }

    private String getGlobalTypeOfMaterialFormatter(Locale locale) {
        Map<String, String> values = getGlobalTypeOfMaterialTranslatedValues(locale);

        final List<String> items = new ArrayList<>();
        values.forEach((key, value) -> {
            items.add(String.format("map['%s'] = '%s';", key, value));
        });
        String valuesString = items.stream().collect(Collectors.joining());

        String formatter = "function(cellValue, options, rowObject) {"
                + "            var map = {};"
                + valuesString
                + "            var newValue = map[cellValue] || '';"
                + "            return newValue;"
                + "        }";

        return formatter;
    }

    @Override
    protected String getQueryForRecords(final Long context) {
        String query = "SELECT %s FROM (SELECT product.id, product.number as code, product.number, product.name, product.ean, product.globaltypeofmaterial, product.category "
                + "FROM basic_product product WHERE product.active = true %s) q ";

        return query;
    }

    @Override
    protected List<String> getGridFields() {
        return Arrays.asList("number", "name", "ean", "globaltypeofmaterial", "category");
    }

    @Override
    protected String getRecordName() {
        return "product";
    }

    private Map<String, String> getGlobalTypeOfMaterialTranslatedValues(Locale locale) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (GlobalTypeOfMaterial globalTypeOfMaterial : GlobalTypeOfMaterial.values()) {
            String key = globalTypeOfMaterial.getStringValue();
            if (!Strings.isNullOrEmpty(key)) {
                values.put(key, translationService.translate("basic.product.globalTypeOfMaterial.value." + key, locale));
            }
        }

        return values;
    }

    private Map<String, Object> prepareConfigForGlobalTypeOfMaterial(Map<String, Object> config) {
        Map<String, Object> globaltypeofmaterial = ((Collection<Map<String, Object>>) config.get("colModel")).stream().filter(entry -> {
            return "globaltypeofmaterial".equals(entry.get("index"));
        }).findAny().get();
        globaltypeofmaterial.put("formatter", getGlobalTypeOfMaterialFormatter(LocaleContextHolder.getLocale()));
        globaltypeofmaterial.put("stype", "select");

        String searchOptionsValue = ":" + translationService.translate("documentGrid.allItem", LocaleContextHolder.getLocale()) + ";";
        searchOptionsValue += getGlobalTypeOfMaterialTranslatedValues(LocaleContextHolder.getLocale()).entrySet().stream().map(entry -> {
            return entry.getKey() + ":" + entry.getValue();
        }).collect(Collectors.joining(";"));
        ((Map<String, Object>) globaltypeofmaterial.get("searchoptions")).put("value", searchOptionsValue);

        return config;
    }

    private Map<String, Object> prepareConfigForCategory(Map<String, Object> config) {
        Map<String, Object> category = ((Collection<Map<String, Object>>) config.get("colModel")).stream().filter(entry -> {
            return "category".equals(entry.get("index"));
        }).findAny().get();

        category.put("stype", "select");

        String categorySearchOptionsValue = ":" + translationService.translate("documentGrid.allItem", LocaleContextHolder.getLocale()) + ";";
        categorySearchOptionsValue += dictionaryService.getKeys("categories").stream().map(item -> {
            return item + ":" + item;
        }).collect(Collectors.joining(";"));
        ((Map<String, Object>) category.get("searchoptions")).put("value", categorySearchOptionsValue);

        return config;
    }
}
