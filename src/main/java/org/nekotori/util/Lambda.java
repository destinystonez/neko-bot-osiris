package org.nekotori.util;


import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 函数式编程工具类，用于优化代码回调写法
 */
@Slf4j
public class Lambda {
    /**
     * 当需要获取某些数据，但是又不关心获取数据中可能发生的异常时，可以采用此方法。
     * 如果需要对异常进行处理，建议使用Monad类
     *
     * @param func 允许抛出异常的Supplier
     * @param <R> 获取数据
     * @return 一个Optional，返回为null或出异常时，返回Optional.empty();
     */
    public static <R> Optional<R> tryGet(CheckedSupplier<R> func) {
        return tryGet(func, "invoke function error");
    }

    public static <R> Optional<R> tryGet(CheckedSupplier<R> func, String message) {
        try {
            return Optional.ofNullable(func.get());
        } catch (Exception e) {
            log.error("{}:{}", message, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 尝试从阻塞队列中获取一个元素，不会无限期阻塞
     *
     * @param <R> 队列元素的类型
     * @param queue 要从中获取元素的阻塞队列
     * @return 包含获取到的元素的Optional对象，如果超时或发生异常则返回空Optional
     */
    public static <R> Optional<R> tryPollOne(BlockingQueue<R> queue) {
        // 使用Lambda.tryGet包装可能抛出异常的操作，设置50毫秒的超时时间尝试获取队列元素
        return Lambda.tryGet(()-> queue.poll(50, TimeUnit.MILLISECONDS));
    }


    /**
     * 尝试执行一个可能抛出异常的可运行任务
     *
     * @param runnable 需要执行的可运行任务，该任务可能抛出受检异常
     */
    public static void tryRun(CheckedRunnable runnable) {
        tryRun(runnable, "invoke function error");
    }


    public static void tryRun(CheckedRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.error("{}:{}", message, e.getMessage());
        }
    }

    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    public interface CheckedRunnable {
        void run() throws Exception;
    }
}