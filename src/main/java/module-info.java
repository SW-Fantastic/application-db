module swdc.application.data {

    requires swdc.application.dependency;
    requires swdc.application.configs;
    requires org.slf4j;
    requires jakarta.inject;
    requires org.hibernate.orm.core;
    requires jakarta.annotation;
    requires com.fasterxml.classmate;
    requires java.xml.bind;
    requires swdc.commons;
    requires jakarta.persistence;

    exports org.swdc.data.anno;
    exports org.swdc.data;

    opens org.swdc.data to swdc.application.dependency;



}