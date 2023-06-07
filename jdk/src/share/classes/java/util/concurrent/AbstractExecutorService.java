/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.*;

/**
 * Provides default implementations of {@link ExecutorService}
 * execution methods. This class implements the {@code submit},
 * {@code invokeAny} and {@code invokeAll} methods using a
 * {@link RunnableFuture} returned by {@code newTaskFor}, which defaults
 * to the {@link FutureTask} class provided in this package.  For example,
 * the implementation of {@code submit(Runnable)} creates an
 * associated {@code RunnableFuture} that is executed and
 * returned. Subclasses may override the {@code newTaskFor} methods
 * to return {@code RunnableFuture} implementations other than
 * {@code FutureTask}.
 *
 * <p><b>Extension example</b>. Here is a sketch of a class
 * that customizes {@link ThreadPoolExecutor} to use
 * a {@code CustomTask} class instead of the default {@code FutureTask}:
 *  <pre> {@code
 * public class CustomThreadPoolExecutor extends ThreadPoolExecutor {
 *
 *   static class CustomTask<V> implements RunnableFuture<V> {...}
 *
 *   protected <V> RunnableFuture<V> newTaskFor(Callable<V> c) {
 *       return new CustomTask<V>(c);
 *   }
 *   protected <V> RunnableFuture<V> newTaskFor(Runnable r, V v) {
 *       return new CustomTask<V>(r, v);
 *   }
 *   // ... add constructors, etc.
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 *
 * @tips 派生自ExecutorService接口，实现了几个非常实现的方法，供子类进行调用。
 */
public abstract class AbstractExecutorService implements ExecutorService {

