package com.example.router;

import com.example.router.annotation.PathParam;
import com.example.router.exception.RouteRegistrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteRegistrationTest {

  private Router router;
  private TestController controller;

  @BeforeEach
  void setUp() {
    router = new Router();
    controller = new TestController();
  }

  @Test
  @DisplayName("same path + same HTTP method registered twice fails immediately")
  void duplicateSameMethodAndPathFails() {
    router.register(HttpMethod.GET, "/users/{id}", controller, "getUser");

    assertThatThrownBy(() ->
        router.register(HttpMethod.GET, "/users/{id}", controller, "fileId"))
        .isInstanceOf(RouteRegistrationException.class)
        .hasMessageContainingAll("already registered", "GET", "/users/{id}");
    assertThat(router.size()).isEqualTo(1);
  }

  @Test
  @DisplayName("same path with a different HTTP method is allowed")
  void samePathDifferentMethodAllowed() {
    router.register(HttpMethod.GET, "/users", controller, "listUsers");

    assertThatCode(() ->
        router.register(HttpMethod.POST, "/users", controller, "saveUser"))
        .doesNotThrowAnyException();
    assertThat(router.size()).isEqualTo(2);
  }

  @Test
  @DisplayName("trailing slash on registration is normalized once, not duplicated")
  void trailingSlashNormalizedAtRegistration() {
    router.register(HttpMethod.GET, "/users/", controller, "listUsers");

    assertThatThrownBy(() ->
        router.register(HttpMethod.GET, "/users", controller, "saveUser"))
        .isInstanceOf(RouteRegistrationException.class)
        .hasMessageContaining("already registered");
  }

  @Test
  @DisplayName("structurally identical patterns with different param names fail")
  void structurallyIdenticalPatternsFail() {
    router.register(HttpMethod.GET, "/users/{id}", controller, "fileId");

    assertThatThrownBy(() -> {
      Object other = new Object() {
        @SuppressWarnings("unused")
        public String handle(@PathParam("userId") String userId) {
          return userId;
        }
      };
      router.register(HttpMethod.GET, "/users/{userId}", other, "handle");
    }).isInstanceOf(RouteRegistrationException.class)
        .hasMessageContaining("structurally identical");
  }

  @Test
  @DisplayName("malformed patterns fail at registration")
  void malformedPatternsFail() {
    assertThatThrownBy(() -> router.register(HttpMethod.GET, "users", controller, "root"))
        .isInstanceOf(RouteRegistrationException.class)
        .hasMessageContaining("must start with '/'");
    assertThatThrownBy(() -> router.register(HttpMethod.GET, "/users//x", controller, "root"))
        .isInstanceOf(RouteRegistrationException.class)
        .hasMessageContaining("empty segments");
    assertThatThrownBy(() -> router.register(HttpMethod.GET, "/users/{}/x", controller, "root"))
        .isInstanceOf(RouteRegistrationException.class)
        .hasMessageContaining("must have a name");
    assertThatThrownBy(() -> router.register(HttpMethod.GET, "/**/x", controller, "root"))
        .isInstanceOf(RouteRegistrationException.class)
        .hasMessageContaining("last segment");
    assertThatThrownBy(() -> router.register(HttpMethod.GET, "/users/*", controller, "root"))
        .isInstanceOf(RouteRegistrationException.class)
        .hasMessageContaining("globs");
    assertThatThrownBy(() -> router.register(HttpMethod.GET, "/users/{id}/{id}", controller, "root"))
        .isInstanceOf(RouteRegistrationException.class)
        .hasMessageContaining("Duplicate path parameter");
  }

  @Test
  @DisplayName("unknown handler method fails immediately")
  void unknownHandlerFails() {
    assertThatThrownBy(() ->
        router.register(HttpMethod.GET, "/users", controller, "doesNotExist"))
        .isInstanceOf(RouteRegistrationException.class)
        .hasMessageContaining("not found");
  }

  @Test
  @DisplayName("duplicate parameter name within one pattern fails")
  void duplicateParamNameInPatternFails() {
    assertThatThrownBy(() ->
        router.register(HttpMethod.GET, "/a/{x}/b/{x}", controller, "root"))
        .isInstanceOf(RouteRegistrationException.class)
        .hasMessageContaining("Duplicate");
  }
}
