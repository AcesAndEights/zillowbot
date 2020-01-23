package helpers;

public class OkHttpClient {

    public static okhttp3.OkHttpClient defaultHttpClient() {
        return new okhttp3.OkHttpClient.Builder()
                .build();
    }
}
