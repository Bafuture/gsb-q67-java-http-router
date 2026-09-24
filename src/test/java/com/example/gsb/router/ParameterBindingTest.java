package com.example.gsb.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParameterBindingTest {

    enum Role {
        ADMIN, USER, GUEST
    }

    static class Controller {
        public String find(@PathVariable("id") long id,
                           @RequestParam("verbose") boolean verbose) {
            return "id=" + id + ",verbose=" + verbose;
        }

        public String byRole(@RequestParam("role") Role role) {
            return "role=" + role;
        }

        public String onDate(@RequestParam("date") LocalDate date) {
            return "date=" + date;
        }

        public String at(@RequestParam("at") LocalDateTime at) {
            return "at=" + at;
        }

        public String optional(@RequestParam(value = "q", required = false) String q) {
            return "q=" + q;
        }

        public String unannotated(String value) {
            return value;
        }
    }

    private Router router;
    private final Controller controller = new Controller();

    @BeforeEach
    void setUp() {
        router = new Router()
                .route("GET", "/users/{id}", controller, "find")
                .route("GET", "/roles", controller, "byRole")
                .route("GET", "/date", controller, "onDate")
                .route("GET", "/time", controller, "at")
                .route("GET", "/search", controller, "optional");
    }

    @Test
    void bindsPathAndQueryParams() {
        HttpResponse response = router.handle("GET", "/users/42?verbose=true");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("id=42,verbose=true");
    }

    @Test
    void bindsEnum() {
        assertThat(router.handle("GET", "/roles?role=ADMIN").body()).isEqualTo("role=ADMIN");
    }

    @Test
    void bindsIsoDateAndDateTime() {
        assertThat(router.handle("GET", "/date?date=2024-02-29").body()).isEqualTo("date=2024-02-29");
        assertThat(router.handle("GET", "/time?at=2024-02-29T10:15:30").body())
                .isEqualTo("at=2024-02-29T10:15:30");
    }

    @Test
    void optionalQueryParamBindsNullWhenMissing() {
        assertThat(router.handle("GET", "/search").body()).isEqualTo("q=null");
    }

    @Test
    void invalidLongReturns400WithClearMessage() {
        HttpResponse response = router.handle("GET", "/users/abc?verbose=true");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body())
                .contains("'id'").contains("'abc'").contains("long");
    }

    @Test
    void invalidEnumReturns400ListingValidValues() {
        HttpResponse response = router.handle("GET", "/roles?role=SUPERUSER");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body())
                .contains("'role'").contains("SUPERUSER")
                .contains("ADMIN").contains("USER").contains("GUEST");
    }

    @Test
    void invalidDateReturns400() {
        HttpResponse response = router.handle("GET", "/date?date=29-02-2024");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).contains("'date'").contains("LocalDate");
    }

    @Test
    void invalidBooleanReturns400() {
        HttpResponse response = router.handle("GET", "/users/1?verbose=yes");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).contains("'verbose'");
    }

    @Test
    void missingRequiredQueryParamReturns400() {
        HttpResponse response = router.handle("GET", "/users/1");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).contains("verbose");
    }

    @Test
    void unannotatedHandlerParameterIsRejectedAtRegistration() {
        assertThatThrownBy(() -> new Router().route("GET", "/bad", controller, "unannotated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@PathVariable");
    }
}
