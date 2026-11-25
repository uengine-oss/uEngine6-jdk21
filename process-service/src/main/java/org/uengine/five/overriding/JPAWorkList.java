package org.uengine.five.overriding;

import org.uengine.hwlife.absence.AbsenceServiceImpl;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.RoleResolutionContext;
import org.uengine.kernel.DirectRoleResolutionContext;
import org.uengine.hwlife.overriding.BoundRoleResolutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.uengine.five.entity.WorklistEntity;
import org.uengine.five.lifecycle.BpmLifecycleService;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.ExecutionRecords;
import org.uengine.five.service.WorkItemAssignmentStateService;
import org.uengine.kernel.KeyedParameter;
import org.uengine.kernel.RoleMapping;
import org.uengine.kernel.Role;
import org.uengine.processmanager.TransactionContext;
import org.uengine.util.UEngineUtil;
import org.uengine.webservices.worklist.DefaultWorkList;
import org.uengine.webservices.worklist.WorkList;

import java.rmi.RemoteException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Created by uengine on 2017. 8. 13..
 */
public class JPAWorkList implements WorkList {

    @Override
    public String reserveWorkItem(RoleMapping roleMapping, KeyedParameter[] parameters, TransactionContext tc) throws RemoteException {
        Map parameterMap = getParameterMap(parameters);
        return addWorkItemImpl(null, roleMapping, parameterMap, true, tc);
    }

    @Override
    public String addWorkItem(RoleMapping roleMapping, KeyedParameter[] parameters, TransactionContext tc) throws RemoteException {
        Map parameterMap = getParameterMap(parameters);
        return addWorkItemImpl(null, roleMapping, parameterMap, false, tc);
    }

    @Override
    public String addWorkItem(String reservedTaskId, RoleMapping roleMapping, KeyedParameter[] parameters, TransactionContext tc) throws RemoteException {
        Map parameterMap = getParameterMap(parameters);
        return addWorkItemImpl(reservedTaskId, roleMapping, parameterMap, false, tc);
    }

    @Autowired
    WorklistRepository worklistRepository;

    @Autowired(required = false)
    BpmLifecycleService bpmLifecycleService;

    @Autowired(required = false)
    WorkItemAssignmentStateService assignmentStateService;

    @Autowired
    AbsenceServiceImpl absenceService;

    protected void prepareWorkItemStorage(Long instanceId) throws Exception {
    }

    protected WorklistEntity findWorkItem(Long taskId) throws Exception {
        return worklistRepository.findById(taskId).orElseThrow();
    }

    protected WorklistEntity findReservedWorkItem(Long instanceId, Long taskId) throws Exception {
        return findWorkItem(taskId);
    }

    protected WorklistEntity createWorkItem() {
        WorklistEntity workItem = new WorklistEntity();
        worklistRepository.save(workItem);
        return workItem;
    }

    protected void saveWorkItem(WorklistEntity workItem) throws Exception {
        worklistRepository.save(workItem);
    }

    @FunctionalInterface
    protected interface DatabaseUpdate {
        void run() throws Exception;
    }

    protected void applyDatabaseUpdates(DatabaseUpdate update) throws Exception {
        update.run();
    }

    void applyDirectAssignmentAgent(RoleMapping mapping, Map parameters, boolean reservation,
                                    TransactionContext tc) throws Exception {
        if (reservation || mapping == null || !UEngineUtil.isNotEmpty(mapping.getEndpoint())
                || !(tc instanceof org.uengine.processmanager.ProcessTransactionContext processTc)) return;
        ProcessInstance instance = processTc.getProcessInstanceInTransaction(
                String.valueOf(parameters.get(KeyedParameter.INSTANCEID)));
        if (instance == null || !(instance.getProcessDefinition().getActivity(
                String.valueOf(parameters.get(KeyedParameter.TRACINGTAG))) instanceof org.uengine.kernel.HumanActivity)) return;
        Role role = instance.getProcessDefinition().getRole(String.valueOf(parameters.get("roleName")));
        if (role == null) return;
        RoleResolutionContext context = role.getRoleResolutionContext();
        while (context instanceof BoundRoleResolutionContext bound) {
            context = bound.getBase();
        }
        if (!(context instanceof DirectRoleResolutionContext)) return;
        if (absenceService.applyActiveAgent(mapping)) {
            parameters.put("groupName", mapping.getGroupName());
            parameters.put("resourceName", mapping.getResourceName());
            instance.putRoleMapping(role.getName(), mapping);
        }
    }

