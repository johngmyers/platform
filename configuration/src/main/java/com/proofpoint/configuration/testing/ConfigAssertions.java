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
package com.proofpoint.configuration.testing;

import com.google.common.collect.ImmutableSet;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationMetadata;
import com.proofpoint.configuration.ConfigurationMetadata.AttributeMetadata;
import com.proofpoint.configuration.MapClasses;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.google.common.collect.Sets.newConcurrentHashSet;
import static com.proofpoint.configuration.ConfigurationMetadata.isConfigClass;
import static java.lang.reflect.Modifier.isPublic;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public final class ConfigAssertions
{
    private static final Method GET_RECORDING_CONFIG_METHOD;

    static {
        try {
            GET_RECORDING_CONFIG_METHOD = $$RecordingConfigProxy.class.getMethod("$$getRecordedConfig");
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private ConfigAssertions()
    {
    }

    public static <T> void assertFullMapping(Map<String, String> properties, T expected)
    {
        assertNotNull(properties, "properties");
        assertNotNull(expected, "expected");

        Class<T> configClass = getClass(expected);
        ConfigurationMetadata<T> metadata = ConfigurationMetadata.getValidConfigurationMetadata(configClass);

        // verify all supplied properties are supported and not deprecated
        assertPropertiesSupported(metadata, properties.keySet(), false);

        // verify that every (non-deprecated) property is tested
        Set<String> untestedProperties = new TreeSet<>();
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            final String property = attribute.getInjectionPoint().getProperty();
            if (property != null) {
                if (!isPropertyTested(property, attribute, properties.keySet())) {
                    untestedProperties.add(property);
                }
            }
        }
        if (!untestedProperties.isEmpty()) {
            fail("Untested properties: " + untestedProperties);
        }

        // verify that none of the values are the same as a default for the configuration
        T actual = newInstance(configClass, properties);
        T defaultInstance = newDefaultInstance(configClass);
        assertAttributesNotEqual(metadata, actual, defaultInstance);

        // verify that a configuration object created from the properties is equivalent to the expected object
        assertAttributesEqual(metadata, actual, expected);
    }

    private static boolean isPropertyTested(String property, AttributeMetadata attribute, Set<String> testedProperties)
    {
        final MapClasses configMap = attribute.getMapClasses();
        if (configMap == null) {
            return testedProperties.contains(property);
        }
        if (isConfigClass(configMap.getValue())) {
            for (String testedProperty : testedProperties) {
                if (testedProperty.startsWith(property) &&
                        testedProperty.charAt(property.length()) == '.' &&
                        testedProperty.substring(property.length() + 1).contains(".")) {
                    return true;
                }
            }
        }
        else {
            for (String testedProperty : testedProperties) {
                if (testedProperty.startsWith(property) &&
                        testedProperty.charAt(property.length()) == '.' &&
                        !testedProperty.substring(property.length() + 1).contains(".")) {
                    return true;
                }
            }
        }
        return false;
    }

    @SafeVarargs
    public static <T> void assertLegacyEquivalence(Class<T> configClass, Map<String, String> currentProperties, Map<String, String>... oldPropertiesList)
    {
        assertNotNull(configClass, "configClass");
        assertNotNull(currentProperties, "currentProperties");
        assertNotNull(oldPropertiesList, "oldPropertiesList");

        ConfigurationMetadata<T> metadata = ConfigurationMetadata.getValidConfigurationMetadata(configClass);

        // verify all current properties are supported and not deprecated
        assertPropertiesSupported(metadata, currentProperties.keySet(), false);

        // verify all old properties are supported (deprecation allowed)
        for (Map<String, String> evenOlderProperties : oldPropertiesList) {
            assertPropertiesSupported(metadata, evenOlderProperties.keySet(), true);
        }

        // verify that all deprecated properties are tested
        Set<String> suppliedDeprecatedProperties = new TreeSet<>();
        for (Map<String, String> evenOlderProperties : oldPropertiesList) {
            suppliedDeprecatedProperties.addAll(evenOlderProperties.keySet());
        }
        TreeSet<String> untestedDeprecatedProperties = new TreeSet<>();
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            for (ConfigurationMetadata.InjectionPointMetaData deprecated : attribute.getLegacyInjectionPoints()) {
                if (!isPropertyTested(deprecated.getProperty(), attribute, suppliedDeprecatedProperties)) {
                    untestedDeprecatedProperties.add(deprecated.getProperty());
                }
            }
        }
        if (!untestedDeprecatedProperties.isEmpty()) {
            fail("Untested deprecated properties: " + untestedDeprecatedProperties);
        }

        // verify property sets create equivalent configurations
        T currentConfiguration = newInstance(configClass, currentProperties);
        for (Map<String, String> evenOlderProperties : oldPropertiesList) {
            T evenOlderConfiguration = newInstance(configClass, evenOlderProperties);
            assertAttributesEqual(metadata, currentConfiguration, evenOlderConfiguration);
        }
    }

    private static void assertPropertiesSupported(ConfigurationMetadata<?> metadata, Set<String> propertyNames, boolean allowDeprecatedProperties)
    {
        Set<String> unsupportedProperties = new TreeSet<>(propertyNames);
        Set<String> deprecatedProperties = new TreeSet<>(propertyNames);
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            final String property = attribute.getInjectionPoint().getProperty();
            final MapClasses mapClasses = attribute.getMapClasses();
            if (property != null) {
                markPropertySupported(property, mapClasses, unsupportedProperties, deprecatedProperties);
            }
            for (ConfigurationMetadata.InjectionPointMetaData deprecated : attribute.getLegacyInjectionPoints()) {
                markPropertySupported(deprecated.getProperty(), mapClasses, unsupportedProperties, null);
            }
        }

        if (!unsupportedProperties.isEmpty()) {
            fail("Properties are not consumed by any configuration attribute: " + unsupportedProperties);
        }

        // check for usage of deprecated properties
        if (!allowDeprecatedProperties && !deprecatedProperties.isEmpty()) {
            fail("Deprecated properties in current properties map: " + deprecatedProperties);
        }
    }

    private static void markPropertySupported(String property, MapClasses mapClasses, Set<String> unsupportedProperties, Set<String> deprecatedProperties)
    {
        if (mapClasses == null) {
            if (deprecatedProperties != null) {
                deprecatedProperties.remove(property);
            }
            unsupportedProperties.remove(property);
        }
        else if (isConfigClass(mapClasses.getValue())) {
            for (Iterator<String> iterator = unsupportedProperties.iterator(); iterator.hasNext(); ) {
                String unsupportedProperty = iterator.next();
                if (unsupportedProperty.startsWith(property) &&
                        unsupportedProperty.charAt(property.length()) == '.' &&
                        unsupportedProperty.substring(property.length() + 1).contains(".")) {
                    if (deprecatedProperties != null) {
                        deprecatedProperties.remove(unsupportedProperty);
                    }
                    iterator.remove();
                }
            }
        }
        else {
            for (Iterator<String> iterator = unsupportedProperties.iterator(); iterator.hasNext(); ) {
                String unsupportedProperty = iterator.next();
                if (unsupportedProperty.startsWith(property) &&
                        unsupportedProperty.charAt(property.length()) == '.' &&
                        !unsupportedProperty.substring(property.length() + 1).contains(".")) {
                    if (deprecatedProperties != null) {
                        deprecatedProperties.remove(unsupportedProperty);
                    }
                    iterator.remove();
                }
            }
        }
    }

    private static <T> void assertAttributesEqual(ConfigurationMetadata<T> metadata, T actual, T expected)
    {
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            Method getter = attribute.getGetter();
            if (getter == null) {
                continue;
            }
            Object actualAttributeValue = invoke(actual, getter);
            Object expectedAttributeValue = invoke(expected, getter);
            assertEquals(actualAttributeValue, expectedAttributeValue, "Value parsed from property for attribute " + attribute.getName());
        }
    }

    private static <T> void assertAttributesNotEqual(ConfigurationMetadata<T> metadata, T actual, T expected)
    {
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            Method getter = attribute.getGetter();
            if (getter == null) {
                continue;
            }
            Object actualAttributeValue = invoke(actual, getter);
            Object expectedAttributeValue = invoke(expected, getter);
            assertNotEquals(actualAttributeValue, expectedAttributeValue, "Attribute " + attribute.getName() + " must be tested with non-default value:");
        }
    }

    public static <T> void assertRecordedDefaults(T recordedConfig)
    {
        $$RecordedConfigData<T> recordedConfigData = getRecordedConfig(recordedConfig);
        Set<Method> invokedMethods = recordedConfigData.getInvokedMethods();

        T config = recordedConfigData.getInstance();

        Class<T> configClass = getClass(config);
        ConfigurationMetadata<?> metadata = ConfigurationMetadata.getValidConfigurationMetadata(configClass);

        // assert class has no package-private config attributes
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            if (!isPublic(attribute.getInjectionPoint().getSetter().getModifiers())) {
                fail("Cannot assertRecordedDefaults() on non-public config setter: " + attribute.getInjectionPoint().getSetter().getName());
            }
            if (!isPublic(attribute.getGetter().getModifiers())) {
                fail("Cannot assertRecordedDefaults() on non-public config getter: " + attribute.getGetter().getName());
            }
        }

        // collect information about the attributes that have been set
        Map<String, Object> attributeValues = new TreeMap<>();
        Set<String> setDeprecatedAttributes = new TreeSet<>();
        Set<Method> validSetterMethods = new HashSet<>();
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            if (attribute.getInjectionPoint().getProperty() != null) {
                validSetterMethods.add(attribute.getInjectionPoint().getSetter());
            }

            if (invokedMethods.contains(attribute.getInjectionPoint().getSetter())) {
                if (attribute.getInjectionPoint().getProperty() != null) {
                    Object value = invoke(config, attribute.getGetter());
                    attributeValues.put(attribute.getName(), value);
                }
                else {
                    setDeprecatedAttributes.add(attribute.getName());
                }
            }
        }

        // verify no deprecated attribute setters have been called
        if (!setDeprecatedAttributes.isEmpty()) {
            fail("Invoked deprecated attribute setter methods: " + setDeprecatedAttributes);
        }

        // verify no other methods have been set
        if (!validSetterMethods.containsAll(invokedMethods)) {
            Set<Method> invalidInvocations = new HashSet<>(invokedMethods);
            invalidInvocations.removeAll(validSetterMethods);
            fail("Invoked setter without @Config: " + invalidInvocations);
        }

        // verify all supplied attributes are supported
        if (!metadata.getAttributes().keySet().containsAll(attributeValues.keySet())) {
            TreeSet<String> unsupportedAttributes = new TreeSet<>(attributeValues.keySet());
            unsupportedAttributes.removeAll(metadata.getAttributes().keySet());
            fail("Unsupported attributes: " + unsupportedAttributes);
        }

        // verify all supplied attributes are supported not deprecated
        Set<String> nonDeprecatedAttributes = new TreeSet<>();
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            if (attribute.getInjectionPoint().getProperty() != null) {
                nonDeprecatedAttributes.add(attribute.getName());
            }
        }
        if (!nonDeprecatedAttributes.containsAll(attributeValues.keySet())) {
            TreeSet<String> unsupportedAttributes = new TreeSet<>(attributeValues.keySet());
            unsupportedAttributes.removeAll(nonDeprecatedAttributes);
            fail("Deprecated attributes: " + unsupportedAttributes);
        }

        // verify all attributes are tested
        if (!attributeValues.keySet().containsAll(nonDeprecatedAttributes)) {
            TreeSet<String> untestedAttributes = new TreeSet<>(nonDeprecatedAttributes);
            untestedAttributes.removeAll(attributeValues.keySet());
            fail("Untested attributes: " + untestedAttributes);
        }

        // create an uninitialized default instance
        T actual = newDefaultInstance(configClass);

        // verify each attribute is either the supplied default value
        for (AttributeMetadata attribute : metadata.getAttributes().values()) {
            Method getter = attribute.getGetter();
            if (getter == null) {
                continue;
            }
            Object actualAttributeValue = invoke(actual, getter);
            Object expectedAttributeValue = attributeValues.get(attribute.getName());

            assertEquals(actualAttributeValue, expectedAttributeValue, "Default value for " + attribute.getName());
        }
    }

    public static <T> T recordDefaults(Class<T> type)
    {
        Class<? extends T> loaded = new ByteBuddy()
                .subclass(type)
                .implement($$RecordingConfigProxy.class)
                .method(ElementMatchers.any())
                .intercept(createInvocationHandler(type))
                .make()
                .load(type.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
        try {
            return loaded.getConstructor().newInstance();
        }
        catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to instantiate proxy class for " + type.getName(), e);
        }
    }

    @SuppressWarnings("ObjectEquality")
    private static <T> InvocationHandlerAdapter createInvocationHandler(Class<T> type)
    {
        final T instance = newDefaultInstance(type);
        Set<Method> invokedMethods = newConcurrentHashSet();

        return InvocationHandlerAdapter.of((proxy, method, args) -> {
            if (GET_RECORDING_CONFIG_METHOD.equals(method)) {
                return new $$RecordedConfigData<>(instance, ImmutableSet.copyOf(invokedMethods));
            }

            invokedMethods.add(method);

            Object result = method.invoke(instance, args);
            if (result == instance) {
                return proxy;
            }
            return result;
        });
    }

    static <T> $$RecordedConfigData<T> getRecordedConfig(T config)
    {
        if (!(config instanceof $$RecordingConfigProxy)) {
            throw new IllegalArgumentException("Configuration was not created with the recordDefaults method");
        }
        //noinspection unchecked
        return (($$RecordingConfigProxy<T>) config).$$getRecordedConfig();
    }

    @SuppressWarnings("checkstyle:TypeName")
    public static class $$RecordedConfigData<T>
    {
        private final T instance;
        private final Set<Method> invokedMethods;

        public $$RecordedConfigData(T instance, Set<Method> invokedMethods)
        {
            this.instance = instance;
            this.invokedMethods = Set.copyOf(invokedMethods);
        }

        public T getInstance()
        {
            return instance;
        }

        public Set<Method> getInvokedMethods()
        {
            return invokedMethods;
        }
    }

    @SuppressWarnings("checkstyle:TypeName")
    public interface $$RecordingConfigProxy<T>
    {
        $$RecordedConfigData<T> $$getRecordedConfig();
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> getClass(T object)
    {
        return (Class<T>) object.getClass();
    }

    private static <T> T newInstance(Class<T> configClass, Map<String, String> properties)
    {
        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        return configurationFactory.build(configClass);
    }

    private static <T> T newDefaultInstance(Class<T> configClass)
    {
        try {
            return configClass.getDeclaredConstructor().newInstance();
        }
        catch (Exception e) {
            throw new AssertionError(String.format("Exception creating default instance of %s", configClass.getName()), e);
        }
    }

    private static <T> Object invoke(T actual, Method getter)
    {
        try {
            return getter.invoke(actual);
        }
        catch (ReflectiveOperationException e) {
            throw new AssertionError("Exception invoking " + getter.toGenericString(), e);
        }
    }
}
