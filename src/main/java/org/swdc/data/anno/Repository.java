package org.swdc.data.anno;

import jakarta.inject.Scope;
import org.swdc.data.RepositoryManager;
import org.swdc.dependency.annotations.ScopeImplement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Scope
@ScopeImplement(RepositoryManager.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Repository {
}