    protected String addWorkItemImpl(String reservedTaskId, RoleMapping roleMapping, Map parameterMap, boolean isReservation, TransactionContext tc) throws RemoteException {

        try{

            Long instId = Long.valueOf(String.valueOf(parameterMap.get(KeyedParameter.INSTANCEID)));
            prepareWorkItemStorage(instId);
            applyDatabaseUpdates(() -> applyDirectAssignmentAgent(roleMapping, parameterMap, isReservation, tc));
            Calendar startDate = Calendar.getInstance();

            if(parameterMap.containsKey("startedTime")){
                startDate.setTimeInMillis(Long.parseLong((String) parameterMap.get("startedTime")));
            }

            Calendar dueDate = null;
            try{
                String dueDateInMSStr = ""+parameterMap.get(KeyedParameter.DUEDATE);
                long dueDateInMS = Long.parseLong(dueDateInMSStr);
                dueDate = Calendar.getInstance();
                dueDate.setTimeInMillis(dueDateInMS);
            }catch(Exception e){
            }

            if(dueDate==null){
                int duration = 0;
                {
                    try{
                        String durationStr = ""+parameterMap.get(KeyedParameter.DURATION);
                        duration = Integer.parseInt(durationStr);
                    }catch(Exception e){
                    }
                }

                if(duration>0){
                    dueDate = Calendar.getInstance();
                    dueDate.setTimeInMillis(startDate.getTimeInMillis() + (long)duration * 86400000L);
                    int dayOfMonth = dueDate.get(Calendar.DAY_OF_MONTH);
                    int year = dueDate.get(Calendar.YEAR);
                    int month = dueDate.get(Calendar.MONTH);
                }
            }

            Integer priority = Integer.valueOf(1);
            {
                try{
                    String priorityStr = ""+parameterMap.get(KeyedParameter.PRIORITY);
                    priority = Integer.valueOf(priorityStr);
                }catch(Exception e){
                }
            }

            final WorklistEntity wl;

            if(reservedTaskId!=null){
                Long taskId = Long.valueOf(reservedTaskId);
                ExecutionRecords.requireSameStorage(instId, taskId);
                wl = findReservedWorkItem(instId, taskId);
            }
            else{
                wl = createWorkItem();
            }

            if(dueDate!=null)
                wl.setDueDate(dueDate.getTime());
            else
                wl.setDueDate(null);

            String definitionName = (String)parameterMap.get("definitionName");
            String instanceName = (String)parameterMap.get("instanceName");
            String instanceId = (String)parameterMap.get(KeyedParameter.INSTANCEID);

            wl.setPriority(priority);
            wl.setTool(""+parameterMap.get(KeyedParameter.TOOL));
            String endpoint = roleMapping != null ? roleMapping.getEndpoint() : null;
            wl.setEndpoint(endpoint);

            //modified
            Timestamp startedTime;
            startedTime = new Timestamp(startDate.getTimeInMillis());
            wl.setStartDate(startedTime);

            wl.setTitle(""+parameterMap.get(KeyedParameter.TITLE));
            wl.setTrcTag(""+parameterMap.get(KeyedParameter.TRACINGTAG));
            wl.setInstId(instId);
            wl.setRootInstId(new Long(""+parameterMap.get(KeyedParameter.ROOTINSTANCEID)));
            wl.setDefId(""+parameterMap.get(KeyedParameter.PROCESSDEFINITION));
            wl.setDefName(""+parameterMap.get(KeyedParameter.PROCESSDEFINITIONNAME));
            wl.setRoleName(""+parameterMap.get("roleName"));
            wl.setRefRoleName(""+parameterMap.get("referenceRoleName"));
            // Diagram-aligned: fill() is triggered at WorkList save time.
            // If resourceName is missing, call RoleMapping.fill() so that IAMService can populate it (Flyweight + isFilled prevents duplication).
            Object resNameObj = parameterMap.get("resourceName");
            String resName = resNameObj != null ? String.valueOf(resNameObj) : null;
            if (!UEngineUtil.isNotEmpty(resName) || resName.equals(endpoint)) {
                try {
                    RoleMapping rm = roleMapping != null ? roleMapping : RoleMapping.create();
                    if (rm != null) {
                        if (!UEngineUtil.isNotEmpty(rm.getEndpoint()) && UEngineUtil.isNotEmpty(endpoint)) rm.setEndpoint(endpoint);

                        Object scope = parameterMap.get("scope");
                        if (scope != null && !UEngineUtil.isNotEmpty(rm.getScope())) rm.setScope(String.valueOf(scope));

                        Object groupName = parameterMap.get("groupName");
                        if (groupName != null && !UEngineUtil.isNotEmpty(rm.getGroupName())) rm.setGroupName(String.valueOf(groupName));

                        Object assignType = parameterMap.get("assignType");
                        if (assignType != null && rm.getAssignType() == 0) {
                            try {
                                rm.setAssignType(Integer.parseInt(String.valueOf(assignType)));
                            } catch (Exception ignore) {
                            }
                        }

                        rm.fill();
                        resName = rm.getResourceName();
                    }
                } catch (Exception ignore) {
                }
            }
            wl.setResName(resName);
            wl.setDefVerId(""+parameterMap.get(KeyedParameter.PROCESSDEFINITIONVERSION));
            Object scopeObj = parameterMap.get("scope");
            String scope = scopeObj == null ? null : String.valueOf(scopeObj);
            wl.setScope(scope);
            Object groupNameObj = parameterMap.get("groupName");
            wl.setGroupCd(groupNameObj != null ? String.valueOf(groupNameObj) : null);
            wl.setAssignType(Integer.parseInt("" + parameterMap.get("assignType")));

            if(parameterMap.containsKey("actType")){
                wl.setActType((String) parameterMap.get("actType"));
            }

            if(parameterMap.containsKey("endDate")){
                Object endDateObj = parameterMap.get("endDate");
                if (endDateObj instanceof Date) {
                    wl.setEndDate(new Timestamp(((Date)endDateObj).getTime()));
                }
            }

//            int i=1;
//            while(parameterMap.containsKey("dispatchParam" + i)){
//                wl.setDispatchParam(" +i, ""+parameterMap.get("dispatchParam" + i));
//                i++;
//            }

            if(parameterMap.containsKey("executionScope")){
                wl.setExecScope((String)parameterMap.get("executionScope"));
                wl.setTitle(wl.getTitle() + "(" + parameterMap.get("executionScopeName") + ")");
            }

            if(parameterMap.containsKey("extValue1")){
                wl.setExt1((String) parameterMap.get("extValue1"));
            }
            if(parameterMap.containsKey("extValue2")){
                wl.setExt2((String) parameterMap.get("extValue2"));
            }
            if(parameterMap.containsKey("extValue3")){
                wl.setExt3((String) parameterMap.get("extValue3"));
            }
            if(parameterMap.containsKey("extValue4")){
                wl.setExt4((String) parameterMap.get("extValue4"));
            }
            if(parameterMap.containsKey("extValue5")){
                wl.setExt5((String) parameterMap.get("extValue5"));
            }
            if(parameterMap.containsKey("apvlYn")){
                wl.setApvlYn(Boolean.parseBoolean((String) parameterMap.get("apvlYn")));
            }
            if(parameterMap.containsKey("imgeScanYn")){
                wl.setImgeScanYn(Boolean.parseBoolean((String) parameterMap.get("imgeScanYn")));
            }

            //dispatching option//////
            try{
                int dispatchingOption = Integer.parseInt((String)parameterMap.get(KeyedParameter.DISPATCHINGOPTION));
                wl.setDispatchOption(dispatchingOption);
            }catch(Exception e){
                wl.setDispatchOption(Role.DISPATCHINGOPTION_ALL);
            }

            //status//////
            String defaultStatus = parameterMap.containsKey(KeyedParameter.DEFAULT_STATUS) ?
                    ""+parameterMap.get(KeyedParameter.DEFAULT_STATUS)
                    :
                    (isReservation ? DefaultWorkList.WORKITEM_STATUS_RESERVED : DefaultWorkList.WORKITEM_STATUS_NEW);
            wl.setStatus(defaultStatus);
            //

            saveWorkItem(wl);
            applyDatabaseUpdates(() -> {
                if (assignmentStateService != null) {
                    Long rootInstId = wl.getRootInstId() != null ? wl.getRootInstId() : wl.getInstId();
                    assignmentStateService.initializeFirstUnitHandler(wl);
                    assignmentStateService.synchronize(rootInstId);
                }
                // endpoint 가 있으면 즉시 배정 확정. 경합(endpoint=null)은 claim 시 발행.
                if (bpmLifecycleService != null) {
                    bpmLifecycleService.onTaskAssigned(wl, tc);
                }
            });

            return String.valueOf(wl.getTaskId());

        }catch(Exception e){

            System.out.println("====================HARD-TO-FIND-ERR: pi=" + parameterMap.get(KeyedParameter.INSTANCEID) + "  tracingTag=" + parameterMap.get(KeyedParameter.TRACINGTAG));

            throw new RemoteException("ExtWorkList", e);
        }
    }

