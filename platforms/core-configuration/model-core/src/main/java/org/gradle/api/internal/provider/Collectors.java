/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.provider;

import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.gradle.api.internal.lambdas.SerializableLambdas.transformer;

public class Collectors {
    public interface ProvidedCollector<T> extends Collector<T> {
        boolean isProvidedBy(Provider<?> provider);
    }

    public static class SingleElement<T> implements Collector<T> {
        private final T element;

        public SingleElement(T element) {
            this.element = element;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> collection) {
            collector.add(element, collection);
            return Value.present();
        }

        @Override
        public void calculateExecutionTimeValue(Action<? super ExecutionTimeValue<? extends Iterable<? extends T>>> visitor) {
            visitor.execute(ExecutionTimeValue.fixedValue(ImmutableList.of(element)));
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SingleElement<?> that = (SingleElement<?>) o;
            return Objects.equal(element, that.element);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(element);
        }

        @Override
        public int size() {
            return 1;
        }
    }

    public static class ElementFromProvider<T> implements ProvidedCollector<T> {
        private final ProviderInternal<? extends T> provider;

        public ElementFromProvider(ProviderInternal<? extends T> provider) {
            this.provider = provider;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return provider.calculatePresence(consumer);
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> collection) {
            Value<? extends T> value = provider.calculateValue(consumer);
            if (value.isMissing()) {
                return value.asType();
            }

            collector.add(value.getWithoutSideEffect(), collection);
            return Value.present().withSideEffect(SideEffect.fixedFrom(value));
        }

        @Override
        public boolean isProvidedBy(Provider<?> provider) {
            return Objects.equal(provider, this.provider);
        }

        @Override
        public void calculateExecutionTimeValue(Action<? super ExecutionTimeValue<? extends Iterable<? extends T>>> visitor) {
            ExecutionTimeValue<? extends T> value = provider.calculateExecutionTimeValue();
            visitValue(visitor, value);
        }

        @Override
        public ValueProducer getProducer() {
            return provider.getProducer();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ElementFromProvider<?> that = (ElementFromProvider<?>) o;
            return Objects.equal(provider, that.provider);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(provider);
        }

        @Override
        public int size() {
            return 1;
        }
    }

    private static <T> void visitValue(Action<? super ValueSupplier.ExecutionTimeValue<? extends Iterable<? extends T>>> visitor, ValueSupplier.ExecutionTimeValue<? extends T> value) {
        if (value.isMissing()) {
            visitor.execute(ValueSupplier.ExecutionTimeValue.missing());
        } else if (value.hasFixedValue()) {
            // transform preserving side effects
            visitor.execute(ValueSupplier.ExecutionTimeValue.value(value.toValue().transform(ImmutableList::of)));
        } else {
            visitor.execute(ValueSupplier.ExecutionTimeValue.changingValue(value.getChangingValue().map(transformer(ImmutableList::of))));
        }
    }

    public static class ElementsFromCollection<T> implements Collector<T> {
        private final Iterable<? extends T> value;

        public ElementsFromCollection(Iterable<? extends T> value) {
            this.value = value;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> collection) {
            collector.addAll(value, collection);
            return Value.present();
        }

