### Server 部分（负责人：胡奕博 241250035）

主要负责整个 HTTP 服务端的基础框架，包括：

1. HttpServer.java —— 服务器入口

    创建 ServerSocket，监听指定端口

    循环接受来自客户端的连接

    为每个连接创建一个 ClientHandler 线程进行处理

    在服务器启动时打印启动信息

    支持关闭服务器（可通过 Ctrl+C 或后续扩展）

    目前实现：

    * 端口监听正常

    * 接收到连接后，会把 Socket 交给 ClientHandler

    * Server 本身不解析 HTTP，只负责调度

    * 后续同学可基于此添加：

    * 日志输出

    * 线程池（替换直接 new Thread 的方式）

2. ClientHandler.java —— 请求处理核心

    负责：

    从 Socket 中读取客户端发送的 HTTP 报文

    调用 HttpRequestParser（下一位同学负责）解析报文

    调用 Router（业务同学负责）找到对应接口

    将业务得到的响应封装为 HttpResponse 并写回给客户端

    支持短连接 / 长连接（通过解析 Connection 头判断）

    目前实现：

   * 已能准确接收原始 HTTP 报文（请求行 + 头部 + body）

   * 已能把报文传给解析模块（暂时留空）

   * 已写好向客户端返回原始字符串的基础结构



后续需要补的：

>在 HttpRequestParser.parse() 中解析出 method / path / headers / body
> 
>在 Router.route(request) 中处理 GET/POST 路径逻辑
> 
>在 HttpResponse 中生成完整 HTTP 响应报文

3. 已完成的接口
    ```
    HttpRequest request = HttpRequestParser.parse(inputStream);
    HttpResponse response = Router.route(request);
    writeResponse(outputStream, response);
    ```

    只需要按照这三个步骤填充功能即可，不需要修改服务器主框架。

4. 注意事项

    解析模块不要在 HttpServer 或 ClientHandler 中写！
    遵循模块分离，不要耦合逻辑。

    请求必须按照 HTTP/1.1 标准解析，否则长连接无法正常工作。

    GET 请求没有 body，POST 需要根据 Content-Length 读取 body。

    返回响应时必须严格按照以下格式写：
    ```
    HTTP/1.1 200 OK
    Content-Type: text/plain
    Content-Length: 5

    hello
    ```

    保持模块之间只通过 request/response 对象通信，不要直接访问 socket。