package com.euronet.eft.portal.inventory.importer;

import java.util.List;

/**
 * One environment block of one sheet row, before it is stored.
 *
 * @param environment  normalised environment code from the column group header (SIT, UAT, PERF, PROD, ...)
 * @param repositories from an optional "Repository" column (comma or newline separated)
 */
public record ParsedDeployment(
    String owner,
    String application,
    String client,
    String environment,
    String contextName,
    String baseUrl,
    String tomcatVersion,
    String javaVersion,
    boolean tomcatAdminDone,
    boolean buildXmlDone,
    List<String> repositories) {
}
