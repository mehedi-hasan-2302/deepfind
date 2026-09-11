package com.deepfind;

import com.deepfind.desktop.DesktopParentConnection;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DeepFindBackendApplication {

    public static void main(String[] args) {
        boolean desktop = Arrays.asList(args).contains("--deepfind.desktop=true");
        var parent = desktop ? new DesktopParentConnection() : null;
        if (parent != null) {
            parent.watch(System.in);
        }
        var application = SpringApplication.run(DeepFindBackendApplication.class, args);
        if (parent != null) {
            int port = application.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            parent.ready(application, port, System.out);
        }
    }
}
