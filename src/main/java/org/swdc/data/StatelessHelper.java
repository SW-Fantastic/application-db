package org.swdc.data;

import jakarta.persistence.*;
import org.hibernate.proxy.HibernateProxy;
import org.swdc.data.anno.StatelessIgnore;
import org.swdc.data.anno.StatelessPrivate;
import org.swdc.ours.common.type.ClassTypeAndMethods;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 本类的目的是通过反射达成对原有的Entity类进行直接的复用，
 * 将Entity按照一定原则进行属性复制从而生成一个同类型的DTO对象
 * 而不需要单独提供一个DTO。
 *
 * 说白了是对JPA的Entity的一个Copy或者Clone。
 */
public class StatelessHelper {

    /**
     * 复制此Entity的内容，返回一个与Hibernate代理无关的DTO对象。
     *
     * 如果发现部分字段本应被Copy但是返回的对象中此字段为空，
     * 请检查调用者是否处于事务处理的Aspect中，如果没有，请为它添加
     * Transaction注解来开启事务处理。
     *
     * @param entity 被Hibernate管理的数据对象
     * @param <T> 数据对象的类型
     * @return 复制后的DTO对象
     */
    public static <T> T stateless(T entity) {
        Field idField = getIdField(entity.getClass());
        if (idField == null) {
            throw new RuntimeException("can not found a field annotated with Id");
        }
        return stateless(entity,new HashMap<>());
    }

