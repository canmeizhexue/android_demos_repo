/*
 * Copyright (C) 2014 Square, Inc.
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
package okhttp3;

import java.io.IOException;
import javax.annotation.Nullable;

/**观察，修改，请求和响应，
 * Observes, modifies, and potentially short-circuits requests going out and the corresponding
 * responses coming back in. Typically interceptors add, remove, or transform headers on the request
 * or response.
 */
public interface Interceptor {

  Response intercept(Chain chain) throws IOException;
  //拦截器链条，，，里面会包含拦截器数组，
  //连接器链条会越来越短，
  interface Chain {
    Request request();
    //这个里面可能会重新构建一个短的拦截器链条，然后用上一个链条的最前面的拦截器调用下一个链条，
    Response proceed(Request request) throws IOException;

    /**
     * Returns the connection the request will be executed on. This is only available in the chains
     * of network interceptors; for application interceptors this is always null.
     */
    @Nullable Connection connection();
  }
}
