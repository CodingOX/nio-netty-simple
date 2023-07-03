package com.prestigeding.nio.reactor;

import java.io.IOException;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

/**
 * MainReactor 类是主反应堆，负责处理服务器端的事件和连接请求。
 * 当前代码永远都在1个线程上
 * 它使用一个 Selector 对象来监听事件`SelectionKey.OP_ACCEPT`，并将接收到的事件分发给子反应堆线程组进行处理。
 */
public class MainReactor implements Runnable {
    private Selector selector;
    private SubReactorThreadGroup subReactorThreadGroup;
    private static final int DEFAULT_IO_THREAD_COUNT = 4;
    private int ioThreadCount = DEFAULT_IO_THREAD_COUNT;

    /**
     * 构造函数，初始化 MainReactor 对象。
     *
     * @param channel ServerSocketChannel 对象，用于注册 ACCEPT 事件
     */
    public MainReactor(ServerSocketChannel channel) {
        try {
            selector = Selector.open();
            // 将事件类型设为 ACCEPT，并将 Channel 注册到 Selector 上
            channel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        subReactorThreadGroup = new SubReactorThreadGroup(ioThreadCount);
    }

    /**
     * 实现 Runnable 接口的 run 方法，运行主反应堆。
     */
    public void run() {
        System.out.println("MainReactor is running");
        while (!Thread.interrupted()) {
            Set<SelectionKey> ops = null;
            try {
                // 等待事件的发生，每隔 1000 毫秒轮询一次
                selector.select(1000);
                ops = selector.selectedKeys();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 处理相关事件
            for (Iterator<SelectionKey> it = ops.iterator(); it.hasNext(); ) {
                SelectionKey key = it.next();
                it.remove();
                try {
                    if (key.isAcceptable()) { // 客户端建立连接
                        System.out.println("收到客户端的连接请求。。。");
                        ServerSocketChannel serverSc = (ServerSocketChannel) key.channel();
                        SocketChannel clientChannel = serverSc.accept();
                        clientChannel.configureBlocking(false);

                        // 将连接请求转发给子反应堆线程组处理
                        subReactorThreadGroup.dispatch(clientChannel);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    System.out.println("客户端主动断开连接。。。。。。。");
                }
            }
        }
    }
}