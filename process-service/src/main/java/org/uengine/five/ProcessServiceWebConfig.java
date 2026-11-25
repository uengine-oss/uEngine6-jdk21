package org.uengine.five;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.data.spel.spi.EvaluationContextExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UrlPathHelper;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.framework.ProcessTransactionContext;
import org.uengine.five.overriding.CLOBProcessInstance;
import org.uengine.five.overriding.EmailServiceLocalImpl;
import org.uengine.five.overriding.FileSimulationProcessInstance;
// import org.uengine.five.overriding.AuditActivityFilter;
import org.uengine.five.overriding.InstanceDataAppendingActivityFilter;
import org.uengine.five.service.WorkItemAssignmentStateService;
import org.uengine.five.overriding.InstanceServiceLocalImpl;
import org.uengine.five.overriding.JPAWorkList;
import org.uengine.five.spring.TenantSecurityEvaluationContextExtension;
import org.uengine.kernel.ActivityFilter;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ProcessInstance;
import org.uengine.modeling.resource.CachedResourceManager;
import org.uengine.modeling.resource.LocalFileStorage;
import org.uengine.modeling.resource.ResourceManager;
import org.uengine.modeling.resource.Storage;
import org.uengine.processmanager.EMailServiceLocal;
import org.uengine.processmanager.DefinitionServiceLocal;
import org.uengine.processmanager.InstanceServiceLocal;
import org.uengine.webservices.worklist.WorkList;

@EnableWebMvc
@Configuration
@ComponentScan(basePackages = {
        "org.uengine.five",
        "org.uengine.kernel",
        "org.uengine.hwlife"
})
public class ProcessServiceWebConfig {

    // @Override
    // public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // registry.setOrder(10);
    // registry.addResourceHandler("/**").addResourceLocations("classpath:/dev/dist/");
    // super.addResourceHandlers(registry);
    // }
    //
    // @Override
    // public void addViewControllers(ViewControllerRegistry registry) {
    // registry.addViewController("/").setViewName("forward:/index.html");
    // super.addViewControllers(registry);
    // }

    @Bean
    public ResourceManager resourceManager() {
        ResourceManager resourceManager = new CachedResourceManager();
        resourceManager.setStorage(storage());
        return resourceManager;
    }

    // @Bean
    // public DataSource dataSource() {
    // final Properties pool = new Properties();
    // pool.put("driverClassName", "com.mysql.jdbc.Driver");
    // pool.put("url",
    // "jdbc:mysql://localhost:3306/uengine?useUnicode=true&characterEncoding=UTF8&useOldAliasMetadataBehavior=true");
    // pool.put("username", "root");
    // pool.put("password", "");
    // pool.put("minIdle", 1);
    // try {
    // return new
    // org.apache.tomcat.jdbc.pool.DataSourceFactory().createDataSource(pool);
    // } catch (Exception e) {
    // throw new RuntimeException(e);
    // }
    // }

    // @Bean
    /**
     *
     * <bean class="CouchbaseStorage">
     * <property name="basePath" value="/"/>
     * <property name="bucketName" value="default"/>
     * <property name="serverIp" value="localhost"/>
     * </bean>
     */
    public Storage storage() {
        LocalFileStorage storage = new LocalFileStorage();
        // storage.setBasePath("/oce/repository");
        // String UserName = System.getenv("USER");
        // storage.setBasePath("/Users/" + UserName);

        storage.setBasePath(basePath);
        storage.reserveSubtree("definition/simulation");

        return storage;
    }

    @Value("${uengine.definition.basePath}") // 추가된 부분
    String basePath = "";
    // @Bean
    // public TenantAwareFilter tenantAwareFilter(){
    // return new TenantAwareFilter();
    // }

    // @Bean
    // public MetadataService metadataService() {
    // DefaultMetadataService metadataService = new DefaultMetadataService();
    // metadataService.setResourceManager(resourceManager());

    // return metadataService;
    // }

    @Bean
    @Scope("prototype")
    public ProcessInstance processInstance(ProcessDefinition procDefinition, String instanceId, Map<?, ?> options)
            throws Exception {
        if (usesFileSimulation(procDefinition, instanceId)) {
            return new FileSimulationProcessInstance(procDefinition, instanceId, options);
        }
        return new CLOBProcessInstance(procDefinition, instanceId, options);
    }

    private boolean usesFileSimulation(ProcessDefinition definition, String instanceId) throws Exception {
        if (instanceId == null) {
            return definition != null
                    && ProcessTransactionContext.getThreadLocalInstance().getFileSimulationStorage(null) != null;
        }
        try {
            return ProcessTransactionContext.isFileSimulationId(Long.valueOf(instanceId));
        } catch (NumberFormatException ignored) {
            // Let the existing instance loader report an invalid ID.
            return false;
        }
    }

    @Bean
    public ActivityFilter instanceDataAppendingFilter(WorkItemAssignmentStateService assignmentStateService) {
        return new InstanceDataAppendingActivityFilter(assignmentStateService);
    }

