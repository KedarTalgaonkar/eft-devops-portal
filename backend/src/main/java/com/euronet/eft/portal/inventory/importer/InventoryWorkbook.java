package com.euronet.eft.portal.inventory.importer;

import java.util.List;

/** Everything read from an inventory workbook. */
public record InventoryWorkbook(int sheetsRead, List<ParsedDeployment> deployments, List<String> warnings) {
}
