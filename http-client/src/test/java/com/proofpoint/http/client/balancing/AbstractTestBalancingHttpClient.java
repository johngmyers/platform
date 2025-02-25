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
package com.proofpoint.http.client.balancing;

import com.proofpoint.http.client.BodySource;
import com.proofpoint.http.client.DynamicBodySource;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.LimitedRetryable;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.StaticBodyGenerator;
import com.proofpoint.testing.TestingTicker;
import com.proofpoint.units.Duration;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.proofpoint.http.client.Request.Builder.preparePut;
import static java.math.BigDecimal.ZERO;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

public abstract class AbstractTestBalancingHttpClient<T extends HttpClient>
{
    protected TestingTicker testingTicker;
    protected HttpServiceBalancer serviceBalancer;
    protected HttpServiceAttempt serviceAttempt1;
    protected HttpServiceAttempt serviceAttempt2;
    protected HttpServiceAttempt serviceAttempt3;
    protected BalancingHttpClientConfig balancingHttpClientConfig;
    protected T balancingHttpClient;
    protected BodySource bodySource;
    protected Request request;
    protected TestingClient httpClient;
    protected ArgumentCaptor<Request> requestArgumentCaptor;
    protected Response response;

    protected interface TestingClient
            extends HttpClient
    {
        TestingClient expectCall(String uri, Response response);

        TestingClient expectCall(String uri, Exception exception);

        TestingClient firstCallNoBodyGenerator();

        void assertDone();
    }

    protected abstract TestingClient createTestingClient();

    protected abstract T createBalancingHttpClient();

    protected abstract void assertHandlerExceptionThrown(ResponseHandler responseHandler, RuntimeException handlerException)
            throws Exception;

    protected abstract void issueRequest()
            throws Exception;

    @BeforeMethod
    protected void setUp()
    {
        testingTicker = new TestingTicker();
        serviceBalancer = mock(HttpServiceBalancer.class);
        serviceAttempt1 = mock(HttpServiceAttempt.class);
        serviceAttempt2 = mock(HttpServiceAttempt.class);
        serviceAttempt3 = mock(HttpServiceAttempt.class);
        when(serviceBalancer.createAttempt()).thenReturn(serviceAttempt1);
        when(serviceAttempt1.getUri()).thenReturn(URI.create("http://s1.example.com"));
        when(serviceAttempt1.next()).thenReturn(serviceAttempt2);
        when(serviceAttempt2.getUri()).thenReturn(URI.create("http://s2.example.com/"));
        when(serviceAttempt2.next()).thenReturn(serviceAttempt3);
        when(serviceAttempt3.getUri()).thenReturn(URI.create("http://s1.example.com"));
        when(serviceAttempt3.next()).thenThrow(new AssertionError("Unexpected call to serviceAttempt3.next()"));
        httpClient = createTestingClient();
        balancingHttpClientConfig = new BalancingHttpClientConfig()
                .setMaxAttempts(3)
                .setMinBackoff(new Duration(1, TimeUnit.MILLISECONDS))
                .setMaxBackoff(new Duration(2, TimeUnit.MILLISECONDS));
        balancingHttpClient = createBalancingHttpClient();
        bodySource = mock(DynamicBodySource.class);
        request = preparePut().setUri(URI.create("v1/service")).setBodySource(bodySource).build();
        requestArgumentCaptor = ArgumentCaptor.forClass(Request.class);
        response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(204);
    }

    @Test
    public void testSuccessfulQuery()
            throws Exception
    {
        httpClient.expectCall("http://s1.example.com/v1/service", response);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markGood();
        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s1.example.com/v1/service");
        verifyNoMoreInteractions(serviceAttempt1, responseHandler);
    }

    @Test
    public void testSuccessfulQueryWithParameters()
            throws Exception
    {
        request = preparePut().setUri(URI.create("v1%2B/service?foo=bar&baz=qu%2Bux")).setBodySource(bodySource).build();
        httpClient.expectCall("http://s1.example.com/v1%2B/service?foo=bar&baz=qu%2Bux", response);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markGood();
        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s1.example.com/v1%2B/service?foo=bar&baz=qu%2Bux");
        verifyNoMoreInteractions(serviceAttempt1, responseHandler);
    }

