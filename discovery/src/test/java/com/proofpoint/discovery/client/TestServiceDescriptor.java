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

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.UUID;

import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;
import static org.testng.Assert.assertNotNull;

public class TestServiceDescriptor
{
    @Test
    public void testToString()
    {
        assertNotNull(new ServiceDescriptor(UUID.fromString("12345678-1234-1234-1234-123456789012"),
                "node",
                "type",
                "pool",
                "location",
                ServiceState.RUNNING, ImmutableMap.of("a", "apple", "b", "banana")));
    }

    @Test
    public void testEquivalence()
    {
        UUID serviceA = UUID.randomUUID();
        UUID serviceB = UUID.randomUUID();
        equivalenceTester()
                .addEquivalentGroup(
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node-X", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type-X", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool-X", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location-X", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a-X", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple-X"))
                )
                .addEquivalentGroup(
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node-X", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type-X", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool-X", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location-X", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a-X", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple-X"))
                )
                .check();
    }
}
