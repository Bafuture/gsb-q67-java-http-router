package com.example.gsb.router;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RouteTableTest {

    static class Controller {
        public String newest() {
            return "new";
        }

        public String byId(@PathVariable("id") long id) {
            return "id";
        }

        public String everything(@PathVariable("**") String rest) {
            return "rest";
        }

        public String create() {
            return "created";
        }
    }

    @Test
    void tableListsRoutesOrderedByPriority() {
        Controller controller = new Controller();
        Router router = new Router()
                .route("GET", "/users/**", controller, "everything")
                .route("GET", "/users/{id}", controller, "byId")
                .route("POST", "/users/{id}", controller, "create")
                .route("GET", "/users/new", controller, "newest");

        String table = router.table();

        assertThat(table).contains("PRIORITY").contains("METHOD").contains("PATTERN").contains("HANDLER");
        assertThat(table).contains("GET").contains("/users/new").contains("Controller#newest");

        int staticRow = table.indexOf("/users/new");
        int paramRow = table.indexOf("/users/{id}");
        int wildcardRow = table.indexOf("/users/**");
        assertThat(staticRow).isLessThan(paramRow);
        assertThat(paramRow).isLessThan(wildcardRow);
    }

    @Test
    void tableContainsOneRowPerRoutePlusHeaderAndSeparator() {
        Controller controller = new Controller();
        Router router = new Router()
                .route("GET", "/a", controller, "newest")
                .route("DELETE", "/b/{id}", controller, "byId");

        String[] lines = router.table().split(System.lineSeparator());
        assertThat(lines).hasSize(4); // header + separator + 2 routes
        assertThat(lines[0]).startsWith("|").endsWith("|");
        assertThat(lines[1]).matches("\\|[-|]+\\|");
    }
}
