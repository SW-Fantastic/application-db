package org.swdc.data;

import jakarta.annotation.Resource;
import jakarta.inject.Named;
import org.swdc.data.anno.Repository;
import org.swdc.dependency.DependencyContext;
import org.swdc.dependency.DependencyScope;
import org.swdc.ours.common.annotations.AnnotationDescription;
import org.swdc.ours.common.annotations.AnnotationDescriptions;
import org.swdc.ours.common.annotations.Annotations;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RepositoryManager implements DependencyScope {

    private Map<Class,List<Object>> typedEntities = new ConcurrentHashMap<>();
    private Map<String,Object> namedEntities = new ConcurrentHashMap<>();

    private DependencyContext context;

    @Override
    public <T> T getByClass(Class<T> clazz) {
        AnnotationDescriptions descs = Annotations.getAnnotations(clazz);
        List<Object> list = typedEntities.get(clazz);
        if (list != null && list.size() > 0) {
            if (list.size() > 1) {
                throw new RuntimeException("多个组件，请使用其他方法获取。");
            }
            return (T)list.get(0);
        }

        DefaultRepository repository = new DefaultRepository();
        ParameterizedType parameterizedType = (ParameterizedType) clazz.getGenericInterfaces()[0];

        Class entityClass = (Class) parameterizedType.getActualTypeArguments()[0];

        repository.init(this.context.getByClass(EMFProviderFactory.class), entityClass);
        JPARepository jpaRepository = (JPARepository) Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{clazz},repository);

        AnnotationDescription named = Annotations.findAnnotationIn(descs,Named.class);
        AnnotationDescription resource = Annotations.findAnnotationIn(descs,Resource.class);

        String name =  clazz.getName();
        if (named != null) {
            name = named.getProperty(String.class,"value");
        }
        if (resource != null) {
            name = resource.getProperty(String.class,"name");
        }
        this.put(name,clazz,jpaRepository);

        return (T)jpaRepository;
    }

    @Override
    public <T> T getByName(String name) {
        return (T)this.namedEntities.get(name);
    }

    @Override
    public <T> List<T> getByAbstract(Class<T> parent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Object> getAllComponent() {
        return typedEntities.values()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    @Override
    public Class getScopeType() {
        return Repository.class;
    }

    @Override
    public <T> T put(String name, Class clazz, Class multiple, T component) {

        component = this.put(name,clazz,component);

        if (multiple != null) {
           throw new RuntimeException("不支持多组件。");
        }

        return component;
    }

    @Override
    public <T> T put(String name, Class clazz, T component) {
        List<Object> list = typedEntities.getOrDefault(clazz,new ArrayList<>());
        list.add(component);
        typedEntities.put(clazz,list);

        if (!name.equals(clazz.getName())) {
            namedEntities.put(name,component);
        }
        return component;
    }

    @Override
    public void setContext(DependencyContext context) {
        this.context = context;
    }
}
