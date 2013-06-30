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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@SuppressWarnings({"unchecked", "deprecation"})
public class TestServiceInventory
{
    @Test
    public void testNullServiceInventory()
            throws Exception
    {
        HttpServiceBalancerImpl balancer = mock(HttpServiceBalancerImpl.class);
        ServiceInventory serviceInventory = new ServiceInventory(new ServiceInventoryConfig(),
                new DiscoveryClientConfig(), new NodeInfo("test"),
                JsonCodec.jsonCodec(ServiceDescriptorsRepresentation.class),
                balancer);

        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 0);
        serviceInventory.updateServiceInventory();
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 0);

        verify(balancer, never()).updateHttpUris(any(Set.class));
    }

    @Test
    public void testDeprecatedServiceInventory()
            throws Exception
    {
        HttpServiceBalancerImpl balancer = mock(HttpServiceBalancerImpl.class);
        ServiceInventory serviceInventory = new ServiceInventory(new ServiceInventoryConfig(),
                new DiscoveryClientConfig().setDiscoveryServiceURI(URI.create("https://example.com:4111")),
                new NodeInfo("test"),
                JsonCodec.jsonCodec(ServiceDescriptorsRepresentation.class),
                balancer);

        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        verify(balancer).updateHttpUris(captor.capture());
        assertEquals(Iterables.size(captor.getValue()), 1);

        URI uri = (URI) Iterables.getOnlyElement(captor.getValue());
        assertEquals(uri, URI.create("https://example.com:4111"));

        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 0);
        serviceInventory.updateServiceInventory();
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 0);
    }

    @Test
    public void testFileServiceInventory()
            throws Exception
    {
        HttpServiceBalancerImpl balancer = mock(HttpServiceBalancerImpl.class);
        ServiceInventoryConfig serviceInventoryConfig = new ServiceInventoryConfig()
                .setServiceInventoryUri(Resources.getResource("service-inventory.json").toURI());

        ServiceInventory serviceInventory = new ServiceInventory(serviceInventoryConfig,
                new DiscoveryClientConfig().setDiscoveryServiceURI(URI.create("http://example.com:4111")),
                new NodeInfo("test"),
                JsonCodec.jsonCodec(ServiceDescriptorsRepresentation.class),
                balancer);

        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 3);
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery")), 2);
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery", "general")), 2);

        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        verify(balancer).updateHttpUris(captor.capture());
        ImmutableSet<URI> expectedUris = ImmutableSet.of(URI.create("http://localhost:8411"), URI.create("http://localhost:8412"));
        assertEquals(captor.getValue(), expectedUris);

        serviceInventory.updateServiceInventory();
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 3);
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery")), 2);
        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors("discovery", "general")), 2);

        verify(balancer, times(2)).updateHttpUris(captor.capture());
        assertEquals(captor.getValue(), expectedUris);
    }

    @Test
    public void testEmptyServiceList()
            throws Exception
    {
        HttpServiceBalancerImpl balancer = mock(HttpServiceBalancerImpl.class);
        ServiceInventoryConfig serviceInventoryConfig = new ServiceInventoryConfig()
                .setServiceInventoryUri(Resources.getResource("service-inventory-empty.json").toURI());

        try {
            ServiceInventory serviceInventory = new ServiceInventory(serviceInventoryConfig,
                    new DiscoveryClientConfig(),
                    new NodeInfo("test"),
                    JsonCodec.jsonCodec(ServiceDescriptorsRepresentation.class),
                    balancer);
            fail("RuntimeException expected");
        }
        catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Invalid ServiceDescriptorList: environment is empty"));
            assertTrue(e.getMessage().contains("Invalid ServiceDescriptorList: serviceDescriptors is null"));
        }
    }

    @Test
    public void testInvalidEnvironment()
            throws Exception
    {
        HttpServiceBalancerImpl balancer = mock(HttpServiceBalancerImpl.class);
        ServiceInventoryConfig serviceInventoryConfig = new ServiceInventoryConfig()
                .setServiceInventoryUri(Resources.getResource("service-inventory.json").toURI());

        try {
            ServiceInventory serviceInventory = new ServiceInventory(serviceInventoryConfig,
                    new DiscoveryClientConfig(),
                    new NodeInfo("test123"),
                    JsonCodec.jsonCodec(ServiceDescriptorsRepresentation.class),
                    balancer);
            fail("RuntimeException expected");
        }
        catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Expected service inventory environment to be test123, but was test"));
        }
    }

    @Test
    public void testInvalidDescriptors()
            throws Exception
    {
        HttpServiceBalancerImpl balancer = mock(HttpServiceBalancerImpl.class);
        ServiceInventoryConfig serviceInventoryConfig = new ServiceInventoryConfig()
                .setServiceInventoryUri(Resources.getResource("service-inventory-invalid.json").toURI());

        try {
            ServiceInventory serviceInventory = new ServiceInventory(serviceInventoryConfig,
                    new DiscoveryClientConfig(),
                    new NodeInfo("test"),
                    JsonCodec.jsonCodec(ServiceDescriptorsRepresentation.class),
                    balancer);
            fail("RuntimeException expected");
        }
        catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: id is null"));
            assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: nodeId is null ServiceDescriptorRepresentation{id=370af416-"));
            assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: type is null ServiceDescriptorRepresentation{id=370af416-"));
            assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: pool is null ServiceDescriptorRepresentation{id=370af416-"));
            assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: location is null ServiceDescriptorRepresentation{id=370af416-"));
            assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: state is null ServiceDescriptorRepresentation{id=370af416-"));
            assertTrue(e.getMessage().contains("Invalid ServiceDescriptor: properties is null ServiceDescriptorRepresentation{id=370af416-"));
        }
    }

    @Test
    public void testPartialDescriptors()
            throws Exception
    {
        HttpServiceBalancerImpl balancer = mock(HttpServiceBalancerImpl.class);
        ServiceInventoryConfig serviceInventoryConfig = new ServiceInventoryConfig()
                .setServiceInventoryUri(Resources.getResource("service-inventory-partial.json").toURI());

        ServiceInventory serviceInventory = new ServiceInventory(serviceInventoryConfig,
                new DiscoveryClientConfig(),
                new NodeInfo("test"),
                JsonCodec.jsonCodec(ServiceDescriptorsRepresentation.class),
                balancer);

        assertEquals(Iterables.size(serviceInventory.getServiceDescriptors()), 1);
    }
}