    public void cancelWorkItem(String taskID, KeyedParameter[] options, TransactionContext tc)
            throws RemoteException {

        try{

            Long id = Long.valueOf(taskID);
            WorklistEntity wl = findWorkItem(id);

            if(wl==null) return;

            wl.setStatus(DefaultWorkList.WORKITEM_STATUS_CANCELLED);
            if(options != null) {
                for(int i=0; i<options.length; i++){
                    KeyedParameter parameter = options[i];
                    if("status".equals(parameter.getKey()) && UEngineUtil.isNotEmpty((String)parameter.getValue())){
                        String status = String.valueOf(parameter.getValue());
                        if(UEngineUtil.isNotEmpty(status)){
                            wl.setStatus(status.toUpperCase());
                        }
                    }
                }
            }

            saveWorkItem(wl);
            applyDatabaseUpdates(() -> notifyTaskTerminated(wl, tc));

        }catch(Exception e){
            throw new RemoteException("ExtWorkList", e);
        }
    }

    public void compensateWorkItem(String taskID, KeyedParameter[] options, TransactionContext tc)
            throws RemoteException {

        try{

            Long id = Long.valueOf(taskID);
            WorklistEntity wl = findWorkItem(id);

            if(wl==null) return;

            wl.setStatus(DefaultWorkList.WORKITEM_STATUS_COMPENSATED);
            if(options != null) {
                for(int i=0; i<options.length; i++){
                    KeyedParameter parameter = options[i];
                    if("status".equals(parameter.getKey()) && UEngineUtil.isNotEmpty((String)parameter.getValue())){
                        String status = String.valueOf(parameter.getValue());
                        if(UEngineUtil.isNotEmpty(status)){
                            wl.setStatus(status.toUpperCase());
                        }
                    }
                }
            }

            saveWorkItem(wl);
            applyDatabaseUpdates(() -> notifyTaskTerminated(wl, tc));

        }catch(Exception e){
            throw new RemoteException("ExtWorkList", e);
        }
    }

