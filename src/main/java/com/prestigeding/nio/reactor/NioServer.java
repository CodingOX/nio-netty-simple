package com.prestigeding.nio.reactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NioServer 类是一个基于 NIO（Non-blocking IO）的服务器类。
 * 它提供了处理客户端连接请求的功能，并将请求转发到主反应堆线程池进行处理。
 */
public class NioServer {

    // 服务器端口号
    private static final int SERVER_PORT = 9080;

    /**
     * 主程序入口，启动服务器。
     */
    public static void main(String[] args) {
        // 创建一个新线程，并将 Acceptor 对象作为任务提交给线程执行
        (new Thread(new Acceptor())).start();
    }

    /**
     * Acceptor 是一个内部静态类，实现了 Runnable 接口。
     * 它负责处理客户端的连接请求，并将请求分发到主反应堆线程池进行处理。
     */
    private static class Acceptor implements Runnable {

        // 主反应堆线程池，用于处理客户端的连接请求
        private static ExecutorService mainReactor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private AtomicInteger num = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("main-reactor-" + num.incrementAndGet());
                return t;
            }
        });

        /**
         * 实现 Runnable 接口的 run 方法，处理客户端连接请求。
         */
        public void run() {
            ServerSocketChannel ssc = null;
            try {
                // 打开 ServerSocketChannel，并配置为非阻塞模式
                ssc = ServerSocketChannel.open();
                ssc.configureBlocking(false);
                ssc.bind(new InetSocketAddress(SERVER_PORT));

                // 将连接请求转发到主反应堆
                dispatch(ssc);

                System.out.println("服务端成功启动。。。。。。");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * 将 ServerSocketChannel 对象的连接请求提交给主反应堆进行处理。
         *
         * @param ssc ServerSocketChannel 对象
         */
        private void dispatch(ServerSocketChannel ssc) {
            mainReactor.submit(new MainReactor(ssc));
        }

    }

}