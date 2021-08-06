package com.proofpoint.jaxrs;

import com.google.inject.Injector;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingAdminHttpServer;
import com.proofpoint.jaxrs.testapi.TestingResource;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Map;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.http.server.testing.TestingAdminHttpServerModule.initializesMainServletTestingAdminHttpServerModule;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.json.JsonCodec.mapJsonCodec;
import static com.proofpoint.testing.Assertions.assertContains;
import static org.testng.Assert.assertEquals;

public class TestOpenApi
{
    private final HttpClient client = new JettyHttpClient();
    private static final JsonCodec<Map<String, Object>> MAP_CODEC = mapJsonCodec(String.class, Object.class);

    private LifeCycleManager lifeCycleManager;
    private TestingAdminHttpServer server;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        explicitJaxrsModule(),
                        initializesMainServletTestingAdminHttpServerModule(),
                        new JsonModule(),
                        new ReportingModule(),
                        binder -> jaxrsBinder(binder).bind(TestingResource.class))
                .initialize();
        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingAdminHttpServer.class);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
            throws Exception
    {
        if (lifeCycleManager != null) {
            lifeCycleManager.stop();
        }
    }

    @AfterClass(alwaysRun = true)
    public void teardownClass()
    {
        Closeables.closeQuietly(client);
    }

    @Test
    public void testOpenApiJson()
    {
        ArrayList<String> resourceList = new ArrayList<>();
        resourceList.add("TestingResource");
        Object expected = Map.of(
                "openapi", "3.0.1",
                "paths", Map.of(
                        "/", Map.of(
                                "get", Map.of(
                                        "tags", resourceList,
                                        "summary", "Testing GET request",
                                        "operationId", "get",
                                        "responses", Map.of(
                                                "200", Map.of("description", "SUCESSFUL"),
                                                "400", Map.of("description", "One or more query parameter(s) is null or empty"),
                                                "503", Map.of("description", "Failed to process")
                                        )),
                                "put", Map.of(
                                        "tags", resourceList,
                                        "summary", "Testing PUT request",
                                        "operationId", "put",
                                        "responses", Map.of(
                                                "200", Map.of("description", "SUCESSFUL"),
                                                "401", Map.of("description", "Unauthorized"),
                                                "503", Map.of("description", "Failed to process")
                                        )),
                                "post", Map.of(
                                        "tags", resourceList,
                                        "summary", "Testing POST request",
                                        "operationId", "post",
                                        "responses", Map.of(
                                                "200", Map.of("description", "SUCESSFUL"),
                                                "400", Map.of("description", "One or more query parameter(s) is null or empty"),
                                                "409", Map.of("description", "State of the resource doesn't permit request."),
                                                "503", Map.of("description", "Failed to process"))),
                                "delete", Map.of(
                                        "tags", resourceList,
                                        "summary", "Testing DELETE request",
                                        "operationId", "delete",
                                        "responses", Map.of(
                                                "200", Map.of("description", "SUCESSFUL"),
                                                "401", Map.of("description", "Unauthorized"),
                                                "503", Map.of("description", "Failed to process")
                                        )
                                )
                        ),
                        "/inrotation.txt", Map.of(
                                "get", Map.of(
                                        "operationId", "get_1",
                                        "responses", Map.of(
                                                "default", Map.of(
                                                        "description", "default response",
                                                        "content", Map.of("text/plain", Map.of())
                                                )
                                        )
                                )
                        ),
                        "/liveness", Map.of(
                                "get", Map.of(
                                        "operationId", "get_2",
                                        "responses", Map.of(
                                                "default", Map.of(
                                                        "description", "default response",
                                                        "content", Map.of("text/plain", Map.of())
                                                ))
                                )
                        )
                )
        );
        Object response = client.execute(
                prepareGet().setUri(uriForOpenSpec("/admin/openapi.json")).build(),
                createJsonResponseHandler(MAP_CODEC));
        assertEquals(response, expected);
    }

    @Test
    public void testOpenApiYaml()
    {
        StringResponse response = client.execute(
                prepareGet().setUri(uriForOpenSpec("/admin/openapi.yaml")).build(),
                createStringResponseHandler());
        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getHeader("Content-Type"), "application/yaml");
        assertContains(response.getBody(), "summary: Testing GET request");
        assertContains(response.getBody(), "description: One or more query parameter(s) is null or empty");

    }

    @Test
    public void testOpenApiAdminJson()
    {
        Object expected = Map.of(
                "openapi", "3.0.1",
                "paths", Map.of(
                        "/admin/jstack", Map.of(
                                "get", Map.of(
                                        "operationId", "get",
                                        "responses", Map.of(
                                                "default", Map.of(
                                                        "description", "default response",
                                                        "content", Map.of(
                                                                "text/plain", Map.of(
                                                                        "schema", Map.of("type", "string")))))
                                        ))
                )
        );
        Object response = client.execute(
                prepareGet().setUri(uriForOpenSpec("/admin/openapi-admin.json")).build(),
                createJsonResponseHandler(MAP_CODEC));
        assertEquals(response, expected);
    }

    @Test
    public void testOpenApiAdminYaml()
    {
        StringResponse response = client.execute(
                prepareGet().setUri(uriForOpenSpec("/admin/openapi-admin.yaml")).build(),
                createStringResponseHandler());
        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getHeader("Content-Type"), "application/yaml");
        assertContains(response.getBody(), "/admin/jstack:");
    }

    private URI uriForOpenSpec(String specLocation)
    {
        return server.getBaseUrl().resolve(specLocation);
    }
}