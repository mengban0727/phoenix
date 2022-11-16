## 生产报错

```log
14-11-2022 16:33:38.681 [35m[http-nio-80-exec-1][0;39m [1;31mERROR[0;39m c.i.d.c.e.GlobalExceptionTranslator.log - Internal Server Error errorMessage="Deadlock found when trying to get lock; try restarting transaction"
org.springframework.dao.DeadlockLoserDataAccessException: 
### Error updating database.  Cause: com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException: Deadlock found when trying to get lock; try restarting transaction
### The error may exist in com/iyiou/dp/intelligence/mapper/IndexLogMapper.java (best guess)
### The error may involve com.iyiou.dp.intelligence.mapper.IndexLogMapper.update-Inline
### The error occurred while setting parameters
### SQL: UPDATE index_log  SET status=?,updated_at=?      WHERE (creator_id = ? AND status = ?) ORDER BY updated_at ASC limit 1
### Cause: com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException: Deadlock found when trying to get lock; try restarting transaction
; Deadlock found when trying to get lock; try restarting transaction; nested exception is com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException: Deadlock found when trying to get lock; try restarting transaction
	at org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator.doTranslate(SQLErrorCodeSQLExceptionTranslator.java:271)
	at org.springframework.jdbc.support.AbstractFallbackSQLExceptionTranslator.translate(AbstractFallbackSQLExceptionTranslator.java:70)
	at org.mybatis.spring.MyBatisExceptionTranslator.translateExceptionIfPossible(MyBatisExceptionTranslator.java:91)
	at org.mybatis.spring.SqlSessionTemplate$SqlSessionInterceptor.invoke(SqlSessionTemplate.java:441)
	at com.sun.proxy.$Proxy121.update(Unknown Source)
	at org.mybatis.spring.SqlSessionTemplate.update(SqlSessionTemplate.java:288)
	at com.baomidou.mybatisplus.core.override.MybatisMapperMethod.execute(MybatisMapperMethod.java:65)
	at com.baomidou.mybatisplus.core.override.MybatisMapperProxy.invoke(MybatisMapperProxy.java:96)
	at com.sun.proxy.$Proxy171.update(Unknown Source)
	at com.iyiou.dp.intelligence.service.impl.IndexChartDataServiceImpl.deleteLogsIfMoreThan20(IndexChartDataServiceImpl.java:637)
	at com.iyiou.dp.intelligence.service.impl.IndexChartDataServiceImpl.addChartData(IndexChartDataServiceImpl.java:565)
	at com.iyiou.dp.intelligence.service.impl.IndexChartDataServiceImpl$$FastClassBySpringCGLIB$$c3c53173.invoke(<generated>)
	at org.springframework.cglib.proxy.MethodProxy.invoke(MethodProxy.java:218)
	at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.invokeJoinpoint(CglibAopProxy.java:783)
	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
	at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:753)
	at org.springframework.transaction.interceptor.TransactionInterceptor$1.proceedWithInvocation(TransactionInterceptor.java:123)
	at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:388)
	at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:186)
	at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:753)
	at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:698)
	at com.iyiou.dp.intelligence.service.impl.IndexChartDataServiceImpl$$EnhancerBySpringCGLIB$$582dc63a.addChartData(<generated>)
	at com.iyiou.dp.intelligence.controller.IndexChartDataController.addChartData(IndexChartDataController.java:42)
```



### 业务背景

```java
用户点击左侧指标，mysql中记录日志，会记录点击的图表chart_id和更新时间，status记录日志的状态，用户可以手动删除，重复点击相同的指标会对时间进行更新。
  页面按照时间展示最近20条浏览的指标，且用户删除一条记录后展示的数量会减少，因此表中最多存在20条status=1正常的数据。
```

![image-20221115171147779](images\image-20221115171147779.png)



### 表结构

![image-20221115170202689](images\image-20221115170202689.png)

### 索引

![image-20221115170224826](images\image-20221115170224826.png)



## 程序代码1

