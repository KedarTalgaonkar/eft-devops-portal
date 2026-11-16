package com.euronet.eft.portal.environment;

import com.euronet.eft.portal.common.EnvironmentCodes;
import com.euronet.eft.portal.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The list of environments and their display order. Unknown codes met during an import are registered
 * automatically, so a new column group in the workbook (e.g. "PERF" or "PROD") needs no code change.
 */
@Service
@Transactional(readOnly = true)
public class EnvironmentCatalog {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentCatalog.class);
    private final EnvironmentRepository repository;

    public EnvironmentCatalog(EnvironmentRepository repository) {
        this.repository = repository;
    }

    private static EnvironmentView view(DeliveryEnvironment e) {
        return new EnvironmentView(e.getCode(), e.getName(), e.getSortOrder(), e.getCategory(), e.isActive());
    }

    public List<EnvironmentView> list() {
        return repository.findAllByOrderBySortOrderAscCodeAsc().stream().map(EnvironmentCatalog::view).toList();
    }

    /**
     * Codes in display order (active ones only).
     */
    public List<String> activeCodes() {
        return repository.findAllByOrderBySortOrderAscCodeAsc().stream()
            .filter(DeliveryEnvironment::isActive)
            .map(DeliveryEnvironment::getCode)
            .toList();
    }

    /**
     * Orders codes by the catalogue's sort order; unknown codes go last, alphabetically.
     */
    public Comparator<String> order() {
        Map<String, Integer> sort = repository.findAll().stream()
            .collect(Collectors.toMap(DeliveryEnvironment::getCode, DeliveryEnvironment::getSortOrder));
        return Comparator.comparing((String c) -> sort.getOrDefault(c, Integer.MAX_VALUE)).thenComparing(Function.identity());
    }

    /**
     * Makes sure the environment exists, creating it after the last one when it is new. Returns its code.
     */
    @Transactional
    public String ensure(String raw) {
        String code = EnvironmentCodes.require(raw);
        if (!repository.existsById(code)) {
            int next = repository.findAll().stream().mapToInt(DeliveryEnvironment::getSortOrder).max().orElse(0) + 10;
            String category = code.startsWith("PROD") || code.equals("DR") ? "PROD" : "NON_PROD";
            repository.save(new DeliveryEnvironment(code, code, next, category));
            log.info("New environment registered: {} (order {})", code, next);
        }
        return code;
    }

    @Transactional
    public EnvironmentView update(String code, UpdateEnvironmentRequest request) {
        DeliveryEnvironment env = repository.findById(EnvironmentCodes.require(code))
            .orElseThrow(() -> new NotFoundException("Environment", code));
        if (request.name() != null && !request.name().isBlank()) {
            env.setName(request.name().strip());
        }
        if (request.sortOrder() != null) {
            env.setSortOrder(request.sortOrder());
        }
        if (request.category() != null) {
            if (!request.category().equals("PROD") && !request.category().equals("NON_PROD")) {
                throw new IllegalArgumentException("category must be PROD or NON_PROD");
            }
            env.setCategory(request.category());
        }
        if (request.active() != null) {
            env.setActive(request.active());
        }
        return view(env);
    }

    public record EnvironmentView(String code, String name, int sortOrder, String category, boolean active) {
    }

    public record UpdateEnvironmentRequest(String name, Integer sortOrder, String category, Boolean active) {
    }
}
