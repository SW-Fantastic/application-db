package org.swdc.data;

import org.hibernate.proxy.HibernateProxyHelper;
import org.swdc.data.anno.StatelessIgnore;
import org.swdc.ours.common.type.ClassTypeAndMethods;

import javax.persistence.*;
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
        return stateless(entity,new ArrayDeque<>());
    }

    /**
     * 创建Entity的脱离Hibernate代理的普通数据对象
     *
     * 如果本应被Copy的字段在返回的对象中为空，请检查调用本方法的
     * 位置是否处于事务处理的Aspect中，如果没有，请为使用它的方法添加
     * Transaction注解开启事务处理。
     *
     * @param entity 被Hibernate管理的Entity
     * @param reversId 如果正在处理的对象是关联的乙方，这里传递另一方的id。
     *
     * @see StatelessIgnore
     *
     * @param <T> 对象的类型
     * @return 复制后的EntityDTO
     */
    public static <T> T stateless(T entity, Deque<Object> reversId) {
        try {
            if (entity == null) {
                return null;
            }

            Class type = entity.getClass();
            Field idField = getIdField(type);
            idField.setAccessible(true);
            Object entityId = idField.get(entity);

            if (entity.getClass().getName().contains("HibernateProxy")) {
                // 是hibernate的代理类，读取它原本的类型。
                type = HibernateProxyHelper.getClassWithoutInitializingProxy(entity);
            }
            T instance = (T)type.getConstructor().newInstance();
            Class currentType = type;
            while (currentType != Object.class) {
                Field[] fields = currentType.getDeclaredFields();
                for (Field field: fields) {
                    try {
                        // Hibernate的代理会使直接操作字段变得很麻烦，
                        // 所以使用对应的Getter和Setter进行操作。
                        Method getter = ClassTypeAndMethods.extractGetter(field);
                        Method setter = ClassTypeAndMethods.extractSetter(field);
                        if (getter == null || setter == null) {
                            continue;
                        }
                        if (field.getAnnotation(StatelessIgnore.class) != null) {
                            StatelessIgnore ignore = field.getAnnotation(StatelessIgnore.class);
                            if (ignore.reverse() && reversId.contains(entityId)) {
                                continue;
                            } else if (!ignore.reverse()) {
                                continue;
                            }
                        }
                        if (field.getAnnotation(ManyToOne.class) != null) {
                            Object obj = getter.invoke(entity);
                            reversId.push(entityId);
                            setter.invoke(instance,stateless(obj,reversId));
                            reversId.pop();
                        } else if (field.getType().getAnnotation(Entity.class) != null) {

                            if (field.getAnnotation(OneToOne.class) != null) {
                                reversId.push(entityId);
                                Object target = getter.invoke(entity);
                                setter.invoke(instance, stateless(target));
                                reversId.pop();
                            } else {
                                Object target = getter.invoke(entity);
                                setter.invoke(instance, stateless(target));
                            }


                        } else if (field.getAnnotation(OneToMany.class) != null || field.getAnnotation(ManyToMany.class) != null) {
                            if (reversId.contains(entityId)) {
                                continue;
                            }
                            reversId.push(entityId);
                            Collection<Object> collection = (Collection) getter.invoke(entity);
                            if (List.class.isAssignableFrom(field.getType())) {
                                List rest = collection.stream()
                                        .map(e -> stateless(e,reversId))
                                        .collect(Collectors.toList());
                                setter.invoke(instance,rest);
                            } else if (Set.class.isAssignableFrom(field.getType())){
                                Set rest = collection.stream()
                                        .map(e -> stateless(e,reversId))
                                        .collect(Collectors.toSet());
                                setter.invoke(instance,rest);
                            }
                            reversId.pop();
                        } else {
                            setter.invoke(instance,getter.invoke(entity));
                        }
                    } catch (Exception e) {
                        // ignore
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
