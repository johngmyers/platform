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
package com.proofpoint.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;

public class JsonModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        // NOTE: this MUST NOT be a singleton because ObjectMappers are mutable.  This means
        // one component could reconfigure the mapper and break all other components
        binder.bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class);

        binder.bind(JsonCodecFactory.class).in(Scopes.SINGLETON);
    }
}
