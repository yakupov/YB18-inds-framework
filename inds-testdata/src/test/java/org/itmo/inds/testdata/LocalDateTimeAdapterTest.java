package org.itmo.inds.testdata;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalDateTimeAdapterTest {
    private final Gson gson = DataSets.gson();

    /**
     * The form Gson's reflection writes for LocalDateTime (as in the stored data sets).
     */
    @Test
    void readsReflectiveForm() {
        final String json = "{\"date\":{\"year\":2017,\"month\":4,\"day\":18},"
                + "\"time\":{\"hour\":0,\"minute\":31,\"second\":5,\"nano\":123000000}}";
        assertEquals(LocalDateTime.of(2017, 4, 18, 0, 31, 5, 123_000_000), gson.fromJson(json, LocalDateTime.class));
    }

    @Test
    void roundTrip() {
        final LocalDateTime t = LocalDateTime.of(2016, 12, 14, 23, 59, 59, 999_999_999);
        final String json = gson.toJson(t);
        assertEquals("{\"date\":{\"year\":2016,\"month\":12,\"day\":14},"
                + "\"time\":{\"hour\":23,\"minute\":59,\"second\":59,\"nano\":999999999}}", json);
        assertEquals(t, gson.fromJson(json, LocalDateTime.class));
    }

    @Test
    void nullAndUnknownFields() {
        assertNull(gson.fromJson("null", LocalDateTime.class));
        final String json = "{\"zone\":\"x\",\"date\":{\"year\":2018,\"month\":1,\"day\":29,\"era\":1},\"time\":{\"hour\":21}}";
        assertEquals(LocalDateTime.of(2018, 1, 29, 21, 0), gson.fromJson(json, LocalDateTime.class));
    }
}
