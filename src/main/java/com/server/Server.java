package com.server;

import java.io.IOException;

interface Server {
    void run() throws Exception;

    void stop() throws Exception;
}
