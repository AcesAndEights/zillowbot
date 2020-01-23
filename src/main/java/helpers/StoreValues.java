package helpers;

import java.util.HashMap;
import java.util.Map;

public class StoreValues {

    public static void storeValues(String key, String value) {
        Map<String, String> hashmap = new HashMap<>();

        hashmap.put(key, value);

        hashmap.entrySet().forEach(entry->{
            System.out.println(entry.getKey() + " " + entry.getValue());
        });
    }
}
