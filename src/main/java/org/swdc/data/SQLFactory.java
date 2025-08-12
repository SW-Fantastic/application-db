package org.swdc.data;


import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

public interface SQLFactory {

     Query createQuery(EntityManager em, SQLParams params);

}
