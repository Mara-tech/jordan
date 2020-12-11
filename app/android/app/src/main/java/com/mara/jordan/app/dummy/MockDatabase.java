package com.mara.jordan.app.dummy;

import com.google.common.collect.Lists;

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

    /**
     * An array of sample (dummy) items.
     */
    public static final List<EasyAction> ITEMS = new ArrayList<EasyAction>();

    /**
     * A map of sample (dummy) items, by ID.
     */
    public static final Map<Long, EasyAction> ITEM_MAP = new HashMap<>();

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

        addItem(EasyAction.builder()
                .id(10L)
                .actionName("think")
                .parameters(Lists.newArrayList(
                        EasyActionParameter.builder().parameterName("subject").parameterType("string").build(),
                        EasyActionParameter.builder().parameterName("duration").parameterType("int").defaultValue(30).build()
                ))
                .parentTask(brainTask)
                .build());
        addItem(EasyAction.builder()
                .id(11L)
                .actionName("idle")
                .parentTask(brainTask)
                .build());
        addItem(EasyAction.builder()
                .id(20L)
                .actionName("walk")
                .parameters(Lists.newArrayList(
                        EasyActionParameter.builder().parameterName("direction").parameterType("float").defaultValue(180.0).build(),
                        EasyActionParameter.builder().parameterName("speed").parameterType("float").defaultValue(5.0).build()
                ))
                .parentTask(legsTask)
                .build());
        addItem(EasyAction.builder()
                .id(21L)
                .actionName("idle")
                .parentTask(legsTask)
                .build());
        addItem(EasyAction.builder()
                .id(30L)
                .actionName("light_vision")
                .parentTask(eyesTask)
                .build());
        addItem(EasyAction.builder()
                .id(31L)
                .actionName("night_vision")
                .parentTask(eyesTask)
                .build());
        addItem(EasyAction.builder()
                .id(32L)
                .actionName("xray_vision")
                .parentTask(eyesTask)
                .build());
        addItem(EasyAction.builder()
                .id(40L)
                .actionName("grab_object")
                .parameters(Lists.newArrayList(
                        EasyActionParameter.builder().parameterName("object_name").defaultValue("gun").build(),
                        EasyActionParameter.builder().parameterName("hand").defaultValue("right").build()
                ))
                .parentTask(armTask)
                .build());
        addItem(EasyAction.builder()
                .id(41L)
                .actionName("use_object")
                .parameters(Lists.newArrayList(
                        EasyActionParameter.builder().parameterName("aim").parameterType("float").defaultValue(0.0).build(),
                        EasyActionParameter.builder().parameterName("hand").defaultValue("right").build()
                ))
                .parentTask(armTask)
                .build());
        addItem(EasyAction.builder()
                .id(42L)
                .actionName("put_object")
                .parentTask(armTask)
                .build());
    }

    private static void addItem(EasyAction item) {
        ITEMS.add(item);
        ITEM_MAP.put(item.getId(), item);
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
}