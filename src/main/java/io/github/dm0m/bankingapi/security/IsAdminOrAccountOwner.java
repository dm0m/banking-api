package io.github.dm0m.bankingapi.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Allows admins, and users who own the account in the method's {@code id} parameter.
 * The annotated method must have a parameter named {@code id} holding an <b>account</b> id.
 * An unknown account id is "not yours" (403), so callers can't probe which ids exist.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('ADMIN') or @access.ownsAccount(authentication, #id)")
public @interface IsAdminOrAccountOwner {
}