```java
//浏览指标记录不存在则新增，记录存在则更新浏览时间 
private void createOrUpdateLog(String chartId, String chartTitle) {
    String userId = AuthContext.getUserId();
    QueryWrapper<IndexLog> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq(IndexDataConstants.CHART_ID, chartId);
    queryWrapper.eq(IndexDataConstants.CREATOR_ID, userId);
    queryWrapper.eq(IndexDataConstants.STATUS, 1);
    IndexLog queryIndexLog = indexLogMapper.selectOne(queryWrapper);
    if (queryIndexLog == null) {// 插入数据库
      IndexLog indexLog = IndexLog.builder()
          .chartId(chartId)
          .chartTitle(chartTitle)
          .createdAt(DateHelper.currTimestamp())
          .updatedAt(DateHelper.currTimestamp())
          .creatorId(Integer.parseInt(userId))
          .status(1)
          .type(1).build();
      int rows = indexLogMapper.insert(indexLog);
      if (rows == 0) {
        throw new ServiceException("指标浏览记录插入失败！");
      }
    } else {// 更新浏览时间
      queryIndexLog.setCreatedAt(DateHelper.currTimestamp());
      queryIndexLog.setUpdatedAt(DateHelper.currTimestamp());
      if (!isSystemChart(chartId)) {
        queryIndexLog.setChartTitle(chartTitle);
      }
      indexLogMapper.updateById(queryIndexLog);
    }
  }
```

### 代码1执行的sql

```java
update index_log set updated_at = #{dateTime} where id = #{id}
```



## 程序代码2

```java
//如果是新增指标浏览记录，只保留20条status=1的日志记录，按照更新时间正序更新status状态

Integer userId = Integer.parseInt(AuthContext.getUserId());
LambdaQueryWrapper<IndexLog> queryWrapper = Wrappers.lambdaQuery();
queryWrapper.eq(IndexLog::getCreatorId, userId);
queryWrapper.eq(IndexLog::getStatus, 1);
queryWrapper.orderByAsc(IndexLog::getUpdatedAt);
Integer currCount = indexLogMapper.selectCount(queryWrapper);
if (currCount > 20) {
    LambdaUpdateWrapper<IndexLog> updateWrapper = Wrappers.lambdaUpdate();
    updateWrapper.eq(IndexLog::getCreatorId, userId);
    updateWrapper.eq(IndexLog::getStatus, 1);
    updateWrapper.orderByAsc(IndexLog::getUpdatedAt);
    updateWrapper.last("limit " + (currCount - 20));
    updateWrapper.set(IndexLog::getStatus, -1);
    updateWrapper.set(IndexLog::getUpdatedAt, DateHelper.currTimestamp());
    indexLogMapper.update(null, updateWrapper);
}
```

### 代码2执行的sql

```sql
UPDATE index_log  SET status=-1,updated_at='2022-11-14 16:33:38.567'
WHERE (creator_id = 205 AND status = 1) ORDER BY updated_at ASC limit 1
```



## 猜测用户行为1

```java
用户连续两次不同的指标，其中一个指标的记录，表中已经存在
```

|      | 第一次点击 事务A                                             | 第二次点击 事务B                                             |
| ---- | ------------------------------------------------------------ | ------------------------------------------------------------ |
|      | begin;                                                       |                                                              |
|      | update index_log set updated_at = '2022-11-14 16:33:38.567' where id = 6281; |                                                              |
|      | 更新成功                                                     |                                                              |
|      |                                                              | begin;                                                       |
|      |                                                              | insert index_log(type,chart_id,chart_title,creator_id,status) values(1,'d1ae1720cbdedd2fa050120294dea587','焦煤:期货收盘价',100,1) |
|      |                                                              | 插入成功                                                     |
|      |                                                              | UPDATE index_log SET status=-1,updated_at='2022-11-14 16:33:38.567'   WHERE (creator_id = 205 AND status = 1) ORDER BY updated_at ASC limit 1 |
|      |                                                              | 阻塞等待                                                     |
|      | UPDATE index_log SET status=-1,updated_at='2022-11-14 16:33:38.567'   WHERE (creator_id = 205 AND status = 1) ORDER BY updated_at ASC limit 1 |                                                              |
|      | commit;                                                      | Deadlock found when trying to get lock; try restarting transaction |



### 线上mysql事务隔离级别

```mysql
select @@tx_isolation;
```



### 第一次分析

### 1.事务A的第一条更新语句持有什么锁？

```sql
update index_log set updated_at = '2022-11-14 16:33:38.567' where id = 6281;

此时对主键id为6281这行记录进行加锁
```



### 2. 事务B第二条更新语句阻塞分析？

```sql
UPDATE index_log  SET status=-1,updated_at='2022-11-14 16:33:38.567'  
WHERE (creator_id = 205 AND status = 1) ORDER BY updated_at ASC limit 1

```

### 3.查看事务B第2条语句执行计划

![image-20221115181819887](images\image-20221115181819887.png)

### 4.加锁分析

