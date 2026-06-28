package com.mara.jordan.client;

import com.mara.jordan.core.JordanConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ActionBuilder {

    private final Map<String, Map<String, Object[]>> actions = new LinkedHashMap<>();
    private String currentActionName;

    public static ActionBuilder withAction(String actionName) {
        return new ActionBuilder().addAction(actionName);
    }

    public ActionBuilder addAction(String actionName) {
        actions.put(actionName, new LinkedHashMap<>());
        currentActionName = actionName;
        return this;
    }

    public ActionBuilder withParameter(String paramName, String paramType) {
        return withParameter(paramName, paramType, null);
    }

    public ActionBuilder withParameter(String paramName, String paramType, Object defaultValue) {
        List<String> validTypes = Arrays.asList(
                JordanConstants.PARAMETER_TYPE_STRING,
                JordanConstants.PARAMETER_TYPE_INT,
                JordanConstants.PARAMETER_TYPE_FLOAT
        );
        if (!validTypes.contains(paramType)) {
            throw new IllegalArgumentException(
                    "Parameter " + paramName + " type '" + paramType + "' must be one of " + validTypes);
        }
        actions.get(currentActionName).put(paramName, new Object[]{paramType, defaultValue});
        return this;
    }

    public List<Map<String, Object>> build() {
        currentActionName = null;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object[]>> entry : actions.entrySet()) {
            Map<String, Object> actionDef = new LinkedHashMap<>();
            actionDef.put("actionName", entry.getKey());
            if (!entry.getValue().isEmpty()) {
                List<Map<String, Object>> paramList = new ArrayList<>();
                for (Map.Entry<String, Object[]> param : entry.getValue().entrySet()) {
                    Map<String, Object> paramDef = new LinkedHashMap<>();
                    paramDef.put("name", param.getKey());
                    paramDef.put("type", param.getValue()[0]);
                    if (param.getValue()[1] != null) {
                        paramDef.put("defaultValue", param.getValue()[1]);
                    }
                    paramList.add(paramDef);
                }
                actionDef.put("parameters", paramList);
            }
            result.add(actionDef);
        }
        return result;
    }
}
