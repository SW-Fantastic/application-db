package org.swdc.data.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * StatelessHelper通过该注解识别私密字段，
 * 这些字段在系统自身有存在的必要，但是在对外提供数据时应该被忽略，
 * 所以通过StatelessHelper的safe方法可以移除这些字段的值。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StatelessPrivate {
}
