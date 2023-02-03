package thread;

import java.util.concurrent.locks.LockSupport;

/**
 * 两个线程交替打印字符串
 *
 * @author zhangjie
 */
public class LockSupportTest {

  private static Thread thread1 = null;
  private static Thread thread2 = null;

  public static void main(String[] args) {
    char[] charArray1 = "123456789".toCharArray();
    char[] charArray2 = "ABCDEFGHI".toCharArray();
    thread1 = new Thread(() -> {
      for (char c : charArray1) {
        System.out.println(c);
        try {
          System.out.println("休眠");
          Thread.sleep(5000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        LockSupport.unpark(thread2);
        LockSupport.park();
      }
      LockSupport.unpark(thread2);
    });

    thread2 = new Thread(() -> {
      for (char c : charArray2) {
        LockSupport.park();
        System.out.println(c);
        LockSupport.unpark(thread1);
      }
      LockSupport.unpark(thread1);
    });
    thread1.setName("algo-thread-1");
    thread2.setName("algo-thread-2");
    thread1.start();
    thread2.start();
  }

}
