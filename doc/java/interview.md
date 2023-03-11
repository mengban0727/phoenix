## 马上消费金融（线下两轮）

1. 对项目的认知，有什么难点，遇到什么问题，有什么收获
2. redis 怎么用的，会产生什么问题（主要是数据一致性，缓存三大问题），持久化机制（有没有遇到过实际问题），主从之间的通讯协议
3. kafka 业务怎么保证消息一致性，采取了哪些措施，会不会丢数据
4. springboot 比 spring 方便在哪，有什么缺点
5. jvm 内存结构，什么情况会导致溢出问题，线上遇到什么问题
6. mysql 索引，锁（问到了间隙锁，实际业务场景有没有遇到），MDL 锁的详细流程，大表怎么加字段
7. nacos 服务发现与注册的原理（顺便问到了zookeeper）
8. 设计模式怎么应用的
9. 线程池怎么用的
10. 场景一：mysql cpu 满载怎么排查
11. 场景二：多个实例平时查询数据库只要 500ms 但是某时刻某些实例返回结果超时，怎么排查（引导从网络，服务多层面考虑）
12. 场景三：微服务中某个服务版本迭代上线后，其余服务怎么感知
13. 场景四：一个长度有限的 int 数组，多个线程写入，主线程求和，设计思路



## 元保(0307-1视频面)

* 手写快排（共享屏幕）

  ```java
  import java.util.Arrays;
  public class QuickSortTest {
    public static void main(String[] args) {
      int[] nums = new int[]{3,1,4,53,2};
      quickSort(nums,0,nums.length-1);
      System.out.println(Arrays.toString(nums));
    }
    public static void quickSort(int[] nums,int start,int end){
      if(start>=end){
        return;
      }
      int pivot = quickSort2(nums, start, end);
      quickSort(nums,start,pivot-1);
      quickSort(nums,pivot+1,end);
    }
  
    public static int quickSort2(int[] nums,int start,int end){
      int pivot = end;
  
      int left = start;
      int right = start;
      while(right<end){
        if(nums[right]>=nums[pivot]){
          right++;
        }else{
          swap(nums,left,right);
          left++;
        }
      }
      swap(nums,left,pivot);
      return left;
    }
  
    private  static void swap(int[] nums, int left, int right) {
      int tmp = nums[left];
      nums[left] = nums[right];
      nums[right] = tmp;
    }
  
  }
  ```

* 平衡二叉树的定义及应用

* 进程和线程

  ```java
  分配资源基本单位，cpu调度基本单位，一个进程可以有多个线程，在java中多个线程共享堆和方法区，每个线程有自己的程序计数器，虚拟机栈和本地方法栈，线程开销小
  ```

* 线程之间的通信

  ```java
  （互斥量Mutex，比如java中的锁Synchronized）,事件通知wait和notify，信号量Semaphore
  
  ```

* 线程池（几种实现）

* 垃圾回收算法

* 设计模式（策略项目中怎么用的）和重构代码

* 线上的qps，gc调优

* 微服务治理

  ```java
  按照什么规则去拆分一个服务，服务注册发现，熔断，限流，链路追踪，配置中心
  ```

* 熔断和降级

* redis的缓存穿透击穿雪崩

* es查询优化

  ```java
  排查问题角度，打开慢日志，利用profile查看query和fetch，
  开发维度，filter,避免使用script，wildcard,合理使用keyword，控制字段返回
  建模角度,设置合理的分片数量，分片数量过多
  集群角度，设置不同角色的角色，主节点，协调节点，数据节点等
  ```

* mongodb的数据存储



## 元保（0310-2线下面）

主要问了下项目情况

* c10k问题
* redis的大key
* mysql索引优化
* git rebase
* 给定a、b两个文件，各存放100亿个url，每个url各占100B，内存限制是1G，让你找出a、b文件共同的url



## 58-1面（已通知二面）

1. 线程池的执行流程，核心线程数 > 队列最大容量，能达到核心线程数的并发度吗？（50个核心线程队列20大小并发度能否达到20）

2. jvm 常见的命令（打印 gc 命令），gc日志，gc耗时长怎么排查

3. 对象创建过程以及生命周期

   ```java
   类加载，内存分配-空闲列表和指针碰撞，内存分配并发问题，cas重试，tlab，初始化零值-对象实例化后不设置初始值也能使用，设置对象头-类型指针，gc分代年龄，hashcode，锁状态标识，init方法，垃圾回收-三色标记
   ```