```sql
由于order by updated_at没有走索引 idx_creator_id_status_created_at（`creator_id`, `status`, `created_at`），
排序的时候会进行回表，按照对扫描到的行进行加锁的规则，会对idx_creator_id_status_created_at索引上20条记录和主键上的20条记录都进行加锁
又因为事务A持有一条主键id=6281的记录锁，因此阻塞等待

---非聚簇索引的加锁规则先在索引记录加锁，然后去聚簇索引加锁。

```

### 5.事务A第二条更新语句加锁

``` sql
UPDATE index_log  SET status=-1,updated_at='2022-11-14 16:33:38.567'  
 WHERE (creator_id = 205 AND status = 1) ORDER BY updated_at ASC limit 1
同样的会对idx_creator_id_status_created_at索引上20条记录和主键上的20条记录都进行加锁，产生死锁
```



### 查看死锁日志

``` verilog
show engine innodb status

LATEST DETECTED DEADLOCK
------------------------
2022-11-14 16:33:38 7f0be6af4700
*** (1) TRANSACTION:
TRANSACTION 483364220, ACTIVE 0.099 sec fetching rows
mysql tables in use 1, locked 1
LOCK WAIT 6 lock struct(s), heap size 1184, 43 row lock(s), undo log entries 1
LOCK BLOCKING MySQL thread id: 5370232 block 5370347
MySQL thread id 5370347, OS thread handle 0x7f0d875e0700, query id 357648539 47.95.37.252 eodata init
UPDATE index_log  SET status=-1,updated_at='2022-11-14 16:33:38.567'  
 
 WHERE (creator_id = 205 AND status = 1) ORDER BY updated_at ASC limit 1
*** (1) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 9580 page no 60 n bits 848 index `idx_creator_id_status_created_at` of table `eodata`.`index_log` trx id 483364220 lock_mode X locks rec but not gap waiting
Record lock, heap no 775 PHYSICAL RECORD: n_fields 4; compact format; info bits 0
 0: len 4; hex 800000cd; asc     ;;
 1: len 1; hex 81; asc  ;;
 2: len 4; hex 6371fd63; asc cq c;;
 3: len 4; hex 800032b2; asc   2 ;;

*** (2) TRANSACTION:
TRANSACTION 483364222, ACTIVE 0.079 sec starting index read
mysql tables in use 1, locked 1
3 lock struct(s), heap size 1184, 2 row lock(s), undo log entries 1
MySQL thread id 5370232, OS thread handle 0x7f0be6af4700, query id 357648542 47.95.37.252 eodata init
UPDATE index_log  SET status=-1,updated_at='2022-11-14 16:33:38.593'  
 
 WHERE (creator_id = 205 AND status = 1) ORDER BY updated_at ASC limit 1
*** (2) HOLDS THE LOCK(S):
RECORD LOCKS space id 9580 page no 60 n bits 848 index `idx_creator_id_status_created_at` of table `eodata`.`index_log` trx id 483364222 lock_mode X locks rec but not gap
Record lock, heap no 775 PHYSICAL RECORD: n_fields 4; compact format; info bits 0
 0: len 4; hex 800000cd; asc     ;;
 1: len 1; hex 81; asc  ;;
 2: len 4; hex 6371fd63; asc cq c;;
 3: len 4; hex 800032b2; asc   2 ;;

*** (2) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 9580 page no 60 n bits 848 index `idx_creator_id_status_created_at` of table `eodata`.`index_log` trx id 483364222 lock_mode X locks rec but not gap waiting
Record lock, heap no 150 PHYSICAL RECORD: n_fields 4; compact format; info bits 0
 0: len 4; hex 800000cd; asc     ;;
 1: len 1; hex 81; asc  ;;
 2: len 4; hex 636b2be6; asc ck+ ;;
 3: len 4; hex 80003236; asc   26;;

*** WE ROLL BACK TRANSACTION (2)
------------
```



## 猜测用户行为2

```java
用户连续多次不同的指标，有表中已存在的记录，也有不存在的记录
```

