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

package org.gradle.tooling.events.problems;


import org.gradle.api.Incubating;

import java.util.List;

/**
 * Represents a reported problems summary.
 *
 * @since 8.6
 */
@Incubating
public interface ProblemAggregation {

    /**
     * Returns the problem category.
     *
     * @return the problem category
     * @since 8.6
     */
    ProblemCategory getCategory();

    /**
     * Returns the problem label.
     *
     * @return the problem label
     * @since 8.6
     */
    Label getLabel();

    /**
     * Returns the details string.
     *
     * @return the problem details
     * @since 8.6
     */
    Details getDetails();

    /**
     * Returns the problem severity.
     *
     * @return the problem severity
     * @since 8.6
     */
    Severity getSeverity();

    /**
     * Returns the locations associated with this problem.
     *
     * @return the locations
     * @since 8.6
     */
    List<Location> getLocations();

    /**
     * Returns the link to the documentation
     *
     * @return the locations
     * @since 8.6
     */
    DocumentationLink getDocumentationLink();

    /**
     * Returns the list of solutions.
     *
     * @return the solutions
     * @since 8.6
     */
    List<Solution> getSolutions();

    /**
     * Additional data associated with this problem.
     *
     * @return a map of additional data
     * @since 8.6
     */
    AdditionalData getAdditionalData();

    /**
     * The number of aggregated problems.
     *
     * @return the number of aggregated problems.
     * @since 8.6
     */
    int getCount();

}
