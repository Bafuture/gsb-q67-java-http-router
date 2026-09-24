package com.example.gsb.router;

/**
 * The outcome of dispatching a request through the router.
 *
 * <p>Status codes produced by the router itself: 200 on success, 204 when the
 * handler returns {@code void}, 400 on parameter binding failure, 404 when no
 * route matches, 405 when the path exists but not for the requested method,
 * 500 when the handler throws.
 */
public record HttpResponse(int status, String body) {

    public static HttpResponse ok(Object body) {
        return new HttpResponse(200, body == null ? "" : String.valueOf(body));
    }

    public static HttpResponse noContent() {
        return new HttpResponse(204, "");
    }

    public static HttpResponse error(int status, String message) {
        return new HttpResponse(status, message);
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Override
    public String toString() {
        return status + " " + body;
    }
}