    /**
     * Returns a {@code RunnableFuture} for the given runnable and default
     * value.
     *
     * @param runnable the runnable task being wrapped
     * @param value the default value for the returned future
     * @return a {@code RunnableFuture} which, when run, will run the
     * underlying runnable and which, as a {@code Future}, will yield
     * the given value as its result and provide for cancellation of
     * the underlying task
     * @since 1.6
     *
     * @tips RunnableFuture类用于获取执行结果，在实际使用时，我们经常使用的是它的子类FutureTask，newTaskFor方法的作用就是将
     * 任务封装成FutureTask对象，后续将FutureTask对象提交到线程池。
     */
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new FutureTask<T>(runnable, value);
    }

    /**
     * Returns a {@code RunnableFuture} for the given callable task.
     *
     * @param callable the callable task being wrapped
     * @return a {@code RunnableFuture} which, when run, will call the
     * underlying callable and which, as a {@code Future}, will yield
     * the callable's result as its result and provide for
     * cancellation of the underlying task
     * @since 1.6
     */
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new FutureTask<T>(callable);
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     *
     * @tips submit方法的逻辑比较简单，就是将任务封装成RunnableFuture对象并提交，执行任务后返回Future结果数据。
     */
    public Future<?> submit(Runnable task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<Void> ftask = newTaskFor(task, null);
        // 本质上还是调用的Executor接口的execute方法
        execute(ftask);
        return ftask;
    }

    /**
     * 在非定时任务类的线程池中提交任务时，本质上都是调用的Executor接口的execute方法。
     */

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task, result);
        execute(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task);
        execute(ftask);
        return ftask;
    }

    /**
     * the main mechanics of invokeAny.
     */
    private <T> T doInvokeAny(Collection<? extends Callable<T>> tasks,
                              boolean timed, long nanos)
        throws InterruptedException, ExecutionException, TimeoutException {
        //提交的任务为空，抛出空指针异常
        if (tasks == null)
            throw new NullPointerException();
        //记录待执行的任务的剩余数量
        int ntasks = tasks.size();
        //任务集合中的数据为空，抛出非法参数异常
        if (ntasks == 0)
            throw new IllegalArgumentException();
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(ntasks);
        //以当前实例对象作为参数构建ExecutorCompletionService对象
        // ExecutorCompletionService负责执行任务，后面调用用poll返回第一个执行结果
        ExecutorCompletionService<T> ecs =
            new ExecutorCompletionService<T>(this);

        // For efficiency, especially in executors with limited
        // parallelism, check to see if previously submitted tasks are
        // done before submitting more of them. This interleaving
        // plus the exception mechanics account for messiness of main
        // loop.

        try {
            // Record exceptions so that if we fail to obtain any
            // result, we can throw the last exception we got.
            // 记录可能抛出的执行异常
            ExecutionException ee = null;
            // 初始化超时时间
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Iterator<? extends Callable<T>> it = tasks.iterator();

            // Start one task for sure; the rest incrementally
            //提交任务，并将返回的结果数据添加到futures集合中
            //提交一个任务主要是确保在进入循环之前开始一个任务
            futures.add(ecs.submit(it.next()));
            --ntasks;
            //记录正在执行的任务数量
            int active = 1;

            for (;;) {
                //从完成任务的BlockingQueue队列中获取并移除下一个将要完成的任务的结果。
                //如果BlockingQueue队列中中的数据为空，则返回null
                //这里的poll()方法是非阻塞方法
                Future<T> f = ecs.poll();
                //获取的结果为空
                if (f == null) {
                    //集合中仍有未执行的任务数量
                    if (ntasks > 0) {
                        //未执行的任务数量减1
                        --ntasks;
                        //提交完成并将结果添加到futures集合中
                        futures.add(ecs.submit(it.next()));
                        //正在执行的任务数量加•1
                        ++active;
                    }
                    //所有任务执行完成，并且返回了结果数据，则退出循环
                    //之所以处理active为0的情况，是因为poll()方法是非阻塞方法，可能导致未返回结果时active为0
                    else if (active == 0)
                        break;
                    //如果timed为true，则执行获取结果数据时设置超时时间，也就是超时获取结果表示
                    else if (timed) {
                        f = ecs.poll(nanos, TimeUnit.NANOSECONDS);
                        if (f == null)
                            throw new TimeoutException();
                        nanos = deadline - System.nanoTime();
                    }
                    //没有设置超时，并且所有任务都被提交了，则一直阻塞，直到返回一个执行结果
                    else
                        f = ecs.take();
                }
                //获取到执行结果，则将正在执行的任务减1，从Future中获取结果并返回
                if (f != null) {
                    --active;
                    try {
                        return f.get();
                    } catch (ExecutionException eex) {
                        ee = eex;
                    } catch (RuntimeException rex) {
                        ee = new ExecutionException(rex);
                    }
                }
            }

            if (ee == null)
                ee = new ExecutionException();
            throw ee;

        } finally {
            //如果从所有执行的任务中获取到一个结果数据，则取消所有执行的任务，不再向下执行
            for (int i = 0, size = futures.size(); i < size; i++)
                futures.get(i).cancel(true);
        }
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
        try {
            return doInvokeAny(tasks, false, 0);
        } catch (TimeoutException cannotHappen) {
            assert false;
            return null;
        }
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        return doInvokeAny(tasks, true, unit.toNanos(timeout));
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        //标识所有任务是否完成
        boolean done = false;
        try {
            //遍历所有任务
            for (Callable<T> t : tasks) {
                // 将每个任务封装成RunnableFuture对象提交任务
                RunnableFuture<T> f = newTaskFor(t);
                //将结果数据添加到futures集合中
                futures.add(f);
                //执行任务
                // invokeAll方法中本质上还是调用Executor接口的execute方法来提交任务。
                execute(f);
            }
            //遍历结果数据集合
            for (int i = 0, size = futures.size(); i < size; i++) {
                Future<T> f = futures.get(i);
                //任务没有完成
                if (!f.isDone()) {
                    try {
                        //阻塞等待任务完成并返回结果
                        f.get();
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    }
                }
            }
            //任务完成（不管是正常结束还是异常完成）
            done = true;
            //返回结果数据集合
            return futures;
        } finally {
            //如果发生中断异常InterruptedException 则取消已经提交的任务
            if (!done)
                for (int i = 0, size = futures.size(); i < size; i++)
                    futures.get(i).cancel(true);
        }
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            for (Callable<T> t : tasks)
                futures.add(newTaskFor(t));

            final long deadline = System.nanoTime() + nanos;
            final int size = futures.size();

            // Interleave time checks and calls to execute in case
            // executor doesn't have any/much parallelism.
            for (int i = 0; i < size; i++) {
                // invokeAll方法中本质上还是调用Executor接口的execute方法来提交任务。
                execute((Runnable)futures.get(i));
                // 在添加执行任务时超时判断，如果超时则立刻返回futures集合
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L)
                    return futures;
            }

            // 遍历所有任务
            for (int i = 0; i < size; i++) {
                Future<T> f = futures.get(i);
                if (!f.isDone()) {
                    //对结果进行判断时进行超时判断
                    if (nanos <= 0L)
                        return futures;
                    try {
                        f.get(nanos, TimeUnit.NANOSECONDS);
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    } catch (TimeoutException toe) {
                        return futures;
                    }
                    //重置任务的超时时间
                    nanos = deadline - System.nanoTime();
                }
            }
            done = true;
            return futures;
        } finally {
            if (!done)
                for (int i = 0, size = futures.size(); i < size; i++)
                    futures.get(i).cancel(true);
        }
    }

}