    @Bean
    public WorkList workList() {
        return new JPAWorkList();
    }

    @Bean
    MappedInterceptor fileSimulationRestReads(ObjectMapper objectMapper) {
        ObjectMapper halMapper = objectMapper.copy().addMixIn(WorklistEntity.class, FileWorklistHal.class);
        return new MappedInterceptor(
                new String[] {"/worklist/search/findWorkListByInstId", "/instances/*"},
                new FileSimulationRestReads(halMapper));
    }

    private interface FileWorklistHal {
        @JsonIgnore
        ProcessInstanceEntity getProcessInstance();
    }

    private record FileSimulationRestReads(ObjectMapper mapper) implements HandlerInterceptor {
        private static final String WORKLIST_SEARCH = "/worklist/search/findWorkListByInstId";
        private static final String INSTANCE_PREFIX = "/instances/";
        private static final TypeReference<Map<String, Object>> HAL_BODY = new TypeReference<>() {};

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            if (!"GET".equals(request.getMethod())) return true;

            String requestPath = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
            boolean worklistSearch = WORKLIST_SEARCH.equals(requestPath);
            String requestId = worklistSearch ? request.getParameter("instId")
                    : requestPath.startsWith(INSTANCE_PREFIX) ? requestPath.substring(INSTANCE_PREFIX.length()) : null;
            Long id;
            try {
                id = Long.valueOf(requestId);
            } catch (NumberFormatException ignored) {
                return true;
            }
            if (!ProcessTransactionContext.isFileSimulationId(id)) return true;

            ProcessTransactionContext context = ProcessTransactionContext.getThreadLocalInstance();
            try (var storage = context.getFileSimulationStorage(id)) {
                Map<String, Object> halBody;
                if (worklistSearch) {
                    List<Map<String, Object>> items = storage.findWorkItems(id).stream()
                            .filter(item -> id.equals(item.getInstId()))
                            .sorted(Comparator.comparing(WorklistEntity::getEndDate,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .map(item -> worklistHal(request, item))
                            .toList();
                    halBody = Map.of(
                            "_embedded", Map.of("worklist", items),
                            "_links", Map.of("self", link(ServletUriComponentsBuilder.fromRequest(request).build().toUriString())));
                } else {
                    var instance = storage.findInstance(id)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                    String self = url(request, "/instances/" + id);
                    halBody = mapper.convertValue(instance, HAL_BODY);
                    halBody.put("_links", Map.of(
                            "self", link(self),
                            "processInstanceEntity", link(self),
                            "workLists", link(self + "/workLists"),
                            "roleMappings", link(self + "/roleMappings")));
                }
                response.setContentType("application/hal+json");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setHeader("Cache-Control", "no-store");
                mapper.writeValue(response.getOutputStream(), halBody);
                return false;
            } finally {
                context.remove();
            }
        }

        private Map<String, Object> worklistHal(HttpServletRequest request, WorklistEntity workItem) {
            Map<String, Object> body = mapper.convertValue(workItem, HAL_BODY);
            body.remove("taskId");
            String self = url(request, "/worklist/" + workItem.getTaskId());
            body.put("_links", Map.of(
                    "self", link(self),
                    "worklistEntity", link(self),
                    "processInstance", link(self + "/processInstance")));
            return body;
        }

        private static Map<String, String> link(String href) {
            return Map.of("href", href);
        }

        private static String url(HttpServletRequest request, String path) {
            return ServletUriComponentsBuilder.fromContextPath(request).path(path).build().toUriString();
        }
    }

    /**
     * 액티비티 실행 감사용 ActivityFilter.
     * afterExecute 시점에 ACTIVITY_EXECUTION 이벤트를 AuditService에 기록하며,
     * GlobalContext.getComponents(ActivityFilter.class)에 포함되려면 여기서 빈으로 등록해야 한다.
     */
    // @Bean
    // public ActivityFilter auditActivityFilter() {
    //     return new AuditActivityFilter();
    // }

    // @Bean
    // public Filter webFilter() {
    // return new TenantAwareFilter();
    // }

    // @Bean
    // public ProcessDefinitionFactory processDefinitionFactory() {
    // return new ProcessDefinitionFactory();
    // }

    // @Bean
    // public ProcessManagerRemote processManagerRemote() {
    // return new ProcessManagerBean();
    // }

    // @Bean
    // public TimerEventJob timerEventJob() {
    // return new TimerEventJob();
    // }

    @Bean
    public InstanceServiceLocal instanceServiceLocal() {
        return new InstanceServiceLocalImpl();
    }

    @Bean
    public DefinitionServiceLocal definitionServiceLocal() {
        return new org.uengine.five.overriding.DefinitionServiceLocalImpl();
    }

    @Bean
    public EMailServiceLocal emailServiceLocal() {
        return new EmailServiceLocalImpl();
    }

    @Bean
    EvaluationContextExtension securityExtension() {
        return new TenantSecurityEvaluationContextExtension();
    }
}
