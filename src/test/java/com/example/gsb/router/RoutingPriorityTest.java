package com.example.gsb.router;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoutingPriorityTest {

    static class UserController {
        public String newest() {
            return "static:new";
        }

        public String byId(@PathVariable("id") long id) {
            return "param:" + id;
        }

        public String everything(@PathVariable("**") String rest) {
            return "wildcard:" + rest;
        }
    }

    static class FileController {
        public String download(@PathVariable("**") String path) {
            return "file:" + path;
        }
    }

    private Router router;

    @BeforeEach
    void setUp() {
        UserController users = new UserController();
        router = new Router()
                .route("GET", "/users/**", users, "everything")
                .route("GET", "/users/{id}", users, "byId")
                .route("GET", "/users/new", users, "newest")
                .route("GET", "/files/**", new FileController(), "download");
    }

    @Test
    void staticSegmentBeatsParamAndWildcard() {
        assertThat(router.handle("GET", "/users/new").body()).isEqualTo("static:new");
    }

    @Test
    void paramSegmentBeatsWildcard() {
        assertThat(router.handle("GET", "/users/42").body()).isEqualTo("param:42");
    }

    @Test
    void wildcardMatchesMultipleSegments() {
        assertThat(router.handle("GET", "/users/a/b/c").body()).isEqualTo("wildcard:a/b/c");
    }

    @Test
    void wildcardCapturesRemainingPath() {
        assertThat(router.handle("GET", "/files/docs/2024/report.pdf").body())
                .isEqualTo("file:docs/2024/report.pdf");
    }

    @Test
    void wildcardMatchesZeroSegments() {
        assertThat(router.handle("GET", "/files").body()).isEqualTo("file:");
    }

    @Test
    void paramDoesNotMatchMultipleSegments() {
        // /users/{id} must not swallow /users/1/2; the wildcard route serves it instead.
        assertThat(router.handle("GET", "/users/1/2").body()).isEqualTo("wildcard:1/2");
    }

    @Test
    void matchReturnsExtractedPathVariables() {
        RouteMatch match = router.match(HttpMethod.GET, "/users/7").orElseThrow();
        assertThat(match.pathVariables()).containsEntry("id", "7");
        assertThat(match.route().pattern().source()).isEqualTo("/users/{id}");
    }

    @Test
    void unknownPathReturns404() {
        HttpResponse response = router.handle("GET", "/nothing/here");
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.body()).contains("No route");
    }

    @Test
    void knownPathWithWrongMethodReturns405() {
        HttpResponse response = router.handle("POST", "/users/new");
        assertThat(response.status()).isEqualTo(405);
    }
}
