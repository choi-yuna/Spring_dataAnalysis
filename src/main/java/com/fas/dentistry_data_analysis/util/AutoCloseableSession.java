package com.fas.dentistry_data_analysis.util;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;

public class AutoCloseableSession implements AutoCloseable {
    private final Session session;

    public AutoCloseableSession(Session session) {
        this.session = session;
    }

    @Override
    public void close() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}

