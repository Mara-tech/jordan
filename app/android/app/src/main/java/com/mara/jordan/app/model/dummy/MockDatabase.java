package com.mara.jordan.app.model.dummy;

import android.util.Log;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import org.apache.commons.collections4.MapUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Helper class for providing sample content for user interfaces created by
 * Android template wizards.
 * <p>
 * TODO: Replace all uses of this class before publishing your app.
 */
public class MockDatabase {

    public static final List<EasyAction> ACTIONS = new ArrayList<EasyAction>();
    public static final Map<Long, EasyAction> ACTIONS_MAP = new HashMap<>();


    public static final List<EasyStatus> STATUSES = new ArrayList<>();
    public static final List<EasyTask> TASKS = new ArrayList<>();

    public static final List<EasyMessage> MESSAGES = new ArrayList<>();
    private static final String TAG = "MockDatabase";


    static {
        EasyTask brainTask = EasyTask.builder()
                .taskId(1L)
                .taskName("brain")
                .progress(70)
                .build();
        EasyTask legsTask = EasyTask.builder()
                .taskId(2L)
                .progress(24)
                .taskName("legs")
                .build();
        EasyTask eyesTask = EasyTask.builder()
                .taskId(3L)
                .taskName("eyes")
                .build();
        EasyTask armTask = EasyTask.builder()
                .taskId(4L)
                .taskName("arm")
                .build();
        addTask(brainTask);
        addTask(legsTask);
        addTask(eyesTask);
        addTask(armTask);

        addAction(EasyAction.builder()
                .id(10L)
                .actionName("think")
                .parameters(Lists.newArrayList(
                        EasyActionParameter.builder().parameterName("subject").parameterType("string").build(),
                        EasyActionParameter.builder().parameterName("duration").parameterType("int").defaultValue(30).build()
                ))
                .parentTask(brainTask)
                .build());
        addAction(EasyAction.builder()
                .id(11L)
                .actionName("idle")
                .parentTask(brainTask)
                .build());
        EasyAction walkAction = EasyAction.builder()
                .id(20L)
                .actionName("walk")
                .parameters(Lists.newArrayList(
                        EasyActionParameter.builder().parameterName("direction").parameterType("float").defaultValue(180.0).build(),
                        EasyActionParameter.builder().parameterName("speed").parameterType("float").defaultValue(5.0).build()
                ))
                .parentTask(legsTask)
                .build();
        addAction(walkAction);
        addAction(EasyAction.builder()
                .id(21L)
                .actionName("idle")
                .parentTask(legsTask)
                .build());
        addAction(EasyAction.builder()
                .id(30L)
                .actionName("light_vision")
                .parentTask(eyesTask)
                .build());
        addAction(EasyAction.builder()
                .id(31L)
                .actionName("night_vision")
                .parentTask(eyesTask)
                .build());
        EasyAction xrayVisionAction = EasyAction.builder()
                .id(32L)
                .actionName("xray_vision")
                .parentTask(eyesTask)
                .build();
        addAction(xrayVisionAction);
        addAction(EasyAction.builder()
                .id(40L)
                .actionName("grab_object")
                .parameters(Lists.newArrayList(
                        EasyActionParameter.builder().parameterName("object_name").defaultValue("gun").build(),
                        EasyActionParameter.builder().parameterName("hand").defaultValue("right").build()
                ))
                .parentTask(armTask)
                .build());
        addAction(EasyAction.builder()
                .id(41L)
                .actionName("use_object")
                .parameters(Lists.newArrayList(
                        EasyActionParameter.builder().parameterName("aim").parameterType("float").defaultValue(0.0).build(),
                        EasyActionParameter.builder().parameterName("hand").defaultValue("right").build()
                ))
                .parentTask(armTask)
                .build());
        addAction(EasyAction.builder()
                .id(42L)
                .actionName("put_object")
                .parentTask(armTask)
                .build());


        addStatus(EasyStatus.builder()
                .id(1L)
                .parentTask(brainTask)
                .timestamp(1607971392285L)
                .type("general")
                .status("I'm starting to think about Human condition.")
                .build()
        );
        addStatus(EasyStatus.builder()
                .id(2L)
                .parentTask(brainTask)
                .timestamp(1607971394285L)
                .type("general")
                .status("My conclusion is : Humans suck")
                .build()
        );

        addStatus(EasyStatus.builder()
                .id(3L)
                .parentTask(legsTask)
                .timestamp(1607971294285L)
                .type("general")
                .status("I'm walking South")
                .build()
        );
        addStatus(EasyStatus.builder()
                .id(4L)
                .parentTask(legsTask)
                .timestamp(1607971394285L)
                .type("success")
                .status("Checkpoint reached !")
                .build()
        );
        addStatus(EasyStatus.builder()
                .id(5L)
                .parentTask(eyesTask)
                .timestamp(1607571294285L)
                .type("failure")
                .status("Couldn't switch to X-RAY vision.")
                .build()
        );
        addStatus(EasyStatus.builder()
                .id(6L)
                .parentTask(eyesTask)
                .timestamp(1607976394285L)
                .type("general")
                .status("I see a silhouette of a man.")
                .build()
        );

        addMessage(EasyMessage.builder()
                .id(1L)
                .action(walkAction)
                .author("cpuyol")
                .audit(Lists.newArrayList(
                        EasyMessageState.builder()
                                .id(100L)
                                .state("SERVER_RECEIVED")
                                .timestamp(1607571394285L)
                                .build()))
                .build()
        );

        addMessage(EasyMessage.builder()
                .id(2L)
                .action(xrayVisionAction)
                .author("mjordan")
                .audit(Lists.newArrayList(
                        EasyMessageState.builder()
                                .id(200L)
                                .state("SERVER_RECEIVED")
                                .timestamp(1607541394285L)
                                .build(),
                        EasyMessageState.builder()
                                .id(201L)
                                .state("MESSAGE_DELIVERED")
                                .timestamp(1607541424285L)
                                .build(),
                        EasyMessageState.builder()
                                .id(202L)
                                .state("CLIENT_RECEIVED")
                                .timestamp(1607541425285L)
                                .build(),
                        EasyMessageState.builder()
                                .id(203L)
                                .state("MESSAGE_ACKNOWLEDGED")
                                .timestamp(1607541426285L)
                                .build(),
                        EasyMessageState.builder()
                                .id(204L)
                                .state("MESSAGE_PROCESSED")
                                .timestamp(1607541427285L)
                                .build()
                        ))
                .build()
        );
    }

