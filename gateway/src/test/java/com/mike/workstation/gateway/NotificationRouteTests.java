package com.mike.workstation.gateway;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import java.util.List;

class NotificationRouteTests {
    @Test
    void onlySseRoutesDisableGlobalResponseTimeoutAndPreserveTokenRelayBoundary() throws Exception {
        var environment = new StandardEnvironment();
        var sources = new YamlPropertySourceLoader().load("gateway", new ClassPathResource("application.yaml"));
        sources.forEach(source -> environment.getPropertySources().addLast(source));
        List<RouteDefinition> routes = Binder.get(environment)
                .bind("spring.cloud.gateway.server.webflux.routes", Bindable.listOf(RouteDefinition.class)).get();
        var streams = routes.stream().filter(route -> route.getId().startsWith("workstation-notifications-")).toList();
        assertThat(streams).hasSize(2);
        assertThat(streams).allSatisfy(route -> {
            assertThat(route.getMetadata().get("response-timeout").toString()).isEqualTo("-1");
            assertThat(route.getPredicates().get(0).getArgs().values())
                    .contains("/api/notifications/stream", "/api/workspaces/*/notes/*/presence/stream");
        });
        var browser = streams.stream().filter(route -> route.getId().endsWith("browser")).findFirst().orElseThrow();
        assertThat(browser.getFilters()).extracting(filter -> filter.getName()).containsExactly("SaveSession", "TokenRelay");
        var bearer = streams.stream().filter(route -> route.getId().endsWith("bearer")).findFirst().orElseThrow();
        assertThat(bearer.getFilters()).isEmpty();
        assertThat(bearer.getPredicates()).extracting(predicate -> predicate.getName()).contains("Header");
        assertThat(routes.stream().filter(route -> !streams.contains(route)))
                .allSatisfy(route -> assertThat(route.getMetadata()).doesNotContainKey("response-timeout"));
    }
}
