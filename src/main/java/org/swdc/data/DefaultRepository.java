package org.swdc.data;

import org.hibernate.proxy.HibernateProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.swdc.data.anno.Modify;
import org.swdc.data.anno.Param;
import org.swdc.data.anno.SQLQuery;
import org.swdc.data.anno.SQLQueryFactory;
import org.swdc.ours.common.type.Converter;
import org.swdc.ours.common.type.Converters;

import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.Query;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

public class DefaultRepository<E, ID> implements InvocationHandler,JPARepository<E, ID> {

    private EMFProviderFactory manager;

    private Class<E> eClass;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Converters converters = new Converters();

    private Map<Class, SQLFactory> sqlFactoryMap = new HashMap<>();


    public void init(EMFProviderFactory module, Class<E> eClass) {
        this.manager = module;
        this.eClass = eClass;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        EntityManager manager = this.manager.getEntityManager();

        String name = method.getName();
        if (name.equals("getOne") || name.equals("getAll")
                || name.equals("removeAll") || name.equals("save") || name.equals("remove")) {
            try {
                return method.invoke(this,args);
            } catch (Exception e) {
                logger.error("failed to execute method, ",e);
                return null;
            }
        }
        try {
            Object.class.getMethod(method.getName(),method.getParameterTypes());
            return method.invoke(this,args);
        } catch (Exception ex) {
        }
        Query query = resolveByQuery(manager, method, args);
        Modify modify = method.getAnnotation(Modify.class);
        if (query != null) {
            // 判断事务是否是在此处开启的，如果是的话，那本方法应该负责释放他
            boolean autoCommit = false;
            if (!manager.getTransaction().isActive()) {
                manager.getTransaction().begin();
                autoCommit = true;
            }

            try {
                Class returnClazz = method.getReturnType();
                if (Set.class.isAssignableFrom(returnClazz)) {
                    List list = query.getResultList();
                    if (list == null || list.size() == 0) {
                        return Collections.emptyList();
                    }
                    return list.stream().collect(Collectors.toSet());
                } else if (List.class.isAssignableFrom(returnClazz)) {
                    List list = query.getResultList();
                    if (list == null || list.size() == 0) {
                        return Collections.emptyList();
                    }
                    return list;
                } else if (Collection.class.isAssignableFrom(returnClazz)) {
                    List list = query.getResultList();
                    if (list == null || list.size() == 0) {
                        return Collections.emptyList();
                    }
                    return list;
                } else if (returnClazz == eClass) {
                    List list = query.getResultList();
                    if (list == null || list.size() == 0) {
                        return null;
                    }
                    return list.get(query.getFirstResult());
                } else if (returnClazz == Integer.class|| returnClazz == int.class || returnClazz == Long.class || returnClazz == long.class) {
                    if (modify == null ) {
                        Object result = query.getSingleResult();
                        if (result != null) {
                            if (result.getClass() == returnClazz) {
                                return result;
                            } else {
                                Converter converter = converters.getConverter(returnClazz,result.getClass());
                                if (converter == null) {
                                    return null;
                                }
                                return converter.convert(result);
                            }
                        }
                    } else {
                        return query.executeUpdate();
                    }
                } else {
                    if (modify != null) {
                        query.executeUpdate();
                    } else {
                        query.getResultList();
                    }
                }
                return null;
            } catch (Exception ex) {
                // 回滚事务
                if (autoCommit) {
                    manager.getTransaction().rollback();
                    manager.close();
                }
                logger.error("fail to execute query: " + method.getName(), ex);
            } finally {
                // 提交事务
                if (manager.getTransaction().isActive()) {
                    manager.flush();
                    if (autoCommit) {
                        manager.getTransaction().commit();
                        manager.close();
                    }
                }
            }
        }
        return null;
    }

