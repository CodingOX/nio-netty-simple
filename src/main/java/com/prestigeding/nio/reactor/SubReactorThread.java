package com.prestigeding.nio.reactor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

public class SubReactorThread extends Thread {
    private Selector selector;
    private ExecutorService businessExecutorPool;
    private List<NioTask> taskList = new ArrayList<NioTask>(512);
    private ReentrantLock taskMainLock = new ReentrantLock();

    /**
     * 业务线程池
     *
     * @param businessExecutorPool 业务线程池对象
     */
    public SubReactorThread(ExecutorService businessExecutorPool) {
        try {
            this.businessExecutorPool = businessExecutorPool;
            this.selector = Selector.open();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * 注册 NioTask 任务到任务列表
     *
     * @param task NioTask 任务对象
     */
    public void register(NioTask task) {
        if (task != null) {
            try {
                taskMainLock.lock();
                taskList.add(task);
            } finally {
                taskMainLock.unlock();
            }
        }
    }

    /**
     * 将请求分发到业务线程池进行处理
     *
     * @param sc        SocketChannel 对象
     * @param reqBuffer 请求的数据缓冲区
     */
    private void dispatch(SocketChannel sc, ByteBuffer reqBuffer) {
        businessExecutorPool.submit(new BusHandler(sc, reqBuffer, this));
    }


    public void run() {
        while (!Thread.interrupted()) {
            Set<SelectionKey> ops = null;
            try {
                selector.select(1000);
                ops = selector.selectedKeys();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                continue;
            }
            // 处理相关事件 #不要阻塞
            for (Iterator<SelectionKey> it = ops.iterator(); it.hasNext(); ) {
                SelectionKey key = it.next();
                it.remove();
                try {
                    if (key.isWritable()) { // 向客户端发送请求
                        SocketChannel clientChannel = (SocketChannel) key.channel();
                        ByteBuffer buf = (ByteBuffer) key.attachment();
                        clientChannel.write(buf); // 发送数据给客户端
                        System.out.println("服务端向客户端发送数据。。。");
                        // 重新注册客户端的读事件
                        clientChannel.register(selector, SelectionKey.OP_READ);
                    } else if (key.isReadable()) { // 接受客户端请求，处理完成后成为可写状态
                        System.out.println("服务端接收客户端连接请求。。。");
                        SocketChannel clientChannel = (SocketChannel) key.channel();
                        ByteBuffer buf = ByteBuffer.allocate(1024);
                        System.out.println(buf.capacity());
                        /**
                         * 这里其实实现的非常不优雅，需要对读取处理办关闭，而且一次读取，
                         * 并不一定能将一个请求读取
                         * 一个请求，也不要会刚好读取到一个完整对请求，
                         * 这里其实是需要编码，解码，也就是通信协议  @todo
                         * 这里如何做，大家可以思考一下，后面我们可以体验netty是否如何优雅处理的。
                         */
                        int rc = clientChannel.read(buf); // 解析请求完毕
                        // 转发请求到具体的业务线程；当然，这里其实可以向dubbo那样，支持转发策略，
                        // 如果执行时间短，比如没有数据库操作等，可以在io线程中执行。本实例，转发到业务线程池
                        dispatch(clientChannel, buf);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    System.out.println("客户端主动断开连接。。。。。。。");
                }
            }

            // 注册事件write
            if (!taskList.isEmpty()) {
                try {
                    taskMainLock.lock();
                    for (Iterator<NioTask> it = taskList.iterator(); it.hasNext(); ) {
                        NioTask task = it.next();
                        try {
                            SocketChannel sc = task.getSc();
                            if (task.getData() != null) { // 向客户端发送数据
                                ByteBuffer byteBuffer = (ByteBuffer) task.getData();
                                byteBuffer.flip();
                                int wc = sc.write(byteBuffer);
                                System.out.println("服务端向客户端发送数据。。。");
                                if (wc < 1 && byteBuffer.hasRemaining()) { // 说明写缓冲区已满，需要注册写事件
                                    sc.register(selector, task.getOp(), task.getData());
                                    continue;
                                }
                                byteBuffer = null; // 释放内存
                            } else {
                                sc.register(selector, task.getOp());
                            }
                        } catch (Throwable e) {
                            e.printStackTrace(); // 忽略异常
                        }
                        it.remove();
                    }

                } finally {
                    taskMainLock.unlock();
                }
            }

        }
    }

}
