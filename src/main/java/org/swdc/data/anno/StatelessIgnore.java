package org.swdc.data.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * StatelessHelper将忽略被标注的字段
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface StatelessIgnore {

    /**
     * 仅在作为其他实体的内容的时候忽略此字段
     * @return
     */
    boolean reverse();

}
