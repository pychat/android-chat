/*
 * Copyright (c) 2020 WildFireChat. All rights reserved.
 */

package cn.wildfire.chat.kit.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import cn.wildfire.chat.kit.net.base.ResultWrapper;
import cn.wildfire.chat.kit.net.base.StatusResult;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;


/**
 * Created by imndx on 2017/12/15.
 */

public class OKHttpHelper {
    private static final String TAG = "OKHttpHelper";
    private static final String WFC_OKHTTP_COOKIE_CONFIG = "WFC_OK_HTTP_COOKIES";
    private static final String AUTHORIZATION_HEADER = "authToken";

    private static WeakReference<Context> AppContext;

    private static OkHttpClient okHttpClient;

    public static void init(Context context) {
        AppContext = new WeakReference<>(context);
        SharedPreferences sp = context.getSharedPreferences(WFC_OKHTTP_COOKIE_CONFIG, Context.MODE_PRIVATE);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS);

        builder.addInterceptor(chain -> {
            Request request = chain.request();
            String host = request.url().host();
            int port = request.url().port();
            String authToken = sp.getString(AUTHORIZATION_HEADER + ":" + host + "-" + port, null);
            // 兼容保存 authToken 时，未考虑端口
            if (TextUtils.isEmpty(authToken)) {
                authToken = sp.getString(AUTHORIZATION_HEADER + ":" + host, null);
            }
            if (!TextUtils.isEmpty(authToken)) {
                request = request.newBuilder()
                    .addHeader(AUTHORIZATION_HEADER, authToken)
                    .build();
            }
            Response response = chain.proceed(request);
            String responseAuthToken = response.header(AUTHORIZATION_HEADER, null);
            if (!TextUtils.isEmpty(responseAuthToken)) {
                sp.edit().putString(AUTHORIZATION_HEADER + ":" + host + "-" + port, responseAuthToken).apply();
            }
            return response;
        });
        okHttpClient = builder.build();
    }

    private static Gson gson = new Gson();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static <T> void get(final String url, Map<String, String> params, final Callback<T> callback) {
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (params != null) {
            HttpUrl.Builder builder = httpUrl.newBuilder();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                builder.addQueryParameter(entry.getKey(), entry.getValue());
            }
            httpUrl = builder.build();
        }

        final Request request = new Request.Builder()
            .url(httpUrl)
            .get()
            .build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) {
                    callback.onFailure(-1, e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.e(TAG, "response=" + response.toString());
                handleResponse(url, call, response, callback);
            }
        });

    }

    public static <T> void post(final String url, Object param, final Callback<T> callback) {
        //RequestBody body = RequestBody.create(JSON, gson.toJson(param));
        RequestBody body = RequestBody.create(JSON, param == null ? "" : gson.toJson(param));
        final Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) {
                    callback.onFailure(-1, e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                handleResponse(url, call, response, callback);

            }
        });
    }

    public static void sse(final String url, Object param, EventSourceListener listener) {
        RequestBody body = RequestBody.create(JSON, param == null ? "" : gson.toJson(param));
        final Request request = new Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "*/*")
            .build();

        EventSources.createFactory(okHttpClient)
            .newEventSource(request, listener);
    }

    public static <T> void put(final String url, Map<String, String> param, final Callback<T> callback) {
        RequestBody body = RequestBody.create(JSON, gson.toJson(param));
        final Request request = new Request.Builder()
            .url(url)
            .put(body)
            .build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) {
                    callback.onFailure(-1, e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                handleResponse(url, call, response, callback);

            }
        });
    }

    public static <T> void upload(String url, Map<String, String> params, File file, MediaType mediaType, final Callback<T> callback) {
        MultipartBody.Builder builder = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.getName(),
                RequestBody.create(mediaType, file));

        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                builder.addFormDataPart(entry.getKey(), entry.getValue());
            }
        }

        RequestBody requestBody = builder.build();

        Request request = new Request.Builder()
            .url(url)
            .post(requestBody)
            .build();
        okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) {
                    callback.onFailure(-1, e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                handleResponse(url, call, response, callback);
            }
        });
    }

    public static void clearCookies() {
        SharedPreferences sp = AppContext.get().getSharedPreferences(WFC_OKHTTP_COOKIE_CONFIG, 0);
        sp.edit().clear().apply();
    }

    private static <T> void handleResponse(String url, Call call, okhttp3.Response response, Callback<T> callback) {
        if (callback != null) {
            if (!response.isSuccessful()) {
                callback.onFailure(response.code(), response.message());
                response.close();
                return;
            }

            Type type;
            if (callback instanceof SimpleCallback) {
                Type types = callback.getClass().getGenericSuperclass();
                type = ((ParameterizedType) types).getActualTypeArguments()[0];
            } else {
                Type[] types = callback.getClass().getGenericInterfaces();
                type = ((ParameterizedType) types[0]).getActualTypeArguments()[0];
            }

            if (type.equals(Void.class)) {
                response.close();
                callback.onSuccess((T) null);
                return;
            }

            if (type.equals(String.class)) {
                try {
                    callback.onSuccess((T) response.body().string());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }


            try {
                StatusResult statusResult;
                if (type instanceof Class && type.equals(StatusResult.class)) {
                    statusResult = gson.fromJson(response.body().string(), StatusResult.class);
                    if (statusResult.isSuccess()) {
                        callback.onSuccess((T) statusResult);
                    } else {
                        callback.onFailure(statusResult.getCode(), statusResult.getMessage());
                    }
                } else {
                    ResultWrapper<T> wrapper = gson.fromJson(response.body().string(), new ResultType(type));
                    if (wrapper == null) {
                        callback.onFailure(-1, "response is null");
                        return;
                    }
                    if (wrapper.isSuccess() && wrapper.getResult() != null) {
                        callback.onSuccess(wrapper.getResult());
                    } else {
                        callback.onFailure(wrapper.getCode(), wrapper.getMessage());
                    }
                }
            } catch (JsonSyntaxException e) {
                e.printStackTrace();
                callback.onFailure(-1, e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                callback.onFailure(-1, e.getMessage());
            }
        }
    }

    private static class ResultType implements ParameterizedType {
        private final Type type;

        public ResultType(Type type) {
            this.type = type;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{type};
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public Type getRawType() {
            return ResultWrapper.class;
        }
    }
}
