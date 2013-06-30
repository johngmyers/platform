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
import com.google.common.collect.ImmutableMap;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public class ServiceDescriptorRepresentation
{
    @NotNull
    private final UUID id;
    @NotNull
    @Size(min = 1, message = "may not be empty")
    private final String nodeId;
    @NotNull
    @Size(min = 1, message = "may not be empty")
    private final String type;
    @NotNull
    @Size(min = 1, message = "may not be empty")
    private final String pool;
    private final String location;
    private final ServiceState state;
    @NotNull
    private final Map<String, String> properties;

    @JsonCreator
    public ServiceDescriptorRepresentation(
            @JsonProperty("id") UUID id,
            @JsonProperty("nodeId") String nodeId,
            @JsonProperty("type") String type,
            @JsonProperty("pool") String pool,
            @JsonProperty("location") String location,
            @JsonProperty("state") ServiceState state,
            @JsonProperty("properties") Map<String, String> properties)
    {
        this.id = id;
        this.nodeId = nodeId;
        this.type = type;
        this.pool = pool;
        this.location = location;
        this.state = state;
        if (properties == null) {
            this.properties = null;
        }
        else {
            this.properties = ImmutableMap.copyOf(properties);
        }
    }

    public ServiceDescriptor toServiceDescriptor()
    {
        return new ServiceDescriptor(id, nodeId, type, pool, location, state, properties);
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("id", id)
                .add("nodeId", nodeId)
                .add("type", type)
                .add("pool", pool)
                .add("location", location)
                .add("state", state)
                .add("properties", properties)
                .toString();
    }
}
