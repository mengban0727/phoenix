## 应用场景1

每个线程独立保存信息，以便其他方法方便获取信息。实际应用时，我们在网关服务中，从请求的Header中拿到token，去redis查询出用户的id，将用户id放入ServerHttpRequest的Header中，经过DispatcherServlet的processRequest方法时，会将requestAttributes设置到RequestContextHolder的ThreadLocal中。因此，我们在其他方法中能够通过RequestContextHolder获取到登录用户的id

### RequestContextHolder

```java
private static final ThreadLocal<RequestAttributes> requestAttributesHolder =
    new NamedThreadLocal<>("Request attributes");

private static final ThreadLocal<RequestAttributes> inheritableRequestAttributesHolder =
    new NamedInheritableThreadLocal<>("Request context");
```



### 通过token拿到用户id

```java
private Session getSession(ServerHttpRequest request) {
    String token = Sessions.getToken(request, false);
    if (token == null) {
        return null;
    }
    try {
        var decodedJWT = Sign.verifySessionToken(token, signingSecret);
        Map<String, Claim> claims = decodedJWT.getClaims();
        var expDate = claims.get("exp").asDate();
        if (expDate.before(new Date())) {
            log.info("token过期时间早于当前时间，判定token失效，需用户重新登录！");
            throw new ServiceException(ResultCode.UN_AUTHORIZED, "请登录");
        }
        var userId = claims.get(Sign.CLAIM_USER_ID).asString();
        String redisToken = stringRedisTemplate.opsForValue().get(CacheConstants.getTokenKey(userId));
        if (redisToken != null && !redisToken.equals(token)) {
            userId = "";
        }
        return Session.builder().userId(userId).build();
    } catch (Exception e) {
        log.error("fail to verify token", "token", token, e);
        return null;
    }
}
```



### 保存用户id到ServerHttpRequest的header

```java
private ServerHttpRequest setAuthHeader(ServerHttpRequest data) {

    String authorization = AuthConstant.AUTHORIZATION_ANONYMOUS_WEB;
    String userId = "";
    Session session = this.getSession(data);
    if (session != null) {
        authorization = AuthConstant.AUTHORIZATION_AUTHENTICATED_USER;
        userId = session.getUserId();
    }

    return data.mutate()
        .header(AuthConstant.AUTHORIZATION_HEADER, authorization)
        .header(AuthConstant.CURRENT_USER_HEADER, userI).build();
}
```



### FrameworkServlet#processRequest

```java
protected final void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

    long startTime = System.currentTimeMillis();
    Throwable failureCause = null;

    LocaleContext previousLocaleContext = LocaleContextHolder.getLocaleContext();
    LocaleContext localeContext = buildLocaleContext(request);

    RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
    ServletRequestAttributes requestAttributes = buildRequestAttributes(request, response, previousAttributes);

    WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
    asyncManager.registerCallableInterceptor(FrameworkServlet.class.getName(), new RequestBindingInterceptor());

    //此处保存request信息
    initContextHolders(request, localeContext, requestAttributes);

    try {
        doService(request, response);
    }
    catch (ServletException | IOException ex) {
        failureCause = ex;
        throw ex;
    }
    catch (Throwable ex) {
        failureCause = ex;
        throw new NestedServletException("Request processing failed", ex);
    }

    finally {
        resetContextHolders(request, previousLocaleContext, previousAttributes);
        if (requestAttributes != null) {
            requestAttributes.requestCompleted();
        }
        logResult(request, response, failureCause, asyncManager);
        publishRequestHandledEvent(request, response, startTime, failureCause);
    }
}
```

### initContextHolders方法保存requestAttributes

```java
private void initContextHolders(HttpServletRequest request,
                                @Nullable LocaleContext localeContext, @Nullable RequestAttributes requestAttributes) {

    if (localeContext != null) {
        LocaleContextHolder.setLocaleContext(localeContext, this.threadContextInheritable);
    }
    if (requestAttributes != null) {
        RequestContextHolder.setRequestAttributes(requestAttributes, this.threadContextInheritable);
    }
}
```

### 通过RequestContextHolder获取用户id

```java
private static String getRequestHeader(String headerName) {
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes instanceof ServletRequestAttributes) {
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        return request.getHeader(headerName);
    }
    return null;
}

public static String getUserId() {
    return getRequestHeader(AuthConstant.CURRENT_USER_HEADER);
}
```

## 应用场景2

保存每个线程独享的对象，为每个线程创建一个副本，确保线程安全。

项目中使用日期格式化SimpleDateFormat时，避免每次请求都new创建一个SimpleDateFormat对象，浪费内存，同时要保证多个线程使用SimpleDateFormat的线程安全，有两种方案，一是synchronized加锁，二是使用ThreadLocal，为线程池中每个线程创建一个SimpleDateFormat对象。

### 常见的写法

```java
class DateFormatHolder {
    public static ThreadLocal<SimpleDateFormat> threadLocal = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("mm:ss");
        }
    };
}
```



### 多种日期格式的DateFormatHolder

```java
static final class DateFormatHolder {
    private static final ThreadLocal<SoftReference<Map<String, SimpleDateFormat>>>
        THREADLOCAL_FORMATS = new ThreadLocal<>();

    DateFormatHolder() {
    }

    public static SimpleDateFormat formatFor(String pattern) {
        SoftReference<Map<String, SimpleDateFormat>> ref = THREADLOCAL_FORMATS.get();
        Map<String, SimpleDateFormat> formats = ref == null ? null : ref.get();
        if (formats == null) {
            formats = new HashMap<>();
            THREADLOCAL_FORMATS.set(new SoftReference<>(formats));
        }
        return formats
            .computeIfAbsent(pattern, key -> new SimpleDateFormat(pattern));
    }

    public static void clearThreadLocal() {
        THREADLOCAL_FORMATS.remove();
    }
}
```





