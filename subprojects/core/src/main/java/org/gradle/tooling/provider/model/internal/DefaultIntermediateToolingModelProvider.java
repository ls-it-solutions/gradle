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

package org.gradle.tooling.provider.model.internal;

import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.Cast;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildToolingModelController;
import org.gradle.internal.buildtree.IntermediateBuildActionRunner;
import org.gradle.internal.snapshot.ValueSnapshotter;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;

@NonNullApi
public class DefaultIntermediateToolingModelProvider implements IntermediateToolingModelProvider {

    private final IntermediateBuildActionRunner actionRunner;
    private final ValueSnapshotter valueSnapshotter;

    public DefaultIntermediateToolingModelProvider(IntermediateBuildActionRunner actionRunner, ValueSnapshotter valueSnapshotter) {
        this.actionRunner = actionRunner;
        this.valueSnapshotter = valueSnapshotter;
    }

    @Override
    public <T> List<T> getModels(List<Project> targets, Class<T> modelType) {
        return getModelsImpl(targets, modelType.getName(), modelType, null);
    }

    @Override
    public <T> List<T> getModels(List<Project> targets, Class<T> modelType, Object modelBuilderParameter) {
        return getModelsImpl(targets, modelType.getName(), modelType, modelBuilderParameter);
    }

    @Override
    public <T> List<T> getModels(List<Project> targets, String modelName, Class<T> modelType, Object modelBuilderParameter) {
        return getModelsImpl(targets, modelName, modelType, modelBuilderParameter);
    }

    @Override
    public <P extends Plugin<Project>> void applyPlugin(List<Project> targets, Class<P> pluginClass) {
        List<Object> rawModels = fetchModels(targets, PluginApplyingBuilder.MODEL_NAME, createPluginApplyingParameter(pluginClass));
        ensureModelTypes(Boolean.class, rawModels);
    }

    private static <P extends Plugin<Project>> PluginApplyingParameter createPluginApplyingParameter(Class<P> pluginClass) {
        return () -> pluginClass;
    }

    private <T> List<T> getModelsImpl(List<Project> targets, String modelName, Class<T> modelType, @Nullable Object modelBuilderParameter) {
        if (targets.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> rawModels = fetchModels(targets, modelName, modelBuilderParameter);
        return ensureModelTypes(modelType, rawModels);
    }

    private List<Object> fetchModels(List<Project> targets, String modelName, @Nullable Object modelBuilderParameter) {
        BuildState buildState = extractSingleBuildState(targets);
        ToolingModelParameter parameterFactory = modelBuilderParameter == null ? null : wrapParameter(modelBuilderParameter);
        return buildState.withToolingModels(controller -> getModels(controller, targets, modelName, parameterFactory));
    }

    private List<Object> getModels(BuildToolingModelController controller, List<Project> targets, String modelName, @Nullable ToolingModelParameter parameterFactory) {
        List<Supplier<Object>> fetchActions = targets.stream()
            .map(targetProject -> (Supplier<Object>) () -> fetchModel(modelName, controller, (ProjectInternal) targetProject, parameterFactory))
            .collect(toList());

        return runFetchActions(fetchActions);
    }

    @Nullable
    private static Object fetchModel(String modelName, BuildToolingModelController controller, ProjectInternal targetProject, @Nullable ToolingModelParameter parameterFactory) {
        ProjectState builderTarget = targetProject.getOwner();
        ToolingModelScope toolingModelScope = controller.locateBuilderForTarget(builderTarget, modelName, parameterFactory != null);
        return toolingModelScope.getModel(modelName, parameterFactory);
    }

    private ToolingModelParameter wrapParameter(Object parameter) {
        return new ToolingModelParameter(parameter, valueSnapshotter, (expectedParameterType, value) -> {
            if (expectedParameterType.isInstance(value)) {
                return value;
            } else {
                throw new IllegalStateException(String.format("Expected model builder parameter type '%s', got '%s'", expectedParameterType.getName(), parameter.getClass().getName()));
            }
        });
    }

    private static BuildState extractSingleBuildState(List<Project> targets) {
        if (targets.isEmpty()) {
            throw new IllegalStateException("Cannot find build state without target projects");
        }

        BuildState result = getBuildState(targets.get(0));

        for (Project target : targets) {
            BuildState projectBuildState = getBuildState(target);
            if (result != projectBuildState) {
                throw new IllegalArgumentException(
                    String.format("Expected target projects to share the same build state. Found at least two: '%s' and '%s'",
                        result.getDisplayName(), projectBuildState.getDisplayName())
                );
            }
        }

        return result;
    }

    private static BuildState getBuildState(Project target) {
        return ((ProjectInternal) target).getOwner().getOwner();
    }

    private static <T> List<T> ensureModelTypes(Class<T> implementationType, List<Object> rawModels) {
        for (Object rawModel : rawModels) {
            if (rawModel == null) {
                throw new IllegalStateException(String.format("Expected model of type %s but found null", implementationType.getName()));
            }
            if (!implementationType.isInstance(rawModel)) {
                throw new IllegalStateException(String.format("Expected model of type %s but found %s", implementationType.getName(), rawModel.getClass().getName()));
            }
        }

        return Cast.uncheckedCast(rawModels);
    }

    private <T> List<T> runFetchActions(List<Supplier<T>> actions) {
        return actionRunner.run(actions);
    }
}