    private static void addTask(EasyTask task) {
        TASKS.add(task);
    }

    private static void addAction(EasyAction item) {
        ACTIONS.add(item);
        ACTIONS_MAP.put(item.getId(), item);
    }


    private static void addStatus(EasyStatus item) {
        STATUSES.add(item);
    }
    private static void addMessage(EasyMessage item) {
        MESSAGES.add(item);
    }

    public static List<String> getTaskNames() {
        List<String> list = new ArrayList<>();
        for (EasyTask TASK : TASKS) {
            String taskName = TASK.getTaskName();
            list.add(taskName);
        }
        return list;
    }

    public static List<EasyStatus> selectStatus(String textQuery, Map<String, Boolean> typeFilter, Map<String, Boolean> taskFilter) {
        List<EasyStatus> list = new ArrayList<>();
        for (EasyStatus s : STATUSES) {
            boolean validStatus = true;
            if(!Strings.isNullOrEmpty(textQuery)) {
                validStatus = s.status.toLowerCase().contains(textQuery.toLowerCase());
            }
            boolean validType = true;
            if(!MapUtils.isEmpty(typeFilter)){
                String type = s.getType();
                if(!typeFilter.containsKey(type)) {
                    Log.e(TAG, "Type " + type + " is not handled by type filter (from Dialog). Check StatusFilterTypeAdapter");
                    validType = true;
                } else {
                    validType = typeFilter.get(type);
                }
            }
            boolean validTask = true;
            if(!MapUtils.isEmpty(taskFilter)){
                String task = s.getParentTask().getTaskName();
                if(!taskFilter.containsKey(task)) {
                    Log.e(TAG, "Task " + task + " is not handled by task filter (from Dialog). Check StatusFilterTaskAdapter");
                    validTask = true;
                } else {
                    validTask = taskFilter.get(task);
                }
            }
            if(validStatus && validType && validTask){
                list.add(s);
            }
        }
        return list;
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class EasyAction {
        private long id;
        private String actionName;
        private EasyTask parentTask;
        private List<EasyActionParameter> parameters = new ArrayList<>();


        @Override
        public String toString() {
            return actionName;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class EasyActionParameter {
        private String parameterName;
        private String parameterType;
        private Object defaultValue;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class EasyTask {
        private long taskId;
        private String taskName;
        private Integer progress;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class EasyStatus {
        private long id;
        private String type;
        private String status;
        private long timestamp;
        private EasyTask parentTask;
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class EasyMessage {
        private long id;
        private String author;
        private List<EasyMessageState> audit;
        private EasyAction action;
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class EasyMessageState {
        private long id;
        private long timestamp;
        private String state;
    }
}