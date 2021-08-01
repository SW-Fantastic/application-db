package org.swdc.data;

import jakarta.inject.Provider;

import java.util.List;
import java.util.Set;

public abstract class EMFProvider implements Provider<EMFProviderFactory> {

    private EMFProviderFactory factory;

    @Override
    public EMFProviderFactory get() {
        if (factory != null) {
            return factory;
        }
        factory = new EMFProviderFactory(registerEntities(),this.getComputeURL());
        return factory;
    }

    public abstract List<Class> registerEntities();

    public String getComputeURL() {
        return null;
    }

}
