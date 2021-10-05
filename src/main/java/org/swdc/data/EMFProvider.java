package org.swdc.data;

import jakarta.inject.Provider;
import org.hibernate.dialect.Dialect;
import org.swdc.data.anno.Configure;

import java.util.List;
import java.util.Set;

public abstract class EMFProvider implements Provider<EMFProviderFactory> {

    private EMFProviderFactory factory;

    @Override
    public EMFProviderFactory get() {
        if (factory != null) {
            return factory;
        }
        factory = new EMFProviderFactory(registerEntities());
        factory.initialize();

        Configure configure = this.getClass().getAnnotation(Configure.class);
        if (configure != null) {
            if (!configure.url().isEmpty()) {
                factory.url(configure.url());
            }

            if (configure.driver() != Object.class && configure.dialect() != Dialect.class) {
                factory.driver(configure.driver().getName(),configure.dialect().getName());
            }
        }

        return factory;
    }

    public abstract List<Class> registerEntities();


}