    public void completeWorkItem(String taskID, KeyedParameter[] options, TransactionContext tc)
            throws RemoteException {

        try{
            Calendar now = Calendar.getInstance();

            Long id = Long.valueOf(taskID);
            WorklistEntity wl = findWorkItem(id);
            wl.setStatus(DefaultWorkList.WORKITEM_STATUS_COMPLETED);
            wl.setEndDate(new Timestamp(now.getTimeInMillis()));

            saveWorkItem(wl);
            applyDatabaseUpdates(() -> {
                if (assignmentStateService != null) {
                    assignmentStateService.completeUnitWork(wl);
                }
                notifyTaskTerminated(wl, tc);
            });

        }catch(Exception e){
            throw new RemoteException("ExtWorkList", e);
        }
    }

    public void updateWorkItem(
            String taskId,
            RoleMapping roleMapping,
            KeyedParameter[] parameters,
            TransactionContext tc)
            throws RemoteException {

        try{

            Long id = Long.valueOf(taskId);
            WorklistEntity wlDAO = findWorkItem(id);

            // 변경 전 endpoint 기억 (배정 변경 감지용)
            String previousEndpoint = wlDAO.getEndpoint();

            if (roleMapping != null && UEngineUtil.isNotEmpty(roleMapping.getEndpoint()))
                wlDAO.setEndpoint(roleMapping.getEndpoint());

            String terminateStatus = null;
            for(int i=0; i<parameters.length; i++){
                KeyedParameter parameter = parameters[i];

                if(KeyedParameter.DISPATCHINGOPTION.equals(parameter.getKey())){
                    wlDAO.setDispatchOption(Integer.parseInt(""+parameter.getValue()));
                }else
                if("dispatchParam1".equals(parameter.getKey())){
                    wlDAO.setDispatchParam1(""+parameter.getValue());
                }else
                if(KeyedParameter.DUEDATE.equals(parameter.getKey())){
                    wlDAO.setDueDate((Date)parameter.getValue());
                }else
                if(KeyedParameter.DEFAULT_STATUS.equals(parameter.getKey())){
                    terminateStatus = (String)parameter.getValue();
                    wlDAO.setStatus(terminateStatus);
                }else
                if("endDate".equals(parameter.getKey())){
                    wlDAO.setEndDate((Date)parameter.getValue());
                }else
                if("saveDate".equals(parameter.getKey())){ //임시저장 시간 저장 (11.23)
                    wlDAO.setSaveDate((Date)parameter.getValue());
                }
            }

            saveWorkItem(wlDAO);
            String updatedStatus = terminateStatus;
            applyDatabaseUpdates(() -> notifyTaskUpdated(wlDAO, tc, updatedStatus, previousEndpoint));

        }catch(Exception e){
            throw new RemoteException("ExtWorkList", e);
        }
    }

