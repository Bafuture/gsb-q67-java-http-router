package com.example.gsb.router;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CaseAndTrailingSlashTest {

    static class Controller {
        public String users() {
            return "users";
        }

        public String root() {
            return "root";
        }
    }

    private Router router() {
        Controller controller = new Controller();
        return new Router()
                .route("GET", "/users", controller, "users")
                .route("GET", "/", controller, "root");
    }

    @Test
    void pathsAreCaseSensitive() {
        assertThat(router().handle("GET", "/Users").status()).isEqualTo(404);
        assertThat(router().handle("GET", "/USERS").status()).isEqualTo(404);
        assertThat(router().handle("GET", "/users").status()).isEqualTo(200);
    }

    @Test
    void httpMethodNamesAreCaseInsensitive() {
        assertThat(router().handle("get", "/users").status()).isEqualTo(200);
        assertThat(router().handle("Get", "/users").status()).isEqualTo(200);
    }

    @Test
    void trailingSlashIsIgnored() {
        assertThat(router().handle("GET", "/users/").body()).isEqualTo("users");
    }

    @Test
    void rootPathMatchesExactly() {
        assertThat(router().handle("GET", "/").body()).isEqualTo("root");
        assertThat(router().handle("GET", "").body()).isEqualTo("root");
    }

    @Test
    void patternRegisteredWithTrailingSlashMatchesBothForms() {
        Controller controller = new Controller();
        Router router = new Router().route("GET", "/users/", controller, "users");
        assertThat(router.handle("GET", "/users").body()).isEqualTo("users");
        assertThat(router.handle("GET", "/users/").body()).isEqualTo("users");
    }
}
