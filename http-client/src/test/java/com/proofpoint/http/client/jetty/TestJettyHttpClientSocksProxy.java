package com.proofpoint.http.client.jetty;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.IOException;

import static com.proofpoint.testing.Closeables.closeQuietly;

public class TestJettyHttpClientSocksProxy
// Intermittently fails due to bug in the Jetty SOCKS code        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;
    private TestingSocksProxy testingSocksProxy;

    @BeforeMethod
    public void setUpHttpClient()
            throws IOException
    {
        testingSocksProxy = new TestingSocksProxy().start();
//        httpClient = new JettyHttpClient(createClientConfig(), jettyIoPool, ImmutableList.of(new TestingRequestFilter()));
    }

    @AfterMethod
    public void tearDownHttpClient()
    {
        closeQuietly(httpClient);
        closeQuietly(testingSocksProxy);
    }

//    @Override
//    protected HttpClientConfig createClientConfig()
//    {
//        return new HttpClientConfig()
//                .setHttp2Enabled(false)
//                .setSocksProxy(testingSocksProxy.getHostAndPort());
//    }
//
//    @Override
//    public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
//            throws Exception
//    {
//        return httpClient.execute(request, responseHandler);
//    }
//
//    @Override
//    public <T, E extends Exception> T executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
//            throws Exception
//    {
//        config.setSocksProxy(testingSocksProxy.getHostAndPort());
//        try (
//                JettyHttpClient client = new JettyHttpClient("test-private", config, ImmutableList.of(new TestingRequestFilter()))
//        ) {
//            return client.execute(request, responseHandler);
//        }
//    }
//
//    @Override
//    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*SOCKS4 .*")
//    public void testBadPort()
//            throws Exception
//    {
//        // todo this should be handled by jetty client before connecting to the socks proxy
//        super.testBadPort();
//    }
//
//    @Override
//    @Test(enabled = false)
//    public void testConnectTimeout()
//            throws Exception
//    {
//        // todo jetty client does not timeout the socks proxy connect properly
//        super.testConnectTimeout();
//    }
//
//    @Override
//    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*SOCKS4 .*")
//    public void testConnectionRefused()
//            throws Exception
//    {
//        super.testConnectionRefused();
//    }
//
//    @Override
//    @Test(expectedExceptions = IOException.class, expectedExceptionsMessageRegExp = ".*SOCKS4 .*")
//    public void testUnresolvableHost()
//            throws Exception
//    {
//        super.testUnresolvableHost();
//    }
//
//    @Override
//    @Test(enabled = false)
//    public void testPostMethod()
//    {
//        // Fails on Unix and we don't care about SOCKS
//    }
}
