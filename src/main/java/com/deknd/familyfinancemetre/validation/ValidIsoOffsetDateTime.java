package com.deknd.familyfinancemetre.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = IsoOffsetDateTimeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIsoOffsetDateTime {

	String message() default "must be a valid ISO-8601 date-time with offset";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
