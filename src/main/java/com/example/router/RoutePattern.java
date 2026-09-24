package com.example.router;

import com.example.router.exception.RouteRegistrationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parsed route path pattern.
 *
 * <p>A pattern is an absolute slash-separated path built from three segment
 * kinds:
 * <ul>
 *   <li><b>literal</b> — static text, e.g. {@code users};</li>
 *   <li><b>parameter</b> — {@code {name}}, matches exactly one path segment;</li>
 *   <li><b>wildcard</b> — {@code **}, allowed only as the final segment and
 *       matching zero or more remaining segments.</li>
 * </ul>
 *
 * <p>The raw pattern is normalized so it never ends with a slash (except the
 * root {@code /}); see {@link Router} for the trailing-slash request policy.
 */
public final class RoutePattern implements Comparable<RoutePattern> {

  /** Kind of a single pattern segment. Rank doubles as match specificity: higher wins. */
  public enum SegmentKind {
    WILDCARD(0),
    PARAM(1),
    LITERAL(2);

    private final int rank;

    SegmentKind(int rank) {
      this.rank = rank;
    }

    public int rank() {
      return rank;
    }
  }

  private record Segment(SegmentKind kind, String text) {
  }

  /** Capture-map key under which the {@code /**} remainder is stored. */
  public static final String WILDCARD_VARIABLE = "*";

  private final String raw;
  private final List<Segment> segments;
  private final boolean wildcard;

  private RoutePattern(String raw, List<Segment> segments, boolean wildcard) {
    this.raw = raw;
    this.segments = List.copyOf(segments);
    this.wildcard = wildcard;
  }

