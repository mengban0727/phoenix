package thread;

import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * @author zhangjie
 */
public class AtomicStampedReferenceDemo {

  public static void main(String[] args) {
    // 实例化、取当前值和 stamp 值
    final Integer initialRef = 0, initialStamp = 0;
    final AtomicStampedReference<Integer> asr = new AtomicStampedReference<>(initialRef,
        initialStamp);
    System.out.println("currentValue=" + asr.getReference() + ", currentStamp=" + asr.getStamp());

    // compare and set
    final Integer newReference = 666, newStamp = 999;
    final boolean casResult = asr.compareAndSet(initialRef, newReference, initialStamp, newStamp);
    System.out.println("currentValue=" + asr.getReference()
        + ", currentStamp=" + asr.getStamp()
        + ", casResult=" + casResult);

    // 获取当前的值和当前的 stamp 值
    int[] arr = new int[1];
    final Integer currentValue = asr.get(arr);
    final int currentStamp = arr[0];
    System.out.println("currentValue=" + currentValue + ", currentStamp=" + currentStamp);

    // 单独设置 stamp 值
    final boolean attemptStampResult = asr.attemptStamp(newReference, 88);
    System.out.println("currentValue=" + asr.getReference()
        + ", currentStamp=" + asr.getStamp()
        + ", attemptStampResult=" + attemptStampResult);

    // 重新设置当前值和 stamp 值
    asr.set(initialRef, initialStamp);
    System.out.println("currentValue=" + asr.getReference() + ", currentStamp=" + asr.getStamp());

    // [不推荐使用，除非搞清楚注释的意思了] weak compare and set
    // 困惑！weakCompareAndSet 这个方法最终还是调用 compareAndSet 方法。[版本: jdk-8u191]
    // 但是注释上写着 "May fail spuriously and does not provide ordering guarantees,
    // so is only rarely an appropriate alternative to compareAndSet."
    // todo 感觉有可能是 jvm 通过方法名在 native 方法里面做了转发
    final boolean wCasResult = asr
        .weakCompareAndSet(initialRef, newReference, initialStamp, newStamp);
    System.out.println("currentValue=" + asr.getReference()
        + ", currentStamp=" + asr.getStamp()
        + ", wCasResult=" + wCasResult);
  }
}
