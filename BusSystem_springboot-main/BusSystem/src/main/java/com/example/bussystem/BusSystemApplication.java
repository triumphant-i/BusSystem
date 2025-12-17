package com.example.bussystem;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j // 如果报错，确保你pom里有lombok，或者把log.info改成System.out.println
@SpringBootApplication
public class BusSystemApplication {

    public static void main(String[] args) throws UnknownHostException {
        // 1. 获取运行上下文
        ConfigurableApplicationContext application = SpringApplication.run(BusSystemApplication.class, args);

        // 2. 获取环境变量（为了获取端口号）
        Environment env = application.getEnvironment();
        String ip = InetAddress.getLocalHost().getHostAddress();
        String port = env.getProperty("server.port", "8080"); // 默认8080
        String path = env.getProperty("server.servlet.context-path", "");

        // 3. 在控制台打印超大的 Swagger 地址
        System.out.println("\n----------------------------------------------------------");
        System.out.println("\t后端项目启动成功! (API文档地址如下):");
        System.out.println("\tLocal: \t\thttp://localhost:" + port + path + "/doc.html");
        System.out.println("\tNetwork: \thttp://" + ip + ":" + port + path + "/doc.html");
        System.out.println("----------------------------------------------------------\n");
    }
}