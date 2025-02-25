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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.Files;
import com.google.common.net.InetAddresses;
import com.google.common.net.MediaType;
import com.google.inject.CreationException;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.HttpStatus;
import com.proofpoint.http.client.HttpUriBuilder;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.log.Logging;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.HttpUriBuilder.uriBuilderFrom;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.http.server.HttpServerBinder.httpServerBinder;
import static java.io.OutputStream.nullOutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestHttpServerModule
{
    private Injector injector;
    private File tempDir;

    private Map<String, String> properties;

    @BeforeSuite
    public void setupSuite()
    {
        Logging.initialize();
    }

    @BeforeMethod
    public void setup()
            throws IOException
    {
        injector = null;
        tempDir = Files.createTempDir().getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
        properties = Map.of(
                "http-server.http.port", "0",
                "http-server.log.path", new File(tempDir, "http-request.log").getAbsolutePath());
    }

    @AfterMethod
    public void tearDown()
            throws IOException
    {
        try {
            LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
            lifeCycleManager.stop();
        }
        catch (Exception ignored) {
        }

        deleteRecursively(tempDir.toPath(), ALLOW_INSECURE);
    }

    @Test
    public void testCanConstructServer()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(new HttpServerModule(),
                        new TestingNodeModule(),
                        new TestingMBeanModule(),
                        new ReportingModule(),
                        binder -> {
                            binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class);
                            binder.bind(Servlet.class).annotatedWith(TheAdminServlet.class).to(DummyServlet.class);
                        })
                .setRequiredConfigurationProperties(properties)
                .initialize();

        HttpServer server = injector.getInstance(HttpServer.class);
        assertNotNull(server);
    }

    @Test
    public void testHttpServerUri()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(new HttpServerModule(),
                        new TestingNodeModule(),
                        new TestingMBeanModule(),
                        new ReportingModule(),
                        binder -> {
                            binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class);
                            binder.bind(Servlet.class).annotatedWith(TheAdminServlet.class).to(DummyServlet.class);
                        })
                .setRequiredConfigurationProperties(properties)
                .initialize();

        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        HttpServer server = injector.getInstance(HttpServer.class);
        assertNotNull(server);
        server.start();
        try {
            HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);
            assertNotNull(httpServerInfo);
            assertNotNull(httpServerInfo.getHttpUri());
            assertEquals(httpServerInfo.getHttpUri().getScheme(), "http");
            assertEquals(InetAddresses.forUriString(httpServerInfo.getHttpUri().getHost()), nodeInfo.getInternalIp());
            assertNull(httpServerInfo.getHttpsUri());
        }
        catch (Exception e) {
            server.stop();
        }
    }

    @Test
    public void testServer()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(new HttpServerModule(),
                        new TestingNodeModule(),
                        new TestingMBeanModule(),
                        new ReportingModule(),
                        binder -> {
                            binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class);
                            binder.bind(Servlet.class).annotatedWith(TheAdminServlet.class).to(DummyServlet.class);
                            newSetBinder(binder, Filter.class, TheServlet.class).addBinding().to(DummyFilter.class).in(Scopes.SINGLETON);
                            httpServerBinder(binder).bindResource("/", "webapp/user").withWelcomeFile("user-welcome.txt");
                            httpServerBinder(binder).bindResource("/", "webapp/user2");
                            httpServerBinder(binder).bindResource("path", "webapp/user").withWelcomeFile("user-welcome.txt");
                            httpServerBinder(binder).bindResource("path", "webapp/user2");
                        })
                .setRequiredConfigurationProperties(properties)
                .initialize();

        HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);

        try (HttpClient client = new JettyHttpClient()) {

            // test servlet bound correctly
            URI httpUri = httpServerInfo.getHttpUri();
            StatusResponse response = client.execute(prepareGet().setUri(httpUri).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_OK);

            // test filter bound correctly
            response = client.execute(prepareGet().setUri(httpUri.resolve("/filter")).build(), createStatusResponseHandler());

            assertEquals(response.getStatusCode(), HttpServletResponse.SC_PAYMENT_REQUIRED);

            // test http resources
            assertResource(httpUri, client, "", "welcome user!");
            assertResource(httpUri, client, "user-welcome.txt", "welcome user!");
            assertResource(httpUri, client, "user.txt", "user");
            assertResource(httpUri, client, "user2.txt", "user2");
            assertResource(httpUri, client, "path", "welcome user!");
            assertResource(httpUri, client, "path/", "welcome user!");
            assertResource(httpUri, client, "path/user-welcome.txt", "welcome user!");
            assertResource(httpUri, client, "path/user.txt", "user");
            assertResource(httpUri, client, "path/user2.txt", "user2");
        }
    }

    @Test
    public void testSessionHandler()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(new HttpServerModule().withSessionHandler(),
                        new TestingNodeModule(),
                        new TestingMBeanModule(),
                        new ReportingModule(),
                        binder -> {
                            binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class);
                            binder.bind(Servlet.class).annotatedWith(TheAdminServlet.class).to(DummyServlet.class);
                        })
                .setRequiredConfigurationProperties(properties)
                .initialize();

        SessionHandler result = injector.getInstance(SessionHandler.class);
        assertNotNull(result);
    }

    @Test
    public void testAllowAmbiguousUrls()
        throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(new HttpServerModule().allowAmbiguousUris(),
                        new TestingNodeModule(),
                        new TestingMBeanModule(),
                        new ReportingModule(),
                        binder -> {
                            binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class);
                            binder.bind(Servlet.class).annotatedWith(TheAdminServlet.class).to(DummyServlet.class);
                            httpServerBinder(binder).bindResource("path", "webapp/user").withWelcomeFile("user-welcome.txt");
                            httpServerBinder(binder).bindResource("path//foo", "webapp/user").withWelcomeFile("user-welcome.txt");
                        })
                .setRequiredConfigurationProperties(properties)
                .initialize();
        HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);
        HttpServerModuleOptions moduleOptions = injector.getInstance(HttpServerModuleOptions.class);
        assertTrue(moduleOptions.isAllowAmbiguousUris());

        try (HttpClient client = new JettyHttpClient()) {
            URI httpUri = httpServerInfo.getHttpUri();
            assertResource(httpUri, client, "path/", "welcome user!");
            assertResource(httpUri, client, "path//foo", "welcome user!");
        }
    }

    @Test
    public void testEnableVirtualThreads()
            throws Exception
    {
        try {
            Injector injector = bootstrapTest()
                    .withModules(new HttpServerModule().enableVirtualThreads(),
                            new TestingNodeModule(),
                            new TestingMBeanModule(),
                            new ReportingModule(),
                            binder -> {
                                binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class);
                                binder.bind(Servlet.class).annotatedWith(TheAdminServlet.class).to(DummyServlet.class);
                                httpServerBinder(binder).bindResource("path", "webapp/user").withWelcomeFile("user-welcome.txt");
                                httpServerBinder(binder).bindResource("path//foo", "webapp/user").withWelcomeFile("user-welcome.txt");
                            })
                    .setRequiredConfigurationProperties(properties)
                    .initialize();
            HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);
            HttpServerModuleOptions moduleOptions = injector.getInstance(HttpServerModuleOptions.class);
            assertTrue(moduleOptions.isEnableVirtualThreads());

            try (HttpClient client = new JettyHttpClient()) {
                URI httpUri = httpServerInfo.getHttpUri();
                assertResource(httpUri, client, "path/", "welcome user!");
            }
        }
        catch (CreationException e) {
            if (Runtime.version().feature() >= 18) {
                throw e;
            }
            assertThat(e).hasMessageContaining("Virtual threads are not supported");
            return;
        }
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(18);
    }

    private void assertResource(URI baseUri, HttpClient client, String path, String contents)
    {
        HttpUriBuilder uriBuilder = uriBuilderFrom(baseUri);
        StringResponse data = client.execute(prepareGet().setUri(uriBuilder.appendPath(path).build()).build(), createStringResponseHandler());
        assertEquals(data.getStatusCode(), HttpStatus.OK.code());
        MediaType contentType = MediaType.parse(data.getHeader(CONTENT_TYPE));
        assertTrue(PLAIN_TEXT_UTF_8.is(contentType), "Expected text/plain but got " + contentType);
        assertEquals(data.getBody().trim(), contents);
    }

    private static final class EchoServlet
            extends HttpServlet
    {
        private int responseStatusCode = 300;
        private final ListMultimap<String, String> responseHeaders = ArrayListMultimap.create();
        public String responseBody;
        private String remoteAddress;

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
                throws IOException
        {
            request.getInputStream().transferTo(nullOutputStream());

            remoteAddress = request.getRemoteAddr();
            for (Entry<String, String> entry : responseHeaders.entries()) {
                response.addHeader(entry.getKey(), entry.getValue());
            }

            response.setStatus(responseStatusCode);

            if (responseBody != null) {
                response.getOutputStream().write(responseBody.getBytes(UTF_8));
            }
        }
    }
}
