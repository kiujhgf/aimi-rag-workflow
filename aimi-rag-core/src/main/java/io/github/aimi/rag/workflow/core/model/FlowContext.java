package io.github.aimi.rag.workflow.core.model;

import io.github.aimi.rag.workflow.core.exception.TypeMismatchException;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FlowContext {

    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final AtomicInteger version = new AtomicInteger(0);

    public static FlowContext create() {
        return new FlowContext();
    }

    public static FlowContext from(Map<String, Object> initial) {
        FlowContext context = new FlowContext();
        context.values.putAll(initial);
        return context;
    }

    @SuppressWarnings("unchecked")
    public <V> V get(String key) {
        return (V) values.get(key);
    }

    @SuppressWarnings("unchecked")
    public <V> V get(String key, V defaultValue) {
        V value = (V) values.get(key);
        return value != null ? value : defaultValue;
    }

    @SuppressWarnings("unchecked")
    public <V> V get(String key, Class<V> type) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new TypeMismatchException(key, type, value.getClass());
        }
        return (V) value;
    }

    public <V> FlowContext set(String key, V value) {
        values.put(key, value);
        version.incrementAndGet();
        return this;
    }

    public FlowContext setIfAbsent(String key, Object value) {
        values.putIfAbsent(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <V> V computeIfAbsent(String key, java.util.function.Supplier<V> supplier) {
        return (V) values.computeIfAbsent(key, k -> supplier.get());
    }

    @SuppressWarnings("unchecked")
    public <V> V compute(String key, java.util.function.BiFunction<String, V, V> remappingFunction) {
        return (V) values.compute(key, (k, v) -> remappingFunction.apply(k, (V) v));
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public FlowContext remove(String key) {
        values.remove(key);
        version.incrementAndGet();
        return this;
    }

    public Map<String, Object> getAll() {
        return Collections.unmodifiableMap(values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public void clear() {
        values.clear();
        version.incrementAndGet();
    }

    public int getVersion() {
        return version.get();
    }

    public long incrementCounter(String key) {
        AtomicLong counter = (AtomicLong) values.computeIfAbsent(key, k -> new AtomicLong(0));
        return counter.incrementAndGet();
    }

    public long getCounter(String key) {
        AtomicLong counter = (AtomicLong) values.get(key);
        return counter != null ? counter.get() : 0;
    }
}