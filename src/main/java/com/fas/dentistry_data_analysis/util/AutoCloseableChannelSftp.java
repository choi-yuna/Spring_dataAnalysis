package com.fas.dentistry_data_analysis.util;

import com.jcraft.jsch.ChannelSftp;

public class AutoCloseableChannelSftp implements AutoCloseable {
    private final ChannelSftp channelSftp;

    public AutoCloseableChannelSftp(ChannelSftp channelSftp) {
        this.channelSftp = channelSftp;
    }

    @Override
    public void close() {
        if (channelSftp != null && channelSftp.isConnected()) {
            channelSftp.disconnect();
        }
    }
}
