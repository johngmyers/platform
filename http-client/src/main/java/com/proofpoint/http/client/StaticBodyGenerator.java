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
package com.proofpoint.http.client;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.nio.charset.Charset;

public non-sealed class StaticBodyGenerator implements BodySource
{
    public static StaticBodyGenerator createStaticBodyGenerator(String body, Charset charset)
    {
        return new StaticBodyGenerator(body.getBytes(charset));
    }

    public static StaticBodyGenerator createStaticBodyGenerator(byte[] body)
    {
        return new StaticBodyGenerator(body);
    }

    private final byte[] body;

    protected StaticBodyGenerator(byte[] body)
    {
        this.body = body;
    }

    @Override
    public final long getLength() {
        return body.length;
    }

    @SuppressFBWarnings("EI_EXPOSE_REP")
    public byte[] getBody()
    {
        return body;
    }
}