    @Test
    public void testSuccessfulQueryNullPath()
            throws Exception
    {
        httpClient.expectCall("http://s1.example.com/", response);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response))).thenReturn("test response");

        request = preparePut().setUri(new URI(null, null, null, null)).setBodySource(bodySource).build();
        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markGood();
        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s1.example.com/");
        verifyNoMoreInteractions(serviceAttempt1, responseHandler);
    }

    @Test
    public void testSuccessfulQueryAnnouncedPrefix()
            throws Exception
    {
        serviceBalancer = mock(HttpServiceBalancer.class);
        serviceAttempt1 = mock(HttpServiceAttempt.class);
        when(serviceBalancer.createAttempt()).thenReturn(serviceAttempt1);
        when(serviceAttempt1.getUri()).thenReturn(URI.create("http://s3.example.com/prefix"));
        balancingHttpClient = createBalancingHttpClient();

        httpClient.expectCall("http://s3.example.com/prefix/v1/service", response);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markGood();
        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s3.example.com/prefix/v1/service");
        verifyNoMoreInteractions(serviceAttempt1, responseHandler);
    }

    @Test
    public void testDoesntRetryOnHandlerException()
            throws Exception
    {
        httpClient.expectCall("http://s1.example.com/v1/service", response);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        Exception testException = new Exception("test exception");
        when(responseHandler.handle(any(Request.class), same(response))).thenThrow(testException);

        try {
            String returnValue = balancingHttpClient.execute(request, responseHandler);
            fail("expected exception, got " + returnValue);
        }
        catch (Exception e) {
            assertSame(e, testException);
        }

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markBad("204 status code", "Exception");
        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s1.example.com/v1/service");
        verifyNoMoreInteractions(serviceAttempt1, responseHandler);
    }

    @Test
    public void testRetryOnHttpClientException()
            throws Exception
    {
        httpClient.expectCall("http://s1.example.com/v1/service", new ConnectException());
        httpClient.expectCall("http://s2.example.com/v1/service", response);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markBad("ConnectException");
        verify(serviceAttempt1).next();
        verify(serviceAttempt2, atLeastOnce()).getUri();
        verify(serviceAttempt2).markGood();
        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s2.example.com/v1/service");
        verifyNoMoreInteractions(serviceAttempt1, serviceAttempt2, responseHandler);
    }

    @Test(dataProvider = "retryStatus")
    public void testRetryOnRetryableStatus(int retryStatus)
            throws Exception
    {
        Response retryResponse = mock(Response.class);
        when(retryResponse.getStatusCode()).thenReturn(retryStatus);

        httpClient.expectCall("http://s1.example.com/v1/service", retryResponse);
        httpClient.expectCall("http://s2.example.com/v1/service", response);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markBad(retryStatus + " status code");
        verify(serviceAttempt1).next();
        verify(serviceAttempt2, atLeastOnce()).getUri();
        verify(serviceAttempt2).markGood();
        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s2.example.com/v1/service");
        verifyNoMoreInteractions(serviceAttempt1, serviceAttempt2, responseHandler);
    }

    @DataProvider(name = "retryStatus")
    public Object[][] getRetryStatus()
    {
        return new Object[][] {
                new Object[] {408},
                new Object[] {429},
                new Object[] {499},
                new Object[] {500},
                new Object[] {502},
                new Object[] {503},
                new Object[] {504},
                new Object[] {598},
                new Object[] {599},
        };
    }

    @Test
    public void testWithANoRetryHeader()
            throws Exception
    {
        Response response500 = mock(Response.class);
        when(response500.getStatusCode()).thenReturn(500);
        when(response500.getHeader("X-Proofpoint-Retry")).thenReturn("no");

        httpClient.expectCall("http://s1.example.com/v1/service", response500);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response500))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markBad("500 status code");
        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response500));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s1.example.com/v1/service");
        verifyNoMoreInteractions(serviceAttempt1, serviceAttempt2, responseHandler);
    }

    @Test
    public void testDoesntRetryOnResponseSingleUseBodyGeneratorUsed()
            throws Exception
    {
        bodySource = new TestingLimitedRetryableSource();
        request = preparePut().setUri(URI.create("v1/service")).setBodySource(bodySource).build();

        Response response503 = mock(Response.class);
        when(response503.getStatusCode()).thenReturn(503);

        httpClient.expectCall("http://s1.example.com/v1/service", new ConnectException());
        httpClient.expectCall("http://s2.example.com/v1/service", response503);
        httpClient.firstCallNoBodyGenerator();

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response503))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markBad("ConnectException");
        verify(serviceAttempt1).next();
        verify(serviceAttempt2, atLeastOnce()).getUri();
        verify(serviceAttempt2).markBad("503 status code");
        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response503));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s2.example.com/v1/service");
        verifyNoMoreInteractions(serviceAttempt1, serviceAttempt2, serviceAttempt3, responseHandler);
    }

    @Test
    public void testDoesntRetryOnExceptionSingleUseBodyGeneratorUsed()
            throws Exception
    {
        bodySource = spy(new TestingLimitedRetryableSource());
        request = preparePut().setUri(URI.create("v1/service")).setBodySource(bodySource).build();

        Response response503 = mock(Response.class);
        when(response503.getStatusCode()).thenReturn(503);
        ConnectException connectException = new ConnectException();

        httpClient.expectCall("http://s1.example.com/v1/service", response503);
        httpClient.expectCall("http://s2.example.com/v1/service", connectException);
        httpClient.firstCallNoBodyGenerator();

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handleException(any(Request.class), same(connectException))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markBad("503 status code");
        verify(serviceAttempt1).next();
        verify(serviceAttempt2, atLeastOnce()).getUri();
        verify(serviceAttempt2).markBad("ConnectException");
        verify(responseHandler).handleException(requestArgumentCaptor.capture(), same(connectException));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s2.example.com/v1/service");
        verifyNoMoreInteractions(serviceAttempt1, serviceAttempt2, serviceAttempt3, responseHandler);
    }

    @Test
    public void testOnlyOneMaxAttempt()
            throws Exception
    {
        balancingHttpClientConfig.setMaxAttempts(1);
        balancingHttpClient = createBalancingHttpClient();

        assertNoRetry();
    }

    private void assertNoRetry()
            throws Exception
    {
        Response response500 = mock(Response.class);
        when(response500.getStatusCode()).thenReturn(500);

        httpClient.expectCall("http://s1.example.com/v1/service", response500);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response500))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response500));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s1.example.com/v1/service");
    }

    private void assertOneRetry()
            throws Exception
    {
        Response response500 = mock(Response.class);
        when(response500.getStatusCode()).thenReturn(500);

        httpClient.expectCall("http://s1.example.com/v1/service", response500);
        httpClient.expectCall("http://s2.example.com/v1/service", response500);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response500))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response500));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s2.example.com/v1/service");
    }

    private void assertTwoRetries()
            throws Exception
    {
        Response response500 = mock(Response.class);
        when(response500.getStatusCode()).thenReturn(500);

        httpClient.expectCall("http://s1.example.com/v1/service", response500);
        httpClient.expectCall("http://s2.example.com/v1/service", response500);
        httpClient.expectCall("http://s1.example.com/v1/service", response500);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response500))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response500));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s1.example.com/v1/service");
    }

    private void successfulCall()
            throws Exception
    {
        httpClient.expectCall("http://s1.example.com/v1/service", response);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s1.example.com/v1/service");
    }

    @Test
    public void testNoRetryBudget()
            throws Exception
    {
        balancingHttpClientConfig.setRetryBudgetRatio(ZERO).setRetryBudgetMinPerSecond(0);
        balancingHttpClient = createBalancingHttpClient();

        assertNoRetry();
    }

    @Test
    public void testRatioRetryBudget()
            throws Exception
    {
        balancingHttpClientConfig
                .setMinBackoff(new Duration(0, SECONDS))
                .setMaxBackoff(new Duration(0, SECONDS))
                .setMaxAttempts(3)
                .setRetryBudgetRatioPeriod(new Duration(10, SECONDS))
                .setRetryBudgetRatio(BigDecimal.valueOf(0.5))
                .setRetryBudgetMinPerSecond(0);
        balancingHttpClient = createBalancingHttpClient();

        for (int i = 0; i < 100; ++i) {
            assertNoRetry();
            assertOneRetry();
        }
    }

    @Test
    public void testRatioRetryBudgetExpires()
            throws Exception
    {
        balancingHttpClientConfig
                .setMinBackoff(new Duration(0, SECONDS))
                .setMaxBackoff(new Duration(0, SECONDS))
                .setMaxAttempts(3)
                .setRetryBudgetRatioPeriod(new Duration(10, SECONDS))
                .setRetryBudgetRatio(BigDecimal.valueOf(0.5))
                .setRetryBudgetMinPerSecond(0);
        balancingHttpClient = createBalancingHttpClient();

        for (int i = 0; i < 100; ++i) {
            successfulCall();
        }

        for (int i = 0; i < 5; ++i) {
            assertTwoRetries();
        }

        testingTicker.elapseTime(10, SECONDS);
        for (int i = 0; i < 100; ++i) {
            assertNoRetry();
            assertOneRetry();
        }
    }

    @Test
    public void testMinRetryBudget()
            throws Exception
    {
        balancingHttpClientConfig
                .setMinBackoff(new Duration(0, SECONDS))
                .setMaxBackoff(new Duration(0, SECONDS))
                .setMaxAttempts(2)
                .setRetryBudgetRatioPeriod(new Duration(10, SECONDS))
                .setRetryBudgetRatio(ZERO)
                .setRetryBudgetMinPerSecond(1);
        balancingHttpClient = createBalancingHttpClient();

        // Starts out with ten tokens, one for each second in the period.
        for (int i = 0; i < 10; ++i) {
            testingTicker.elapseTime(1, SECONDS);
            assertOneRetry();
        }

        for (int i = 0; i < 100; ++i) {
            assertNoRetry();
            assertNoRetry();
            assertNoRetry();
            testingTicker.elapseTime(1, SECONDS);
            assertOneRetry();
        }
    }

    @Test
    public void testBothMinAndRatioRetryBudget()
            throws Exception
    {
        balancingHttpClientConfig
                .setMinBackoff(new Duration(0, SECONDS))
                .setMaxBackoff(new Duration(0, SECONDS))
                .setMaxAttempts(2)
                .setRetryBudgetRatioPeriod(new Duration(10, SECONDS))
                .setRetryBudgetRatio(BigDecimal.valueOf(0.5))
                .setRetryBudgetMinPerSecond(1);
        balancingHttpClient = createBalancingHttpClient();

        // Starts out with ten tokens, one for each second in the period.
        // Each second we send two requests, which adds another token.
        for (int i = 0; i < 10; ++i) {
            testingTicker.elapseTime(1, SECONDS);
            assertOneRetry();
            assertOneRetry();
        }

        for (int i = 0; i < 100; ++i) {
            assertNoRetry();
            assertOneRetry();
            assertNoRetry();
            assertOneRetry();
            testingTicker.elapseTime(1, SECONDS);
            assertOneRetry();
            assertOneRetry();
        }
    }

    @Test
    public void testSuccessOnLastTry503()
            throws Exception
    {
        Response response503 = mock(Response.class);
        when(response503.getStatusCode()).thenReturn(503);

        httpClient.expectCall("http://s1.example.com/v1/service", new ConnectException());
        httpClient.expectCall("http://s2.example.com/v1/service", response503);
        httpClient.expectCall("http://s1.example.com/v1/service", response);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markBad("ConnectException");
        verify(serviceAttempt1).next();
        verify(serviceAttempt2, atLeastOnce()).getUri();
        verify(serviceAttempt2).markBad("503 status code");
        verify(serviceAttempt2).next();
        verify(serviceAttempt3, atLeastOnce()).getUri();
        verify(serviceAttempt3).markGood();
        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s1.example.com/v1/service");
        verifyNoMoreInteractions(serviceAttempt1, serviceAttempt2, serviceAttempt3, responseHandler);
    }

    @Test
    public void testSuccessOnLastTryException()
            throws Exception
    {
        Response response503 = mock(Response.class);
        when(response503.getStatusCode()).thenReturn(503);

        httpClient.expectCall("http://s1.example.com/v1/service", response503);
        httpClient.expectCall("http://s2.example.com/v1/service", new ConnectException());
        httpClient.expectCall("http://s1.example.com/v1/service", response);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markBad("503 status code");
        verify(serviceAttempt1).next();
        verify(serviceAttempt2, atLeastOnce()).getUri();
        verify(serviceAttempt2).markBad("ConnectException");
        verify(serviceAttempt2).next();
        verify(serviceAttempt3, atLeastOnce()).getUri();
        verify(serviceAttempt3).markGood();
        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s1.example.com/v1/service");
        verifyNoMoreInteractions(serviceAttempt1, serviceAttempt2, serviceAttempt3, responseHandler);
    }

    @Test
    public void testGiveUpOnHttpClientException()
            throws Exception
    {
        Response response503 = mock(Response.class);
        when(response503.getStatusCode()).thenReturn(503);
        ConnectException connectException = new ConnectException();

        httpClient.expectCall("http://s1.example.com/v1/service", new ConnectException());
        httpClient.expectCall("http://s2.example.com/v1/service", response503);
        httpClient.expectCall("http://s1.example.com/v1/service", connectException);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        Exception testException = new Exception("test exception");
        when(responseHandler.handleException(any(Request.class), same(connectException))).thenThrow(testException);

        try {
            String returnValue = balancingHttpClient.execute(request, responseHandler);
            fail("expected exception, got " + returnValue);
        }
        catch (Exception e) {
            assertSame(e, testException);
        }

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markBad("ConnectException");
        verify(serviceAttempt1).next();
        verify(serviceAttempt2, atLeastOnce()).getUri();
        verify(serviceAttempt2).markBad("503 status code");
        verify(serviceAttempt2).next();
        verify(serviceAttempt3, atLeastOnce()).getUri();
        verify(serviceAttempt3).markBad("ConnectException", "Exception");
        verify(responseHandler).handleException(requestArgumentCaptor.capture(), same(connectException));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s1.example.com/v1/service");
        verifyNoMoreInteractions(serviceAttempt1, serviceAttempt2, serviceAttempt3, responseHandler);
    }

    @Test
    public void testGiveUpOnHttpClientExceptionWithDefault()
            throws Exception
    {
        Response response503 = mock(Response.class);
        when(response503.getStatusCode()).thenReturn(503);
        ConnectException connectException = new ConnectException();

        httpClient.expectCall("http://s1.example.com/v1/service", new ConnectException());
        httpClient.expectCall("http://s2.example.com/v1/service", response503);
        httpClient.expectCall("http://s1.example.com/v1/service", connectException);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handleException(any(Request.class), same(connectException))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response");

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markBad("ConnectException");
        verify(serviceAttempt1).next();
        verify(serviceAttempt2, atLeastOnce()).getUri();
        verify(serviceAttempt2).markBad("503 status code");
        verify(serviceAttempt2).next();
        verify(serviceAttempt3, atLeastOnce()).getUri();
        verify(serviceAttempt3).markBad("ConnectException");
        verify(responseHandler).handleException(requestArgumentCaptor.capture(), same(connectException));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s1.example.com/v1/service");
        verifyNoMoreInteractions(serviceAttempt1, serviceAttempt2, serviceAttempt3, responseHandler);
    }

    @Test
    public void testGiveUpOn408Status()
            throws Exception
    {
        Response response503 = mock(Response.class);
        when(response503.getStatusCode()).thenReturn(503);
        Response response408 = mock(Response.class);
        when(response408.getStatusCode()).thenReturn(408);

        httpClient.expectCall("http://s1.example.com/v1/service", new ConnectException());
        httpClient.expectCall("http://s2.example.com/v1/service", response503);
        httpClient.expectCall("http://s1.example.com/v1/service", response408);

        ResponseHandler<String, Exception> responseHandler = mock(ResponseHandler.class);
        when(responseHandler.handle(any(Request.class), same(response408))).thenReturn("test response");

        String returnValue = balancingHttpClient.execute(request, responseHandler);
        assertEquals(returnValue, "test response", "return value from .execute()");

        httpClient.assertDone();

        verify(serviceAttempt1, atLeastOnce()).getUri();
        verify(serviceAttempt1).markBad("ConnectException");
        verify(serviceAttempt1).next();
        verify(serviceAttempt2, atLeastOnce()).getUri();
        verify(serviceAttempt2).markBad("503 status code");
        verify(serviceAttempt2).next();
        verify(serviceAttempt3, atLeastOnce()).getUri();
        verify(serviceAttempt3).markBad("408 status code");
        verify(responseHandler).handle(requestArgumentCaptor.capture(), same(response408));
        assertEquals(requestArgumentCaptor.getValue().getUri().toString(), "http://s1.example.com/v1/service");
        verifyNoMoreInteractions(serviceAttempt1, serviceAttempt2, serviceAttempt3, responseHandler);
    }

    @Test
    public void testCreateAttemptException()
            throws Exception
    {
        serviceBalancer = mock(HttpServiceBalancer.class);
        RuntimeException balancerException = new RuntimeException("test balancer exception");
        when(serviceBalancer.createAttempt()).thenThrow(balancerException);

        balancingHttpClient = createBalancingHttpClient();

        ResponseHandler responseHandler = mock(ResponseHandler.class);
        RuntimeException handlerException = new RuntimeException("test responseHandler exception");
        when(responseHandler.handleException(any(Request.class), any(Exception.class))).thenThrow(handlerException);

        assertHandlerExceptionThrown(responseHandler, handlerException);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(responseHandler).handleException(same(request), captor.capture());
        assertSame(captor.getValue(), balancerException, "Exception passed to ResponseHandler");
        verifyNoMoreInteractions(responseHandler);
    }

    @Test
    public void testNextAttemptException()
            throws Exception
    {
        httpClient.expectCall("http://s1.example.com/v1/service", new ConnectException());

        serviceBalancer = mock(HttpServiceBalancer.class);
        serviceAttempt1 = mock(HttpServiceAttempt.class);
        when(serviceBalancer.createAttempt()).thenReturn(serviceAttempt1);
        when(serviceAttempt1.getUri()).thenReturn(URI.create("http://s1.example.com"));
        RuntimeException balancerException = new RuntimeException("test balancer exception");
        when(serviceAttempt1.next()).thenThrow(balancerException);

        balancingHttpClient = createBalancingHttpClient();

        ResponseHandler responseHandler = mock(ResponseHandler.class);
        RuntimeException handlerException = new RuntimeException("test responseHandler exception");
        when(responseHandler.handleException(any(Request.class), any(Exception.class))).thenThrow(handlerException);

        assertHandlerExceptionThrown(responseHandler, handlerException);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(responseHandler).handleException(same(request), captor.capture());
        assertSame(captor.getValue(), balancerException, "Exception passed to ResponseHandler");
        verifyNoMoreInteractions(responseHandler);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUriWithScheme()
            throws Exception
    {
        request = preparePut().setUri(new URI("http", null, "/v1/service", null)).setBodySource(bodySource).build();
        issueRequest();
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* has a host component")
    public void testUriWithHost()
            throws Exception
    {
        request = preparePut().setUri(new URI(null, "example.com", "v1/service", null)).setBodySource(bodySource).build();
        issueRequest();
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* path starts with '/'")
    public void testUriWithAbsolutePath()
            throws Exception
    {
        request = preparePut().setUri(new URI(null, null, "/v1/service", null)).setBodySource(bodySource).build();
        issueRequest();
    }


    @Test(expectedExceptions = CustomError.class)
    public void testHandlesUndeclaredThrowable()
            throws Exception
    {
        httpClient.expectCall("http://s1.example.com/v1/service", response);

        balancingHttpClient.execute(request, new ThrowErrorResponseHandler());
    }

    public static class ThrowErrorResponseHandler implements ResponseHandler<String, Exception>
    {
        @Override
        public String handleException(Request request, Exception exception)
        {
            throw new UnsupportedOperationException("not yet implemented");
        }

        @Override
        public String handle(Request request, Response response)
        {
            throw new CustomError();
        }
    }

    private static class CustomError
            extends Error
    {
    }

    private static class TestingLimitedRetryableSource
        extends StaticBodyGenerator
        implements LimitedRetryable
    {
        private final AtomicBoolean used = new AtomicBoolean(false);

        protected TestingLimitedRetryableSource()
        {
            super(new byte[0]);
        }

        @Override
        public byte[] getBody()
        {
            if (used.getAndSet(true)) {
                fail("TestingLimitedRetryableSource called twice");
            }
            return super.getBody();
        }

        @Override
        public boolean isRetryable()
        {
            return !used.get();
        }
    }
}
