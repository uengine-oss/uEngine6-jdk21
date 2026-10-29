package org.uengine.five.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.web.bind.annotation.RequestParam;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.RoleMappingEntity;
import org.uengine.five.entity.WorklistEntity;

/** Executes the repository's actual JPQL and count queries against isolated in-memory databases. */
public class ProcessInstanceSearchFilterTest {
    enum Profile { POSTGRES_STYLE, ORACLE }

    private static final Map<Profile, SessionFactory> FACTORIES = new HashMap<>();
    private static final Map<Profile, List<String>> SQL = new HashMap<>();

    @BeforeAll
    static void createIsolatedDatabases() throws Exception {
        for (Profile profile : Profile.values()) {
            List<String> statements = new ArrayList<>();
            SQL.put(profile, statements);
            String jdbcUrl = "jdbc:h2:mem:instance_search_" + profile
                    + ";NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1" + (profile == Profile.ORACLE ? ";MODE=Oracle" : "");
            if (profile == Profile.ORACLE) {
                // Hibernate 6.4 reads Oracle sequence metadata before creating the isolated schema.
                // H2's Oracle mode does not supply this catalog; an empty fixture means no sequences exist yet.
                try (var connection = DriverManager.getConnection(jdbcUrl)) {
                    connection.createStatement().execute("CREATE TABLE ALL_SEQUENCES ("
                            + "SEQUENCE_NAME VARCHAR(128), MIN_VALUE BIGINT, MAX_VALUE BIGINT, INCREMENT_BY BIGINT)");
                }
            }
            Configuration configuration = new Configuration()
                    .addAnnotatedClass(ProcessInstanceEntity.class)
                    .addAnnotatedClass(WorklistEntity.class)
                    .addAnnotatedClass(RoleMappingEntity.class)
                    .setProperty("hibernate.connection.url", jdbcUrl)
                    .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                    .setProperty("hibernate.show_sql", "false")
                    .setStatementInspector((StatementInspector) sql -> { statements.add(sql); return sql; });
            if (profile == Profile.ORACLE) {
                configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.OracleDialect");
            }
            SessionFactory factory = configuration.buildSessionFactory();
            FACTORIES.put(profile, factory);
            try (Session session = factory.openSession()) {
                // The existing Oracle query uses this UDF. Keep its contract without a real Oracle service.
                session.doWork(connection -> connection.createStatement().execute(
                        "CREATE ALIAS IF NOT EXISTS REGEXP_LIKE_YN FOR '"
                                + ProcessInstanceSearchFilterTest.class.getName() + ".regexpLikeYn'"));
                session.beginTransaction();
                persist(session, "Loan-001-A", "AbC001", "홍길동", "test/root", "Running", 1);
                persist(session, "loan-001-B", "AbC002", "김검토", "test/root", "Completed", 2);
                persist(session, "OTHER-003", "user_UUID-003", "홍길동", "test/other", "Running", 3);
                persist(session, "CASE%_\\004", "EMP%_\\004", "LiteralPercent", "test/root", "Running", 4);
                persist(session, "CASEXX004", "EMPXX004", "Other", "test/root", "Running", 5);
                persist(session, null, null, null, "test/root", "Running", 6);
                persist(session, "Loan-001-G", "id-007", "Alice 007", "test/root", "Running", 7);
                session.getTransaction().commit();
            }
        }
    }

    @AfterAll
    static void closeIsolatedDatabases() {
        FACTORIES.values().forEach(SessionFactory::close);
    }

    public static int regexpLikeYn(String value, String pattern) {
        return value != null && pattern != null && java.util.regex.Pattern.compile(pattern).matcher(value).find() ? 1 : 0;
    }

    @ParameterizedTest
    @EnumSource(Profile.class)
    void omittedEmptyAndWhitespaceFiltersKeepRowsWithNullFields(Profile profile) throws Exception {
        assertEquals(7, search(profile, Map.of(), 0, 20).total());
        assertEquals(7, search(profile, Map.of("corrKey", "", "requester", ""), 0, 20).total());
        assertEquals(7, search(profile, Map.of("corrKey", "   ", "requester", "   "), 0, 20).total());
    }

    @ParameterizedTest
    @EnumSource(Profile.class)
    void corrKeyIsCaseInsensitiveTrimmedSubstring(Profile profile) throws Exception {
        assertEquals(List.of("Loan-001-G", "loan-001-B", "Loan-001-A"),
                search(profile, Map.of("corrKey", " LOAN-001 "), 0, 20).keys());
        assertEquals(3, search(profile, Map.of("corrKey", "001"), 0, 20).total());
    }

