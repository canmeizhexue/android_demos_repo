/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3.internal.connection;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.Socket;
import okhttp3.Address;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Route;
import okhttp3.internal.Internal;
import okhttp3.internal.Util;
import okhttp3.internal.http.HttpCodec;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;

import static okhttp3.internal.Util.closeQuietly;

/**这个类协调三个实体之间的关系，每一个请求都会有一个全新的StreamAllocation对象，
 * This class coordinates the relationship between three entities:
 *
 *
 * <ul>
 *     链接  与远端服务器之间的物理套接字链接 可能会建立很慢，所以要支持取消
 *     <li><strong>Connections:</strong> physical socket connections to remote servers. These are
 *         potentially slow to establish so it is necessary to be able to cancel a connection
 *         currently being connected.
 *         流   基于链接之上的逻辑http请求响应对  ， 每个链接有分配限制，定义了链接可以支持的并发流数量，
 *         http1的链接一次只能携带一对流，但是http2的链接一次可以携带多对流
 *     <li><strong>Streams:</strong> logical HTTP request/response pairs that are layered on
 *         connections. Each connection has its own allocation limit, which defines how many
 *         concurrent streams that connection can carry. HTTP/1.x connections can carry 1 stream
 *         at a time, HTTP/2 typically carry multiple.
 *
 *         调用，流的逻辑序列，通常是初始请求和后续请求，我们更希望一个请求的所有流都是在同一个链接上进行的，
 *     <li><strong>Calls:</strong> a logical sequence of streams, typically an initial request and
 *         its follow up requests. We prefer to keep all streams of a single call on the same
 *         connection for better behavior and locality.
 * </ul>
 *
 * 这个类的实例代表调用Call,可以使用基于多个链接的多个流，
 * <p>Instances of this class act on behalf of the call, using one or more streams over one or more
 * connections. This class has APIs to release each of the above resources:
 *
 * <ul>
 *     noNewStreams阻止后续因为新流而使用这个链接，，
 *     <li>{@link #noNewStreams()} prevents the connection from being used for new streams in the
 *         future. Use this after a {@code Connection: close} header, or when the connection may be
 *         inconsistent.
 *     <li>{@link #streamFinished streamFinished()} releases the active stream from this allocation.
 *         Note that only one stream may be active at a given time, so it is necessary to call
 *         {@link #streamFinished streamFinished()} before creating a subsequent stream with {@link
 *         #newStream newStream()}.
 *     <li>{@link #release()} removes the call's hold on the connection. Note that this won't
 *         immediately free the connection if there is a stream still lingering. That happens when a
 *         call is complete but its response body has yet to be fully consumed.
 * </ul>
 *
 * 这个类支持异步取消，如果一个http2的流是活动状态，取消操作只会针对这个当前的流，而不会影响到同一个链接上的其他流
 * 但是如果还在TLS握手阶段，那么会影响到整个链接
 * <p>This class supports {@linkplain #cancel asynchronous canceling}. This is intended to have the
 * smallest blast radius possible. If an HTTP/2 stream is active, canceling will cancel that stream
 * but not the other streams sharing its connection. But if the TLS handshake is still in progress
 * then canceling may break the entire connection.
 */
public final class StreamAllocation {
  public final Address address;
  private Route route;
  private final ConnectionPool connectionPool;
  private final Object callStackTrace;

  // State guarded by connectionPool.
  private final RouteSelector routeSelector;
  private int refusedStreamCount;
  private RealConnection connection;
  private boolean released;
  private boolean canceled;
  private HttpCodec codec;

  public StreamAllocation(ConnectionPool connectionPool, Address address, Object callStackTrace) {
    this.connectionPool = connectionPool;
    this.address = address;
    //构建了一个路由选择器，
    this.routeSelector = new RouteSelector(address, routeDatabase());
    this.callStackTrace = callStackTrace;
  }

  public HttpCodec newStream(OkHttpClient client, boolean doExtensiveHealthChecks) {
    int connectTimeout = client.connectTimeoutMillis();//链接超时，
    int readTimeout = client.readTimeoutMillis();//读超时，
    int writeTimeout = client.writeTimeoutMillis();//写超时，
    //链接失败的时候
    boolean connectionRetryEnabled = client.retryOnConnectionFailure();

    try {
      //找到一个链接，
      RealConnection resultConnection = findHealthyConnection(connectTimeout, readTimeout,
          writeTimeout, connectionRetryEnabled, doExtensiveHealthChecks);
      //
      HttpCodec resultCodec = resultConnection.newCodec(client, this);

      synchronized (connectionPool) {
        codec = resultCodec;
        return resultCodec;
      }
    } catch (IOException e) {
      throw new RouteException(e);
    }
  }

