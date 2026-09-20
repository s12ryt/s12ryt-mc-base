package dev.s12ryt.mcapp.api;

/**
 * App 簡化 Web 路由介面。
 *
 * <p>抽象 HTTP 請求/回應，不暴露任何 Javalin/Servlet 類型，App 無需依賴 Javalin。
 */
public interface AppRouter {

    /** 掛載 GET 路由。pattern 以 / 開頭，實際路徑為 /apps/{appId}{pattern}。 */
    void get(String pattern, AppHandler handler);

    /** 掛載 POST 路由。 */
    void post(String pattern, AppHandler handler);

    /** 掛載 PUT 路由。 */
    void put(String pattern, AppHandler handler);

    /** 掛載 DELETE 路由。 */
    void delete(String pattern, AppHandler handler);

    /** 路由處理器。 */
    @FunctionalInterface
    interface AppHandler {
        AppHttpResponse handle(AppHttpRequest request) throws Exception;
    }
}