  /**
   * Parses a raw pattern such as {@code /users/{id}} or {@code /files/**}.
   *
   * @throws RouteRegistrationException if the pattern is malformed
   */
  public static RoutePattern parse(String rawPattern) {
    if (rawPattern == null || rawPattern.isEmpty()) {
      throw new RouteRegistrationException("Route path must not be null or empty");
    }
    if (!rawPattern.startsWith("/")) {
      throw new RouteRegistrationException(
          "Route path must start with '/': " + rawPattern);
    }
    String normalized = rawPattern;
    if (rawPattern.length() > 1 && rawPattern.endsWith("/")) {
      normalized = rawPattern.substring(0, rawPattern.length() - 1);
    }
    if ("/".equals(normalized)) {
      return new RoutePattern("/", List.of(), false);
    }
    String[] rawSegments = normalized.substring(1).split("/", -1);
    List<Segment> parsed = new ArrayList<>();
    List<String> paramNames = new ArrayList<>();
    boolean sawWildcard = false;
    for (int i = 0; i < rawSegments.length; i++) {
      String piece = rawSegments[i];
      if (piece.isEmpty()) {
        throw new RouteRegistrationException(
            "Route path must not contain empty segments ('//'): " + rawPattern);
      }
      if ("**".equals(piece)) {
        if (i != rawSegments.length - 1) {
          throw new RouteRegistrationException(
              "Wildcard '**' is only allowed as the last segment: " + rawPattern);
        }
        sawWildcard = true;
        parsed.add(new Segment(SegmentKind.WILDCARD, "**"));
        continue;
      }
      if (piece.contains("*")) {
        throw new RouteRegistrationException(
            "Single-segment '*' globs are not supported; use '{param}' or a "
                + "trailing '/**': " + rawPattern);
      }
      if (piece.startsWith("{") && piece.endsWith("}")) {
        if (piece.length() == 2) {
          throw new RouteRegistrationException(
              "Path parameter must have a name: " + rawPattern);
        }
        String name = piece.substring(1, piece.length() - 1);
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
          throw new RouteRegistrationException(
              "Invalid path parameter name '" + name + "' in: " + rawPattern);
        }
        if (paramNames.contains(name)) {
          throw new RouteRegistrationException(
              "Duplicate path parameter name '" + name + "' in: " + rawPattern);
        }
        paramNames.add(name);
        parsed.add(new Segment(SegmentKind.PARAM, name));
        continue;
      }
      if (piece.startsWith("{") || piece.endsWith("}")) {
        throw new RouteRegistrationException(
            "Malformed path parameter (use '{'name'}') in: " + rawPattern);
      }
      parsed.add(new Segment(SegmentKind.LITERAL, piece));
    }
    return new RoutePattern(normalized, parsed, sawWildcard);
  }

  /**
   * Matches this pattern against a request path. Matching is byte-for-byte
   * case-sensitive on literal segments; percent-decoding is applied to
   * captured parameter and wildcard values, not to literals.
   *
   * @param requestSegments path segments of the incoming request (already split)
   * @return captured variable name/value pairs in declaration order, or
   *     {@code null} when the path does not match
   */
  public Map<String, String> match(List<String> requestSegments) {
    int fixed = wildcard ? segments.size() - 1 : segments.size();
    if (requestSegments.size() < fixed || (!wildcard && requestSegments.size() != fixed)) {
      return null;
    }
    Map<String, String> captured = new LinkedHashMap<>();
    for (int i = 0; i < fixed; i++) {
      Segment expected = segments.get(i);
      String actual = requestSegments.get(i);
      switch (expected.kind()) {
        case LITERAL -> {
          if (!expected.text().equals(actual)) {
            return null;
          }
        }
        case PARAM -> captured.put(expected.text(), decode(actual));
        case WILDCARD -> throw new IllegalStateException("wildcard outside suffix");
      }
    }
    if (wildcard) {
      String remainder = String.join("/", requestSegments.subList(fixed, requestSegments.size()));
      captured.put(WILDCARD_VARIABLE, decode(remainder));
    }
    return captured;
  }

  private static String decode(String raw) {
    try {
      return java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return raw;
    }
  }

  /** Number of non-wildcard segments. */
  public int fixedSegmentCount() {
    return wildcard ? segments.size() - 1 : segments.size();
  }

  public boolean isWildcard() {
    return wildcard;
  }

  /** Names of all {@code {param}} segments, in path order. */
  public java.util.List<String> parameterNames() {
    java.util.List<String> names = new java.util.ArrayList<>();
    for (Segment segment : segments) {
      if (segment.kind() == SegmentKind.PARAM) {
        names.add(segment.text());
      }
    }
    return names;
  }

  public SegmentKind segmentKind(int index) {
    return segments.get(index).kind();
  }

  /**
   * Two patterns are structurally identical when they have the same shape
   * (equal literals and parameters/wildcards at the same positions), even if
   * the parameter names differ. Such patterns are indistinguishable to the
   * matcher and cannot be registered for the same HTTP method.
   */
  public boolean structurallyEquals(RoutePattern other) {
    if (this.segments.size() != other.segments.size()
        || this.wildcard != other.wildcard) {
      return false;
    }
    for (int i = 0; i < segments.size(); i++) {
      Segment a = this.segments.get(i);
      Segment b = other.segments.get(i);
      if (a.kind() != b.kind()) {
        return false;
      }
      if (a.kind() == SegmentKind.LITERAL && !a.text().equals(b.text())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Total ordering by routing priority, most specific first:
   * non-wildcard patterns precede wildcard patterns; more fixed segments win;
   * at the same depth literal segments beat parameters segment-by-segment;
   * raw pattern text and then the compared key break ties deterministically.
   */
  @Override
  public int compareTo(RoutePattern other) {
    if (this.wildcard != other.wildcard) {
      return this.wildcard ? 1 : -1;
    }
    int byCount = Integer.compare(other.fixedSegmentCount(), this.fixedSegmentCount());
    if (byCount != 0) {
      return byCount;
    }
    int limit = Math.min(this.segments.size(), other.segments.size());
    for (int i = 0; i < limit; i++) {
      int rankDiff = Integer.compare(
          other.segments.get(i).kind().rank(),
          this.segments.get(i).kind().rank());
      if (rankDiff != 0) {
        return rankDiff;
      }
    }
    return this.raw.compareTo(other.raw);
  }

  public String raw() {
    return raw;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RoutePattern that)) {
      return false;
    }
    return Objects.equals(raw, that.raw);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(raw);
  }

  @Override
  public String toString() {
    return raw;
  }
}
