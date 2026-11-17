package io.github.dm0m.bankingapi.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Allows admins, and users whose token's customerId claim matches the method's {@code id} parameter.
 * The annotated method must have a parameter named {@code id} holding a <b>customer</b> id;
 * for account ids use {@link IsAdminOrAccountOwner}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('ADMIN') or (hasRole('USER') and principal.claims['customerId'] == #id)")
public @interface IsAdminOrCustomer {
}
