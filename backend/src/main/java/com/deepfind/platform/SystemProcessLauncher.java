package com.deepfind.platform;

import java.io.IOException;
import java.util.List;

@FunctionalInterface
interface SystemProcessLauncher {

    void launch(List<String> command) throws IOException;
}
