module com.proofpoint.configuration {
    exports com.proofpoint.configuration;
    exports com.proofpoint.configuration.testing;
    requires com.google.guice;
    requires com.google.common;
    requires jakarta.annotation;
    requires jakarta.validation;
    requires org.hibernate.validator;
    requires auto.value.annotations;
    requires jakarta.inject;
    requires com.fasterxml.jackson.databind;
    requires net.bytebuddy;
    requires org.testng;
    opens com.proofpoint.configuration to org.hibernate.validator, org.testng, com.google.guice, net.bytebuddy;
}