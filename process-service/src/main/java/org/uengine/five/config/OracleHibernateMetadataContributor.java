package org.uengine.five.config;

import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.spi.MetadataBuilderContributor;
import org.hibernate.query.sqm.function.FunctionKind;
import org.hibernate.query.sqm.function.PatternBasedSqmFunctionDescriptor;
import org.hibernate.query.sqm.produce.function.internal.PatternRenderer;

/**
 * H2/Oracle 공통: JPQL의 regexp_like를 REGEXP_LIKE SQL 함수로 변환 (Hibernate 6 API).
 * Oracle에서 REGEXP_LIKE(...)=1 이 ORA-00907(누락된 우괄호)를 일으키므로,
 * (REGEXP_LIKE(?1, ?2)) 형태로 괄호로 감싸서 등록.
 */
public class OracleHibernateMetadataContributor implements MetadataBuilderContributor {

    @Override
    public void contribute(MetadataBuilder metadataBuilder) {
        metadataBuilder.applySqlFunction(
                "regexp_like",
                new PatternBasedSqmFunctionDescriptor(
                        new PatternRenderer("(REGEXP_LIKE(?1, ?2))"),
                        null, null, null, "regexp_like", FunctionKind.NORMAL, null)
        );
        metadataBuilder.applySqlFunction(
                "REGEXP_LIKE_YN",
                new PatternBasedSqmFunctionDescriptor(
                        new PatternRenderer("REGEXP_LIKE_YN(?1, ?2)"),
                        null, null, null, "REGEXP_LIKE_YN", FunctionKind.NORMAL, null)
        );
        metadataBuilder.applySqlFunction(
                "regexp_like_yn",
                new PatternBasedSqmFunctionDescriptor(
                        new PatternRenderer("REGEXP_LIKE_YN(?1, ?2)"),
                        null, null, null, "regexp_like_yn", FunctionKind.NORMAL, null)
        );
    }
}
