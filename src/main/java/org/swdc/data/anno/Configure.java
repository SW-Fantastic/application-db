package org.swdc.data.anno;

import org.hibernate.dialect.Dialect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Configure {

    String url();

    Class driver() default Object.class;

    Class<? extends Dialect> dialect() default Dialect.class;

}
