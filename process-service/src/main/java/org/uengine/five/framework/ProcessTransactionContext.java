package org.uengine.five.framework;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.uengine.contexts.UserContext;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.service.DefinitionServiceUtil;
import org.uengine.five.spring.SecurityAwareServletFilter;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.kernel.ActivityInstanceContext;
import org.uengine.kernel.DefaultProcessInstance;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.TransactionListener;
import org.uengine.util.ForLoop;

/**
 * Created by uengine on 2017. 11. 10..
 */
public class ProcessTransactionContext implements org.uengine.processmanager.ProcessTransactionContext {

    static ThreadLocal<org.uengine.five.framework.ProcessTransactionContext> local = new ThreadLocal<org.uengine.five.framework.ProcessTransactionContext>();
    private boolean commitable;
    private FileSimulationStorage fileSimulationStorage;
    private boolean annotatedBoundary;

    protected ProcessTransactionContext() {
        if (local.get() != null && local.get().isCommitable()) {
            throw new RuntimeException("There's uncommitted transactionContext remains.");
        }
        setCommitable(true);
        local.set(this);
    }

    public static org.uengine.five.framework.ProcessTransactionContext getThreadLocalInstance() {
        org.uengine.five.framework.ProcessTransactionContext tc = local.get();
        if (tc == null) {
            // if the local instance is obtained without @ProcessTransactional demarcation
            tc = new org.uengine.five.framework.ProcessTransactionContext();
            tc.setCommitable(false);
        }
        return tc;
    }

    public static boolean isFileSimulationId(Long id) {
        return FileSimulationStorage.ownsId(id);
    }

    public void beginFileSimulation() throws Exception {
        if (!isCommitable() || fileSimulationStorage != null) {
            throw new IllegalStateException("A fresh writable process transaction is required for file simulation");
        }
        String owner = hasHttpRequest() ? SecurityAwareServletFilter.getVerifiedFileExecutionActor()
                : UserContext.getThreadLocalInstance().getUserId();
        if (owner == null || owner.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        fileSimulationStorage = FileSimulationStorage.create(owner);
    }

    public FileSimulationStorage getFileSimulationStorage(Long entityId) throws Exception {
        if (entityId == null) return fileSimulationStorage;
        if (!isFileSimulationId(entityId)) return null;
        boolean http = hasHttpRequest();
        String actor = http ? SecurityAwareServletFilter.getVerifiedFileExecutionActor() : null;
        if (fileSimulationStorage != null) {
            if (fileSimulationStorage.contains(entityId)) return requireFileOwner(fileSimulationStorage, http, actor);
            if (isCommitable()) throw new IllegalStateException("A file simulation cannot mutate another simulation");
        }
        FileSimulationStorage storage = FileSimulationStorage.open(entityId, isCommitable());
        try {
            requireFileOwner(storage, http, actor);
        } catch (Exception unauthorized) {
            storage.close();
            throw unauthorized;
        }
        if (isCommitable() || annotatedBoundary) fileSimulationStorage = storage;
        return storage;
    }

    private static boolean hasHttpRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes;
    }

