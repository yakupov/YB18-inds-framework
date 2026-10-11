package org.itmo.inds.testdata;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Gson type adapter for {@link LocalDateTime} in the form Gson's reflection wrote it into the existing data sets
 * ({@code RunConfiguration.timestamp}): {@code {"date":{"year":..,"month":..,"day":..},"time":{"hour":..,"minute":..,
 * "second":..,"nano":..}}}. Needed because Gson cannot reflect into {@code java.time} classes on JDK 17. Unknown
 * fields are skipped; missing time fields read as 0.
 */
public final class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
    @Override
    public void write(JsonWriter out, LocalDateTime value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        out.name("date").beginObject()
                .name("year").value(value.getYear())
                .name("month").value(value.getMonthValue())
                .name("day").value(value.getDayOfMonth())
                .endObject();
        out.name("time").beginObject()
                .name("hour").value(value.getHour())
                .name("minute").value(value.getMinute())
                .name("second").value(value.getSecond())
                .name("nano").value(value.getNano())
                .endObject();
        out.endObject();
    }

    @Override
    public LocalDateTime read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        int year = 0, month = 0, day = 0, hour = 0, minute = 0, second = 0, nano = 0;
        in.beginObject();
        while (in.hasNext()) {
            final String part = in.nextName();
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                continue;
            }
            if (!part.equals("date") && !part.equals("time")) {
                in.skipValue();
                continue;
            }
            in.beginObject();
            while (in.hasNext()) {
                final String field = in.nextName();
                switch (part + "." + field) {
                    case "date.year" -> year = in.nextInt();
                    case "date.month" -> month = in.nextInt();
                    case "date.day" -> day = in.nextInt();
                    case "time.hour" -> hour = in.nextInt();
                    case "time.minute" -> minute = in.nextInt();
                    case "time.second" -> second = in.nextInt();
                    case "time.nano" -> nano = in.nextInt();
                    default -> in.skipValue();
                }
            }
            in.endObject();
        }
        in.endObject();
        return LocalDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute, second, nano));
    }
}
