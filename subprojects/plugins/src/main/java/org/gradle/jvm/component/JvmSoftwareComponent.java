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

package org.gradle.jvm.component;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Incubating;
import org.gradle.api.component.ComponentFeature;
import org.gradle.api.component.ComponentWithFeatures;

/**
 * The component created by the {@code java} plugin. This component is a configurable {@link ComponentWithFeatures}.
 *
 * @since 8.2
 */
@Incubating
public interface JvmSoftwareComponent extends ComponentWithFeatures {

    /**
     * {@inheritDoc}
     */
    @Override
    ExtensiblePolymorphicDomainObjectContainer<ComponentFeature> getFeatures();

}
