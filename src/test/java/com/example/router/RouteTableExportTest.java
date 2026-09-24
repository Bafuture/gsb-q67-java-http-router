package com.example.router;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteTableExportTest {

  private Router router;
  private TestController controller;

  @BeforeEach
  void setUp() {
    router = new Router();
    controller = new TestController();
  }

  @Test
  @DisplayName("table is rendered in priority order with aligned columns")
  void tableInPriorityOrder() {
    router.register(HttpMethod.POST, "/files/{id}", controller, "fileId")
        .register(HttpMethod.GET, "/files/**", controller, "download")
        .register(HttpMethod.GET, "/files/meta", controller, "filesMeta")
        .register(HttpMethod.GET, "/users/{id}", controller, "getUser");

    String table = router.renderRouteTable();
    String[] lines = table.split("\\R");
    assertThat(lines[0]).startsWith("PRIORITY").contains("METHOD", "PATH", "HANDLER");
    assertThat(lines[1]).matches("-+  -+  -+  -+");

    // Order is pattern priority first, HTTP method as the tie-breaker:
    // the literal wins; among the identical-shape parameter routes POST < GET
    // alphabetically; the wildcard suffix comes last.
    assertThat(removePadding(lines[2])).isEqualTo("1|GET|/files/meta|TestController#filesMeta");
    assertThat(removePadding(lines[3])).isEqualTo("2|POST|/files/{id}|TestController#fileId");
    assertThat(removePadding(lines[4])).isEqualTo("3|GET|/users/{id}|TestController#getUser");
    assertThat(removePadding(lines[5])).isEqualTo("4|GET|/files/**|TestController#download");
  }

  private static String removePadding(String line) {
    return String.join("|", line.strip().split("\\s{2,}"));
  }

  @Test
  @DisplayName("routes() follows the same ordering as the table")
  void routesAccessorOrdering() {
    router.register(HttpMethod.GET, "/files/**", controller, "download")
        .register(HttpMethod.GET, "/files/{id}", controller, "fileId")
        .register(HttpMethod.GET, "/files/meta", controller, "filesMeta");

    assertThat(router.routes())
        .extracting(route -> route.pattern().raw())
        .containsExactly("/files/meta", "/files/{id}", "/files/**");
  }

  @Test
  @DisplayName("empty router renders only header and separator")
  void emptyTable() {
    String table = router.renderRouteTable();
    assertThat(table.split("\\R")).hasSize(2);
    assertThat(table).contains("PRIORITY");
  }

  @Test
  @DisplayName("priority sequence matches the documented static > param > wildcard rule")
  void documentedPriority() {
    router.register(HttpMethod.GET, "/files/**", controller, "download")
        .register(HttpMethod.GET, "/files/meta", controller, "filesMeta")
        .register(HttpMethod.GET, "/files/{id}", controller, "fileId");

    String table = router.renderRouteTable();
    int literal = table.indexOf("/files/meta");
    int param = table.indexOf("/files/{id}");
    int wildcard = table.indexOf("/files/**");
    assertThat(literal).isBetween(0, param);
    assertThat(param).isLessThan(wildcard);
  }
}