        @Override
        public void calculateExecutionTimeValue(Action<? super ExecutionTimeValue<? extends Iterable<? extends T>>> visitor) {
            visitor.execute(ExecutionTimeValue.fixedValue(value));
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ElementsFromCollection<?> that = (ElementsFromCollection<?>) o;
            return Objects.equal(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public int size() {
            return Iterables.size(value);
        }
    }

    public static class ElementsFromCollectionProvider<T> implements ProvidedCollector<T> {
        private final ProviderInternal<? extends Iterable<? extends T>> provider;

        public ElementsFromCollectionProvider(ProviderInternal<? extends Iterable<? extends T>> provider) {
            this.provider = provider;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return provider.calculatePresence(consumer);
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> collection) {
            Value<? extends Iterable<? extends T>> value = provider.calculateValue(consumer);
            return collectEntriesFromValue(collector, collection, value);
        }

        @Override
        public void calculateExecutionTimeValue(Action<? super ExecutionTimeValue<? extends Iterable<? extends T>>> visitor) {
            visitor.execute(provider.calculateExecutionTimeValue());
        }

        @Override
        public ValueProducer getProducer() {
            return provider.getProducer();
        }

        @Override
        public boolean isProvidedBy(Provider<?> provider) {
            return Objects.equal(this.provider, provider);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ElementsFromCollectionProvider<?> that = (ElementsFromCollectionProvider<?>) o;
            return Objects.equal(provider, that.provider);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(provider);
        }

        @Override
        public int size() {
            if (provider instanceof CollectionProviderInternal) {
                return ((CollectionProviderInternal) provider).size();
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    private static <T> ValueSupplier.Value<Void> collectEntriesFromValue(ValueCollector<T> collector, ImmutableCollection.Builder<T> collection, ValueSupplier.Value<? extends Iterable<? extends T>> value) {
        if (value.isMissing()) {
            return value.asType();
        }

        collector.addAll(value.getWithoutSideEffect(), collection);
        return ValueSupplier.Value.present().withSideEffect(ValueSupplier.SideEffect.fixedFrom(value));
    }

    public static class ElementsFromArray<T> implements Collector<T> {
        private final T[] value;

        ElementsFromArray(T[] value) {
            this.value = value;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> dest) {
            for (T t : value) {
                collector.add(t, dest);
            }
            return Value.present();
        }

        @Override
        public void calculateExecutionTimeValue(Action<? super ExecutionTimeValue<? extends Iterable<? extends T>>> visitor) {
            visitor.execute(ExecutionTimeValue.fixedValue(ImmutableList.copyOf(value)));
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }

        @Override
        public int size() {
            return value.length;
        }
    }

    public static class TypedCollector<T> implements ProvidedCollector<T> {
        private final Class<? extends T> type;
        protected final Collector<T> delegate;
        private final ValueCollector<T> valueCollector;

        public TypedCollector(@Nullable Class<? extends T> type, Collector<T> delegate) {
            this.type = type;
            this.delegate = delegate;
            this.valueCollector = ValueSanitizers.collectorFor(type);
        }

        @Nullable
        public Class<? extends T> getType() {
            return type;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return delegate.calculatePresence(consumer);
        }

        public void collectInto(ImmutableCollection.Builder<T> builder) {
            collectEntries(ValueConsumer.IgnoreUnsafeRead, valueCollector, builder);
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> dest) {
            return delegate.collectEntries(consumer, collector, dest);
        }

        @Override
        public boolean isProvidedBy(Provider<?> provider) {
            return delegate instanceof ProvidedCollector && ((ProvidedCollector<T>) delegate).isProvidedBy(provider);
        }

        @Override
        public void calculateExecutionTimeValue(Action<? super ExecutionTimeValue<? extends Iterable<? extends T>>> visitor) {
            delegate.calculateExecutionTimeValue(visitor);
        }

        @Override
        public ValueProducer getProducer() {
            return delegate.getProducer();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TypedCollector<?> that = (TypedCollector<?>) o;
            return Objects.equal(type, that.type) &&
                Objects.equal(delegate, that.delegate);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(type, delegate);
        }
    }

    public static class FilteringCollector<T> implements Collector<T> {
        private final Collector<T> upstream;
        private final Spec<T> predicate;
        private final Supplier<ImmutableCollection.Builder<T>> collectionFactory;

        @Override
        public ValueProducer getProducer() {
            return upstream.getProducer();
        }

        public FilteringCollector(Collector<T> upstream, Spec<T> predicate, Supplier<ImmutableCollection.Builder<T>> collectionFactory) {
            this.upstream = upstream;
            this.predicate = predicate;
            this.collectionFactory = collectionFactory;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return upstream.calculatePresence(consumer);
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> dest) {
            ImmutableCollection.Builder<T> baseBuilder = collectionFactory.get();
            Value<Void> baseValue = upstream.collectEntries(consumer, collector, baseBuilder);
            if (baseValue.isMissing()) {
                return baseValue;
            }
            ImmutableCollection<T> baseElements = baseBuilder.build();
            dest.addAll(Iterables.filter(baseElements, predicate::isSatisfiedBy));
            return baseValue;
        }

        @Override
        public void calculateExecutionTimeValue(Action<? super ExecutionTimeValue<? extends Iterable<? extends T>>> visitor) {
            ExecutionTimeValue<? extends Iterable<? extends T>>[] baseValue = Cast.uncheckedCast(new ExecutionTimeValue[1]);
            upstream.calculateExecutionTimeValue(it -> baseValue[0] = it);
            //TODO-RC need to filter the execution time value
            visitor.execute(baseValue[0]);
        }
    }

    public static class PlusCollector<T> implements Collector<T> {
        private final Collector<T> left;
        private final Collector<T> right;
        private final boolean pruning;

        public PlusCollector(Collector<T> left, Collector<T> right, boolean pruning) {
            this.left = left;
            this.right = right;
            this.pruning = pruning;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return
                pruning ?
                    (left.calculatePresence(consumer) || right.calculatePresence(consumer))
                    :
                    (left.calculatePresence(consumer) && right.calculatePresence(consumer));

        }

        @Override
        public int size() {
            return left.size() + right.size();
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> dest) {
            Value<Void> leftValue = left.collectEntries(consumer, collector, dest);
            if (leftValue.isMissing() && !pruning) {
                return leftValue;
            }
            Value<Void> rightValue = right.collectEntries(consumer, collector, dest);
            if (rightValue.isMissing() && (!pruning || leftValue.isMissing())) {
                return rightValue;
            }
            return Value.present()
                .withSideEffect(SideEffect.fixedFrom(leftValue))
                .withSideEffect(SideEffect.fixedFrom(rightValue));
        }

        @Override
        public void calculateExecutionTimeValue(Action<? super ExecutionTimeValue<? extends Iterable<? extends T>>> visitor) {
            left.calculateExecutionTimeValue(visitor);
            right.calculateExecutionTimeValue(visitor);
        }

        @Override
        public ValueProducer getProducer() {
            return left.getProducer().plus(right.getProducer());
        }
    }

    public static class MinusCollector<T> implements Collector<T> {
        private final Collector<T> left;
        private final Collector<T> right;
        private final boolean pruning;
        private final Supplier<ImmutableCollection.Builder<T>> collectionFactory;

        public MinusCollector(Collector<T> left, Collector<T> right, boolean pruning, Supplier<ImmutableCollection.Builder<T>> collectionFactory) {
            this.left = left;
            this.right = right;
            this.pruning = pruning;
            this.collectionFactory = collectionFactory;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return
                left.calculatePresence(consumer);
        }

        @Override
        public int size() {
            return left.size();
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> dest) {
            ImmutableCollection.Builder<T> leftBuilder = collectionFactory.get();
            Value<Void> leftValue = left.collectEntries(consumer, collector, leftBuilder);
            if (leftValue.isMissing() && !pruning) {
                return leftValue;
            }
            ImmutableCollection.Builder<T> rightBuilder = collectionFactory.get();
            Value<Void> rightValue = right.collectEntries(consumer, collector, rightBuilder);
            if (rightValue.isMissing() && (!pruning || leftValue.isMissing())) {
                //TODO-RC this is not right
                return rightValue;
            }

            Stream<T> baseElements = leftBuilder.build().stream();
            ImmutableCollection<T> rightCollection = rightBuilder.build();
            baseElements.filter(Predicates.not(rightCollection::contains)).forEach(dest::add);
            return Value.present()
                .withSideEffect(SideEffect.fixedFrom(leftValue));
        }

        @Override
        public void calculateExecutionTimeValue(Action<? super ExecutionTimeValue<? extends Iterable<? extends T>>> visitor) {
            //TODO-RC what is the correct way of implemeting this?
            left.calculateExecutionTimeValue(visitor);
            right.calculateExecutionTimeValue(visitor);
        }

        @Override
        public ValueProducer getProducer() {
            return left.getProducer().plus(right.getProducer());
        }
    }
}
