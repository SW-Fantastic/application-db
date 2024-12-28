package org.swdc.data;

import javax.persistence.EntityManager;
import javax.persistence.Query;

public interface SQLFactory {

     Query createQuery(EntityManager em, SQLParams params);

}
