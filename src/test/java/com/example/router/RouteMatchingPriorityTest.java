package com.example.router;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static segments must beat parameter segments, and parameter segments must
 * beat the {@code /**} wildcard, including when registrations arrive in an
 * order that would let a wildcard "shadow" the more specific routes.
 */
class RouteMatchingPriorityTest {

  private Router router;
  private TestController controller;

  @BeforeEach
  void setUp() {
    router = new Router();
    controller = new TestController();
  }

  @Test
  @DisplayName("literal beats parameter at the same segment depth")
  void literalBeatsParameter() {
    router.register(HttpMethod.GET, "/users/{id}", controller, "getUser")
        .register(HttpMethod.GET, "/users/static-profile", controller, "staticProfile");

    assertThat(router.dispatch("GET /users/static-profile?role=USER"))
        .isEqualTo("static-profile");
    assertThat(router.dispatch("GET /users/7?role=ADMIN")).isEqualTo("user 7 role=ADMIN detail=null");
  }

  @Test
  @DisplayName("priority holds regardless of registration order")
  void priorityHoldsInReverseRegistrationOrder() {
    router.register(HttpMethod.GET, "/files/**", controller, "download")
        .register(HttpMethod.GET, "/files/meta", controller, "filesMeta")
        .register(HttpMethod.GET, "/files/{id}", controller, "fileId");

    assertThat(router.dispatch("GET /files/meta")).isEqualTo("files-meta");
    assertThat(router.dispatch("GET /files/42")).isEqualTo("file:42");
    assertThat(router.dispatch("GET /files/a/b/c.txt")).isEqualTo("download:a/b/c.txt");
  }

  @Test
  @DisplayName("parameter beats wildcard for the same depth")
  void parameterBeatsWildcard() {
    router.register(HttpMethod.GET, "/files/**", controller, "download")
        .register(HttpMethod.GET, "/files/{id}", controller, "fileId");

    assertThat(router.dispatch("GET /files/report.pdf")).isEqualTo("file:report.pdf");
    assertThat(router.dispatch("GET /files/dir/report.pdf")).isEqualTo("download:dir/report.pdf");
  }

  @Test
  @DisplayName("wildcard matches the empty remainder")
  void wildcardMatchesEmptyRemainder() {
    router.register(HttpMethod.GET, "/files/**", controller, "download");

    assertThat(router.dispatch("GET /files")).isEqualTo("download:");
  }

  @Test
  @DisplayName("a more specific deeper prefix wins over a shallower one")
  void deeperPrefixWins() {
    router.register(HttpMethod.GET, "/**", controller, "download")
        .register(HttpMethod.GET, "/users/{id}", controller, "getUser");

    assertThat(router.dispatch("GET /users/9?role=GUEST")).isEqualTo("user 9 role=GUEST detail=null");
  }

  @Test
  @DisplayName("HTTP method participates in selection: same path, different methods coexist")
  void methodDistinguishesRoutes() {
    router.register(HttpMethod.GET, "/users/{id}", controller, "getUser")
        .register(HttpMethod.POST, "/users", controller, "saveUser");

    assertThat(router.dispatch("POST /users")).isEqualTo("saved");
  }

  @Test
  @DisplayName("leftmost differing segment decides priority")
  void leftmostSegmentDecides() {
    Object paramFirst = new Object() {
      @SuppressWarnings("unused")
      public String handle(@com.example.router.annotation.PathParam("a") String a) {
        return "param-first:" + a;
      }
    };
    Object literalFirst = new Object() {
      @SuppressWarnings("unused")
      public String handle(@com.example.router.annotation.PathParam("b") String b) {
        return "literal-first:" + b;
      }
    };
    router.register(HttpMethod.GET, "/x/{a}/static", paramFirst, "handle")
        .register(HttpMethod.GET, "/x/static/{b}", literalFirst, "handle");

    // Both patterns match /x/static/static, but the second has a literal in
    // segment 1 and therefore wins regardless of registration order.
    assertThat(router.dispatch("GET /x/static/static")).isEqualTo("literal-first:static");
    assertThat(router.dispatch("GET /x/other/static")).isEqualTo("param-first:other");
  }
}
