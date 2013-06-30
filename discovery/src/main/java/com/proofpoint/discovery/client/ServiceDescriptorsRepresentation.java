/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.discovery.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

public class ServiceDescriptorsRepresentation
{
    @NotNull
    private final String environment;
    @NotNull
    @Valid
    private final List<ServiceDescriptorRepresentation> serviceDescriptorRepresentations;

    @JsonCreator
    public ServiceDescriptorsRepresentation(
            @JsonProperty("environment") String environment,
            @JsonProperty("services") List<ServiceDescriptorRepresentation> serviceDescriptorRepresentations)
    {
        this.environment = environment;
        if (serviceDescriptorRepresentations == null) {
            this.serviceDescriptorRepresentations = null;
        }
        else {
            this.serviceDescriptorRepresentations = ImmutableList.copyOf(serviceDescriptorRepresentations);
        }
    }

    public String getEnvironment()
    {
        return environment;
    }

    public List<ServiceDescriptor> getServiceDescriptors()
    {
        ImmutableList.Builder<ServiceDescriptor> builder = ImmutableList.builder();
        for (ServiceDescriptorRepresentation representation : serviceDescriptorRepresentations) {
            builder.add(representation.toServiceDescriptor());
        }
        return builder.build();
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("environment", environment)
                .add("serviceDescriptorRepresentations", serviceDescriptorRepresentations)
                .toString();
    }
}
