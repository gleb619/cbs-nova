package cbs.nova.starter.config.registrarfixture;

import cbs.nova.starter.annotation.SpringHelper;

/**
 * Fixture used by {@code SpringHelperBeanDefinitionRegistrarTest}. Lives in a dedicated test
 * sub-package so the production scan over {@code cbs.nova.starter.helper} cannot pick it up.
 */
@SpringHelper(name = "annotatedHelper")
public class AnnotatedHelper {
}