  /**寻找一个链接，，，里面有循环，，
   * Finds a connection and returns it if it is healthy. If it is unhealthy the process is repeated
   * until a healthy connection is found.
   */
  private RealConnection findHealthyConnection(int connectTimeout, int readTimeout,
      int writeTimeout, boolean connectionRetryEnabled, boolean doExtensiveHealthChecks)
      throws IOException {
    while (true) {

      RealConnection candidate = findConnection(connectTimeout, readTimeout, writeTimeout,
          connectionRetryEnabled);
      //如果这是一个全新的链接，我们可以跳过后续的健康检查
      // If this is a brand new connection, we can skip the extensive health checks.
      synchronized (connectionPool) {
        if (candidate.successCount == 0) {
          return candidate;
        }
      }

      // Do a (potentially slow) check to confirm that the pooled connection is still good. If it
      // isn't, take it out of the pool and start again.
      if (!candidate.isHealthy(doExtensiveHealthChecks)) {
        noNewStreams();
        continue;
      }

      return candidate;
    }
  }

  /**找到一个链接，
   * Returns a connection to host a new stream. This prefers the existing connection if it exists,
   * then the pool, finally building a new connection.
   */
  private RealConnection findConnection(int connectTimeout, int readTimeout, int writeTimeout,
      boolean connectionRetryEnabled) throws IOException {
    Route selectedRoute;
    synchronized (connectionPool) {
      if (released) throw new IllegalStateException("released");
      if (codec != null) throw new IllegalStateException("codec != null");
      if (canceled) throw new IOException("Canceled");

      // Attempt to use an already-allocated connection.
      RealConnection allocatedConnection = this.connection;
      if (allocatedConnection != null && !allocatedConnection.noNewStreams) {
        return allocatedConnection;
      }

      // Attempt to get a connection from the pool.
      //最后一个参数是null,这是去找一个可以重用的链接，，第一次应该是找不到的
      Internal.instance.get(connectionPool, address, this, null);
      if (connection != null) {
        return connection;
      }

      selectedRoute = route;
    }

    // If we need a route, make one. This is a blocking operation.
    if (selectedRoute == null) {
      selectedRoute = routeSelector.next();
    }

    RealConnection result;
    synchronized (connectionPool) {
      if (canceled) throw new IOException("Canceled");
      //现在我们有了ip地址，
      // Now that we have an IP address, make another attempt at getting a connection from the pool.
      // This could match due to connection coalescing.
      Internal.instance.get(connectionPool, address, this, selectedRoute);
      if (connection != null) {
        route = selectedRoute;
        return connection;
      }
      //下面就不是重用的了，重用的在上面某个分支已经返回了
      //第一次就是走的这里，上面的重用机制在第一次是用不到的，
      // Create a connection and assign it to this allocation immediately. This makes it possible
      // for an asynchronous cancel() to interrupt the handshake we're about to do.
      route = selectedRoute;
      refusedStreamCount = 0;


      //构建一个RealConnection，，这个时候这个selectedRoute肯定不为null了，，
      result = new RealConnection(connectionPool, selectedRoute);
      //将connection和StreamAllocation进行关联，
      acquire(result);
    }

    // Do TCP + TLS handshakes. This is a blocking operation.
    //进行链接，底层会创建套接字，进行握手等操作，
    result.connect(connectTimeout, readTimeout, writeTimeout, connectionRetryEnabled);

    routeDatabase().connected(result.route());

    Socket socket = null;
    synchronized (connectionPool) {
      //将链接加入连接池，，，
      // Pool the connection.
      Internal.instance.put(connectionPool, result);

      // If another multiplexed connection to the same address was created concurrently, then
      // release this connection and acquire that one.
      if (result.isMultiplexed()) {
        //如果同时创建了一个与同一个地址的多路复用的链接，
        socket = Internal.instance.deduplicate(connectionPool, address, this);
        result = connection;
      }
    }
    closeQuietly(socket);

    return result;
  }
  //指定的流结束了，
  public void streamFinished(boolean noNewStreams, HttpCodec codec) {
    Socket socket;
    synchronized (connectionPool) {
      if (codec == null || codec != this.codec) {
        throw new IllegalStateException("expected " + this.codec + " but was " + codec);
      }
      if (!noNewStreams) {
        //累加链接的成功次数，
        connection.successCount++;
      }
      socket = deallocate(noNewStreams, false, true);
    }
    closeQuietly(socket);
  }

  public HttpCodec codec() {
    synchronized (connectionPool) {
      return codec;
    }
  }

  private RouteDatabase routeDatabase() {
    return Internal.instance.routeDatabase(connectionPool);
  }

  public synchronized RealConnection connection() {
    return connection;
  }

  public void release() {
    Socket socket;
    synchronized (connectionPool) {
      socket = deallocate(false, true, false);
    }
    closeQuietly(socket);
  }

  /** 禁止在这个链接上创建新流  Forbid new streams from being created on the connection that hosts this allocation. */
  public void noNewStreams() {
    Socket socket;
    synchronized (connectionPool) {
      socket = deallocate(true, false, false);
    }
    closeQuietly(socket);
  }

