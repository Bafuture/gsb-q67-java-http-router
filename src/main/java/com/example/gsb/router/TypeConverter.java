package com.example.gsb.router;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Converts raw string parameter values into handler method parameter types.
 *
 * <p>Supported types: {@code String}, all primitive types and their wrappers,
 * {@code BigInteger}, {@code BigDecimal}, {@code UUID}, any {@code enum} (matched
 * case-sensitively against the constant names) and the common
 * {@code java.time} types ({@code LocalDate}, {@code LocalTime}, {@code LocalDateTime},
 * {@code OffsetDateTime}, {@code ZonedDateTime}, {@code Instant}), all parsed from
 * their ISO-8601 text form.
 *
 * <p>Booleans are strict: only {@code "true"} and {@code "false"} (case-insensitive)
 * are accepted.
 */
public final class TypeConverter {

    private static final Map<Class<?>, Function<String, Object>> CONVERTERS = new HashMap<>();

    static {
        register(String.class, s -> s);
        register(int.class, Integer::parseInt);
        register(Integer.class, Integer::parseInt);
        register(long.class, Long::parseLong);
        register(Long.class, Long::parseLong);
        register(short.class, Short::parseShort);
        register(Short.class, Short::parseShort);
        register(byte.class, Byte::parseByte);
        register(Byte.class, Byte::parseByte);
        register(double.class, Double::parseDouble);
        register(Double.class, Double::parseDouble);
        register(float.class, Float::parseFloat);
        register(Float.class, Float::parseFloat);
        register(boolean.class, TypeConverter::parseBoolean);
        register(Boolean.class, TypeConverter::parseBoolean);
        register(BigInteger.class, BigInteger::new);
        register(BigDecimal.class, BigDecimal::new);
        register(UUID.class, UUID::fromString);
        register(LocalDate.class, LocalDate::parse);
        register(LocalTime.class, LocalTime::parse);
        register(LocalDateTime.class, LocalDateTime::parse);
        register(OffsetDateTime.class, OffsetDateTime::parse);
        register(ZonedDateTime.class, ZonedDateTime::parse);
        register(Instant.class, Instant::parse);
    }

    private TypeConverter() {
    }

    private static void register(Class<?> type, Function<String, Object> converter) {
        CONVERTERS.put(type, converter);
    }

    private static Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("expected 'true' or 'false'");
    }

    public static boolean supports(Class<?> type) {
        return type.isEnum() || CONVERTERS.containsKey(type);
    }

    /**
     * Converts {@code rawValue} to {@code targetType}.
     *
     * @throws ParameterBindingException with a precise message when the value cannot be converted
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object convert(String parameterName, String rawValue, Class<?> targetType) {
        if (rawValue == null) {
            if (targetType.isPrimitive()) {
                throw new ParameterBindingException(
                        "Parameter '" + parameterName + "' is missing and cannot be bound to primitive "
                                + targetType.getSimpleName());
            }
            return null;
        }
        try {
            if (targetType.isEnum()) {
                return Enum.valueOf((Class<? extends Enum>) targetType, rawValue);
            }
            Function<String, Object> converter = CONVERTERS.get(targetType);
            if (converter == null) {
                throw new ParameterBindingException(
                        "Parameter '" + parameterName + "': unsupported target type " + targetType.getName());
            }
            return converter.apply(rawValue);
        } catch (ParameterBindingException e) {
            throw e;
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            throw new ParameterBindingException(errorMessage(parameterName, rawValue, targetType), e);
        }
    }

    private static String errorMessage(String parameterName, String rawValue, Class<?> targetType) {
        StringBuilder message = new StringBuilder("Failed to bind parameter '")
                .append(parameterName).append("': value '").append(rawValue)
                .append("' cannot be converted to ").append(targetType.getSimpleName());
        if (targetType.isEnum()) {
            message.append(". Valid values: ");
            Object[] constants = targetType.getEnumConstants();
            for (int i = 0; i < constants.length; i++) {
                if (i > 0) {
                    message.append(", ");
                }
                message.append(((Enum<?>) constants[i]).name());
            }
        }
        return message.toString();
    }
}
