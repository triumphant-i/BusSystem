// 文件路径: main/java/com/example/bussystem/BusSystemApplication.java

package com.example.bussystem;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
@SpringBootApplication
public class BusSystemApplication {

    public static void main(String[] args) throws UnknownHostException {
        SpringApplication app = new SpringApplication(BusSystemApplication.class);
        Environment env = app.run(args).getEnvironment();

        String ip = InetAddress.getLocalHost().getHostAddress();
        String port = env.getProperty("server.port", "8080");
        String path = env.getProperty("server.servlet.context-path", "");

        System.out.println("\n----------------------------------------------------------");
        System.out.println("\t公交系统启动成功!");
        System.out.println("\t后端 API 文档: \thttp://localhost:" + port + path + "/doc.html");
        // 这里假设你的前端 Vue 运行在 5173 (Vite默认) 或 8080
        System.out.println("\t前端管理员入口: \thttp://localhost:5173/admin (请确认Vue端口)");
        System.out.println("----------------------------------------------------------\n");
    }
}