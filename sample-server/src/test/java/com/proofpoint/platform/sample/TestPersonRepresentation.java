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
package com.proofpoint.platform.sample;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.json.JsonCodec;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.json.testing.JsonTester.decodeJson;
import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;
import static com.proofpoint.testing.ValidationAssertions.assertValidates;
import static org.assertj.core.api.Assertions.assertThat;

public class TestPersonRepresentation
{
    private final JsonCodec<PersonRepresentation> codec = jsonCodec(PersonRepresentation.class);
    private Map<String, String> jsonStructure;

    @BeforeMethod
    public void setup()
    {
        jsonStructure = new HashMap<>(ImmutableMap.of(
                "name", "Mr Foo",
                "email", "foo@example.com"));
    }

    @Test
    public void testJsonDecode()
    {
        PersonRepresentation personRepresentation = assertValidates(decodeJson(codec, jsonStructure));
        assertThat(personRepresentation.toPerson()).isEqualTo(new Person("foo@example.com", "Mr Foo"));
    }

    @Test
    public void testNoEmail()
    {
        jsonStructure.remove("email");
        assertFailsValidation(decodeJson(codec, jsonStructure), "email", "is missing", NotNull.class);
    }

    @Test
    public void testInvalidEmail()
    {
        jsonStructure.put("email", "invalid");
        assertFailsValidation(decodeJson(codec, jsonStructure), "email", "is malformed", Pattern.class);
    }

    @Test
    public void testNoName()
    {
        jsonStructure.remove("name");
        assertFailsValidation(decodeJson(codec, jsonStructure), "name", "is missing", NotNull.class);
    }
}
