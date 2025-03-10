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
package com.proofpoint.http.server;

import jakarta.annotation.Nullable;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import javax.net.ssl.SSLSession;

import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

public class DispatchingRequestLogHandler
        implements org.eclipse.jetty.server.RequestLog
{
    private static final String REQUEST_SSL_SESSION_ATTRIBUTE = "org.eclipse.jetty.servlet.request.ssl_session";

    private final RequestLog logger;
    private final RequestStats stats;
    private final DetailedRequestStats detailedRequestStats;
    private final ClientAddressExtractor clientAddressExtractor;

    public DispatchingRequestLogHandler(@Nullable RequestLog logger, RequestStats stats, DetailedRequestStats detailedRequestStats, ClientAddressExtractor clientAddressExtractor)
    {
        this.logger = logger;
        this.stats = requireNonNull(stats, "stats is null");
        this.detailedRequestStats = requireNonNull(detailedRequestStats, "detailedRequestStats is null");
        this.clientAddressExtractor = requireNonNull(clientAddressExtractor, "clientAddressExtractor is null");
    }

    @Override
    public void log(Request request, Response response)
    {
        SSLSession sslSession = (SSLSession) request.getAttribute(REQUEST_SSL_SESSION_ATTRIBUTE);
        RequestTiming timings = RequestTimingEventHandler.timings(request);
        if (logger != null) {
            logger.log(HttpRequestEvent.createHttpRequestEvent(request, response, sslSession, timings, clientAddressExtractor));
        }

        stats.record(Request.getContentBytesRead(request), Response.getContentBytesWritten(response), timings.timeToCompletion());
        detailedRequestStats.requestTimeByCode(response.getStatus(), response.getStatus() / 100).add(timings.timeToCompletion());

        if (sslSession != null) {
            detailedRequestStats.tlsRequest(sslSession.getProtocol(), sslSession.getCipherSuite()).add(1);
        }
    }
}
