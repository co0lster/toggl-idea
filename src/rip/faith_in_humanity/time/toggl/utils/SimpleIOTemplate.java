package rip.faith_in_humanity.time.toggl.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.time.ZonedDateTime;
import java.util.Base64;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

public class SimpleIOTemplate implements Closeable {

    private static final Gson gson;

    static {
        gson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, new TypeAdapter<ZonedDateTime>() {
                    @Override
                    public void write(JsonWriter jsonWriter, ZonedDateTime zonedDateTime) throws IOException {
                        if (zonedDateTime != null) {
                            jsonWriter.value(zonedDateTime.format(ISO_OFFSET_DATE_TIME));
                        }
                    }

                    @Override
                    public ZonedDateTime read(JsonReader jsonReader) throws IOException {
                        return ZonedDateTime.parse(jsonReader.nextString(), ISO_OFFSET_DATE_TIME);
                    }
                }).create();


    }

    private URL url = null;

    private HttpURLConnection connection = null;

    private RequestMethod requestMethod = RequestMethod.GET;

    public enum RequestMethod {
        GET,
        POST
    }

    public SimpleIOTemplate(String url) throws MalformedURLException {
        this(url, RequestMethod.GET);
    }

    public SimpleIOTemplate(String url, RequestMethod requestMethod) throws MalformedURLException {
        this.url = new URL(url);
        this.requestMethod = requestMethod;
    }

    private void openIfNecessary() {
        try {
            if (connection == null) {
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(requestMethod.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public SimpleIOTemplate addAuth(String user, String pass) {
        return addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes()));
    }

    public SimpleIOTemplate addHeader(String key, String value) {
        openIfNecessary();
        connection.setRequestProperty(key, value);
        return this;
    }

    public <T> Response<T> send(Class<T> response) {
        connection.setDoInput(true);
        connection.setDoOutput(true);
        try {
            if (connection.getResponseCode() > 299) {
                System.err.println(IOUtils.toString(connection.getErrorStream()));
                return new Response<>(connection.getResponseCode(), null);
            } else {
                try (InputStream stream = connection.getInputStream()) {
                    return new Response<>(connection.getResponseCode(), gson.fromJson(IOUtils.toString(stream), response));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <T, X> Response<T> send(X sendObj, Class<T> response) {
        connection.setDoInput(true);
        connection.setDoOutput(true);
        addHeader("Content-Type", "application/json");
        try {
            connection.getOutputStream().write(gson.toJson(sendObj, sendObj.getClass()).getBytes());
            if (connection.getResponseCode() > 299) {
                System.err.println(IOUtils.toString(connection.getErrorStream()));
                return new Response<>(connection.getResponseCode(), null);
            }
            try (InputStream stream = connection.getInputStream()) {
                return new Response<>(connection.getResponseCode(), gson.fromJson(IOUtils.toString(stream), response));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JSONObject fire(JSONObject object) {
        connection.setDoInput(true);
        connection.setDoOutput(true);
        addHeader("Content-Type", "application/json");
        try {
            connection.getOutputStream().write(object.toString().getBytes());
            if (connection.getResponseCode() > 299) {
                System.err.println(IOUtils.toString(connection.getErrorStream()));
            }
            try (InputStream stream = connection.getInputStream()) {
                return new JSONObject(IOUtils.toString(stream));
            } catch (IOException | JSONException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JSONObject fire() {
        connection.setDoInput(true);
        connection.setDoOutput(true);
        try (InputStream stream = connection.getInputStream()) {
            return new JSONObject(IOUtils.toString(stream));
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        IOUtils.silentClose(connection);
    }
}
