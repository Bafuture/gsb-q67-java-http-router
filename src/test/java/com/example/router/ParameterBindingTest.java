package com.example.router;

import com.example.router.annotation.PathParam;
import com.example.router.annotation.QueryParam;
import com.example.router.exception.ParamBindingException;
import com.example.router.exception.RouteRegistrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParameterBindingTest {

  private Router router;
  private TestController controller;

  @BeforeEach
  void setUp() {
    router = new Router();
    controller = new TestController();
  }

  @Nested
  @DisplayName("successful conversions")
  class SuccessCases {

    @Test
    @DisplayName("path long + query enum + Boolean wrapper")
    void bindsPathAndQueryValues() {
      router.register(HttpMethod.GET, "/users/{id}", controller, "getUser");

      assertThat(router.dispatch("GET /users/42?role=ADMIN&detail=true"))
          .isEqualTo("user 42 role=ADMIN detail=true");
    }

    @Test
    @DisplayName("boolean is case-insensitive")
    void booleanCaseInsensitive() {
      router.register(HttpMethod.GET, "/users", controller, "listUsers");

      assertThat(router.dispatch("GET /users?page=3&active=TRUE"))
          .isEqualTo("users page=3 active=true");
    }

    @Test
    @DisplayName("optional query parameter defaults to null when absent")
    void optionalQueryParameterIsNull() {
      router.register(HttpMethod.GET, "/age", controller, "age");

      assertThat(router.dispatch("GET /age")).isEqualTo("age=null");
      assertThat(router.dispatch("GET /age?age=18")).isEqualTo("age=18");
    }

    @Test
    @DisplayName("ISO date and custom dd/MM/yyyy pattern")
    void dateTypes() {
      router.register(HttpMethod.GET, "/by-date", controller, "byDate");

      assertThat(router.dispatch("GET /by-date?day=2026-09-24&since=24/09/2026"))
          .isEqualTo("day=2026-09-24 since=2026-09-24");
    }

    @Test
    @DisplayName("time and instant ISO-8601")
    void timeAndInstant() {
      InstantController instantController = new InstantController();
      router.register(HttpMethod.GET, "/time", instantController, "at")
          .register(HttpMethod.GET, "/now", instantController, "instant");

      assertThat(router.dispatch("GET /time?t=13:45:30"))
          .isEqualTo("time=13:45:30 datetime=null");
      assertThat(router.dispatch("GET /now?i=2026-09-24T01:02:03Z"))
          .isEqualTo("instant=2026-09-24T01:02:03Z");
    }

    @Test
    @DisplayName("percent-encoded path and query values are decoded as UTF-8")
    void percentDecoding() {
      router.register(HttpMethod.GET, "/files/{id}", controller, "fileId");

      assertThat(router.dispatch("GET /files/a%20b?x=%E4%B8%AD%E6%96%87"))
          .isEqualTo("file:a b");
    }
  }

  @Nested
  @DisplayName("conversion failures are reported with name, location and target type")
  class FailureCases {

    @Test
    @DisplayName("non-numeric path parameter fails clearly")
    void nonNumericPathParam() {
      router.register(HttpMethod.GET, "/users/{id}", controller, "getUser");

      assertThatThrownBy(() -> router.dispatch("GET /users/abc?role=USER"))
          .isInstanceOf(ParamBindingException.class)
          .hasMessageContainingAll("path parameter 'id'", "abc", "long");
    }

    @Test
    @DisplayName("unknown enum constant lists allowed values")
    void invalidEnum() {
      router.register(HttpMethod.GET, "/users/{id}", controller, "getUser");

      assertThatThrownBy(() -> router.dispatch("GET /users/1?role=ROOT"))
          .isInstanceOf(ParamBindingException.class)
          .hasMessageContainingAll("query parameter 'role'", "ROOT",
              "ADMIN", "USER", "GUEST");
    }

    @Test
    @DisplayName("bad boolean fails clearly")
    void badBoolean() {
      router.register(HttpMethod.GET, "/users", controller, "listUsers");

      assertThatThrownBy(() -> router.dispatch("GET /users?page=1&active=yes"))
          .isInstanceOf(ParamBindingException.class)
          .hasMessageContainingAll("query parameter 'active'", "yes", "boolean");
    }

    @Test
    @DisplayName("missing required query parameter fails clearly")
    void missingRequiredQueryParam() {
      router.register(HttpMethod.GET, "/users", controller, "listUsers");

      assertThatThrownBy(() -> router.dispatch("GET /users?page=1"))
          .isInstanceOf(ParamBindingException.class)
          .hasMessageContaining("Query parameter 'active' is required");
    }

    @Test
    @DisplayName("malformed ISO date fails clearly")
    void badIsoDate() {
      router.register(HttpMethod.GET, "/by-date", controller, "byDate");

      assertThatThrownBy(() -> router.dispatch("GET /by-date?day=24-09-2026"))
          .isInstanceOf(ParamBindingException.class)
          .hasMessageContainingAll("query parameter 'day'", "24-09-2026", "LocalDate");
    }

    @Test
    @DisplayName("malformed custom pattern date fails clearly")
    void badCustomPatternDate() {
      router.register(HttpMethod.GET, "/by-date", controller, "byDate");

      assertThatThrownBy(() -> router.dispatch("GET /by-date?day=2026-09-24&since=2026-09-24"))
          .isInstanceOf(ParamBindingException.class)
          .hasMessageContainingAll("query parameter 'since'", "LocalDate");
    }

    @Test
    @DisplayName("malformed query percent-encoding fails")
    void badPercentEncoding() {
      router.register(HttpMethod.GET, "/users", controller, "listUsers");

      assertThatThrownBy(() -> router.dispatch("GET /users?page=1&active=%ZZ"))
          .isInstanceOf(ParamBindingException.class)
          .hasMessageContaining("Malformed percent-encoding");
    }
  }

  @Nested
  @DisplayName("signature validation at registration time")
  class RegistrationValidation {

    @Test
    @DisplayName("unknown @PathParam name is rejected immediately")
    void unknownPathVariable() {
      // fileId binds a parameter named 'id', which is absent from this pattern.
      assertThatThrownBy(() ->
          router.register(HttpMethod.GET, "/users/{userId}", controller, "fileId"))
          .isInstanceOf(RouteRegistrationException.class)
          .hasMessageContaining("@PathParam(\"id\")")
          .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("unsupported parameter type is rejected immediately")
    void unsupportedType() {
      Object bad = new Object() {
        @SuppressWarnings("unused")
        public String handle(@QueryParam("when") java.util.UUID uuid) {
          return uuid.toString();
        }
      };
      assertThatThrownBy(() -> router.register(HttpMethod.GET, "/x", bad, "handle"))
          .isInstanceOf(RouteRegistrationException.class)
          .hasMessageContaining("unsupported type");
    }

    @Test
    @DisplayName("optional primitive query parameter is rejected immediately")
    void optionalPrimitiveRejected() {
      Object bad = new Object() {
        @SuppressWarnings("unused")
        public String handle(@QueryParam(value = "page", required = false) int page) {
          return String.valueOf(page);
        }
      };
      assertThatThrownBy(() -> router.register(HttpMethod.GET, "/x", bad, "handle"))
          .isInstanceOf(RouteRegistrationException.class)
          .hasMessageContaining("optional")
          .hasMessageContaining("int");
    }

    @Test
    @DisplayName("custom pattern on Instant is rejected immediately")
    void instantPatternRejected() {
      Object bad = new Object() {
        @SuppressWarnings("unused")
        public String handle(@QueryParam(value = "i", pattern = "yyyy") Instant i) {
          return i.toString();
        }
      };
      assertThatThrownBy(() -> router.register(HttpMethod.GET, "/x", bad, "handle"))
          .isInstanceOf(RouteRegistrationException.class)
          .hasMessageContaining("Instant");
    }
  }

  /** Extra handler carrying time/instant parameters. */
  public static class InstantController {

    public String at(@QueryParam("t") LocalTime t,
                     @QueryParam(value = "dt", required = false) LocalDateTime dateTime) {
      return "time=" + t + " datetime=" + dateTime;
    }

    public String instant(@QueryParam("i") Instant instant) {
      return "instant=" + instant;
    }

    @SuppressWarnings("unused")
    public String unused(@PathParam("id") String id) {
      return id;
    }
  }
}