    public Query resolveByQuery(EntityManager em, Method method, Object[] args) {

        SQLQuery sqlQuery = method.getAnnotation(SQLQuery.class);
        Query query = null;

        if (sqlQuery == null) {
            SQLQueryFactory factory = method.getAnnotation(SQLQueryFactory.class);
            if (factory != null) {
                try {

                    SQLFactory sqlFactory = null;

                    if (sqlFactoryMap.containsKey(factory.value())) {
                        sqlFactory = sqlFactoryMap.get(factory.value());
                    } else {
                        sqlFactory = factory.value()
                                .newInstance();
                        sqlFactoryMap.put(factory.value(),sqlFactory);
                    }

                    Map<String,Object> params = new HashMap<>();
                    Parameter[] parameters = method.getParameters();
                    for(int index = 0; index < parameters.length; index ++) {
                        Param param = parameters[index].getAnnotation(Param.class);
                        if (param != null) {
                            params.put(param.value(),args[index]);
                        }
                    }

                    query = sqlFactory.createQuery(em, new SQLParams(params));
                    if (query == null) {
                        return null;
                    }

                } catch (Exception e) {
                    return null;
                }
            }
        } else {

            if (method.getReturnType() == Integer.class || method.getReturnType() == int.class) {
                // 聚合函数
                query = em.createQuery(sqlQuery.value(),Integer.class);
            } else if (method.getReturnType() == Long.class || method.getReturnType() == long.class) {
                // 聚合函数
                query = em.createQuery(sqlQuery.value(),Long.class);
            } else if (method.getReturnType() == eClass) {
                query = em.createQuery(sqlQuery.value(),eClass);
            } else if (Collection.class.isAssignableFrom(method.getReturnType())){
                query = em.createQuery(sqlQuery.value());
            } else {
                query = em.createQuery(sqlQuery.value());
            }

            Parameter[] params = method.getParameters();
            if (query.getParameters().size() != method.getParameters().length) {
                logger.error("can not create query because parameters size dose not matches");
                logger.error("method: " + method.getName());
                return null;
            }
            for (int index = 0; index <params.length; index ++) {
                Param qParam = params[index].getAnnotation(Param.class);
                String name = qParam.value();
                if (qParam.searchBy()) {
                    query.setParameter(name,"%" + args[index] + "%");
                } else {
                    query.setParameter(name,args[index]);
                }
            }
            if (sqlQuery.firstResult() != -1) {
                query.setFirstResult(sqlQuery.firstResult());
            }
            if(sqlQuery.maxResult() != -1) {
                query.setMaxResults(sqlQuery.maxResult());
            }

        }

        return query;
    }

    @Override
    public E getOne(ID id) {
        EntityManager entityManager = this.manager.getEntityManager();
        if (entityManager == null) {
            logger.error("no entity manager at current thread");
            return null;
        }
        return entityManager.find(eClass,id);
    }

    @Override
    public List<E> getAll() {
        EntityManager entityManager = this.manager.getEntityManager();
        if (entityManager == null) {
            logger.error("no entity manager at current thread");
            return new ArrayList<>();
        }
        Query query = entityManager.createQuery("from " + eClass.getSimpleName(),eClass);
        return query.getResultList();
    }

    @Override
    public E save(E entry) {
        EntityManager entityManager = this.manager.getEntityManager();
        if (entityManager == null) {
            logger.error("no entity manager at current thread");
            return null;
        }
        boolean autoCommit = false;
        if (!entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().begin();
            autoCommit = true;
        }
        Field idField = getIdField(entry.getClass());
        if (idField == null) {
            logger.error("no id field found");
            return null;
        }
        try {
            idField.setAccessible(true);
            Object id = idField.get(entry);
            if (id == null) {
                entityManager.persist(entry);
                entityManager.flush();
                if (autoCommit) {
                    entityManager.getTransaction().commit();
                }
                return entry;
            }
            E entExisted = this.getOne((ID) id);
            if (entExisted == null) {
                idField.set(entry, null);
                entityManager.persist(entry);
                entityManager.flush();
                if (autoCommit) {
                    entityManager.getTransaction().commit();
                }
                return entry;
            }
            entry = entityManager.merge(entry);
            entityManager.flush();
            if (autoCommit) {
                entityManager.getTransaction().commit();
            }
            return entry;
        } catch (Exception ex) {
            logger.error("error persistent entry: " + entry.getClass().getSimpleName(), ex);
            return null;
        }
    }

    private Field getIdField(Class target) {
        return StatelessHelper.getIdField(target);
    }

    @Override
    public void removeAll(Collection<E> entities) {
        EntityManager entityManager = this.manager.getEntityManager();
        if (entityManager == null) {
            logger.error("no entity manager at current thread");
            return;
        }
        boolean autoCommit = false;
        if (!entityManager.getTransaction().isActive()) {
            autoCommit = true;
            entityManager.getTransaction().begin();
        }
        for (E entity: entities) {
            if (entity instanceof HibernateProxy) {
                entityManager.refresh(entity);
            }
            try {
                entityManager.remove(entity);
            } catch (Exception ex) {
                Field idField = getIdField(eClass);
                idField.setAccessible(true);
                try {
                    ID id = (ID) idField.get(entity);
                    E target = getOne(id);
                    entityManager.remove(target);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if(autoCommit) {
            entityManager.getTransaction().commit();
        }

    }

    @Override
    public void remove(E entry) {
        EntityManager entityManager = this.manager.getEntityManager();
        if (entityManager == null) {
            logger.error("no entity manager at current thread");
            return;
        }
        boolean autoCommit = false;
        if (!entityManager.getTransaction().isActive()) {
            autoCommit = true;
            entityManager.getTransaction().begin();
        }
        Field idField = getIdField(entry.getClass());
        if (idField == null) {
            logger.error("no id field found");
            return;
        }
        try {
            idField.setAccessible(true);
            Object id = idField.get(entry);
            if (id == null) {
                logger.error("entity not persistent, can not remove now");
                return;
            }
            entry = entityManager.find(eClass,id);
            entityManager.remove(entry);
            if(autoCommit) {
                entityManager.getTransaction().commit();
            }
        } catch (Exception e) {
            logger.error("fail to remove entity",e);
        }
    }

}
