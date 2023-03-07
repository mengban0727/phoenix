package thread;

/**
 * @author zhangjie
 */
public class FooBar1 {

  private static boolean flag =false;

  public static void main(String[] args) {

    Thread thread1 = new Thread(() -> {
      String s1 = "123456";
      for (int i = 0; i < s1.length(); i++) {
        synchronized (FooBar1.class) {
          while (!flag) {
            try {
              FooBar1.class.wait();
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
          flag = false;
          System.out.println(s1.charAt(i));
          FooBar1.class.notify();
        }
      }

    });

    Thread thread2 = new Thread(() -> {
      String s2 = "abcdef";
      for (int i = 0; i < s2.length(); i++) {
        synchronized (FooBar1.class) {
          while (flag) {
            try {
              FooBar1.class.wait();
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
          flag = true;
          System.out.println(s2.charAt(i));
          FooBar1.class.notify();

        }
      }

    });

    thread1.start();
    thread2.start();


  }

}
