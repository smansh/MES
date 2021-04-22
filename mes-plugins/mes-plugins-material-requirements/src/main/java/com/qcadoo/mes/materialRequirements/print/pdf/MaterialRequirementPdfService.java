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
package com.qcadoo.mes.materialRequirements.print.pdf;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfCell;
import com.lowagie.text.pdf.PdfPTable;
import com.qcadoo.localization.api.TranslationService;
import com.qcadoo.localization.api.utils.DateUtils;
import com.qcadoo.mes.basic.constants.BasicConstants;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.basicProductionCounting.BasicProductionCountingService;
import com.qcadoo.mes.materialFlowResources.MaterialFlowResourcesService;
import com.qcadoo.mes.materialRequirements.constants.MaterialRequirementFields;
import com.qcadoo.mes.materialRequirements.print.MaterialRequirementDataService;
import com.qcadoo.mes.materialRequirements.print.MaterialRequirementEntry;
import com.qcadoo.mes.materialRequirements.print.WarehouseDateKey;
import com.qcadoo.mes.materialRequirements.util.EntityOrderNumberComparator;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.technologies.constants.MrpAlgorithm;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.report.api.FontUtils;
import com.qcadoo.report.api.pdf.HeaderAlignment;
import com.qcadoo.report.api.pdf.PdfDocumentService;
import com.qcadoo.report.api.pdf.PdfHelper;

@Service
public final class MaterialRequirementPdfService extends PdfDocumentService {

    private final int[] defaultMatReqHeaderColumnWidth = new int[] { 25, 25, 24, 13, 13 };

    private final int[] defaultOrderHeaderColumnWidth = new int[] { 37, 37, 13, 13 };

    @Autowired
    private TranslationService translationService;

    @Autowired
    private NumberService numberService;

    @Autowired
    private PdfHelper pdfHelper;

    @Autowired
    private BasicProductionCountingService basicProductionCountingService;

    @Autowired
    private MaterialRequirementDataService materialRequirementDataService;

    @Autowired
    private MaterialFlowResourcesService materialFlowResourcesService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Override
    protected void buildPdfContent(final Document document, final Entity materialRequirement, final Locale locale)
            throws DocumentException {
        String documentTitle = translationService.translate("materialRequirements.materialRequirement.report.title", locale);
        String documentAuthor = translationService.translate("qcadooReport.commons.generatedBy.label", locale);

        pdfHelper.addDocumentHeader(document, "", documentTitle, documentAuthor,
                materialRequirement.getDateField(MaterialRequirementFields.DATE));

        addPanel(document, materialRequirement, locale);

        document.add(
                new Paragraph(translationService.translate("materialRequirements.materialRequirement.report.paragrah", locale),
                        FontUtils.getDejavuBold11Dark()));

        Map<String, HeaderAlignment> orderHeadersWithAlignments = Maps.newLinkedHashMap();

        orderHeadersWithAlignments.put(translationService.translate("orders.order.number.label", locale), HeaderAlignment.LEFT);
        orderHeadersWithAlignments.put(translationService.translate("orders.order.name.label", locale), HeaderAlignment.LEFT);
        orderHeadersWithAlignments.put(translationService.translate("orders.order.product.label", locale), HeaderAlignment.LEFT);
        orderHeadersWithAlignments.put(
                translationService.translate("materialRequirements.materialRequirement.report.order.plannedQuantity", locale),
                HeaderAlignment.RIGHT);
        orderHeadersWithAlignments.put(
                translationService.translate("materialRequirements.materialRequirement.report.product.unit", locale),
                HeaderAlignment.LEFT);

        addOrderSeries(document, materialRequirement, orderHeadersWithAlignments);

        document.add(
                new Paragraph(translationService.translate("materialRequirements.materialRequirement.report.paragrah2", locale),
                        FontUtils.getDejavuBold11Dark()));

        if (materialRequirement.getBooleanField(MaterialRequirementFields.INCLUDE_WAREHOUSE)
                || materialRequirement.getBooleanField(MaterialRequirementFields.INCLUDE_START_DATE_ORDER)) {
            addGroupedDataSeries(document, materialRequirement, locale);
        } else {
            Map<String, HeaderAlignment> productHeadersWithAlignments = Maps.newLinkedHashMap();
            productHeadersWithAlignments.put(translationService.translate("basic.product.number.label", locale),
                    HeaderAlignment.LEFT);
            productHeadersWithAlignments.put(translationService.translate("basic.product.name.label", locale),
                    HeaderAlignment.LEFT);
            productHeadersWithAlignments.put(
                    translationService.translate("technologies.technologyOperationComponent.quantity.label", locale),
                    HeaderAlignment.RIGHT);
            productHeadersWithAlignments.put(
                    translationService.translate("materialRequirements.materialRequirement.report.product.unit", locale),
                    HeaderAlignment.LEFT);

            addDataSeries(document, materialRequirement, productHeadersWithAlignments);
        }
    }

