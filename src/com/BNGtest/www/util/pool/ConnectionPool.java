package com.BNGtest.www.util.pool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Vector;

/**
 * 数据库连接池实现类
 * @author sunhaohang
 * @date 2020/5/22
 */
public class ConnectionPool {

    //连接池的配置对象
    private PoolConfig config;
    //记录连接池的连接数
    private int count;
    //连接池是否被激活
    private boolean isActive;
    //空闲连接集合
    private Vector<Connection> freeConn = new Vector<Connection>();
    //正在使用的连接集合
    private Vector<Connection> userConn = new Vector<Connection>();
    //同一个线程无论请求多少次都使用同一个连接（使用ThreadLocal确保）
    // 每一个线程都私有一个连接
    private static ThreadLocal<Connection> threadLocal = new ThreadLocal<Connection>();

    /*
    初始化连接池配置
     */
    public ConnectionPool(PoolConfig config) {
        this.config = config;
    }

    public void init() {
        //建立初始连接
        for (int i = 0; i < config.getInitConn(); i++) {
            //获取连接对象
            Connection conn;
            try {
                conn = getNewConnection();
                freeConn.add(conn);
                count++;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            //连接池激活
            isActive = true;
            }
    }

    /*
    获取新数据库连接
     */
    private synchronized Connection getNewConnection() throws SQLException {
        Connection conn = null;
        conn = DriverManager.getConnection(config.getUrl(),config.getUserName(),config.getPassword());
        return conn;
    }

    public synchronized Connection getConnection() {
        Connection conn = null;
        //当前连接总数小于配置的最大连接数才去获取
        try{
            if (count < config.getMaxActiveConn()) {
                //空闲集合中有连接数
                if (freeConn.size() > 0) {
                    //从空闲集合中取出
                    conn = freeConn.get(0);
                    //移除该连接
                    freeConn.remove(0);
                } else {
                    //拿到新连接
                    conn = getNewConnection();
                    count++;
                }
                if (isEnable(conn)) {
                    //添加到已经使用的连接
                    userConn.add(conn);
                } else {
                    count--;
                    //递归调用到可用的连接
                    conn = getConnection();
                }
            } else {
                //当达到最大连接数，只能阻塞等待
                //线程睡眠了一段时间
                wait(config.getWaitTime());
                //递归调用
                conn = getConnection();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        //将获取的conn设置到本地变量ThreadLocal
        threadLocal.set(conn);
        return conn;
    }

    public synchronized void releaseConnection(Connection conn) {
        try {
            if (isEnable(conn)) {
                //空闲连接数没有达到最大
                if (freeConn.size() < config.getMaxConn()) {
                    //放回集合
                    freeConn.add(conn);
                } else {
                    conn.close();
                }
                userConn.remove(conn);
                count--;
                threadLocal.remove();
                //放回连接池后说明有连接可用，唤醒阻塞的线程获取连接
                notifyAll();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /*
     获取当前线程的本地变量连接
     */
    public Connection getCurrentConnection() {
        return threadLocal.get();
        }

        /*
        判断该连接是否可用
         */
    private boolean isEnable(Connection conn) throws SQLException {
        if (conn == null) {
            return false;
        }
        if (conn.isClosed()) {
            return false;
        }
        return true;
    }
}

