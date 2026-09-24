package com.example.gsb.router;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A parsed route path pattern such as {@code /users/{id}} or {@code /files/**}.
 *
 * <p>Matching policy:
 * <ul>
 *   <li>Matching is case-sensitive: {@code /Users} does not match {@code /users}.</li>
 *   <li>A single trailing slash is ignored: {@code /users/} matches pattern {@code /users}.
 *       The root path {@code /} is a valid pattern on its own.</li>
 *   <li>{@code {name}} declares a path parameter capturing exactly one segment.</li>
 *   <li>{@code **} is only allowed as the last segment and captures zero or more
 *       remaining segments (joined with {@code /}).</li>
 * </ul>
 *
 * <p>Priority: static segments beat parameter segments, parameter segments beat the
 * wildcard. Comparison is done segment by segment from left to right.
 */
public final class PathPattern implements Comparable<PathPattern> {

    enum SegmentType {
        STATIC, PARAM, WILDCARD
    }

    record Segment(SegmentType type, String value) {
    }

    private final String source;
    private final List<Segment> segments;

    private PathPattern(String source, List<Segment> segments) {
        this.source = source;
        this.segments = segments;
    }

    public static PathPattern parse(String pattern) {
        if (pattern == null || pattern.isBlank() || !pattern.startsWith("/")) {
            throw new IllegalArgumentException("Pattern must start with '/': " + pattern);
        }
        List<Segment> segments = new ArrayList<>();
        for (String raw : splitPath(pattern)) {
            if (raw.equals("**")) {
                segments.add(new Segment(SegmentType.WILDCARD, "**"));
            } else if (raw.startsWith("{") && raw.endsWith("}") && raw.length() > 2) {
                String name = raw.substring(1, raw.length() - 1).trim();
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Empty path parameter name in: " + pattern);
                }
                segments.add(new Segment(SegmentType.PARAM, name));
            } else if (raw.contains("{") || raw.contains("}") || raw.contains("*")) {
                throw new IllegalArgumentException("Illegal segment '" + raw + "' in pattern: " + pattern);
            } else {
                segments.add(new Segment(SegmentType.STATIC, raw));
            }
        }
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).type() == SegmentType.WILDCARD && i != segments.size() - 1) {
                throw new IllegalArgumentException("Wildcard '**' must be the last segment: " + pattern);
            }
        }
        return new PathPattern(pattern, List.copyOf(segments));
    }

    /**
     * Splits a path into segments. Leading/trailing slashes and empty segments
     * produced by them are dropped, which implements the trailing-slash policy.
     */
    static List<String> splitPath(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }

    /**
     * Matches a concrete request path against this pattern.
     *
     * @return extracted path variables (the wildcard is stored under key {@code **}),
     *         or {@code null} when the path does not match
     */
    public Map<String, String> match(String path) {
        List<String> parts = splitPath(path);
        Map<String, String> vars = new HashMap<>();
        int i = 0;
        for (Segment segment : segments) {
            if (segment.type() == SegmentType.WILDCARD) {
                vars.put("**", String.join("/", parts.subList(i, parts.size())));
                return vars;
            }
            if (i >= parts.size()) {
                return null;
            }
            String part = parts.get(i);
            if (segment.type() == SegmentType.STATIC) {
                if (!segment.value().equals(part)) {
                    return null;
                }
            } else {
                vars.put(segment.value(), part);
            }
            i++;
        }
        return i == parts.size() ? vars : null;
    }

    /**
     * Structural equality used for duplicate detection: two patterns conflict when
     * they have the same shape, i.e. identical static segments and the same segment
     * kinds in every position. Parameter names are irrelevant, so
     * {@code /users/{id}} conflicts with {@code /users/{name}}.
     */
    public boolean conflictsWith(PathPattern other) {
        if (segments.size() != other.segments.size()) {
            return false;
        }
        for (int i = 0; i < segments.size(); i++) {
            Segment a = segments.get(i);
            Segment b = other.segments.get(i);
            if (a.type() != b.type()) {
                return false;
            }
            if (a.type() == SegmentType.STATIC && !a.value().equals(b.value())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Orders patterns by matching priority, most specific first. Compared segment
     * by segment: STATIC &lt; PARAM &lt; WILDCARD. A pattern that ends where the other
     * continues with a wildcard wins; otherwise the longer pattern is considered
     * more specific. Ties are broken by the source string for a stable order.
     */
    @Override
    public int compareTo(PathPattern other) {
        int common = Math.min(segments.size(), other.segments.size());
        for (int i = 0; i < common; i++) {
            Segment a = segments.get(i);
            Segment b = other.segments.get(i);
            if (a.type() != b.type()) {
                return a.type().compareTo(b.type());
            }
            if (a.type() == SegmentType.STATIC) {
                int byName = a.value().compareTo(b.value());
                if (byName != 0) {
                    return byName;
                }
            }
        }
        if (segments.size() != other.segments.size()) {
            boolean thisLonger = segments.size() > other.segments.size();
            Segment next = thisLonger ? segments.get(common) : other.segments.get(common);
            if (next.type() == SegmentType.WILDCARD) {
                // The shorter pattern wins over one that only continues with '**'.
                return thisLonger ? 1 : -1;
            }
            return thisLonger ? -1 : 1;
        }
        return source.compareTo(other.source);
    }

    public String source() {
        return source;
    }

    @Override
    public String toString() {
        return source;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PathPattern other && source.equals(other.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source);
    }
}
