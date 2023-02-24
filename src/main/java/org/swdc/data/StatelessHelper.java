package org.swdc.data;

import org.hibernate.proxy.HibernateProxyHelper;
import org.swdc.data.anno.StatelessIgnore;
import org.swdc.dependency.utils.ReflectionUtil;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;
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
        return stateless(entity,false);
    }

    /**
     * 创建Entity的脱离Hibernate代理的普通数据对象
     *
     * 如果本应被Copy的字段在返回的对象中为空，请检查调用本方法的
     * 位置是否处于事务处理的Aspect中，如果没有，请为使用它的方法添加
     * Transaction注解开启事务处理。
     *
     * @param entity 被Hibernate管理的Entity
     * @param reverse 此对象是否处于其他对象的关系中
     *                根据此项将会忽略一些字段的复制以防止StackOverflow
     *                和不必要的数据加载。
     *
     *                使用StatelessIgnore注解将会阻止字段在本方法的Copy。
     * @see StatelessIgnore
     *
     * @param <T> 对象的类型
     * @return 复制后的EntityDTO
     */
    public static <T> T stateless(T entity, boolean reverse) {
        try {
            Class type = entity.getClass();
            if (entity.getClass().getName().contains("HibernateProxy")) {
                // 是hibernate的代理类，读取它原本的类型。
                type = HibernateProxyHelper.getClassWithoutInitializingProxy(entity);
            }
            T instance = (T)type.getConstructor().newInstance();
            Field[] fields = type.getDeclaredFields();

            for (Field field: fields) {
                try {
                    // Hibernate的代理会使直接操作字段变得很麻烦，
                    // 所以使用对应的Getter和Setter进行操作。
                    Method getter = ReflectionUtil.extractGetter(field);
                    Method setter = ReflectionUtil.extractSetter(field);
                    if (getter == null || setter == null) {
                        continue;
                    }
                    if (field.getAnnotation(StatelessIgnore.class) != null) {
                        StatelessIgnore ignore = field.getAnnotation(StatelessIgnore.class);
                        if (ignore.reverse() && reverse) {
                            continue;
                        } else if (!ignore.reverse()) {
                            continue;
                        }
                    }
                    if (field.getAnnotation(ManyToOne.class) != null) {
                        Object obj = getter.invoke(entity);
                        setter.invoke(instance,stateless(obj,true));
                    } else if (field.getType().getAnnotation(Entity.class) != null) {
                        Object target = getter.invoke(entity);
                        setter.invoke(instance, stateless(target));
                    } else if (field.getAnnotation(OneToMany.class) != null) {
                        if (reverse) {
                            continue;
                        }
                        Collection<Object> collection = (Collection) getter.invoke(entity);
                        if (List.class.isAssignableFrom(field.getType())) {
                            List rest = collection.stream()
                                    .map(e -> stateless(e,true))
                                    .collect(Collectors.toList());
                            setter.invoke(instance,rest);
                        } else if (Set.class.isAssignableFrom(field.getType())){
                            Set rest = collection.stream()
                                    .map(e -> stateless(e,true))
                                    .collect(Collectors.toSet());
                            setter.invoke(instance,rest);
                        }
                    } else {
                        setter.invoke(instance,getter.invoke(entity));
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            return instance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



}
