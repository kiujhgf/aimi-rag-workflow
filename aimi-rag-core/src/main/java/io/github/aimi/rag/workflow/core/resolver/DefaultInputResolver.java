package io.github.aimi.rag.workflow.core.resolver;

import io.github.aimi.rag.workflow.core.exception.ResolveInputException;
import io.github.aimi.rag.workflow.core.model.FlowContext;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.Modifier;
import java.util.Collection;

public class DefaultInputResolver<I> implements InputResolver<I> {

    protected final Class<I> type;
    protected final String stepName;

    public DefaultInputResolver(Class<I> type, String stepName) {
        this.type = type;
        this.stepName = stepName;
    }

    @Override
    public I resolve(FlowContext context) throws ResolveInputException {
        if (type == null) {
            throw new ResolveInputException(stepName, "type", "Type cannot be null");
        }

        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            return resolveByType(context);
        }

        return resolveByFields(context);
    }

    @SuppressWarnings("unchecked")
    protected I resolveByType(FlowContext context) throws ResolveInputException {
        String[] candidateKeys = generateCandidateKeys(type);

        for (String key : candidateKeys) {
            Object value = context.get(key);
            if (value != null && type.isInstance(value)) {
                return (I) value;
            }
        }

        throw new ResolveInputException(stepName, String.join(", ", candidateKeys),
                "No input found in FlowContext for type: " + type.getSimpleName());
    }

    protected I resolveByFields(FlowContext context) throws ResolveInputException {
        try {
            I instance = type.getDeclaredConstructor().newInstance();
            Field[] fields = type.getDeclaredFields();

            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);
                String fieldName = field.getName();
                Class<?> fieldType = field.getType();

                Object value = resolveFieldValue(context, fieldName, fieldType);
                if (value != null) {
                    field.set(instance, value);
                }
            }

            return instance;
        } catch (NoSuchMethodException e) {
            return resolveByType(context);
        } catch (Exception e) {
            throw new ResolveInputException(stepName, type.getSimpleName(), e);
        }
    }

    protected Object resolveFieldValue(FlowContext context, String fieldName, Class<?> fieldType) {
        Object value = context.get(fieldName);
        if (value != null && fieldType.isInstance(value)) {
            return value;
        }
        return null;
    }

    private String[] generateCandidateKeys(Class<?> type) {
        String simpleName = type.getSimpleName();
        String lowerFirst = simpleName.substring(0, 1).toLowerCase() + simpleName.substring(1);

        return new String[]{
                lowerFirst,
                simpleName,
                simpleName.toLowerCase(),
                lowerFirst + "s",
                simpleName + "s",
                "input",
                "data"
        };
    }

    protected static <T> T get(FlowContext context, String key, Class<T> type) throws ResolveInputException {
        Object value = context.get(key);
        if (value == null) {
            throw new ResolveInputException("util", key, "Key '" + key + "' not found in FlowContext");
        }
        if (!type.isInstance(value)) {
            throw new ResolveInputException("util", key,
                    "Expected type: " + type.getSimpleName() +
                            ", but got: " + value.getClass().getSimpleName());
        }
        return type.cast(value);
    }

    protected static <T> T get(FlowContext context, String key, Class<T> rawType, Type elementType) throws ResolveInputException {
        Object value = context.get(key);
        if (value == null) {
            throw new ResolveInputException("util", key, "Key '" + key + "' not found in FlowContext");
        }
        if (!rawType.isInstance(value)) {
            throw new ResolveInputException("util", key,
                    "Expected type: " + rawType.getSimpleName() +
                            ", but got: " + value.getClass().getSimpleName());
        }
        if (elementType != null && value instanceof Collection<?> collection) {
            validateCollectionElementType(collection, elementType, key);
        }
        return rawType.cast(value);
    }

    private static void validateCollectionElementType(Collection<?> collection, Type elementType, String key) throws ResolveInputException {
        Class<?> elementClass = elementType instanceof Class<?> ? (Class<?>) elementType 
                : elementType instanceof ParameterizedType ? (Class<?>) ((ParameterizedType) elementType).getRawType() 
                : Object.class;
        
        for (Object item : collection) {
            if (item != null && !elementClass.isInstance(item)) {
                throw new ResolveInputException("util", key,
                        "Collection contains element of type: " + item.getClass().getSimpleName() +
                                ", expected: " + elementClass.getSimpleName());
            }
        }
    }

    protected static <T> T getOrDefault(FlowContext context, String key, Class<T> type, T defaultValue) {
        Object value = context.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!type.isInstance(value)) {
            return defaultValue;
        }
        return type.cast(value);
    }

    protected static boolean has(FlowContext context, String key) {
        return context.get(key) != null;
    }

    public static class ByKeyResolver<I> extends DefaultInputResolver<I> {

        private final String key;
        private final Class<?> rawType;
        private final Type elementType;

        public ByKeyResolver(String key, Class<?> rawType, String stepName) {
            this(key, rawType, null, stepName);
        }

        public ByKeyResolver(String key, Class<?> rawType, Type elementType, String stepName) {
            super(null, stepName);
            this.key = key;
            this.rawType = rawType;
            this.elementType = elementType;
        }

        @SuppressWarnings("unchecked")
        @Override
        public I resolve(FlowContext context) throws ResolveInputException {
            if (elementType != null) {
                return (I) get(context, key, rawType, elementType);
            }
            return (I) get(context, key, rawType);
        }
    }

    public static class ByKeyOrDefaultResolver<I> extends DefaultInputResolver<I> {

        private final String key;
        private final Class<I> type;
        private final I defaultValue;

        public ByKeyOrDefaultResolver(String key, Class<I> type, I defaultValue) {
            super(null, "util");
            this.key = key;
            this.type = type;
            this.defaultValue = defaultValue;
        }

        @Override
        public I resolve(FlowContext context) throws ResolveInputException {
            return getOrDefault(context, key, type, defaultValue);
        }
    }

    public static class ByMultipleKeysResolver<I> extends DefaultInputResolver<I> {

        private final String[] keys;
        private final Class<?> rawType;
        private final Type elementType;

        public ByMultipleKeysResolver(String[] keys, Class<?> rawType, String stepName) {
            this(keys, rawType, null, stepName);
        }

        public ByMultipleKeysResolver(String[] keys, Class<?> rawType, Type elementType, String stepName) {
            super(null, stepName);
            this.keys = keys;
            this.rawType = rawType;
            this.elementType = elementType;
        }

        @SuppressWarnings("unchecked")
        @Override
        public I resolve(FlowContext context) throws ResolveInputException {
            for (String key : keys) {
                Object value = getOrDefault(context, key, rawType, elementType, null);
                if (value != null) {
                    return (I) value;
                }
            }
            throw new ResolveInputException(stepName, String.join(", ", keys), "No input found in FlowContext");
        }
    }

    protected static <T> T getOrDefault(FlowContext context, String key, Class<T> type, Type elementType, T defaultValue) {
        Object value = context.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!type.isInstance(value)) {
            return defaultValue;
        }
        if (elementType != null && value instanceof Collection<?> collection) {
            try {
                validateCollectionElementType(collection, elementType, key);
            } catch (ResolveInputException e) {
                return defaultValue;
            }
        }
        return type.cast(value);
    }
}