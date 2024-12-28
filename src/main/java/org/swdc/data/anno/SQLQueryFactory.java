package org.swdc.data.anno;

import org.swdc.data.SQLFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SQLQueryFactory {

    Class<? extends SQLFactory> value();

}
