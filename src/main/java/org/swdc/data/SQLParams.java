package org.swdc.data;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SQLParams {

    private Map<String,Object> params;

    public SQLParams(Map<String,Object> params) {
        this.params = params;
    }

    public <T> T get(String key) {
        return (T) params.get(key);
    }

    public boolean containsKey(String key) {
        boolean contains = params.containsKey(key);
        if (contains) {
            Object val = params.get(key);
            if (val == null) {
                return false;
            }
            if (val instanceof String) {
                return !((String) val).isEmpty();
            }
            return true;
        }
        return false;
    }

    public List<String> getKeys() {
        return params.keySet().stream().filter(this::containsKey)
                .collect(Collectors.toList());
    }

}
