package com.alibaba.otter.canal.server.netty;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.apache.commons.lang.StringUtils;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import com.alibaba.otter.canal.common.AbstractCanalLifeCycle;
import com.alibaba.otter.canal.server.CanalServer;
import com.alibaba.otter.canal.server.embeded.CanalServerWithEmbeded;
import com.alibaba.otter.canal.server.netty.handler.ClientAuthenticationHandler;
import com.alibaba.otter.canal.server.netty.handler.FixedHeaderFrameDecoder;
import com.alibaba.otter.canal.server.netty.handler.HandshakeInitializationHandler;
import com.alibaba.otter.canal.server.netty.handler.SessionHandler;

/**
 * 基于netty网络服务的server实现
 * 
 * @author jianghang 2012-7-12 下午01:34:49
 * @version 1.0.0
 */
public class CanalServerWithNetty extends AbstractCanalLifeCycle implements CanalServer {

    private CanalServerWithEmbeded embededServer;                // 嵌入式server
    private String                 ip;
    private int                    port;
    private Channel                serverChannel          = null;
    private ServerBootstrap        bootstrap              = null;
    private boolean                stopInstanceAsPossible = true; // 针对client close时是否尽可能释放instance

    public CanalServerWithNetty(){
    }

    public CanalServerWithNetty(CanalServerWithEmbeded embededServer){
        this.embededServer = embededServer;
    }

    public void start() {
        super.start();

        if (!embededServer.isStart()) {
            embededServer.start();
        }

        this.bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                                                                               Executors.newCachedThreadPool()));

        // 构造对应的pipeline
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipelines = Channels.pipeline();
                pipelines.addLast(FixedHeaderFrameDecoder.class.getName(), new FixedHeaderFrameDecoder());
                pipelines.addLast(HandshakeInitializationHandler.class.getName(), new HandshakeInitializationHandler());
                pipelines.addLast(ClientAuthenticationHandler.class.getName(),
                                  new ClientAuthenticationHandler(embededServer));

                SessionHandler sessionHandler = new SessionHandler(embededServer);
                sessionHandler.setStopInstanceAsPossible(stopInstanceAsPossible);
                pipelines.addLast(SessionHandler.class.getName(), sessionHandler);
                return pipelines;
            }
        });

        // 启动
        if (StringUtils.isNotEmpty(ip)) {
            this.serverChannel = bootstrap.bind(new InetSocketAddress(this.ip, this.port));
        } else {
            this.serverChannel = bootstrap.bind(new InetSocketAddress(this.port));
        }
    }

    public void stop() {
        super.stop();

        if (this.serverChannel != null) {
            this.serverChannel.close().awaitUninterruptibly(1000);
        }

        if (this.bootstrap != null) {
            this.bootstrap.releaseExternalResources();
        }

        if (embededServer.isStart()) {
            embededServer.stop();
        }
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isStopInstanceAsPossible() {
        return stopInstanceAsPossible;
    }

    public void setStopInstanceAsPossible(boolean stopInstanceAsPossible) {
        this.stopInstanceAsPossible = stopInstanceAsPossible;
    }

    public void setEmbededServer(CanalServerWithEmbeded embededServer) {
        this.embededServer = embededServer;
    }

}