  /**释放由这个分配器持有的资源，
   * Releases resources held by this allocation. If sufficient resources are allocated, the
   * connection will be detached or closed. Callers must be synchronized on the connection pool.
   *
   * <p>Returns a closeable that the caller should pass to {@link Util#closeQuietly} upon completion
   * of the synchronized block. (We don't do I/O while synchronized on the connection pool.)
   */
  private Socket deallocate(boolean noNewStreams, boolean released, boolean streamFinished) {
    //检测当前线程是否持有指定的锁
    assert (Thread.holdsLock(connectionPool));

    if (streamFinished) {
      //流结束，，，codec清空，，但是socket可以还在，connection也是可以还在的，
      this.codec = null;
    }
    if (released) {
      this.released = true;
    }
    Socket socket = null;
    if (connection != null) {
      if (noNewStreams) {

        connection.noNewStreams = true;
      }
      if (this.codec == null && (this.released || connection.noNewStreams)) {
        //释放链接，
        release(connection);
        if (connection.allocations.isEmpty()) {
          connection.idleAtNanos = System.nanoTime();
          if (Internal.instance.connectionBecameIdle(connectionPool, connection)) {
            //这个链接需要关闭，链接对应的Socket
            socket = connection.socket();
          }
        }
        connection = null;
      }
    }
    return socket;
  }

  public void cancel() {
    HttpCodec codecToCancel;
    RealConnection connectionToCancel;
    synchronized (connectionPool) {
      canceled = true;
      codecToCancel = codec;
      connectionToCancel = connection;
    }
    if (codecToCancel != null) {
      codecToCancel.cancel();
    } else if (connectionToCancel != null) {
      connectionToCancel.cancel();
    }
  }

  public void streamFailed(IOException e) {
    Socket socket;
    boolean noNewStreams = false;

    synchronized (connectionPool) {
      if (e instanceof StreamResetException) {
        StreamResetException streamResetException = (StreamResetException) e;
        if (streamResetException.errorCode == ErrorCode.REFUSED_STREAM) {
          refusedStreamCount++;
        }
        // On HTTP/2 stream errors, retry REFUSED_STREAM errors once on the same connection. All
        // other errors must be retried on a new connection.
        if (streamResetException.errorCode != ErrorCode.REFUSED_STREAM || refusedStreamCount > 1) {
          noNewStreams = true;
          route = null;
        }
      } else if (connection != null
          && (!connection.isMultiplexed() || e instanceof ConnectionShutdownException)) {
        noNewStreams = true;

        // If this route hasn't completed a call, avoid it for new connections.
        if (connection.successCount == 0) {
          if (route != null && e != null) {
            routeSelector.connectFailed(route, e);
          }
          route = null;
        }
      }
      socket = deallocate(noNewStreams, false, true);
    }

    closeQuietly(socket);
  }

  /**要和release配对使用，将指定的Connection和当前的StreamAllocation进行关联
   * Use this allocation to hold {@code connection}. Each call to this must be paired with a call to
   * {@link #release} on the same connection.
   */
  public void acquire(RealConnection connection) {
    assert (Thread.holdsLock(connectionPool));
    if (this.connection != null) throw new IllegalStateException();

    this.connection = connection;
    connection.allocations.add(new StreamAllocationReference(this, callStackTrace));
  }

  /** 难道是一个Socket对应一个Connection,,一个Connection上面可以有多个StreamAllocation?? ???
   * Remove this allocation from the connection's list of allocations. */
  private void release(RealConnection connection) {
    for (int i = 0, size = connection.allocations.size(); i < size; i++) {
      Reference<StreamAllocation> reference = connection.allocations.get(i);
      if (reference.get() == this) {
        connection.allocations.remove(i);
        return;
      }
    }
    throw new IllegalStateException();
  }

  /**
   * Release the connection held by this connection and acquire {@code newConnection} instead. It is
   * only safe to call this if the held connection is newly connected but duplicated by {@code
   * newConnection}. Typically this occurs when concurrently connecting to an HTTP/2 webserver.
   *
   * <p>Returns a closeable that the caller should pass to {@link Util#closeQuietly} upon completion
   * of the synchronized block. (We don't do I/O while synchronized on the connection pool.)
   */
  public Socket releaseAndAcquire(RealConnection newConnection) {
    //检测是否持有锁，
    assert (Thread.holdsLock(connectionPool));
    if (codec != null || connection.allocations.size() != 1) throw new IllegalStateException();

    // Release the old connection.
    Reference<StreamAllocation> onlyAllocation = connection.allocations.get(0);
    Socket socket = deallocate(true, false, false);

    // Acquire the new connection.
    this.connection = newConnection;
    newConnection.allocations.add(onlyAllocation);

    return socket;
  }

  public boolean hasMoreRoutes() {
    return route != null || routeSelector.hasNext();
  }

  @Override public String toString() {
    RealConnection connection = connection();
    return connection != null ? connection.toString() : address.toString();
  }
  //弱引用的子类，
  public static final class StreamAllocationReference extends WeakReference<StreamAllocation> {
    /**
     * Captures the stack trace at the time the Call is executed or enqueued. This is helpful for
     * identifying the origin of connection leaks.
     */
    public final Object callStackTrace;

    StreamAllocationReference(StreamAllocation referent, Object callStackTrace) {
      super(referent);
      this.callStackTrace = callStackTrace;
    }
  }
}
