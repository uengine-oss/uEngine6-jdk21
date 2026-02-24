package org.uengine.five.config;

import org.hibernate.dialect.OracleDialect;

/**
 * Oracle 12c+ 호환 Dialect. REGEXP_LIKE 등 커스텀 함수는
 * OracleHibernateMetadataContributor에서 Hibernate 6 API로 등록.
 */
public class Oracle12cDialectWithRegexp extends OracleDialect {
}
