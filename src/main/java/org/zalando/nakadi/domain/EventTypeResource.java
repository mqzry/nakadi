package org.zalando.nakadi.domain;

import org.zalando.nakadi.plugin.api.authz.AuthorizationAttribute;
import org.zalando.nakadi.plugin.api.authz.AuthorizationService;
import org.zalando.nakadi.plugin.api.authz.Resource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EventTypeResource implements Resource {

    private final String name;
    private final String type;
    private final Map<AuthorizationService.Operation, List<AuthorizationAttribute>> attributes;

    public EventTypeResource(final String name,
                             final String type,
                             final Map<AuthorizationService.Operation, List<AuthorizationAttribute>> attributes) {
        this.name = name;
        this.type = type;
        this.attributes = attributes;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Optional<List<AuthorizationAttribute>> getAttributesForOperation(AuthorizationService.Operation operation) {
        return Optional.ofNullable(attributes.get(operation));
    }

}
