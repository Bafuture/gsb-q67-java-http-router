package com.example.router;

import com.example.router.exception.NoRouteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract for the two normalization-sensitive policies:
 * <ul>
 *   <li>case sensitivity — method names, literal path segments and enum
 *       values are case-sensitive (method enums accept standard casing but
 *       never fold path text);</li>
 *   <li>trailing slashes — only root {@code /} may end with a slash; every
 *       other trailing slash is an explicit client error, never a redirect
 *       or silent normalization.</li>
 * </ul>
 */
class CaseAndTrailingSlashTest {

  private Router router;
  private TestController controller;

  @BeforeEach
  void setUp() {
    router = new Router();
    controller = new TestController();
    router.register(HttpMethod.GET, "/", controller, "root")
        .register(HttpMethod.GET, "/users", controller, "listUsers")
        .register(HttpMethod.GET, "/users/{id}", controller, "getUser");
  }

  @Test
  @DisplayName("literal path segments are case-sensitive")
  void literalSegmentsAreCaseSensitive() {
    assertThat(router.dispatch("GET /users?page=1&active=false"))
        .isEqualTo("users page=1 active=false");

    assertThatThrownBy(() -> router.dispatch("GET /Users?page=1&active=false"))
        .isInstanceOf(NoRouteException.class)
        .hasMessageContaining("No route matches");
    assertThatThrownBy(() -> router.dispatch("GET /USERS?page=1&active=false"))
        .isInstanceOf(NoRouteException.class);
  }

  @Test
  @DisplayName("enum query values are case-sensitive")
  void enumValuesAreCaseSensitive() {
    assertThat(router.dispatch("GET /users/1?role=USER").toString()).contains("role=USER");
    assertThatThrownBy(() -> router.dispatch("GET /users/1?role=user"))
        .isInstanceOf(com.example.router.exception.ParamBindingException.class)
        .hasMessageContaining("query parameter 'role'");
  }

  @Test
  @DisplayName("HTTP method accepts upper and standard lower case")
  void methodNameCasing() {
    assertThat(HttpMethod.from("get")).isEqualTo(HttpMethod.GET);
    assertThat(HttpMethod.from("POST")).isEqualTo(HttpMethod.POST);
    assertThatThrownBy(() -> HttpMethod.from("pOsT"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("root path with slash is the only trailing slash allowed")
  void rootTrailingSlashAllowed() {
    assertThat(router.dispatch("GET /")).isEqualTo("root");
  }

  @Test
  @DisplayName("trailing slash on a non-root path is rejected")
  void trailingSlashRejected() {
    assertThatThrownBy(() -> router.dispatch("GET /users/"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("trailing slash");
    assertThatThrownBy(() -> router.dispatch("GET /users/42/"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("trailing slash");
  }

  @Test
  @DisplayName("empty path segments are rejected")
  void emptySegmentsRejected() {
    assertThatThrownBy(() -> router.dispatch("GET /users//42"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("empty segments");
  }

  @Test
  @DisplayName("a wrong HTTP method on an existing path exposes allowed methods")
  void methodNotAllowedExposesAllowHeaderData() {
    assertThatThrownBy(() -> router.dispatch("DELETE /users/42"))
        .isInstanceOf(NoRouteException.class)
        .satisfies(ex -> assertThat(((NoRouteException) ex).allowedMethods())
            .containsExactly("GET"));
  }
}
