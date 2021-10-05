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

public class EMFProviderFactory {

    private EntityManagerFactory entityFactory;

    private static Map<Thread, javax.persistence.EntityManager> localEm = new ConcurrentHashMap<>();

    private Logger logger = LoggerFactory.getLogger(EMFProviderFactory.class);

    private List<Class> entities = new ArrayList<>();

    private Properties hibernateConfig = null;

    // 允许用户有限度的在代码中配置一些属性。

   // private String url;

   // private String driver;

   // private String dialect;

    public EMFProviderFactory(List<Class> entities) {
        this.entities = entities;
    }

    @PostConstruct
    public void initialize() {
        try {
            InputStream defaultPropSteam = EMFProvider.class.getModule().getResourceAsStream("hibernate.properties");

            hibernateConfig = new Properties();
            hibernateConfig.load(defaultPropSteam);

            defaultPropSteam.close();
        } catch (Exception e) {

        }
    }

    public void url(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        hibernateConfig.put("hibernate.connection.url",url);
    }

    public void driver(String driver,String dialect) {
        if (driver == null || driver.isEmpty()) {
            return;
        }
        if (dialect == null || dialect.isEmpty()) {
            return;
        }
        hibernateConfig.put("hibernate.connection.driver_class",driver);
        hibernateConfig.put("hibernate.dialect",dialect);
    }

    public void create() {
        try {
            Properties properties = new Properties();
            InputStream inputStream = this.getClass().getModule().getResourceAsStream("hibernate.properties");

            if (inputStream != null) {
                properties.load(inputStream);
                inputStream.close();
            }

            for (Map.Entry<Object,Object> prop: hibernateConfig.entrySet()) {
                if (!properties.contains(prop.getKey())) {
                    properties.put(prop.getKey(),prop.getValue());
                }
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
        if (entityFactory == null) {
            return;
        }
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
        entityFactory = null;
    }

    public javax.persistence.EntityManager getEntityManager() {
        if (entityFactory == null) {
            throw new RuntimeException("please start jpa first");
        }
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
