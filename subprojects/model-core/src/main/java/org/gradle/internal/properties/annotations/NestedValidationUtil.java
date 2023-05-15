/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.properties.annotations;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.NonNullApi;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.util.stream.Collectors;

import static org.gradle.internal.reflect.validation.Severity.WARNING;

/**
 * Utility methods for validating {@link org.gradle.api.tasks.Nested} properties.
 */
@NonNullApi
public class NestedValidationUtil  {
    /**
     * Validates that the {@link org.gradle.api.tasks.Nested} annotation
     * supports the given bean type.
     * <p>
     * Only types with annotated properties are supported. Not supported are
     * types from the Java SE API, except for types from
     * the {@code java.util.function} package.
     *
     * @param validationContext the validation context
     * @param propertyName the name of the property
     * @param beanType the type of the bean
     */
    public static void validateBeanType(
        TypeValidationContext validationContext,
        String propertyName,
        Class<?> beanType
    ) {
        if (!isSupportedType(beanType)) {
            validationContext.visitPropertyProblem(problem ->
                problem.withId(ValidationProblemId.NESTED_TYPE_UNSUPPORTED)
                    .reportAs(WARNING)
                    .forProperty(propertyName)
                    .withDescription(() -> "where nested type '" + beanType.getName() + "' is not supported")
                    .happensBecause("Nested types must declare annotated properties")
                    .addPossibleSolution("Declare annotated properties on the nested type, e.g. 'Provider<T>', 'Iterable<T>', or '<MapProperty<K, V>>', where 'T' and 'V' must have one or more annotated properties")
                    .documentedAt("validation_problems", "unsupported_nested_type")
            );
        }
    }

    private static final String SUPPORTED_PACKAGE_NAME = "java.util.function";

    private static boolean isJavaSE(Class<?> type) {
        return type.getName().startsWith("java.") || type.getName().startsWith("javax.");
    }
    private static boolean isSupportedType(Class<?> type) {
        return !isJavaSE(type) || type.getName().startsWith(SUPPORTED_PACKAGE_NAME);
    }

    /**
     * Validates that the {@link org.gradle.api.tasks.Nested} annotation
     * supports the given map key type.
     * @param validationContext the validation context
     * @param propertyName the name of the property
     * @param keyType the type of the map key
     */
    public static void validateKeyType(
        TypeValidationContext validationContext,
        String propertyName,
        Class<?> keyType
    ) {
        if (!SUPPORTED_KEY_TYPES.contains(keyType)) {
            validationContext.visitPropertyProblem(problem ->
                problem.withId(ValidationProblemId.NESTED_MAP_UNSUPPORTED_KEY_TYPE)
                    .reportAs(WARNING)
                    .forProperty(propertyName)
                    .withDescription(() -> "where key of nested map is of type '" + keyType.getName() + "'")
                    .happensBecause("Key of nested map must be one of the following types: " + getSupportedKeyTypes())
                    .addPossibleSolution("Change type of key to one of the following types: " + getSupportedKeyTypes())
                    .documentedAt("validation_problems", "unsupported_key_type_of_nested_map")
            );
        }
    }

    private static final ImmutableSet<Class<?>> SUPPORTED_KEY_TYPES = ImmutableSet.of(Enum.class, Integer.class, String.class);

    private static String getSupportedKeyTypes() {
        return SUPPORTED_KEY_TYPES.stream().map(cls -> "'" + cls.getSimpleName() + "'").collect(Collectors.joining(", "));
    }
}
