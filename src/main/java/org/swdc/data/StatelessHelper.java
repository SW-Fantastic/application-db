package org.swdc.data;

import org.swdc.data.anno.StatelessIgnore;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StatelessHelper {

    public static <T> T stateless(T entity) {
        return stateless(entity,false);
    }

    public static <T> T stateless(T entity, boolean reverse) {
        try {
            T instance = (T)entity.getClass().getConstructor().newInstance();

            Field[] fields = entity.getClass().getDeclaredFields();
            for (Field field: fields) {
                try {
                    field.setAccessible(true);
                    if (field.getAnnotation(StatelessIgnore.class) != null) {
                        StatelessIgnore ignore = field.getAnnotation(StatelessIgnore.class);
                        if (ignore.reverse() && reverse) {
                            continue;
                        } else if (!ignore.reverse()) {
                            continue;
                        }
                    }
                    if (field.getAnnotation(ManyToOne.class) != null) {
                        if (!reverse) {
                            continue;
                        }
                        Object obj = field.get(entity);
                        field.set(instance,stateless(obj,true));
                    } else if (field.getType().getAnnotation(Entity.class) != null) {
                        Object target = field.get(entity);
                        field.set(instance, stateless(target));
                    } else if (field.getAnnotation(OneToMany.class) != null) {
                        if (reverse) {
                            continue;
                        }
                        Collection<Object> collection = (Collection) field.get(entity);
                        if (List.class.isAssignableFrom(field.getType())) {
                            List rest = collection.stream()
                                    .map(e -> stateless(e,true))
                                    .collect(Collectors.toList());
                            field.set(instance,rest);
                        } else if (Set.class.isAssignableFrom(field.getType())){
                            Set rest = collection.stream()
                                    .map(e -> stateless(e,true))
                                    .collect(Collectors.toSet());
                            field.set(instance,rest);
                        }
                    } else {
                        field.set(instance,field.get(entity));
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
