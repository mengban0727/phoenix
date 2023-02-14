## 背景

通过redis的incr命令做单位时间内的计数限制，为了保证incr和expire命令的原子性，采用lua脚本的方式

## Lua脚本

```java
String INCR_LUA =
    "local current = redis.call('incrBy',KEYS[1],ARGV[1]);"
    + " if current == tonumber(ARGV[1]) then"
    + " redis.call('expire',KEYS[1],ARGV[2])"
    + " end;"
    + " return current;";
```



## RedisTemplate调用

```java
Long result =(Long) redisTemplate.execute(new DefaultRedisScript<>(INCR_LUA, Long.class),Collections.singletonList("sms_send_code_check_8618856020000"), 1,"60");
```



## 异常

```java
Caused by: redis.clients.jedis.exceptions.JedisDataException: ERR Error running script (call to f_c611ed7487fbf6826eb6dba723cbb3ee6679598e): @user_script:1: ERR value is not an integer or out of range 
	at redis.clients.jedis.Protocol.processError(Protocol.java:132)
	at redis.clients.jedis.Protocol.process(Protocol.java:166)
	at redis.clients.jedis.Protocol.read(Protocol.java:220)
	at redis.clients.jedis.Connection.readProtocolWithCheckingBroken(Connection.java:278)
	at redis.clients.jedis.Connection.getOne(Connection.java:256)
	at redis.clients.jedis.BinaryJedis.evalsha(BinaryJedis.java:3411)
	at org.springframework.data.redis.connection.jedis.JedisScriptingCommands.evalSha(JedisScriptingCommands.java:160)
	... 85 more
```



## redis中key存在，过期时间为-1

![](../images/redis_20230214193931.png)



## Lua脚本的原子性并不是ACID中的A

expire命令执行失败，并不会回滚前面执行过的incr命令。

lua脚本将要执行的命令封装成一个事务，保证脚本作为一个整体执行，不被其他命令打断，但是如果事务执行过程中，产生了错误，并不会回滚已经执行过的命令，lua脚本的原子性保证的是不可被拆分的原子性，而不是事务中ACID中要么都执行要么都不执行的原子性。



## 问题原因

value的序列化方式配置的是Jackson2JsonRedisSerializer，所以执行expire命令时，value不能被转换成整数



## 解决方法

* value设置为整数

  ```java
  Long result =(Long) redisTemplate.execute(new DefaultRedisScript<>(INCR_LUA, Long.class),Collections.singletonList("sms_send_code_check_8618856020000"), 1,60);
  ```

  

* 指定脚本的参数序列化方式为StringRedisSerializer

  ```java
  Long result =(Long) redisTemplate.execute(new DefaultRedisScript<>(INCR_LUA, Long.class),new StringRedisSerializer(),
              new StringRedisSerializer(),Collections.singletonList("sms_send_code_check_8618856020000"), "1","60");
  ```

  

