package org.swdc.data;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.swdc.dependency.annotations.Aspect;
import org.swdc.dependency.annotations.Interceptor;
import org.swdc.dependency.interceptor.AspectAt;
import org.swdc.dependency.interceptor.ProcessPoint;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

@Interceptor
public class Transaction {

    @Inject
    private EMFProviderFactory emf;

    @Inject
    private Logger logger;

    @Aspect(byAnnotation = Transaction.class,at= AspectAt.AROUND)
    public Object transaction(ProcessPoint processPoint) throws Throwable{
        boolean closeAfterCommit = false;
        EntityManager manager = emf.getEntityManager();
        // EntityManager本身就是线程相关的，所以获取之后可以直接使用
        EntityTransaction transaction = manager.getTransaction();
        try {
            if (transaction.isActive()) {
                return processPoint.process();
            } else {
                closeAfterCommit = true;
                transaction.begin();
            }
            return processPoint.process();
        } catch (Exception e) {
            logger.error("fail to process transaction method: ",e);
            transaction.rollback();
            return null;
        } finally {
            transaction.commit();
            if (closeAfterCommit) {
                manager.close();
            }
        }
    }

}
