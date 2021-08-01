package org.swdc.data;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public  class EMFProviderFactory {

    private EntityManagerFactory entityFactory;

    private static Map<Thread, javax.persistence.EntityManager> localEm = new ConcurrentHashMap<>();

    private Logger logger = LoggerFactory.getLogger(EMFProviderFactory.class);

    private List<Class> entities = new ArrayList<>();

    // 允许用户有限度的在代码中配置一些属性。

    private String url;

    public EMFProviderFactory(List<Class> entities,String url) {
        this.entities = entities;
        this.url = url;
    }

    @PostConstruct
    public void initialize() {
        try {
            Properties properties = new Properties();
            InputStream inputStream = this.getClass().getModule().getResourceAsStream("hibernate.properties");
            InputStream defaultPropSteam = EMFProvider.class.getModule().getResourceAsStream("hibernate.properties");

            Properties defaultProp = new Properties();
            defaultProp.load(defaultPropSteam);

            if (inputStream == null) {
                properties = defaultProp;
            } else {
                properties.load(inputStream);
                inputStream.close();
            }

            for (Map.Entry<Object,Object> prop: defaultProp.entrySet()) {
                if (!properties.contains(prop.getKey())) {
                    properties.put(prop.getKey(),prop.getValue());
                }
            }

            defaultPropSteam.close();

            if (this.url != null) {
                properties.put("hibernate.connection.url",url);
            }

            properties.put(org.hibernate.jpa.AvailableSettings.LOADED_CLASSES,entities);

            this.entityFactory = Persistence.createEntityManagerFactory("default", properties);
            logger.info("database is ready.");
        } catch (Exception e) {
            logger.error("无法载入数据库链接。",e);
        }
    }

    @PreDestroy
    public void destroy(){
        for (Map.Entry<Thread,EntityManager> ent: localEm.entrySet()) {
            EntityManager em = ent.getValue();
            if (em.isOpen()) {
                EntityTransaction tx = em.getTransaction();
                if (tx != null && tx.isActive()) {
                    em.flush();
                }
                em.close();
            }
        }
        entityFactory.close();
    }

    public javax.persistence.EntityManager getEntityManager() {
        EntityManager entityManager = localEm.get(Thread.currentThread());
        if (entityManager == null || !entityManager.isOpen()) {
            entityManager = entityFactory.createEntityManager();
            localEm.put(Thread.currentThread(),entityManager);
        }

        if (!entityManager.isOpen()) {
            entityManager.clear();
            entityManager.close();
            localEm.remove(Thread.currentThread());

            entityManager = entityFactory.createEntityManager();
            localEm.put(Thread.currentThread(),entityManager);
        }

        entityManager.clear();

        return entityManager;
    }


}
