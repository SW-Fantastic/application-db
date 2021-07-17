module swdc.application.data {

    //requires swdc.application.configs;
    requires swdc.application.dependency;
    requires hibernate.entitymanager;
    requires java.persistence;
    requires slf4j.api;
    requires jakarta.inject;
    requires org.hibernate.orm.core;
    requires jakarta.annotation;
    requires com.fasterxml.classmate;
    requires java.xml.bind;

    exports org.swdc.data.anno;
    exports org.swdc.data;

}