package com.example.gsb.router;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DuplicateRouteTest {

    static class Controller {
        public String a() {
            return "a";
        }

        public String b() {
            return "b";
        }

        public String byId(@PathVariable("id") long id) {
            return "id";
        }

        public String byName(@PathVariable("name") String name) {
            return "name";
        }
    }

    private final Controller controller = new Controller();

    @Test
    void sameMethodAndPathFailsImmediately() {
        Router router = new Router().route("GET", "/users", controller, "a");
        assertThatThrownBy(() -> router.route("GET", "/users", controller, "b"))
                .isInstanceOf(DuplicateRouteException.class)
                .hasMessageContaining("GET /users");
    }

    @Test
    void sameShapeWithDifferentParamNamesConflicts() {
        Router router = new Router().route("GET", "/users/{id}", controller, "byId");
        assertThatThrownBy(() -> router.route("GET", "/users/{name}", controller, "byName"))
                .isInstanceOf(DuplicateRouteException.class);
    }

    @Test
    void samePathWithDifferentMethodIsAllowed() {
        Router router = new Router().route("GET", "/users", controller, "a");
        assertThatCode(() -> router.route("POST", "/users", controller, "b"))
                .doesNotThrowAnyException();
    }

    @Test
    void differentShapeIsAllowed() {
        Router router = new Router()
                .route("GET", "/users/{id}", controller, "byId")
                .route("GET", "/users/new", controller, "a");
        assertThatCode(() -> router.route("GET", "/users/{id}/posts", controller, "b"))
                .doesNotThrowAnyException();
    }

    @Test
    void trailingSlashVariantOfExistingRouteConflicts() {
        Router router = new Router().route("GET", "/users", controller, "a");
        assertThatThrownBy(() -> router.route("GET", "/users/", controller, "b"))
                .isInstanceOf(DuplicateRouteException.class);
    }
}
