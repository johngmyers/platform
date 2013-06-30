/*
 * Copyright 2013 Proofpoint, Inc.
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
import com.google.common.collect.Maps;
import com.proofpoint.json.JsonCodec;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.json.testing.JsonTester.decodeJson;
import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;
import static com.proofpoint.testing.ValidationAssertions.assertValidates;
import static org.testng.Assert.assertEquals;

public class TestServiceDescriptorRepresentation
{
    private final JsonCodec<ServiceDescriptorRepresentation> codec = jsonCodec(ServiceDescriptorRepresentation.class);
    private Map<String, Object> json;

    @BeforeMethod
    public void setup()
    {
        json = Maps.newHashMap(ImmutableMap.<String, Object>of(
                "id", "12345678-1234-1234-1234-123456789012",
                "nodeId", "node",
                "type", "type",
                "pool", "pool",
                "location", "location"));
        json.put("properties", ImmutableMap.of(
                "a", "apple",
                "b", "banana"
        ));
    }

    @Test
    public void testJsonDecode()
            throws Exception
    {
        ServiceDescriptor expected = new ServiceDescriptor(UUID.fromString("12345678-1234-1234-1234-123456789012"),
                "node",
                "type",
                "pool",
                "location",
                ServiceState.RUNNING, ImmutableMap.of("a", "apple", "b", "banana"));

        ServiceDescriptorRepresentation representation = assertValidates(decodeJson(codec, json));
        ServiceDescriptor actual = representation.toServiceDescriptor();

        assertEquals(actual, expected);
        assertEquals(actual.getId(), expected.getId());
        assertEquals(actual.getNodeId(), expected.getNodeId());
        assertEquals(actual.getType(), expected.getType());
        assertEquals(actual.getPool(), expected.getPool());
        assertEquals(actual.getLocation(), expected.getLocation());
        assertEquals(actual.getProperties(), expected.getProperties());
    }

    @Test
    public void testMissingId()
    {
        json.remove("id");
        assertFailsValidation(decodeJson(codec, json), "id", "may not be null", NotNull.class);
    }

    @Test
    public void testEmptyId()
    {
        json.put("id", "");
        assertFailsValidation(decodeJson(codec, json), "id", "may not be null", NotNull.class);
    }

    @Test
    public void testMissingNodeId()
    {
        json.remove("nodeId");
        assertFailsValidation(decodeJson(codec, json), "nodeId", "may not be null", NotNull.class);
    }

    @Test
    public void testEmptyNodeId()
    {
        json.put("nodeId", "");
        assertFailsValidation(decodeJson(codec, json), "nodeId", "may not be empty", Size.class);
    }

    @Test
    public void testMissingType()
    {
        json.remove("type");
        assertFailsValidation(decodeJson(codec, json), "type", "may not be null", NotNull.class);
    }

    @Test
    public void testEmptyType()
    {
        json.put("type", "");
        assertFailsValidation(decodeJson(codec, json), "type", "may not be empty", Size.class);
    }

    @Test
    public void testMissingPool()
    {
        json.remove("pool");
        assertFailsValidation(decodeJson(codec, json), "pool", "may not be null", NotNull.class);
    }

    @Test
    public void testEmptyPool()
    {
        json.put("pool", "");
        assertFailsValidation(decodeJson(codec, json), "pool", "may not be empty", Size.class);
    }

    @Test
    public void testMissingProperties()
    {
        json.remove("properties");
        assertFailsValidation(decodeJson(codec, json), "properties", "may not be null", NotNull.class);
    }

    @Test
    public void testEmptyProperties()
    {
        json.put("properties", ImmutableMap.<String, String>of());
        assertValidates(decodeJson(codec, json));
    }
}