|      | 第一次点击 事务A                                             | 第二次点击 事务B                                             | 第三次点击事务C                                              |
| ---- | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
|      | begin;                                                       |                                                              |                                                              |
|      | insert index_log(type,chart_id,chart_title,creator_id,status) values(1,'d1ae1720cbdedd2fa050120294dea587','焦煤:期货收盘价',100,1) |                                                              |                                                              |
|      | 插入成功                                                     |                                                              |                                                              |
|      | UPDATE index_log SET status=-1,updated_at='2022-11-14 16:33:38.567'   WHERE (creator_id = 205 AND status = 1) ORDER BY updated_at ASC limit 1 |                                                              |                                                              |
|      |                                                              | begin;                                                       |                                                              |
|      |                                                              | update index_log set updated_at = '2022-11-14 16:33:38.567' where id = 6280; |                                                              |
|      |                                                              | 阻塞等待；                                                   |                                                              |
|      |                                                              |                                                              | begin;                                                       |
|      |                                                              |                                                              | update index_log set updated_at = '2022-11-14 16:33:38.567' where id = 6281; |
|      |                                                              |                                                              | 阻塞等待；                                                   |
|      | commit;                                                      | 更新成功                                                     | 更新成功                                                     |
|      |                                                              | UPDATE index_log SET status=-1,updated_at='2022-11-14 16:33:38.567'   WHERE (creator_id = 205 AND status = 1) ORDER BY updated_at ASC limit 1 |                                                              |
|      |                                                              | 阻塞等待                                                     |                                                              |
|      |                                                              |                                                              | UPDATE index_log SET status=-1,updated_at='2022-11-14 16:33:38.567'   WHERE (creator_id = 205 AND status = 1) ORDER BY updated_at ASC limit 1 |
|      |                                                              | Deadlock found when trying to get lock; try restarting transaction | commit;                                                      |



### 再分析

```sql
1.因为rds的默认隔离级别是rc读已提交，所以事务B和事务C，代码if (currCount > 20) 查询是成立的。

2.分析死锁时，可以只看事务B和事务C，关键点在于事务B的第二条更新语句为什么会被事务C的第一条更新语句阻塞？

```

### 查看事务B第二条语句等待的锁

```sql
lock_index:idx_creator_id_status_created_at
lock_data:23,1,23, 1, 0x6344CBE7, 6281   其中0x6344CBE7换算成时间2022-11-14 16:33:38.567
```



![image-20221115194316146](images\image-20221115194316146.png)



### 解决方法

```sql
由于排序filesort,会扫描索引上20条记录，同时回表会对主键上的记录进行加锁，因此更改索引为idx_creator_id_status_created_at（`creator_id`, `status`, `updated_at`），此时ORDER BY updated_at ASC limit 1 就只对索引记录的第一条和主键上的记录进行加锁
```



### 更改索引后还是会有死锁问题

```mysql
百思不得其解,按照道理来说这条语句UPDATE index_log SET status=-1,updated_at='2022-11-14 16:33:38.567'  WHERE (creator_id = 205 AND status = 1) ORDER BY updated_at ASC limit 1，trx_rows_locked锁住的记录数就是索引记录和主键上的记录，但还是会阻塞等待。

又是困扰中............
```

### 再看看执行计划

![image-20221115215443258](images\image-20221115215443258.png)

![image-20221115215643482](images\image-20221115215643482.png)

### mysql 5.6版本问题

```
从执行计划可以看到，查询语句能够利用索引进行排序，但是更新语句还是有filesort,因此推测还是会扫描20条记录回表排序，加锁的范围没有变小。
```



### 解决办法

```sql
1. mysql换成更高版本5.7
2. 改写语句，由于更改索引后，默认是按照updated_at升序，不需要再指定order by updated_at asc
UPDATE index_log SET status=-1,updated_at='2022-11-14 16:33:38.567'  WHERE (creator_id = 205 AND status = 1) limit 1
```



### 实验附加相关sql

```mysql
SESSION1:
begin;
update index_log set updated_at = '2022-11-14 16:33:38.527' where id = 6280;
UPDATE index_log  SET status=-1,updated_at='2022-11-14 16:33:38.567'  
 WHERE (creator_id = 23 AND status = 1 )  ORDER BY updated_at ASC limit 1 
ROLLBACK;
-- select version()
-- select * from index_log where (creator_id = 23 AND status = 1 ) ORDER BY updated_at ASC  limit 1 for update

SESSION2:
begin;
update index_log set updated_at = '2022-11-14 16:33:38.567' where id = 6281;
UPDATE index_log  SET status=-1,updated_at='2022-11-14 16:33:38.593'  
WHERE (creator_id = 23 AND status = 1)  ORDER BY updated_at ASC limit 1
ROLLBACK;
-- select updated_at from index_log where creator_id = 23 AND status = 1 AND updated_at = '2022-10-11 09:50:31' for update

SESSION3:
select*from information_schema.innodb_locks;

SESSION4:
show engine innodb status

SESSION5:
select*from information_schema.innodb_trx;
```