    private void addGroupedDataSeries(final Document document, final Entity materialRequirement, final Locale locale)
            throws DocumentException {
        Map<WarehouseDateKey, List<MaterialRequirementEntry>> entriesMap = materialRequirementDataService
                .getGroupedData(materialRequirement);

        boolean includeWarehouse = materialRequirement.getBooleanField(MaterialRequirementFields.INCLUDE_WAREHOUSE);
        boolean includeStartDateOrder = materialRequirement.getBooleanField(MaterialRequirementFields.INCLUDE_START_DATE_ORDER);
        boolean showCurrentStockLevel = materialRequirement.getBooleanField(MaterialRequirementFields.SHOW_CURRENT_STOCK_LEVEL);

        List<Integer> defaultOrderHeaderColumnWidth = Lists.newArrayList();

        Map<String, HeaderAlignment> headersWithAlignments = Maps.newLinkedHashMap();

        if (includeWarehouse) {
            defaultOrderHeaderColumnWidth.add(20);
            headersWithAlignments.put(
                    translationService.translate("materialRequirements.materialRequirement.report.warehouse", locale),
                    HeaderAlignment.LEFT);
        }

        if (includeStartDateOrder) {
            defaultOrderHeaderColumnWidth.add(20);
            headersWithAlignments.put(
                    translationService.translate("materialRequirements.materialRequirement.report.startDateOrder", locale),
                    HeaderAlignment.LEFT);
        }

        defaultOrderHeaderColumnWidth.add(30);
        headersWithAlignments.put(translationService.translate("basic.product.number.label", locale), HeaderAlignment.LEFT);
        defaultOrderHeaderColumnWidth.add(30);
        headersWithAlignments.put(translationService.translate("basic.product.name.label", locale), HeaderAlignment.LEFT);
        defaultOrderHeaderColumnWidth.add(19);

        headersWithAlignments.put(
                translationService.translate("technologies.technologyOperationComponent.quantity.label", locale),
                HeaderAlignment.RIGHT);
        defaultOrderHeaderColumnWidth.add(9);

        headersWithAlignments.put(
                translationService.translate("materialRequirements.materialRequirement.report.product.unit", locale),
                HeaderAlignment.LEFT);
        if (showCurrentStockLevel) {
            defaultOrderHeaderColumnWidth.add(23);
            headersWithAlignments.put(
                    translationService.translate("materialRequirements.materialRequirement.report.currentStock", locale),
                    HeaderAlignment.RIGHT);
        }

        List<String> headers = Lists.newLinkedList(headersWithAlignments.keySet());

        int[] defaultOrderHeaderColumnWidthInt = new int[defaultOrderHeaderColumnWidth.size()];
        for (int i = 0; i < defaultOrderHeaderColumnWidth.size(); i++) {
            defaultOrderHeaderColumnWidthInt[i] = defaultOrderHeaderColumnWidth.get(i);
        }

        PdfPTable table = pdfHelper.createTableWithHeader(headersWithAlignments.size(), headers, true,
                defaultOrderHeaderColumnWidthInt, headersWithAlignments);

        List<WarehouseDateKey> keys = Lists.newArrayList(entriesMap.keySet());

        if (includeWarehouse) {
            keys.sort(Comparator.comparing(WarehouseDateKey::getWarehouseNumber).thenComparing(WarehouseDateKey::getDate));
        } else {
            keys.sort(Comparator.comparing(WarehouseDateKey::getDate));
        }

        Map<Long, Map<Long, BigDecimal>> quantitiesInStock = Maps.newHashMap();

        if (showCurrentStockLevel) {
            List<MaterialRequirementEntry> entries = Lists.newArrayList();

            for (WarehouseDateKey key : keys) {
                if (Objects.nonNull(key.getWarehouseId())) {
                    entries.addAll(entriesMap.get(key));
                }
            }

            quantitiesInStock = materialRequirementDataService.getQuantitiesInStock(entries);
        }

        String actualWarehouse = "";
        Date actualDate = null;

        int keyIndex = 0;
        for (WarehouseDateKey key : keys) {
            keyIndex += 1;

            List<MaterialRequirementEntry> materials = entriesMap.get(key);
            Map<String, MaterialRequirementEntry> neededProductQuantities = getNeededProductQuantities(materials);
            List<String> materialKeys = Lists.newArrayList(neededProductQuantities.keySet());

            materialKeys.sort(Comparator.naturalOrder());

            int index = 0;
            for (String materialKey : materialKeys) {
                index += 1;

                MaterialRequirementEntry material = neededProductQuantities.get(materialKey);

                table.getDefaultCell().disableBorderSide(PdfCell.BOTTOM);
                table.getDefaultCell().disableBorderSide(PdfCell.TOP);

                if (index == materialKeys.size() && keyIndex == keys.size()) {
                    table.getDefaultCell().enableBorderSide(PdfCell.BOTTOM);
                }

                boolean fillDateIfWarehouseChanged = false;

                if (includeWarehouse) {
                    if (!actualWarehouse.equals(key.getWarehouseNumber())) {
                        table.getDefaultCell().enableBorderSide(PdfCell.TOP);
                        table.addCell(new Phrase(key.getWarehouseNumber(), FontUtils.getDejavuRegular7Dark()));

                        actualWarehouse = key.getWarehouseNumber();

                        fillDateIfWarehouseChanged = true;
                    } else {
                        table.addCell(new Phrase("", FontUtils.getDejavuRegular7Dark()));
                    }
                }

                if (includeStartDateOrder) {
                    Date date = key.getDate();

                    if (Objects.isNull(actualDate) || !actualDate.equals(date) || fillDateIfWarehouseChanged) {
                        if (Objects.isNull(date)) {
                            actualDate = null;

                            table.getDefaultCell().enableBorderSide(PdfCell.TOP);
                            table.addCell(new Phrase("", FontUtils.getDejavuRegular7Dark()));
                        } else {
                            actualDate = new Date(date.getTime());

                            table.getDefaultCell().enableBorderSide(PdfCell.TOP);
                            table.addCell(new Phrase(DateUtils.toDateString(actualDate), FontUtils.getDejavuRegular7Dark()));
                        }
                    } else {
                        table.addCell(new Phrase("", FontUtils.getDejavuRegular7Dark()));
                    }
                }

                table.getDefaultCell().enableBorderSide(PdfCell.BOTTOM);
                table.getDefaultCell().enableBorderSide(PdfCell.TOP);

                table.addCell(new Phrase(material.getNumber(), FontUtils.getDejavuRegular7Dark()));
                table.addCell(new Phrase(material.getName(), FontUtils.getDejavuRegular7Dark()));
                table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(new Phrase(numberService.format(material.getPlannedQuantity()), FontUtils.getDejavuBold7Dark()));
                table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_LEFT);

                String unit = material.getUnit();
                if (Objects.isNull(unit)) {
                    table.addCell(new Phrase("", FontUtils.getDejavuRegular7Dark()));
                } else {
                    table.addCell(new Phrase(unit, FontUtils.getDejavuRegular7Dark()));
                }

                if (showCurrentStockLevel) {
                    if (Objects.nonNull(key.getWarehouseId())) {
                        table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_RIGHT);
                        table.addCell(new Phrase(
                                numberService.format(materialRequirementDataService.getQuantity(quantitiesInStock, material)),
                                FontUtils.getDejavuBold7Dark()));
                        table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_LEFT);
                    } else {
                        table.addCell(new Phrase(numberService.format(BigDecimal.ZERO), FontUtils.getDejavuRegular7Dark()));
                    }
                }
            }
        }

        document.add(table);
    }

    private Map<String, MaterialRequirementEntry> getNeededProductQuantities(final List<MaterialRequirementEntry> materials) {
        Map<String, MaterialRequirementEntry> neededProductQuantities = Maps.newHashMap();

        for (MaterialRequirementEntry mre : materials) {
            String product = mre.getNumber();

            if (neededProductQuantities.containsKey(product)) {
                BigDecimal pQ = mre.getPlannedQuantity().add(neededProductQuantities.get(product).getPlannedQuantity());
                mre.setPlannedQuantity(pQ);
            }

            neededProductQuantities.put(product, mre);
        }

        return neededProductQuantities;
    }

    private void addPanel(final Document document, final Entity materialRequirement, final Locale locale)
            throws DocumentException {
        PdfPTable panelTable = pdfHelper.createPanelTable(2);

        pdfHelper.addTableCellAsOneColumnTable(panelTable,
                translationService.translate("materialRequirements.materialRequirement.report.panel.number", locale),
                materialRequirement.getStringField(MaterialRequirementFields.NUMBER));
        pdfHelper.addTableCellAsOneColumnTable(panelTable,
                translationService.translate("materialRequirements.materialRequirement.report.panel.name", locale),
                StringUtils.isEmpty(materialRequirement.getStringField(MaterialRequirementFields.NAME)) ? ""
                        : materialRequirement.getStringField(MaterialRequirementFields.NAME));
        pdfHelper.addTableCellAsOneColumnTable(panelTable,
                translationService.translate("materialRequirements.materialRequirement.report.panel.mrpAlgorithm", locale),
                translationService.translate("materialRequirements.materialRequirement.mrpAlgorithm.value."
                        + materialRequirement.getStringField(MaterialRequirementFields.MRP_ALGORITHM), locale));
        pdfHelper.addTableCellAsOneColumnTable(panelTable, "", "");

        panelTable.setSpacingAfter(20);
        panelTable.setSpacingBefore(20);

        document.add(panelTable);
    }

    private void addDataSeries(final Document document, final Entity materialRequirement,
            final Map<String, HeaderAlignment> headersWithAlignments) throws DocumentException {
        List<Entity> orders = materialRequirement.getManyToManyField(MaterialRequirementFields.ORDERS);

        MrpAlgorithm algorithm = MrpAlgorithm
                .parseString(materialRequirement.getStringField(MaterialRequirementFields.MRP_ALGORITHM));

        Map<Long, BigDecimal> neededProductQuantities = basicProductionCountingService.getNeededProductQuantities(orders,
                algorithm);

        List<String> headers = Lists.newLinkedList(headersWithAlignments.keySet());

        PdfPTable table = pdfHelper.createTableWithHeader(headersWithAlignments.size(), headers, true,
                defaultOrderHeaderColumnWidth, headersWithAlignments);

        List<Entity> products = dataDefinitionService.get(BasicConstants.PLUGIN_IDENTIFIER, BasicConstants.MODEL_PRODUCT).find()
                .add(SearchRestrictions.in("id", neededProductQuantities.keySet())).list().getEntities();

        products.sort(Comparator.comparing(p -> p.getStringField(ProductFields.NUMBER)));

        for (Entity product : products) {
            table.addCell(new Phrase(product.getStringField(ProductFields.NUMBER), FontUtils.getDejavuRegular7Dark()));
            table.addCell(new Phrase(product.getStringField(ProductFields.NAME), FontUtils.getDejavuRegular7Dark()));
            table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(new Phrase(numberService.format(neededProductQuantities.get(product.getId())),
                    FontUtils.getDejavuBold7Dark()));
            table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_LEFT);

            String unit = product.getStringField(ProductFields.UNIT);
            if (Objects.isNull(unit)) {
                table.addCell(new Phrase("", FontUtils.getDejavuRegular7Dark()));
            } else {
                table.addCell(new Phrase(unit, FontUtils.getDejavuRegular7Dark()));
            }
        }

        document.add(table);
    }

    private void addOrderSeries(final Document document, final Entity materialRequirement,
            final Map<String, HeaderAlignment> headersWithAlignments) throws DocumentException {
        List<Entity> orders = materialRequirement.getManyToManyField(MaterialRequirementFields.ORDERS);

        Collections.sort(orders, new EntityOrderNumberComparator());

        List<String> headers = Lists.newLinkedList(headersWithAlignments.keySet());

        PdfPTable table = pdfHelper.createTableWithHeader(headersWithAlignments.size(), headers, true,
                defaultMatReqHeaderColumnWidth, headersWithAlignments);

        for (Entity order : orders) {
            table.addCell(new Phrase(order.getStringField(OrderFields.NUMBER), FontUtils.getDejavuRegular7Dark()));
            table.addCell(new Phrase(order.getStringField(OrderFields.NAME), FontUtils.getDejavuRegular7Dark()));

            Entity product = (Entity) order.getField(OrderFields.PRODUCT);

            if (Objects.isNull(product)) {
                BigDecimal plannedQuantity = order.getDecimalField(OrderFields.PLANNED_QUANTITY);
                plannedQuantity = Objects.isNull(plannedQuantity) ? BigDecimal.ZERO : plannedQuantity;

                table.addCell(new Phrase("", FontUtils.getDejavuRegular7Dark()));
                table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(new Phrase(numberService.format(plannedQuantity), FontUtils.getDejavuRegular7Dark()));
                table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(new Phrase("", FontUtils.getDejavuRegular7Dark()));
            } else {
                BigDecimal plannedQuantity = order.getDecimalField(OrderFields.PLANNED_QUANTITY);
                plannedQuantity = Objects.isNull(plannedQuantity) ? BigDecimal.ZERO : plannedQuantity;

                table.addCell(new Phrase(product.getStringField(ProductFields.NAME), FontUtils.getDejavuRegular7Dark()));
                table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(new Phrase(numberService.format(plannedQuantity), FontUtils.getDejavuRegular7Dark()));
                table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_LEFT);

                String unit = product.getStringField(ProductFields.UNIT);
                if (Objects.isNull(unit)) {
                    table.addCell(new Phrase("", FontUtils.getDejavuRegular7Dark()));
                } else {
                    table.addCell(new Phrase(unit, FontUtils.getDejavuRegular7Dark()));
                }
            }
        }

        document.add(table);
    }

    @Override
    public String getReportTitle(final Locale locale) {
        return translationService.translate("materialRequirements.materialRequirement.report.title", locale);
    }

}