    @ParameterizedTest
    @EnumSource(Profile.class)
    void requesterMatchesNameEmployeeIdAndUuid(Profile profile) throws Exception {
        assertEquals(List.of("OTHER-003", "Loan-001-A"), search(profile, Map.of("requester", " 홍길동 "), 0, 20).keys());
        assertEquals(List.of("loan-001-B", "Loan-001-A"), search(profile, Map.of("requester", "abc00"), 0, 20).keys());
        assertEquals(List.of("OTHER-003"), search(profile, Map.of("requester", "uuid-003"), 0, 20).keys());
    }

    @ParameterizedTest
    @EnumSource(Profile.class)
    void bothNewFiltersAndExistingDefinitionAndStatusAreAnIntersection(Profile profile) throws Exception {
        assertEquals(List.of("Loan-001-A"), search(profile,
                Map.of("corrKey", "loan-001", "requester", "홍길동", "defId", "test/root", "status", "Running"), 0, 20).keys());
        assertEquals(0, search(profile,
                Map.of("corrKey", "other-003", "requester", "홍길동", "defId", "test/root"), 0, 20).total());
    }

    @ParameterizedTest
    @EnumSource(Profile.class)
    void existingInitEpFilterRemainsIndependent(Profile profile) throws Exception {
        assertEquals(List.of("loan-001-B"), search(profile, Map.of("initEp", "AbC002"), 0, 20).keys());
        assertEquals(0, search(profile, Map.of("initEp", "AbC002", "requester", "홍길동"), 0, 20).total());
    }

    @ParameterizedTest
    @EnumSource(Profile.class)
    void sqlWildcardCharactersAreLiteralForNewFilters(Profile profile) throws Exception {
        assertEquals(List.of("CASE%_\\004"), search(profile, Map.of("corrKey", "%_\\"), 0, 20).keys());
        assertEquals(List.of("CASE%_\\004"), search(profile, Map.of("requester", "%_\\"), 0, 20).keys());
    }

    @ParameterizedTest
    @EnumSource(Profile.class)
    void pageRowsAndCountUseTheSameNewFilters(Profile profile) throws Exception {
        Map<String, Object> filters = Map.of("corrKey", "loan-001", "status", "Running");
        Result first = search(profile, filters, 0, 1);
        Result second = search(profile, filters, 1, 1);
        assertEquals(2, first.total());
        assertEquals(2, second.total());
        assertEquals(List.of("Loan-001-G"), first.keys());
        assertEquals(List.of("Loan-001-A"), second.keys());
        if (profile == Profile.ORACLE) {
            assertTrue(SQL.get(profile).stream().anyMatch(sql -> sql.toLowerCase().contains("instr(")),
                    "Oracle dialect must render JPQL LOCATE as INSTR");
        }
    }

    @Test
    void oracleControllerParametersAreOptionalAndQueryIsJpql() throws Exception {
        Method method = searchMethod(ProcessInstanceSearchController.class);
        int checked = 0;
        for (var parameter : method.getParameters()) {
            if (parameter.getName().equals("corrKey") || parameter.getName().equals("requester")) {
                assertFalse(parameter.getAnnotation(RequestParam.class).required());
                checked++;
            }
        }
        assertEquals(2, checked);
        assertFalse(searchMethod(ProcessInstanceRepositoryOracle.class).getAnnotation(Query.class).nativeQuery());
    }

    private static Result search(Profile profile, Map<String, ?> filters, int first, int size) throws Exception {
        Class<?> repository = profile == Profile.ORACLE ? ProcessInstanceRepositoryOracle.class : ProcessInstanceRepositoryH2.class;
        Query annotation = searchMethod(repository).getAnnotation(Query.class);
        try (Session session = FACTORIES.get(profile).openSession()) {
            var rows = session.createQuery(annotation.value(), ProcessInstanceEntity.class);
            var count = session.createQuery(annotation.countQuery(), Long.class);
            for (var parameter : rows.getParameters()) {
                rows.setParameter(parameter.getName(), filters.get(parameter.getName()));
                count.setParameter(parameter.getName(), filters.get(parameter.getName()));
            }
            List<String> keys = rows.setFirstResult(first).setMaxResults(size).getResultList().stream()
                    .map(ProcessInstanceEntity::getCorrKey).toList();
            return new Result(keys, count.getSingleResult());
        }
    }

    private static Method searchMethod(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals("findFilterICanSee")).findFirst().orElseThrow();
    }

    private static void persist(Session session, String key, String endpoint, String requesterName, String definition,
            String status, int day) {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setCorrKey(key);
        instance.setInitEp(endpoint);
        instance.setInitRsNm(requesterName);
        instance.setDefId(definition);
        instance.setStatus(status);
        instance.setStartedDate(new Date(day * 86_400_000L));
        instance.setSubProcess(false);
        instance.setEventHandler(false);
        session.persist(instance);
    }

    private record Result(List<String> keys, long total) {}
}