    private static FileSimulationStorage requireFileOwner(FileSimulationStorage storage, boolean http, String actor) {
        if (http && (actor == null || !actor.equals(storage.getOwner()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return storage;
    }

    @FunctionalInterface
    public interface FileSimulationCallback {
        void run() throws Exception;
    }

    public static void runFileSimulationCallback(Long instanceId, FileSimulationCallback callback) throws Exception {
        if (!isFileSimulationId(instanceId)) throw new IllegalArgumentException("Expected a file simulation instance ID");
        ProcessTransactionContext previous = local.get();
        ProcessTransactionContext context = new ProcessTransactionContext();
        try {
            if (context.getFileSimulationStorage(instanceId).findInstance(instanceId).isEmpty()) {
                context.rollback();
                return;
            }
            callback.run();
            context.commit();
        } catch (Exception failure) {
            try {
                context.rollback();
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            if (local.get() == context) context.remove();
            if (previous != null) local.set(previous);
        }
    }

    static void closeFileSimulationRead() throws Exception {
        ProcessTransactionContext current = local.get();
        if (current != null && current.fileSimulationStorage != null) current.remove();
    }

    void setAnnotatedBoundary() {
        annotatedBoundary = true;
    }


    List<TransactionListener> transactionListeners = new ArrayList<TransactionListener>();

    public void addTransactionListener(TransactionListener tl) {
        transactionListeners.add(tl);
    }

    public List<TransactionListener> getTransactionListeners() {
        return transactionListeners;
    }

    transient List<ActivityInstanceContext> executedActivities = new ArrayList<ActivityInstanceContext>();

    public void addExecutedActivityInstanceContext(ActivityInstanceContext aic) {
        if (!executedActivities.contains(aic)) {
            executedActivities.add(aic);
        }
    }

    public List<ActivityInstanceContext> getExecutedActivityInstanceContextsInTransaction() {
        return executedActivities;
    }

    transient Map<String, ProcessInstance> cachedProcessInstances = new Hashtable<String, ProcessInstance>();

    public void registerProcessInstance(ProcessInstance pi) {
        cachedProcessInstances.put(pi.getInstanceId(), pi);
    }

    public ProcessInstance getProcessInstanceInTransaction(String instanceId) {
        return cachedProcessInstances.get(instanceId);
    }

    public Map<String, ProcessInstance> getProcessInstancesInTransaction() {
        return cachedProcessInstances;
    }

    @Override
    public void registerProcessInstance(DefaultProcessInstance defaultProcessInstance) {
        this.cachedProcessInstances.put(defaultProcessInstance.getInstanceId(), defaultProcessInstance);
    }

    transient Map<String, Object> sharedContexts = new Hashtable<String, Object>();

    public void setSharedContext(String contextKey, Object value) {
        if (value == null) {
            sharedContexts.remove(contextKey);
        } else {
            sharedContexts.put(contextKey, value);
        }
    }

    public Object getSharedContext(String contextKey) {
        if (!sharedContexts.containsKey(contextKey)) {
            return null;
        }
        return sharedContexts.get(contextKey);
    }

    protected void beforeCommit() throws Exception {
        ForLoop beforeCommitLoop = new ForLoop() {
            public void logic(Object target) {
                TransactionListener tl = (TransactionListener) target;
                try {
                    tl.beforeCommit(org.uengine.five.framework.ProcessTransactionContext.this);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        };

        beforeCommitLoop.run((ArrayList) transactionListeners);
    }

    public void commit() throws Exception {
        if (!isCommitable()) {
            throw new Exception(
                    "This transaction is uncommitable. If you want to commit this, mark with @ProcessTransactional at the beginning of the service method.");
        }
        if (fileSimulationStorage == null) {
            beforeCommit();
            remove();
        } else {
            try {
                beforeCommit();
                fileSimulationStorage.commit();
            } finally {
                remove();
            }
        }
        afterCommit();
    }

    private void afterCommit() throws Exception {
        for (TransactionListener tl : getTransactionListeners()) {
            tl.afterCommit(this);
        }
        
    }

    public void rollback() throws Exception {
        remove();
    }

    @Override
    public void releaseResources() throws Exception {

    }

    @Override
    public void addDebugInfo(String s) {

    }

    public void remove() throws Exception {
        local.remove();
        if (fileSimulationStorage != null) {
            fileSimulationStorage.close();
            fileSimulationStorage = null;
        }
    }

    public void setCommitable(boolean commitable) {
        this.commitable = commitable;
    }

    public boolean isCommitable() {
        return commitable;
    }

    @Override
    public void addDebugInfo(Object message) {

    }

    @Override
    public StringBuilder getDebugInfo() {
        return null;
    }

    // @Override
    // public ServletRequest getServletRequest() {
    // return null;
    // }

    // @Override
    // public ServletResponse getServletResponse() {
    // return null;
    // }

    // @Override
    // public ProcessManagerBean getProcessManager() {
    // return null;
    // }

    @Override
    public boolean isManagedTransaction() {
        return false;
    }

    @Override
    public ProcessDefinition getProcessDefinition(String pdvid) throws Exception {
        DefinitionServiceUtil definitionService = ProcessServiceApplication.getApplicationContext()
                .getBean(DefinitionServiceUtil.class);

        Object definition = definitionService.getDefinition(pdvid);
        if (definition instanceof ProcessDefinition) {
            ProcessDefinition processDefinition = (ProcessDefinition) definition;

            return processDefinition;
        } else {
            throw new RuntimeException("It is not a ProcessDefinition type with defId: " + pdvid);
        }
        // return null;
    }

    public ProcessDefinition getProcessDefinition(String pdvid, String version) throws Exception {
        DefinitionServiceUtil definitionService = ProcessServiceApplication.getApplicationContext()
                .getBean(DefinitionServiceUtil.class);

        Object definition = definitionService.getDefinition(pdvid, version);
        if (definition instanceof ProcessDefinition) {
            ProcessDefinition processDefinition = (ProcessDefinition) definition;

            return processDefinition;
        } else {
            throw new RuntimeException("It is not a ProcessDefinition type with defId: " + pdvid);
        }
        // return null;
    }
}