    private void notifyTaskTerminated(WorklistEntity workItem, TransactionContext tc) {
        if (bpmLifecycleService != null) {
            bpmLifecycleService.onTaskTerminated(workItem, tc);
        }
    }

    private void notifyTaskUpdated(WorklistEntity workItem, TransactionContext tc, String status,
                                   String previousEndpoint) {
        if (bpmLifecycleService == null) return;

        if (DefaultWorkList.WORKITEM_STATUS_DELEGATED.equalsIgnoreCase(status)) {
            // 완전 위임: 원 workitem DELEGATED 종료. 신규 workitem은 executeActivity에서 생성한다.
            bpmLifecycleService.onTaskTerminated(workItem, tc);
        } else if (UEngineUtil.isNotEmpty(workItem.getEndpoint())
                && !Objects.equals(workItem.getEndpoint(), previousEndpoint)) {
            // 원소유 유지 위임·담당자 재배정에서 endpoint가 변경된 경우만 발행한다.
            bpmLifecycleService.onTaskAssignmentChanged(workItem, tc);
        }
    }

    private Map getParameterMap(KeyedParameter[] parameters){
        Map parameterMap = new HashMap();

        for(int i=0; i<parameters.length; i++){
            parameterMap.put(parameters[i].getKey(), parameters[i].getValue());
        }

        return parameterMap;
    }


}