4. mysql 聚簇和非聚簇（问到2次）非聚簇索引存储的数据块的地址

5. mysql中的锁到java中锁，意向锁，redolog与undolog，synchronized 锁的原理

6. mysql 默认select * 按什么排序，查询语句的执行流程

7. mysql主从同步是push还是pull



## 360-1面

体验不是很好，多对一，且对方不开摄像头

1. kafka 与其他 mq 的对比，如何保证数据一致性和顺序性，多个消费者怎么保证消息消费的顺序性

2. kafka有什么缺点

   ```java
   不支持消息重试，短轮询方式实时性比较低，支持的队列数量比较低，kafka不支持定时消息，kafka不支持事务消息
   ```

3. springboot 有哪些核心注解，启动流程（推测是想问自动装配）

4. redis 有哪些数据结构，应用场景

5. es使用情况



## 广州小迈-1面

ConcurrentHashMap实现有了解吗，讲一讲
AQS了解吗
底层是怎么实现的
IO模型有几种，说一说
JVM内存区域划分
垃圾判定算法有几种
GC Roots包含了哪些对象
类加载器了解吗
类加载过程
双亲委派机制了解吗，说一下
怎么打破双亲委派
JVM锁升级过程
可逆吗？都不可逆？
mysql的事务隔离级别有哪些
MVCC机制有了解吗（说了下定义）
mysql还有什么日志文件
具体怎么实现的
说一下索引
abc复合索引，a=1 and b>2 and c = 2,走索引吗，为什么
什么是覆盖索引
ES更新一条数据的过程是怎样的
倒排索引是怎么实现的
线上有没有遇到过问题，你印象中比较深的？jvm，es，redis



## 广州小迈-2面

springboot自动装配原理有用过吗？
为什么要自定义stater
rest controller和controller有什么区别？
resource和autowired有什么区别？querfier
redision有用过吗？
redis分布式锁，如果一个方法超时时间是10秒，但是临界区操作没有执行完，怎么续约？
怎么实现超时释放功能的？
spring事务有了解吗？transactional
事务的传播属性有了解吗？都用过哪些？
一个public方法，内部调用了一个public方法，如果内部方法出现错误，事务会回滚吗？
如果this.内部方法也会吗？
一个查询sql很慢，你会从哪些角度来优化？有什么认识？
你怎么看sql有没有走索引？
索引模糊查询，%放左边，为什么不行，放右边就行？
B+树和平衡二叉树有什么区别？
MVCC有了解吗？
select是什么读，当前读还是快照读
还有哪些是当前读？
mysql锁有哪些？
有遇到死锁吗？你是怎么解决的？
线上mysql死锁了，怎么解决？mysql自动释放，但是处于事务中会报错的
有看过死锁日志吗
线程池在实际工作中有用到过吗？说一下
用的jdk提供的还是自定义的？
主要关注哪些参数？
kafka能保持顺序性吗？同一个分区
为什么需要分区的概念？
消息积压线上遇到过吗？
消息积压怎么快速消费？
C端商品详情页查询接口怎么提升QPS？有什么措施，加节点，加配置除外？多线程，缓存
C端高并发插入接口，怎么提升？mq
一个接口要关联6.7张表，查出来得2-3秒，你怎么去优化？宽表，es，mongo
订单5分钟用户不操作，不支付，就关闭订单，这个业务场景有什么思路来实现？定时任务
线上遇到过OOM吗，怎么排查，思路有哪些？
线上频繁full gc，应该怎么做？
sql题目：一张表，学生ID，课程ID，课程分数，查询每一门成绩大于90的学生ID
group by。 having min > 90



## 其他公司

1. 怎么避免频繁 fullgc
2. 如何终止一个线程
3. tcp 三次握手
4. netty丢包
5. tomcat 打破双亲委派机制
6. 外部容器和 spring 框架的区别
7. 为什么要区分用户态和内核态
8. future.get() 原理
9. 匿名内部类
10. linux 指定一个后台守护线程，如何定期执行脚本
11. aqs 抽象类模板
12. 场景：多个服务器上需要执行一些 shell 命令，想使用一个中心进行配置，类似分布式调度系统，怎么设计
13. 场景：大集群的情况下可能会遇到一些什么问题