    /**
     * 创建Entity的脱离Hibernate代理的普通数据对象
     *
     * 如果本应被Copy的字段在返回的对象中为空，请检查调用本方法的
     * 位置是否处于事务处理的Aspect中，如果没有，请为使用它的方法添加
     * Transaction注解开启事务处理。
     *
     * @param entity 被Hibernate管理的Entity
     * @param typedReversId 反向复制的ID集合，用以防止无限递归。
     *
     * @see StatelessIgnore
     *
     * @param <T> 对象的类型
     * @return 复制后的EntityDTO
     */
    private static <T> T stateless(T entity, Map<Class,Deque<Object>> typedReversId) {
        try {
            if (entity == null) {
                return null;
            }

            Class type = entity.getClass();
            Field idField = getIdField(type);
            idField.setAccessible(true);
            Object entityId = idField.get(entity);

            if (entity.getClass().getName().contains("HibernateProxy")) {
                HibernateProxy proxy = (HibernateProxy)entity;
                type = proxy.getHibernateLazyInitializer().getPersistentClass();
            }
            T instance = (T)type.getConstructor().newInstance();
            Class currentType = type;
            while (currentType != Object.class) {
                Field[] fields = currentType.getDeclaredFields();
                for (Field field: fields) {
                    // Hibernate的代理会使直接操作字段变得很麻烦，
                    // 所以使用对应的Getter和Setter进行操作。
                    Method getter = ClassTypeAndMethods.extractGetter(field);
                    Method setter = ClassTypeAndMethods.extractSetter(field);
                    if (getter == null || setter == null) {
                        continue;
                    }

                    Deque<Object> reversId = typedReversId.computeIfAbsent(
                            field.getType(), k -> new ArrayDeque<>()
                    );

                    if (field.getAnnotation(StatelessIgnore.class) != null) {
                        StatelessIgnore ignore = field.getAnnotation(StatelessIgnore.class);
                        if (ignore.reverse() && reversId.contains(entityId)) {
                           continue;
                        } else if (!ignore.reverse()){
                            continue;
                        }
                    }
                    if (field.getAnnotation(ManyToOne.class) != null) {

                        if (reversId.contains(entityId)) {
                            continue;
                        }

                        Object obj = getter.invoke(entity);

                        reversId.push(entityId);
                        setter.invoke(instance,stateless(obj,typedReversId));
                        reversId.pop();

                    } else if (field.getType().getAnnotation(Entity.class) != null) {

                        if (field.getAnnotation(OneToOne.class) != null) {

                            if (reversId.contains(entityId)) {
                                continue;
                            }

                            reversId.push(entityId);
                            Object target = getter.invoke(entity);
                            setter.invoke(instance, stateless(target,typedReversId));
                            reversId.pop();

                        } else {
                            Object target = getter.invoke(entity);
                            setter.invoke(instance, stateless(target,typedReversId));
                        }

                    } else if (field.getAnnotation(OneToMany.class) != null || field.getAnnotation(ManyToMany.class) != null) {
                        if (reversId.contains(entityId)) {
                            continue;
                        }
                        reversId.push(entityId);
                        Collection<Object> collection = (Collection) getter.invoke(entity);

                        if (List.class.isAssignableFrom(field.getType())) {
                            if (collection == null) {
                                setter.invoke(instance,Collections.emptyList());
                                continue;
                            }
                            List rest = collection.stream()
                                    .map(e -> stateless(e,typedReversId))
                                    .collect(Collectors.toList());
                            setter.invoke(instance,rest);
                        } else if (Set.class.isAssignableFrom(field.getType())){
                            if (collection == null) {
                                setter.invoke(instance,Collections.emptySet());
                                continue;
                            }
                            Set rest = collection.stream()
                                    .map(e -> stateless(e,typedReversId))
                                    .collect(Collectors.toSet());
                            setter.invoke(instance,rest);
                        }
                        reversId.pop();
                    } else {
                        setter.invoke(instance,getter.invoke(entity));
                    }
                }

                currentType = currentType.getSuperclass();
                if (currentType == Object.class) {
                    break;
                }

                Annotation anno = currentType.getAnnotation(MappedSuperclass.class);
                if (anno == null) {
                    break;
                }

            }

            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将私密字段清空，防止泄露敏感信息。
     * 被注解@StatelessPrivate注解的字段是敏感字段，它们会正常出现在Stateless的结果中，
     * 这是为了方便系统内部使用，但是不应该出现在对外开放的接口中，所以应当使用这个方法清空它们。
     *
     * @param entity 实体对象
     * @return entity对象本身，用于链式调用。
     * @param <T> 实体对象的类型
     */
    public static <T> T safety(T entity) {
        try {
            if (entity == null) {
                return null;
            }

            Class type = entity.getClass();
            Class currentType = type;
            while (currentType != Object.class) {
                Field[] fields = currentType.getDeclaredFields();
                for (Field field: fields) {

                    Method setter = ClassTypeAndMethods.extractSetter(field);
                    Method getter = ClassTypeAndMethods.extractGetter(field);
                    StatelessPrivate statelessPrivate = field.getAnnotation(StatelessPrivate.class);
                    if (setter == null || getter == null) {
                        continue;
                    }

                    if (statelessPrivate != null) {
                        setter.invoke(entity,(Object) null);
                    } else {
                        if (ClassTypeAndMethods.isBasicType(field.getType()) || ClassTypeAndMethods.isBoxedType(field.getType())) {
                            continue;
                        } else {
                            Object data = getter.invoke(entity);
                            if (data == null) {
                                continue;
                            }
                            if (Collection.class.isAssignableFrom(field.getType())) {
                                // 获取集合的泛型类型
                                List<Class> param = ClassTypeAndMethods.getFieldParameters(field);
                                if (!param.isEmpty()) {
                                    // 如果是基本类型或者包装类，则忽略
                                    if (ClassTypeAndMethods.isBoxedType(param.get(0)) || ClassTypeAndMethods.isBasicType(param.get(0))) {
                                        continue;
                                    }
                                }
                                // 如果是集合，则递归处理每个元素
                                Collection collection = (Collection) data;
                                for (Object o : collection) {
                                    if (o == null) {
                                        continue;
                                    }
                                    safety(o);
                                }
                            } else {
                                safety(data);
                            }
                        }
                    }
                }
                currentType = currentType.getSuperclass();
            }

            return entity;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static Field getIdField(Class target) {
        Class clazz = target;
        while (clazz != null) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                Id id = field.getAnnotation(Id.class);
                if (id == null) {
                    continue;
                } else {
                    return field;
                }
            }
            clazz = clazz.getSuperclass();
            if (clazz == Object.class) {
                return null;
            }
        }
        return null;
    }


}
