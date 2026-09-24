package com.example.router;

import com.example.router.exception.RouteRegistrationException;
import com.example.router.exception.RouteInvocationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WildcardRouteTest {

  private Router router;
  private TestController controller;

  @BeforeEach
  void setUp() {
    router = new Router();
    controller = new TestController();
    router.register(HttpMethod.GET, "/files/**", controller, "download");
  }

  @Test
  @DisplayName("captures a nested remainder including slash-separated segments")
  void capturesNestedRemainder() {
    assertThat(router.dispatch("GET /files/docs/spec/v1.pdf"))
        .isEqualTo("download:docs/spec/v1.pdf");
  }

  @Test
  @DisplayName("captures an empty remainder at the prefix itself")
  void capturesEmptyRemainder() {
    assertThat(router.dispatch("GET /files")).isEqualTo("download:");
  }

  @Test
  @DisplayName("does not match outside its prefix")
  void prefixIsRespected() {
    assertThatThrownBy(() -> router.dispatch("GET /other/x"))
        .isInstanceOf(com.example.router.exception.NoRouteException.class);
  }

  @Test
  @DisplayName("exceptions thrown by handlers are wrapped with the original cause")
  void handlerExceptionWrapped() {
    Object failing = new Object() {
      @SuppressWarnings("unused")
      public String handle() {
        throw new IllegalStateException("boom");
      }
    };
    router.register(HttpMethod.GET, "/boom/**", failing, "handle");

    assertThatThrownBy(() -> router.dispatch("GET /boom/x"))
        .isInstanceOf(RouteInvocationException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .rootCause().hasMessage("boom");
  }

  @Test
  @DisplayName("@WildcardParam requires a wildcard pattern and a String type")
  void wildcardBindingValidation() {
    Object bad = new Object() {
      @SuppressWarnings("unused")
      public String handle(@com.example.router.annotation.WildcardParam Integer rest) {
        return String.valueOf(rest);
      }
    };
    assertThatThrownBy(() -> router.register(HttpMethod.GET, "/x/**", bad, "handle"))
        .isInstanceOf(RouteRegistrationException.class)
        .hasMessageContaining("String");
    assertThatThrownBy(() -> router.register(HttpMethod.GET, "/x", controller, "download"))
        .isInstanceOf(RouteRegistrationException.class)
        .hasMessageContaining("wildcard");
  }
}
